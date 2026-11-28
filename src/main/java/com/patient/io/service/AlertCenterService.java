package com.patient.io.service;

import com.patient.io.common.BusinessException;
import com.patient.io.common.TenantContext;
import com.patient.io.common.UserContext;
import com.patient.io.vo.AlertItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局告警中心。
 *
 * 告警不另建流水表：四类异常都可以由业务表实时推导，落表反而会出现「数据修好了、告警还在」。
 * 这里只把「认领/关闭」的处置轨迹存进 alert_ack，用稳定 key 关联，过期告警自然消失。
 */
@Slf4j
@Service
public class AlertCenterService {

    /** 补液临近结束的提醒阈值（分钟） */
    private static final int INFUSION_WARN_MINUTES = 15;
    /** 夜班交班提醒的起始时间 */
    private static final LocalTime HANDOVER_REMIND_AFTER = LocalTime.of(18, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> list(Long wardId, String type, String status) {
        List<AlertItemVO> items = new ArrayList<>();
        buildBalanceAlerts(items, wardId);
        buildInfusionAlerts(items, wardId);
        buildAssignAlerts(items, wardId);
        buildHandoverAlerts(items, wardId);

        applyAckStates(items);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("total", items.size());
        counts.put("open", countByStatus(items, "OPEN"));
        counts.put("acked", countByStatus(items, "ACKED"));
        counts.put("closed", countByStatus(items, "CLOSED"));
        counts.put("high", items.stream().filter(i -> "HIGH".equals(i.getLevel()) && !"CLOSED".equals(i.getStatus())).count());

        List<AlertItemVO> filtered = new ArrayList<>();
        boolean wantAll = "ALL".equalsIgnoreCase(status);
        for (AlertItemVO item : items) {
            if (type != null && !type.isEmpty() && !"ALL".equalsIgnoreCase(type) && !type.equalsIgnoreCase(item.getType())) {
                continue;
            }
            if (wantAll) {
                // 全部：已关闭的也要列出来，便于回溯
                filtered.add(item);
                continue;
            }
            if (status != null && !status.isEmpty()) {
                if (!status.equalsIgnoreCase(item.getStatus())) {
                    continue;
                }
            } else if ("CLOSED".equals(item.getStatus())) {
                // 默认只看未关闭的
                continue;
            }
            filtered.add(item);
        }

        filtered.sort((a, b) -> {
            int closedDiff = ("CLOSED".equals(a.getStatus()) ? 1 : 0) - ("CLOSED".equals(b.getStatus()) ? 1 : 0);
            if (closedDiff != 0) return closedDiff;
            int levelDiff = levelWeight(b.getLevel()) - levelWeight(a.getLevel());
            if (levelDiff != 0) return levelDiff;
            return Long.compare(b.getMinutes(), a.getMinutes());
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", filtered);
        result.put("counts", counts);
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    /** 顶栏铃铛只关心未关闭数量 */
    public Map<String, Object> badge() {
        Map<String, Object> data = list(null, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) data.get("counts");
        int unhandled = ((Number) counts.get("open")).intValue() + ((Number) counts.get("acked")).intValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unhandled", unhandled);
        result.put("high", counts.get("high"));
        return result;
    }

    public void ack(String key) {
        AlertItemVO alert = findByKey(key);
        if (alert == null) {
            throw new BusinessException("这条告警已自动消除，无需处理");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO alert_ack (hospital_id, alert_key, alert_type, ref_id, patient_id, ward_id, level, status, owner_id, owner_name, ack_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACKED', ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE status = 'ACKED', owner_id = VALUES(owner_id), owner_name = VALUES(owner_name), " +
                        "ack_time = VALUES(ack_time), close_time = NULL",
                resolveHospitalId(alert.getPatientId()), key, alert.getType(), alert.getRefId(),
                alert.getPatientId(), alert.getWardId(), alert.getLevel(),
                UserContext.getUserId(), currentUserName(), now);
        log.info("告警已认领: key={}, by={}", key, currentUserName());
    }

    public void close(String key, String note) {
        AlertItemVO alert = findByKey(key);
        Long hospitalId = alert == null ? TenantContext.getHospitalId() : resolveHospitalId(alert.getPatientId());
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO alert_ack (hospital_id, alert_key, alert_type, ref_id, patient_id, ward_id, level, status, owner_id, owner_name, ack_time, close_time, note) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'CLOSED', ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE status = 'CLOSED', close_time = VALUES(close_time), note = VALUES(note), " +
                        "owner_id = COALESCE(owner_id, VALUES(owner_id)), owner_name = COALESCE(owner_name, VALUES(owner_name))",
                hospitalId == null ? 1L : hospitalId, key,
                alert == null ? "UNKNOWN" : alert.getType(),
                alert == null ? null : alert.getRefId(),
                alert == null ? null : alert.getPatientId(),
                alert == null ? null : alert.getWardId(),
                alert == null ? "MID" : alert.getLevel(),
                UserContext.getUserId(), currentUserName(), now, now,
                note == null ? "" : note);
        log.info("告警已关闭: key={}, note={}", key, note);
    }

    // ------------------------------------------------------------------
    // 四类告警的生成
    // ------------------------------------------------------------------

    private void buildBalanceAlerts(List<AlertItemVO> items, Long wardId) {
        StringBuilder sql = new StringBuilder(
                "SELECT ds.patient_id, ds.ward_id, ds.record_date, ds.net_balance, ds.urine_output, " +
                        "ds.total_intake, ds.total_output, ds.update_time, p.patient_name, p.bed_no, w.ward_name " +
                        "FROM daily_summary ds JOIN patient p ON ds.patient_id = p.id " +
                        "LEFT JOIN ward w ON ds.ward_id = w.id " +
                        "WHERE ds.is_abnormal = 1 AND p.status = 1 AND p.is_deleted = 0 AND ds.record_date >= ? ");
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(LocalDate.now().minusDays(1)));
        appendWard(sql, args, wardId, "ds.ward_id");
        appendHospital(sql, args, "p.hospital_id");

        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), args.toArray())) {
            AlertItemVO item = new AlertItemVO();
            Long patientId = toLong(row.get("patient_id"));
            LocalDate date = toDate(row.get("record_date"));
            item.setKey("BALANCE:" + patientId + ":" + date);
            item.setType("BALANCE");
            item.setTypeLabel("出入量失衡");
            item.setPatientId(patientId);
            item.setPatientName(str(row.get("patient_name")));
            item.setBedNo(str(row.get("bed_no")));
            item.setWardId(toLong(row.get("ward_id")));
            item.setWardName(str(row.get("ward_name")));
            item.setRefId(patientId);
            item.setTarget("patient");

            BigDecimal net = decimal(row.get("net_balance"));
            BigDecimal urine = decimal(row.get("urine_output"));
            boolean severe = (urine != null && urine.compareTo(new BigDecimal("400")) < 0)
                    || (net != null && net.compareTo(new BigDecimal("-1000")) < 0);
            item.setLevel(severe ? "HIGH" : "MID");
            item.setLevelLabel(severe ? "高" : "中");
            item.setTitle("24h 净平衡 " + signed(net) + " ml");
            item.setDetail("入 " + plain(row.get("total_intake")) + " / 出 " + plain(row.get("total_output"))
                    + (urine == null ? "" : "，尿量 " + urine.stripTrailingZeros().toPlainString() + " ml")
                    + "（" + date + "）");
            item.setOccurredAt(toLocalDateTime(row.get("update_time")));
            fillMinutes(item);
            item.setStatus("OPEN");
            items.add(item);
        }
    }

    private void buildInfusionAlerts(List<AlertItemVO> items, Long wardId) {
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(INFUSION_WARN_MINUTES);
        StringBuilder sql = new StringBuilder(
                "SELECT i.id, i.patient_id, i.ward_id, i.patient_name, i.bed_no, i.capacity, i.usage_type, " +
                        "i.estimated_end_time, w.ward_name " +
                        "FROM infusion_record i LEFT JOIN ward w ON i.ward_id = w.id " +
                        "WHERE i.status = 'INFUSING' AND i.is_deleted = 0 AND i.estimated_end_time <= ? ");
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(deadline));
        appendWard(sql, args, wardId, "i.ward_id");
        appendHospital(sql, args, "i.hospital_id");

        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), args.toArray())) {
            AlertItemVO item = new AlertItemVO();
            Long id = toLong(row.get("id"));
            LocalDateTime endTime = toLocalDateTime(row.get("estimated_end_time"));
            long lateMinutes = endTime == null ? 0 : Duration.between(endTime, LocalDateTime.now()).toMinutes();
            boolean timeout = lateMinutes > 0;

            item.setKey("INFUSION:" + id);
            item.setType("INFUSION");
            item.setTypeLabel("补液超时");
            item.setPatientId(toLong(row.get("patient_id")));
            item.setPatientName(str(row.get("patient_name")));
            item.setBedNo(str(row.get("bed_no")));
            item.setWardId(toLong(row.get("ward_id")));
            item.setWardName(str(row.get("ward_name")));
            item.setRefId(id);
            item.setTarget("infusion");
            item.setLevel(timeout ? "HIGH" : "MID");
            item.setLevelLabel(timeout ? "高" : "中");
            item.setTitle(timeout ? "已超时 " + lateMinutes + " 分钟未拔针" : "剩余不足 " + INFUSION_WARN_MINUTES + " 分钟");
            item.setDetail(str(row.get("usage_type")) + " " + plain(row.get("capacity")) + " ml，预计 "
                    + (endTime == null ? "-" : endTime.toLocalTime().withNano(0)));
            item.setOccurredAt(endTime);
            fillMinutes(item);
            item.setStatus("OPEN");
            items.add(item);
        }
    }

    private void buildAssignAlerts(List<AlertItemVO> items, Long wardId) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id AS patient_id, p.ward_id, p.patient_name, p.bed_no, p.create_time, w.ward_name " +
                        "FROM patient p LEFT JOIN ward w ON p.ward_id = w.id " +
                        "LEFT JOIN nurse_patient_relation r ON r.patient_id = p.id " +
                        "WHERE p.status = 1 AND p.is_deleted = 0 AND r.id IS NULL ");
        List<Object> args = new ArrayList<>();
        appendWard(sql, args, wardId, "p.ward_id");
        appendHospital(sql, args, "p.hospital_id");

        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), args.toArray())) {
            AlertItemVO item = new AlertItemVO();
            Long patientId = toLong(row.get("patient_id"));
            item.setKey("ASSIGN:" + patientId + ":" + LocalDate.now());
            item.setType("ASSIGN");
            item.setTypeLabel("未指派责任护士");
            item.setPatientId(patientId);
            item.setPatientName(str(row.get("patient_name")));
            item.setBedNo(str(row.get("bed_no")));
            item.setWardId(toLong(row.get("ward_id")));
            item.setWardName(str(row.get("ward_name")));
            item.setRefId(patientId);
            item.setTarget("assignment");
            item.setLevel("MID");
            item.setLevelLabel("中");
            item.setTitle("床位未指派责任护士");
            item.setDetail("入院 " + toDate(row.get("create_time")) + "，患者异常时找不到第一责任人");
            item.setOccurredAt(toLocalDateTime(row.get("create_time")));
            fillMinutes(item);
            item.setStatus("OPEN");
            items.add(item);
        }
    }

    private void buildHandoverAlerts(List<AlertItemVO> items, Long wardId) {
        if (LocalTime.now().isBefore(HANDOVER_REMIND_AFTER)) {
            return;
        }
        LocalDate today = LocalDate.now();
        StringBuilder sql = new StringBuilder(
                "SELECT w.id AS ward_id, w.ward_name, h.id AS handover_id, h.status, " +
                        "(SELECT COUNT(*) FROM patient p WHERE p.ward_id = w.id AND p.status = 1 AND p.is_deleted = 0) AS patient_count " +
                        "FROM ward w LEFT JOIN shift_handover h ON h.ward_id = w.id AND h.shift_date = ? AND h.shift_type = 'NIGHT' " +
                        "WHERE w.status = 1 ");
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(today));
        if (wardId != null) {
            sql.append(" AND w.id = ? ");
            args.add(wardId);
        }
        appendHospital(sql, args, "w.hospital_id");

        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), args.toArray())) {
            int patientCount = ((Number) row.get("patient_count")).intValue();
            if (patientCount == 0) {
                continue;
            }
            String handoverStatus = str(row.get("status"));
            if ("SUBMITTED".equals(handoverStatus) || "LOCKED".equals(handoverStatus) || "CONFIRMED".equals(handoverStatus)) {
                continue;
            }
            AlertItemVO item = new AlertItemVO();
            Long ward = toLong(row.get("ward_id"));
            item.setKey("HANDOVER:" + ward + ":" + today);
            item.setType("HANDOVER");
            item.setTypeLabel("交班未提交");
            item.setWardId(ward);
            item.setWardName(str(row.get("ward_name")));
            item.setRefId(toLong(row.get("handover_id")));
            item.setTarget("handover");
            item.setLevel("HIGH");
            item.setLevelLabel("高");
            item.setTitle("夜班交班仍未提交");
            item.setDetail("在院 " + patientCount + " 人，" + (handoverStatus.isEmpty() ? "尚未创建交班记录" : "当前为草稿"));
            item.setOccurredAt(LocalDateTime.of(today, HANDOVER_REMIND_AFTER));
            fillMinutes(item);
            item.setStatus("OPEN");
            items.add(item);
        }
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void applyAckStates(List<AlertItemVO> items) {
        if (items.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (AlertItemVO item : items) {
            keys.add(item.getKey());
        }
        Map<String, Map<String, Object>> ackMap = new LinkedHashMap<>();
        for (int start = 0; start < keys.size(); start += 500) {
            List<String> chunk = keys.subList(start, Math.min(start + 500, keys.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT alert_key, status, owner_name, ack_time, close_time, note FROM alert_ack WHERE alert_key IN (" + placeholders + ")",
                    chunk.toArray());
            for (Map<String, Object> row : rows) {
                ackMap.put(str(row.get("alert_key")), row);
            }
        }
        for (AlertItemVO item : items) {
            Map<String, Object> ack = ackMap.get(item.getKey());
            if (ack == null) {
                item.setStatus("OPEN");
                item.setStatusLabel("待处理");
                continue;
            }
            String status = str(ack.get("status"));
            item.setStatus(status.isEmpty() ? "OPEN" : status);
            item.setOwnerName(str(ack.get("owner_name")));
            item.setAckTime(toLocalDateTime(ack.get("ack_time")));
            item.setCloseTime(toLocalDateTime(ack.get("close_time")));
            item.setNote(str(ack.get("note")));
            item.setStatusLabel("ACKED".equals(item.getStatus()) ? "处理中" : ("CLOSED".equals(item.getStatus()) ? "已关闭" : "待处理"));
        }
    }

    private AlertItemVO findByKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new BusinessException("告警标识不能为空");
        }
        Map<String, Object> data = list(null, null, "ALL");
        @SuppressWarnings("unchecked")
        List<AlertItemVO> items = (List<AlertItemVO>) data.get("items");
        for (AlertItemVO item : items) {
            if (key.equals(item.getKey())) {
                return item;
            }
        }
        return null;
    }

    private Long resolveHospitalId(Long patientId) {
        Long hospitalId = TenantContext.getHospitalId();
        if (hospitalId != null) {
            return hospitalId;
        }
        if (patientId != null) {
            try {
                return jdbcTemplate.queryForObject("SELECT hospital_id FROM patient WHERE id = ?", Long.class, patientId);
            } catch (Exception ignored) {
                // 患者不存在时退回默认租户
            }
        }
        return 1L;
    }

    private String currentUserName() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return "系统";
        }
        String role = UserContext.getRole();
        try {
            if ("SUPER_ADMIN".equals(role) || "HOSPITAL_ADMIN".equals(role)) {
                return jdbcTemplate.queryForObject("SELECT real_name FROM sys_admin_user WHERE id = ?", String.class, userId);
            }
            return jdbcTemplate.queryForObject("SELECT real_name FROM nurse WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            return "操作人#" + userId;
        }
    }

    private static void appendWard(StringBuilder sql, List<Object> args, Long wardId, String column) {
        if (wardId != null) {
            sql.append(" AND ").append(column).append(" = ? ");
            args.add(wardId);
        }
    }

    private static void appendHospital(StringBuilder sql, List<Object> args, String column) {
        Long hospitalId = TenantContext.getHospitalId();
        if (hospitalId != null) {
            sql.append(" AND ").append(column).append(" = ? ");
            args.add(hospitalId);
        }
    }

    private static void fillMinutes(AlertItemVO item) {
        if (item.getOccurredAt() == null) {
            item.setMinutes(0);
            return;
        }
        item.setMinutes(Math.max(0, Duration.between(item.getOccurredAt(), LocalDateTime.now()).toMinutes()));
    }

    private static long countByStatus(List<AlertItemVO> items, String status) {
        return items.stream().filter(i -> status.equals(i.getStatus())).count();
    }

    private static int levelWeight(String level) {
        if ("HIGH".equalsIgnoreCase(level)) return 3;
        if ("LOW".equalsIgnoreCase(level)) return 1;
        return 2;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String plain(Object o) {
        if (o == null) return "0";
        try {
            return new BigDecimal(String.valueOf(o)).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String signed(BigDecimal value) {
        if (value == null) return "0";
        String s = value.stripTrailingZeros().toPlainString();
        return value.signum() > 0 ? "+" + s : s;
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) return null;
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
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

    private static LocalDate toDate(Object o) {
        if (o == null) return null;
        if (o instanceof Date) return ((Date) o).toLocalDate();
        if (o instanceof LocalDate) return (LocalDate) o;
        try {
            return LocalDate.parse(String.valueOf(o).substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toLocalDateTime(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp) return ((Timestamp) o).toLocalDateTime();
        if (o instanceof LocalDateTime) return (LocalDateTime) o;
        try {
            return Timestamp.valueOf(String.valueOf(o)).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }
}
