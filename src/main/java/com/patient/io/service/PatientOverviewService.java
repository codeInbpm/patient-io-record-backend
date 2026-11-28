package com.patient.io.service;

import com.patient.io.common.BusinessException;
import com.patient.io.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 患者 360 概览。
 *
 * 过去看一个患者要在看板、查房、补液、患者档案之间跳 3~4 次，
 * 回来还要重新选病区。这里一次把「这人现在什么情况」拼齐，抽屉里就能看完。
 */
@Slf4j
@Service
public class PatientOverviewService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> overview(Long patientId) {
        List<Map<String, Object>> patients = jdbcTemplate.queryForList(
                "SELECT p.*, w.ward_name, w.building FROM patient p LEFT JOIN ward w ON p.ward_id = w.id WHERE p.id = ?",
                patientId);
        if (patients.isEmpty()) {
            throw new BusinessException("患者不存在或已出院归档");
        }
        Map<String, Object> patient = patients.get(0);

        // 租户兜底：非超管只能看本院患者
        Long hospitalId = TenantContext.getHospitalId();
        if (hospitalId != null && !hospitalId.equals(toLong(patient.get("hospital_id")))) {
            throw new BusinessException(403, "无权查看其他医院的患者");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("id", toLong(patient.get("id")));
        base.put("patientName", patient.get("patient_name"));
        base.put("bedNo", patient.get("bed_no"));
        base.put("hospitalNo", patient.get("hospital_no"));
        base.put("gender", toLong(patient.get("gender")));
        base.put("age", patient.get("age"));
        base.put("wardId", toLong(patient.get("ward_id")));
        base.put("wardName", patient.get("ward_name"));
        base.put("building", patient.get("building"));
        base.put("diagnosis", patient.get("diagnosis"));
        base.put("nursingLevel", patient.get("nursing_level"));
        base.put("admissionDate", toDateStr(patient.get("admission_date")));
        base.put("weight", patient.get("weight"));
        base.put("drainageTubes", patient.get("drainage_tubes"));
        base.put("status", toLong(patient.get("status")));
        result.put("patient", base);

        // 责任护士
        List<Map<String, Object>> nurses = jdbcTemplate.queryForList(
                "SELECT n.real_name, n.phone FROM nurse_patient_relation r JOIN nurse n ON r.nurse_id = n.id " +
                        "WHERE r.patient_id = ?", patientId);
        List<String> nurseNames = new ArrayList<>();
        for (Map<String, Object> n : nurses) {
            if (n.get("real_name") != null) {
                nurseNames.add(String.valueOf(n.get("real_name")));
            }
        }
        result.put("nurses", nurseNames);
        result.put("assigned", !nurseNames.isEmpty());

        // 近 7 天出入量
        List<Map<String, Object>> summaries = jdbcTemplate.queryForList(
                "SELECT record_date, total_intake, total_output, net_balance, urine_output, is_abnormal " +
                        "FROM daily_summary WHERE patient_id = ? AND record_date >= ? ORDER BY record_date ASC",
                patientId, Date.valueOf(LocalDate.now().minusDays(6)));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> s : summaries) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", toDateStr(s.get("record_date")));
            point.put("intake", s.get("total_intake"));
            point.put("output", s.get("total_output"));
            point.put("net", s.get("net_balance"));
            point.put("urine", s.get("urine_output"));
            point.put("abnormal", toLong(s.get("is_abnormal")));
            trend.add(point);
        }
        result.put("trend", trend);

        Map<String, Object> today = new LinkedHashMap<>();
        if (!summaries.isEmpty()) {
            Map<String, Object> last = summaries.get(summaries.size() - 1);
            today.put("date", toDateStr(last.get("record_date")));
            today.put("intake", last.get("total_intake"));
            today.put("output", last.get("total_output"));
            today.put("net", last.get("net_balance"));
            today.put("urine", last.get("urine_output"));
            today.put("abnormal", toLong(last.get("is_abnormal")));
        }
        result.put("today", today);

        // 在滴补液
        List<Map<String, Object>> infusions = jdbcTemplate.queryForList(
                "SELECT id, capacity, usage_type, start_time, estimated_end_time, nurse_name " +
                        "FROM infusion_record WHERE patient_id = ? AND status = 'INFUSING' AND is_deleted = 0 " +
                        "ORDER BY estimated_end_time ASC", patientId);
        List<Map<String, Object>> infusionList = new ArrayList<>();
        for (Map<String, Object> i : infusions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", toLong(i.get("id")));
            item.put("capacity", i.get("capacity"));
            item.put("usageType", i.get("usage_type"));
            item.put("startTime", toStr(i.get("start_time")));
            item.put("estimatedEndTime", toStr(i.get("estimated_end_time")));
            item.put("nurseName", i.get("nurse_name"));
            LocalDateTime end = toLocal(i.get("estimated_end_time"));
            item.put("remainingMinutes", end == null ? null : java.time.Duration.between(LocalDateTime.now(), end).toMinutes());
            infusionList.add(item);
        }
        result.put("infusions", infusionList);

        // 最近 3 次交班（本病区）
        Long wardId = toLong(patient.get("ward_id"));
        List<Map<String, Object>> handovers = jdbcTemplate.queryForList(
                "SELECT id, shift_date, shift_type, status, submit_by_name, submit_time, receiver_name, receive_time, memo, report_text " +
                        "FROM shift_handover WHERE ward_id = ? ORDER BY shift_date DESC, id DESC LIMIT 3", wardId);
        List<Map<String, Object>> handoverList = new ArrayList<>();
        for (Map<String, Object> h : handovers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", toLong(h.get("id")));
            item.put("shiftDate", toDateStr(h.get("shift_date")));
            item.put("shiftType", h.get("shift_type"));
            item.put("status", h.get("status"));
            item.put("submitByName", h.get("submit_by_name"));
            item.put("submitTime", toStr(h.get("submit_time")));
            item.put("receiverName", h.get("receiver_name"));
            item.put("receiveTime", toStr(h.get("receive_time")));
            String memo = h.get("memo") == null ? "" : String.valueOf(h.get("memo"));
            item.put("memo", memo.length() > 80 ? memo.substring(0, 80) + "…" : memo);
            handoverList.add(item);
        }
        result.put("handovers", handoverList);

        // 未处理反馈
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM record_correction_request WHERE patient_id = ? AND status = 'PENDING'", Integer.class, patientId);
        result.put("pendingFeedback", pending == null ? 0 : pending);

        // 今日记录条数
        Integer intakeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM intake_record WHERE patient_id = ? AND record_date = ?",
                Integer.class, patientId, Date.valueOf(LocalDate.now()));
        Integer outputCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM output_record WHERE patient_id = ? AND record_date = ?",
                Integer.class, patientId, Date.valueOf(LocalDate.now()));
        Map<String, Object> recordCount = new LinkedHashMap<>();
        recordCount.put("intake", intakeCount == null ? 0 : intakeCount);
        recordCount.put("output", outputCount == null ? 0 : outputCount);
        result.put("recordCount", recordCount);

        return result;
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

    private static String toDateStr(Object o) {
        if (o == null) return null;
        if (o instanceof Date) return ((Date) o).toLocalDate().toString();
        String s = String.valueOf(o);
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    private static String toStr(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp) {
            return ((Timestamp) o).toLocalDateTime().toString().replace('T', ' ');
        }
        return String.valueOf(o);
    }

    private static LocalDateTime toLocal(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp) return ((Timestamp) o).toLocalDateTime();
        return null;
    }

    /** 供前端格式化使用 */
    public static String formatAmount(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
