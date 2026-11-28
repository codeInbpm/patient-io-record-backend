package com.patient.io.service;

import com.patient.io.common.BusinessException;
import com.patient.io.common.TenantContext;
import com.patient.io.common.UserContext;
import com.patient.io.dto.FeedbackHandleDTO;
import com.patient.io.vo.FeedbackItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反馈处理中心。
 *
 * 护士端（小程序）与家属端上报的「记错了 / 出现异常症状 / 家属诉求」都落在
 * record_correction_request 一张表里，后台此前没有任何出口。这里统一收口：
 * 列出 → 核对 → 处置（修正 / 核对无误 / 驳回 / 转交 / 回复 / 延后）→ 回传状态。
 *
 * 唯一处理人是护士长；医院管理员与超管只读（校验放在 Controller 层）。
 */
@Slf4j
@Service
public class FeedbackCenterService {

    /** 各类型的处理时限（分钟），超时即置顶并计入时效统计 */
    private static final int SLA_SYMPTOM = 15;
    private static final int SLA_CORRECTION = 240;
    private static final int SLA_FAMILY = 120;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RbacService rbacService;

    @Autowired
    private FluidRecordService fluidRecordService;

    @Autowired
    private RecordAuditService recordAuditService;

    private static final String BASE_FROM =
            " FROM record_correction_request r " +
                    "LEFT JOIN patient p ON r.patient_id = p.id " +
                    "LEFT JOIN ward w ON p.ward_id = w.id ";

    private static final String BASE_SELECT =
            "SELECT r.id, r.record_id, r.category, r.patient_id, r.amount, r.item_name, r.record_time, " +
                    "r.remark, r.reason, r.status, r.create_time, r.handle_time, r.level, r.source, " +
                    "r.handle_action, r.handle_note, r.handler_name, r.corrected_amount, r.defer_until, " +
                    "r.sync_status, r.requester_role, p.patient_name, p.bed_no, p.ward_id, p.hospital_no, " +
                    "w.ward_name ";

    /**
     * 列表 + 统计。
     *
     * @param status PENDING / DONE / ALL
     */
    public Map<String, Object> list(Long wardId, String category, String status, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        appendFilters(where, args, wardId, category, status, keyword);

        String countSql = "SELECT COUNT(*) " + BASE_FROM + where;
        Integer totalObj = jdbcTemplate.queryForObject(countSql, Integer.class, args.toArray());
        int total = totalObj == null ? 0 : totalObj;

        int safeSize = size <= 0 || size > 100 ? 20 : size;
        int safePage = page <= 0 ? 1 : page;

        String sql = BASE_SELECT + BASE_FROM + where +
                " ORDER BY CASE WHEN r.status = 'PENDING' THEN 0 ELSE 1 END, " +
                " CASE WHEN r.status = 'PENDING' THEN r.create_time END ASC, r.handle_time DESC " +
                " LIMIT ?, ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((safePage - 1) * safeSize);
        pageArgs.add(safeSize);

        List<FeedbackItemVO> items = jdbcTemplate.query(sql, FEEDBACK_ROW, pageArgs.toArray());

        // 超时优先 + 高优优先 + 等待最久的靠前
        if (!"DONE".equalsIgnoreCase(status)) {
            items.sort((a, b) -> {
                if (a.isOverdue() != b.isOverdue()) {
                    return a.isOverdue() ? -1 : 1;
                }
                int levelDiff = levelWeight(b.getLevel()) - levelWeight(a.getLevel());
                if (levelDiff != 0) {
                    return levelDiff;
                }
                return Long.compare(b.getWaitMinutes(), a.getWaitMinutes());
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("summary", summary(wardId));
        return result;
    }

    public FeedbackItemVO detail(Long id) {
        StringBuilder where = new StringBuilder(" WHERE r.id = ? ");
        List<FeedbackItemVO> rows = jdbcTemplate.query(BASE_SELECT + BASE_FROM + where, FEEDBACK_ROW, id);
        if (rows.isEmpty()) {
            throw new BusinessException("这条反馈不存在或已删除");
        }
        return rows.get(0);
    }

    /**
     * 头部 KPI。pending 口径只算真正待处理的，延后未到期的仍计入待处理但不算超时。
     */
    public Map<String, Object> summary(Long wardId) {
        StringBuilder where = new StringBuilder(" WHERE r.status = 'PENDING' ");
        List<Object> filterArgs = new ArrayList<>();
        Long hospitalId = TenantContext.getHospitalId();
        if (hospitalId != null) {
            where.append(" AND p.hospital_id = ? ");
            filterArgs.add(hospitalId);
        }
        if (wardId != null) {
            where.append(" AND p.ward_id = ? ");
            filterArgs.add(wardId);
        }

        LocalDateTime now = LocalDateTime.now();
        // 注意参数顺序：SELECT 里的三个时间阈值在 WHERE 之前，必须先放
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(now.minusMinutes(SLA_SYMPTOM)));
        args.add(Timestamp.valueOf(now.minusMinutes(SLA_FAMILY)));
        args.add(Timestamp.valueOf(now.minusMinutes(SLA_CORRECTION)));
        args.addAll(filterArgs);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT " +
                        " COUNT(*) AS pending_total, " +
                        " SUM(CASE WHEN r.category = 'SYMPTOM' THEN 1 ELSE 0 END) AS symptom, " +
                        " SUM(CASE WHEN r.category IN ('INTAKE','OUTPUT') THEN 1 ELSE 0 END) AS correction, " +
                        " SUM(CASE WHEN r.category = 'FAMILY' THEN 1 ELSE 0 END) AS family, " +
                        " SUM(CASE WHEN r.category = 'SYMPTOM' AND r.create_time < ? THEN 1 " +
                        "          WHEN r.category = 'FAMILY' AND r.create_time < ? THEN 1 " +
                        "          WHEN r.category IN ('INTAKE','OUTPUT') AND r.create_time < ? THEN 1 " +
                        "          ELSE 0 END) AS overdue " +
                        "FROM record_correction_request r LEFT JOIN patient p ON r.patient_id = p.id " + where,
                args.toArray());

        LocalDate today = LocalDate.now();
        List<Object> doneArgs = new ArrayList<>();
        doneArgs.add(Timestamp.valueOf(today.atStartOfDay()));
        String doneWhere = " WHERE r.handle_time >= ? AND r.status <> 'PENDING' ";
        if (hospitalId != null) {
            doneWhere += " AND p.hospital_id = ? ";
            doneArgs.add(hospitalId);
        }
        if (wardId != null) {
            doneWhere += " AND p.ward_id = ? ";
            doneArgs.add(wardId);
        }
        Map<String, Object> done = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS handled_today, " +
                        " AVG(TIMESTAMPDIFF(MINUTE, r.create_time, r.handle_time)) AS avg_minutes " +
                        "FROM record_correction_request r LEFT JOIN patient p ON r.patient_id = p.id " + doneWhere,
                doneArgs.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pendingTotal", toInt(row.get("pending_total")));
        result.put("symptom", toInt(row.get("symptom")));
        result.put("correction", toInt(row.get("correction")));
        result.put("family", toInt(row.get("family")));
        result.put("overdue", toInt(row.get("overdue")));
        result.put("handledToday", toInt(done.get("handled_today")));
        Object avg = done.get("avg_minutes");
        result.put("avgHandleMinutes", avg == null ? 0 : Math.round(((Number) avg).doubleValue()));
        return result;
    }

    /**
     * 处置一条反馈。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, FeedbackHandleDTO dto) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("SELECT * FROM record_correction_request WHERE id = ?", id);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException("这条反馈不存在或已删除");
        }

        if (!"PENDING".equals(str(row.get("status")))) {
            throw new BusinessException("这条反馈已经处理过了，不能重复处理");
        }

        String action = dto.getAction() == null ? "" : dto.getAction().trim().toUpperCase();
        String category = str(row.get("category"));
        boolean recordCategory = "INTAKE".equals(category) || "OUTPUT".equals(category);

        if (!recordCategory && ("FIX".equals(action) || "REJECT".equals(action))) {
            throw new BusinessException("症状与家属反馈不涉及数值修正，请选择「已处置 / 转交 / 回复」");
        }
        if (!recordCategory && "CONFIRM".equals(action)) {
            throw new BusinessException("家属反馈没有可核对的原记录，请选择「回复 / 转交」");
        }

        Long recordId = toLong(row.get("record_id"));
        Long patientId = toLong(row.get("patient_id"));
        String note = dto.getNote() == null ? "" : dto.getNote().trim();
        String handlerName = currentHandlerName();
        Long handlerId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();

        if ("FIX".equals(action)) {
            // FIX 会直接改写原记录，属高危操作：除 feedback:handle 外还需要 feedback:fix
            if (!rbacService.hasPermission("feedback:fix")) {
                throw new BusinessException(403, "无权限直接修正原记录，请改用「核对无误 / 转交 / 回复」");
            }
            applyFix(id, row, dto, note, handlerName, handlerId, now);
            return;
        }

        if ("REJECT".equals(action)) {
            if (recordId == null || recordId == 0) {
                throw new BusinessException("这条反馈没有关联记录，无法驳回");
            }
            if (note.isEmpty()) {
                throw new BusinessException("驳回必须填写原因，否则家属端不知道哪里记错了");
            }
            // 先改状态再删记录：删除会把同记录的 PENDING 收口，顺序反了状态就错
            jdbcTemplate.update(
                    "UPDATE record_correction_request SET status = 'REJECTED', handle_action = 'REJECT', " +
                            "handle_note = ?, handler_id = ?, handler_name = ?, handle_time = ?, sync_status = 'SYNCED' " +
                            "WHERE id = ? AND status = 'PENDING'",
                    note, handlerId, handlerName, now, id);

            String reason = "护士长驳回：患者反馈记错，待重新记录（反馈单 " + id + "）";
            if ("INTAKE".equals(category)) {
                recordAuditService.logIntakeDelete(recordId, reason);
                fluidRecordService.deleteIntakeRecord(recordId);
            } else {
                recordAuditService.logOutputDelete(recordId, reason);
                fluidRecordService.deleteOutputRecord(recordId);
            }
            log.info("护士长驳回反馈: id={}, recordId={}, category={}", id, recordId, category);
            return;
        }

        if ("DEFER".equals(action)) {
            int minutes = dto.getDeferMinutes() == null || dto.getDeferMinutes() <= 0 ? 30 : dto.getDeferMinutes();
            jdbcTemplate.update(
                    "UPDATE record_correction_request SET handle_action = 'DEFER', handle_note = ?, " +
                            "handler_id = ?, handler_name = ?, defer_until = ? WHERE id = ? AND status = 'PENDING'",
                    note, handlerId, handlerName, now.plusMinutes(minutes), id);
            return;
        }

        if ("REFER".equals(action) || "REPLY".equals(action)) {
            if (note.isEmpty()) {
                throw new BusinessException("REFER".equals(action) ? "转交医生需填写转交意见" : "回复家属需填写回复内容");
            }
        }

        if (!"CONFIRM".equals(action) && !"REFER".equals(action) && !"REPLY".equals(action)) {
            throw new BusinessException("不支持的处理方式：" + action);
        }

        jdbcTemplate.update(
                "UPDATE record_correction_request SET status = 'HANDLED', handle_action = ?, handle_note = ?, " +
                        "handler_id = ?, handler_name = ?, handle_time = ?, sync_status = 'SYNCED' " +
                        "WHERE id = ? AND status = 'PENDING'",
                action, note, handlerId, handlerName, now, id);

        if (patientId != null) {
            log.info("护士长处理反馈: id={}, action={}, patientId={}", id, action, patientId);
        }
    }

    /**
     * 通过并修正数值：改记录 → 重算日汇总 → 反馈单留痕（修正前后值都在单子上）
     */
    private void applyFix(Long id, Map<String, Object> row, FeedbackHandleDTO dto, String note,
                          String handlerName, Long handlerId, LocalDateTime now) {
        if (dto.getCorrectedAmount() == null) {
            throw new BusinessException("请填写修正后数值");
        }
        BigDecimal corrected = dto.getCorrectedAmount();
        if (corrected.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("修正后数值不能为负数");
        }
        if (corrected.compareTo(new BigDecimal("20000")) > 0) {
            throw new BusinessException("修正后数值超出合理范围（0 ~ 20000），请确认单位是否为 ml");
        }

        Long recordId = toLong(row.get("record_id"));
        String category = str(row.get("category"));
        if (recordId == null || recordId == 0) {
            throw new BusinessException("这条反馈没有关联记录，无法修正，请选择驳回");
        }

        String table = "INTAKE".equals(category) ? "intake_record" : "output_record";
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT id, amount, record_date FROM " + table + " WHERE id = ?", recordId);
        if (records.isEmpty()) {
            throw new BusinessException("原记录已被删除，无法修正，请选择驳回");
        }
        Map<String, Object> record = records.get(0);
        BigDecimal oldAmount = record.get("amount") == null ? BigDecimal.ZERO : new BigDecimal(str(record.get("amount")));

        String appendRemark = "（护士长修正：原 " + oldAmount.stripTrailingZeros().toPlainString()
                + " → " + corrected.stripTrailingZeros().toPlainString() + "，反馈单 " + id + "）";
        jdbcTemplate.update("UPDATE " + table + " SET amount = ?, remark = CONCAT(IFNULL(remark, ''), ?) WHERE id = ?",
                corrected, appendRemark, recordId);

        Object recordDate = record.get("record_date");
        if (recordDate != null) {
            LocalDate date = recordDate instanceof java.sql.Date
                    ? ((java.sql.Date) recordDate).toLocalDate()
                    : LocalDate.parse(str(recordDate));
            fluidRecordService.refreshDailySummary(toLong(row.get("patient_id")), date);
        }

        jdbcTemplate.update(
                "UPDATE record_correction_request SET status = 'HANDLED', handle_action = 'FIX', handle_note = ?, " +
                        "handler_id = ?, handler_name = ?, corrected_amount = ?, handle_time = ?, sync_status = 'SYNCED' " +
                        "WHERE id = ? AND status = 'PENDING'",
                note, handlerId, handlerName, corrected, now, id);

        log.info("护士长修正记录: feedbackId={}, recordId={}, {} -> {}", id, recordId, oldAmount, corrected);
    }

    private void appendFilters(StringBuilder where, List<Object> args, Long wardId, String category,
                               String status, String keyword) {
        Long hospitalId = TenantContext.getHospitalId();
        if (hospitalId != null) {
            where.append(" AND p.hospital_id = ? ");
            args.add(hospitalId);
        }
        if (wardId != null) {
            where.append(" AND p.ward_id = ? ");
            args.add(wardId);
        }
        if (category != null && !category.isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            if ("CORRECTION".equalsIgnoreCase(category)) {
                where.append(" AND r.category IN ('INTAKE','OUTPUT') ");
            } else {
                where.append(" AND r.category = ? ");
                args.add(category.toUpperCase());
            }
        }
        if ("PENDING".equalsIgnoreCase(status)) {
            where.append(" AND r.status = 'PENDING' ");
        } else if ("DONE".equalsIgnoreCase(status)) {
            where.append(" AND r.status IN ('HANDLED','REJECTED','RECORDED') ");
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (p.patient_name LIKE ? OR p.bed_no LIKE ? OR p.hospital_no LIKE ? OR r.item_name LIKE ?) ");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }

    /** 当前操作人姓名：管理员取 sys_admin_user，护士长取 nurse */
    private String currentHandlerName() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return "系统";
        }
        String role = UserContext.getRole();
        try {
            if ("SUPER_ADMIN".equals(role) || "HOSPITAL_ADMIN".equals(role)) {
                String name = jdbcTemplate.queryForObject(
                        "SELECT real_name FROM sys_admin_user WHERE id = ?", String.class, userId);
                return name == null ? "管理员" : name;
            }
            String name = jdbcTemplate.queryForObject(
                    "SELECT real_name FROM nurse WHERE id = ?", String.class, userId);
            return name == null ? "护士长" : name;
        } catch (Exception e) {
            return "操作人#" + userId;
        }
    }

    private static int levelWeight(String level) {
        if ("HIGH".equalsIgnoreCase(level)) return 3;
        if ("LOW".equalsIgnoreCase(level)) return 1;
        return 2;
    }

    private static int slaMinutes(String category) {
        if ("SYMPTOM".equals(category)) return SLA_SYMPTOM;
        if ("FAMILY".equals(category)) return SLA_FAMILY;
        return SLA_CORRECTION;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
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

    private static int toInt(Object o) {
        if (o == null) return 0;
        return ((Number) o).intValue();
    }

    private static final RowMapper<FeedbackItemVO> FEEDBACK_ROW = (rs, i) -> {
        FeedbackItemVO vo = new FeedbackItemVO();
        vo.setId(rs.getLong("id"));
        vo.setRecordId(rs.getLong("record_id"));
        vo.setCategory(rs.getString("category"));
        vo.setPatientId(rs.getLong("patient_id"));
        vo.setAmount(rs.getBigDecimal("amount"));
        vo.setCorrectedAmount(rs.getBigDecimal("corrected_amount"));
        vo.setItemName(rs.getString("item_name"));
        vo.setRemark(rs.getString("remark"));
        vo.setReason(rs.getString("reason"));
        vo.setStatus(rs.getString("status"));
        vo.setLevel(rs.getString("level"));
        vo.setSource(rs.getString("source"));
        vo.setHandleAction(rs.getString("handle_action"));
        vo.setHandleNote(rs.getString("handle_note"));
        vo.setHandlerName(rs.getString("handler_name"));
        vo.setSyncStatus(rs.getString("sync_status"));

        Timestamp recordTime = rs.getTimestamp("record_time");
        vo.setRecordTime(recordTime == null ? null : recordTime.toLocalDateTime());
        Timestamp createTime = rs.getTimestamp("create_time");
        vo.setCreateTime(createTime == null ? null : createTime.toLocalDateTime());
        Timestamp handleTime = rs.getTimestamp("handle_time");
        vo.setHandleTime(handleTime == null ? null : handleTime.toLocalDateTime());
        Timestamp deferUntil = rs.getTimestamp("defer_until");
        vo.setDeferUntil(deferUntil == null ? null : deferUntil.toLocalDateTime());

        vo.setPatientName(rs.getString("patient_name"));
        vo.setBedNo(rs.getString("bed_no"));
        vo.setWardId(rs.getLong("ward_id"));
        vo.setWardName(rs.getString("ward_name"));
        vo.setHospitalNo(rs.getString("hospital_no"));

        String category = vo.getCategory();
        vo.setRecordCategory(category);
        if ("SYMPTOM".equals(category)) {
            vo.setTypeLabel("症状上报");
        } else if ("FAMILY".equals(category)) {
            vo.setTypeLabel("家属反馈");
        } else {
            vo.setTypeLabel("数据更正");
        }

        String level = vo.getLevel();
        vo.setLevelLabel("HIGH".equals(level) ? "高" : ("LOW".equals(level) ? "低" : "中"));

        String requesterRole = rs.getString("requester_role");
        vo.setReporter("PATIENT".equals(requesterRole) ? "家属端" : ("NURSE".equals(requesterRole) || "HEAD_NURSE".equals(requesterRole) ? "护士端" : "小程序端"));
        vo.setSourceLabel("FAMILY".equals(vo.getSource()) ? "家属反馈" : "小程序上报");

        LocalDateTime now = LocalDateTime.now();
        if (vo.getCreateTime() != null) {
            vo.setWaitMinutes(Math.max(0, Duration.between(vo.getCreateTime(), now).toMinutes()));
            if ("PENDING".equals(vo.getStatus())) {
                int sla = slaMinutes(category);
                boolean deferred = vo.getDeferUntil() != null && vo.getDeferUntil().isAfter(now);
                if (!deferred && vo.getWaitMinutes() > sla) {
                    vo.setOverdue(true);
                    vo.setOverdueMinutes(vo.getWaitMinutes() - sla);
                }
            }
        }
        if (vo.getCreateTime() != null && vo.getHandleTime() != null) {
            vo.setHandleMinutes(Math.max(0, Duration.between(vo.getCreateTime(), vo.getHandleTime()).toMinutes()));
        }
        if (vo.getCreateTime() != null) {
            vo.setCode("FK-" + vo.getCreateTime().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-"
                    + String.format("%04d", vo.getId() % 10000));
        }
        return vo;
    };

    /** 供运维/测试使用：把历史反馈补上病区冗余，跨病区筛选才不会漏 */
    public int backfillWard() {
        return jdbcTemplate.update("UPDATE record_correction_request r JOIN patient p ON r.patient_id = p.id " +
                "SET r.ward_id = p.ward_id WHERE r.ward_id IS NULL");
    }
}
