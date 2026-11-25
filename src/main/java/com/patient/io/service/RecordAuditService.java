package com.patient.io.service;

import com.patient.io.common.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 出入量记录删除留痕
 * 临床数据被谁删了、删了什么、为什么删，必须可追溯
 */
@Slf4j
@Service
public class RecordAuditService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void logIntakeDelete(Long recordId, String reason) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT patient_id, amount, item_name, record_time FROM intake_record WHERE id = ?", recordId);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                insert("INTAKE", recordId, row.get("patient_id"), row.get("amount"),
                        String.valueOf(row.get("item_name")), row.get("record_time"), reason);
            }
        } catch (Exception e) {
            log.warn("记录入量删除留痕失败: {}", e.getMessage());
        }
    }

    public void logOutputDelete(Long recordId, String reason) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT patient_id, amount, output_type, record_time FROM output_record WHERE id = ?", recordId);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                insert("OUTPUT", recordId, row.get("patient_id"), row.get("amount"),
                        String.valueOf(row.get("output_type")), row.get("record_time"), reason);
            }
        } catch (Exception e) {
            log.warn("记录出量删除留痕失败: {}", e.getMessage());
        }
    }

    private void insert(String category, Long recordId, Object patientId, Object amount,
                        String itemName, Object recordTime, String reason) {
        jdbcTemplate.update(
                "INSERT INTO record_delete_log (category, record_id, patient_id, amount, item_name, record_time, reason, operator_id, operator_role, create_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                category, recordId, patientId, amount, itemName, recordTime,
                reason == null ? "" : reason,
                UserContext.getUserId(), UserContext.getRole(), LocalDateTime.now());
    }
}
