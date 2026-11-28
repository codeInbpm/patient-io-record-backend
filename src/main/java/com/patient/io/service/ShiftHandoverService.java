package com.patient.io.service;

import com.alibaba.fastjson2.JSON;
import com.patient.io.common.BusinessException;
import com.patient.io.common.TenantContext;
import com.patient.io.common.UserContext;
import com.patient.io.dto.HandoverSaveDTO;
import com.patient.io.vo.ShiftHandoverVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交班工作台。
 *
 * 过去夜查房页的「护士长叮嘱」只是一个 ref，刷新即丢，交班也没有任何状态，
 * 出了问题无从追溯。这里把交班当成一张有状态的单据：
 *   DRAFT（可编辑）→ SUBMITTED（已提交，锁定）→ CONFIRMED（接班护士在小程序端签名确认）
 * PC 端只记录「谁、什么时候、哪个班次」提交；电子签名与接班确认在小程序端完成。
 */
@Slf4j
@Service
public class ShiftHandoverService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public ShiftHandoverVO getCurrent(Long wardId, String shiftDate, String shiftType) {
        LocalDate date = parseDate(shiftDate);
        String type = normalizeShiftType(shiftType);
        List<ShiftHandoverVO> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE h.ward_id = ? AND h.shift_date = ? AND h.shift_type = ? ",
                ROW, wardId, Date.valueOf(date), type);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 保存草稿。已提交/已确认的单据不允许再改内容，要改必须先撤销提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public ShiftHandoverVO save(HandoverSaveDTO dto) {
        LocalDate date = parseDate(dto.getShiftDate());
        String type = normalizeShiftType(dto.getShiftType());
        ShiftHandoverVO current = getCurrent(dto.getWardId(), date.toString(), type);

        if (current == null) {
            Long hospitalId = resolveHospitalId(dto.getWardId());
            jdbcTemplate.update(
                    "INSERT INTO shift_handover (hospital_id, ward_id, shift_date, shift_type, status, memo, report_text, create_time, update_time) " +
                            "VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)",
                    hospitalId == null ? 1L : hospitalId, dto.getWardId(), Date.valueOf(date), type,
                    nullToEmpty(dto.getMemo()), nullToEmpty(dto.getReportText()),
                    LocalDateTime.now(), LocalDateTime.now());
        } else {
            if (!"DRAFT".equals(current.getStatus())) {
                throw new BusinessException("该班次交班已提交，如需修改请先撤销提交");
            }
            jdbcTemplate.update(
                    "UPDATE shift_handover SET memo = ?, report_text = ?, update_time = ? WHERE id = ?",
                    nullToEmpty(dto.getMemo()), nullToEmpty(dto.getReportText()), LocalDateTime.now(), current.getId());
        }
        return getCurrent(dto.getWardId(), date.toString(), type);
    }

    /**
     * 提交交班：记录提交人、时间、班次，并把当时的病区统计快照存下来。
     * 之后即便患者数据变化，也能还原「交班那一刻」的情况。
     */
    @Transactional(rollbackFor = Exception.class)
    public ShiftHandoverVO submit(Long id) {
        ShiftHandoverVO vo = requireById(id);
        if (!"DRAFT".equals(vo.getStatus())) {
            throw new BusinessException("该交班已提交，无需重复提交");
        }
        if (isBlank(vo.getMemo()) && isBlank(vo.getReportText())) {
            throw new BusinessException("交班内容为空，请先填写叮嘱或交班报告");
        }

        int patientCount = count("SELECT COUNT(*) FROM patient WHERE ward_id = ? AND status = 1 AND is_deleted = 0", vo.getWardId());
        int pendingFeedback = count("SELECT COUNT(*) FROM record_correction_request WHERE ward_id = ? AND status = 'PENDING'", vo.getWardId());
        int abnormal = count("SELECT COUNT(*) FROM daily_summary WHERE ward_id = ? AND record_date = ? AND is_abnormal = 1",
                vo.getWardId(), Date.valueOf(LocalDate.now()));
        int infusing = count("SELECT COUNT(*) FROM infusion_record WHERE ward_id = ? AND status = 'INFUSING' AND is_deleted = 0", vo.getWardId());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("patientCount", patientCount);
        stats.put("pendingFeedback", pendingFeedback);
        stats.put("abnormalBalance", abnormal);
        stats.put("infusing", infusing);

        jdbcTemplate.update(
                "UPDATE shift_handover SET status = 'SUBMITTED', submit_by = ?, submit_by_name = ?, submit_time = ?, " +
                        "patient_count = ?, stats_json = ?, update_time = ? WHERE id = ? AND status = 'DRAFT'",
                UserContext.getUserId(), currentUserName(), LocalDateTime.now(),
                patientCount, JSON.toJSONString(stats), LocalDateTime.now(), id);

        log.info("交班已提交: id={}, wardId={}, by={}", id, vo.getWardId(), currentUserName());
        return requireById(id);
    }

    /** 撤销提交：接班人还没确认时，护士长可以撤回继续修改 */
    @Transactional(rollbackFor = Exception.class)
    public ShiftHandoverVO reopen(Long id) {
        ShiftHandoverVO vo = requireById(id);
        if ("CONFIRMED".equals(vo.getStatus())) {
            throw new BusinessException("接班人已确认，不能再撤销");
        }
        if (!"SUBMITTED".equals(vo.getStatus()) && !"LOCKED".equals(vo.getStatus())) {
            throw new BusinessException("当前状态无需撤销");
        }
        jdbcTemplate.update("UPDATE shift_handover SET status = 'DRAFT', update_time = ? WHERE id = ?",
                LocalDateTime.now(), id);
        return requireById(id);
    }

    /**
     * 接班护士在小程序端确认接班（记录签名）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ShiftHandoverVO receive(Long id, String receiverName, String signatureUrl) {
        ShiftHandoverVO vo = requireById(id);
        if (!"SUBMITTED".equals(vo.getStatus())) {
            throw new BusinessException("当前交班不是「已提交」状态，无法确认接班");
        }
        jdbcTemplate.update(
                "UPDATE shift_handover SET status = 'CONFIRMED', receiver_id = ?, receiver_name = ?, " +
                        "receive_time = ?, signature_url = ?, update_time = ? WHERE id = ?",
                UserContext.getUserId(),
                isBlank(receiverName) ? "接班护士" : receiverName,
                LocalDateTime.now(), signatureUrl, LocalDateTime.now(), id);
        return requireById(id);
    }

    /**
     * 小程序端接班确认：电子签名必填。
     * 必须先有签名才允许把单据置为 CONFIRMED，否则「已接班」就是一句口头承诺。
     */
    @Transactional(rollbackFor = Exception.class)
    public ShiftHandoverVO receiveWithSignature(Long id, String receiverName, String signatureUrl) {
        if (isBlank(signatureUrl)) {
            throw new BusinessException("请先手写签名，再确认接班");
        }
        ShiftHandoverVO vo = requireById(id);
        assertWardVisible(vo);
        return receive(id, receiverName, signatureUrl);
    }

    /** 护士只能看/签本人所属病区的交班；护士长与管理员不受限 */
    private void assertWardVisible(ShiftHandoverVO vo) {
        if (!"NURSE".equals(UserContext.getRole())) {
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null || vo.getWardId() == null) {
            return;
        }
        try {
            Long wardId = jdbcTemplate.queryForObject("SELECT ward_id FROM nurse WHERE id = ?", Long.class, userId);
            if (wardId != null && !wardId.equals(vo.getWardId())) {
                throw new BusinessException(403, "该交班不属于您所在病区，无法确认");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
            // 查询失败不阻断，交给后续业务校验
        }
    }

    public ShiftHandoverVO detail(Long id) {
        ShiftHandoverVO vo = requireById(id);
        assertWardVisible(vo);
        return vo;
    }

    /**
     * 护士端待接班列表：近 7 天「已提交（待签名）」与「已确认」。
     * 待签名排在最前，方便接班护士一眼看到还没签的班次。
     */
    public Map<String, Object> pendingForNurse(Long wardId) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        List<Object> args = new ArrayList<>();
        sql.append(" WHERE h.status IN ('SUBMITTED', 'CONFIRMED') AND h.shift_date >= ? ");
        args.add(Date.valueOf(LocalDate.now().minusDays(7)));

        Long hospitalId = TenantContext.getHospitalId();
        if (hospitalId != null) {
            sql.append(" AND h.hospital_id = ? ");
            args.add(hospitalId);
        }
        if (wardId != null) {
            sql.append(" AND h.ward_id = ? ");
            args.add(wardId);
        }
        sql.append(" ORDER BY CASE WHEN h.status = 'SUBMITTED' THEN 0 ELSE 1 END, h.shift_date DESC, h.submit_time DESC LIMIT 50 ");

        List<ShiftHandoverVO> items = jdbcTemplate.query(sql.toString(), ROW, args.toArray());
        int pending = 0;
        for (ShiftHandoverVO item : items) {
            if ("SUBMITTED".equals(item.getStatus())) {
                pending++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("pendingCount", pending);
        return result;
    }

    public Map<String, Object> history(Long wardId, String status, int page, int size) {        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        Long hospitalId = TenantContext.getHospitalId();
        if (hospitalId != null) {
            where.append(" AND h.hospital_id = ? ");
            args.add(hospitalId);
        }
        if (wardId != null) {
            where.append(" AND h.ward_id = ? ");
            args.add(wardId);
        }
        if (!isBlank(status)) {
            where.append(" AND h.status = ? ");
            args.add(status);
        }
        int safeSize = size <= 0 || size > 100 ? 20 : size;
        int safePage = page <= 0 ? 1 : page;

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shift_handover h " + where, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((safePage - 1) * safeSize);
        pageArgs.add(safeSize);
        List<ShiftHandoverVO> items = jdbcTemplate.query(
                BASE_SELECT + where + " ORDER BY h.shift_date DESC, h.shift_type DESC LIMIT ?, ?",
                ROW, pageArgs.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        return result;
    }

    private ShiftHandoverVO requireById(Long id) {
        List<ShiftHandoverVO> rows = jdbcTemplate.query(BASE_SELECT + " WHERE h.id = ? ", ROW, id);
        if (rows.isEmpty()) {
            throw new BusinessException("交班记录不存在");
        }
        return rows.get(0);
    }

    private Long resolveHospitalId(Long wardId) {
        Long hospitalId = TenantContext.getHospitalId();
        if (hospitalId != null) {
            return hospitalId;
        }
        try {
            return jdbcTemplate.queryForObject("SELECT hospital_id FROM ward WHERE id = ?", Long.class, wardId);
        } catch (Exception e) {
            return null;
        }
    }

    private String currentUserName() {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        if (userId == null) {
            return "系统";
        }
        try {
            if ("SUPER_ADMIN".equals(role) || "HOSPITAL_ADMIN".equals(role)) {
                return jdbcTemplate.queryForObject("SELECT real_name FROM sys_admin_user WHERE id = ?", String.class, userId);
            }
            return jdbcTemplate.queryForObject("SELECT real_name FROM nurse WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            return "操作人#" + userId;
        }
    }

    private int count(String sql, Object... args) {
        Integer c = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return c == null ? 0 : c;
    }

    private static String normalizeShiftType(String type) {
        if ("DAY".equalsIgnoreCase(type)) {
            return "DAY";
        }
        return "NIGHT";
    }

    private static LocalDate parseDate(String date) {
        if (isBlank(date)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            throw new BusinessException("交班日期格式应为 yyyy-MM-dd");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** stats_json 解析失败不影响主流程，前端按 null 处理即可 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseStats(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return (Map<String, Object>) JSON.parseObject(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static final String BASE_SELECT =
            "SELECT h.*, w.ward_name FROM shift_handover h LEFT JOIN ward w ON h.ward_id = w.id ";

    private static final RowMapper<ShiftHandoverVO> ROW = (rs, i) -> {
        ShiftHandoverVO vo = new ShiftHandoverVO();
        vo.setId(rs.getLong("id"));
        vo.setWardId(rs.getLong("ward_id"));
        vo.setWardName(rs.getString("ward_name"));
        Date shiftDate = rs.getDate("shift_date");
        vo.setShiftDate(shiftDate == null ? null : shiftDate.toLocalDate());
        vo.setShiftType(rs.getString("shift_type"));
        vo.setShiftTypeLabel("DAY".equals(vo.getShiftType()) ? "白班" : "夜班");

        vo.setStatus(rs.getString("status"));
        vo.setStatusLabel(statusLabel(vo.getStatus()));
        vo.setMemo(rs.getString("memo"));
        vo.setReportText(rs.getString("report_text"));
        vo.setStatsJson(rs.getString("stats_json"));
        vo.setStats(parseStats(vo.getStatsJson()));
        vo.setPatientCount(rs.getInt("patient_count"));

        vo.setSubmitBy(toLong(rs.getObject("submit_by")));
        vo.setSubmitByName(rs.getString("submit_by_name"));
        vo.setSubmitTime(toLocal(rs.getTimestamp("submit_time")));
        vo.setReceiverId(toLong(rs.getObject("receiver_id")));
        vo.setReceiverName(rs.getString("receiver_name"));
        vo.setReceiveTime(toLocal(rs.getTimestamp("receive_time")));
        vo.setSignatureUrl(rs.getString("signature_url"));
        vo.setCreateTime(toLocal(rs.getTimestamp("create_time")));
        vo.setUpdateTime(toLocal(rs.getTimestamp("update_time")));

        vo.setEditable("DRAFT".equals(vo.getStatus()));
        vo.setSubmittable("DRAFT".equals(vo.getStatus()));
        vo.setReopenable("SUBMITTED".equals(vo.getStatus()) || "LOCKED".equals(vo.getStatus()));
        return vo;
    };

    private static LocalDateTime toLocal(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String statusLabel(String status) {
        if ("DRAFT".equals(status)) return "草稿";
        if ("SUBMITTED".equals(status)) return "已提交";
        if ("LOCKED".equals(status)) return "已锁定";
        if ("CONFIRMED".equals(status)) return "接班人已确认";
        return status;
    }
}
