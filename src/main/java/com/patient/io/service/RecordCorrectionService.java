package com.patient.io.service;

import com.patient.io.common.UserContext;
import com.patient.io.common.BusinessException;
import com.patient.io.dto.CorrectionRequestDTO;
import com.patient.io.vo.CorrectionRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 「这条记错了」待核对请求
 *
 * 设计原则：患者端不开放编辑历史记录（一开放，整套数据的可信度就没了），
 * 但患者必须有一个说话的地方——提交请求，护士核对后由护士修正，全程留痕。
 */
@Slf4j
@Service
public class RecordCorrectionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 患者提交核对请求
     */
    public void submit(CorrectionRequestDTO dto) {
        boolean isIntake = "INTAKE".equals(dto.getCategory());
        String sql = isIntake
                ? "SELECT patient_id, amount, item_name, record_time, remark FROM intake_record WHERE id = ?"
                : "SELECT patient_id, amount, output_type, record_time, remark FROM output_record WHERE id = ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, dto.getRecordId());
        if (rows.isEmpty()) {
            throw new BusinessException("这条记录已不存在，可能已被撤销");
        }
        Map<String, Object> row = rows.get(0);
        Object pidObj = row.get("patient_id");
        if (pidObj == null) {
            throw new BusinessException("这条记录缺少患者信息");
        }
        Long patientId = Long.valueOf(String.valueOf(pidObj));

        // 患者只能对「自己床位」的记录提请求，避免家属点到别人
        if ("PATIENT".equals(UserContext.getRole())) {
            List<Map<String, Object>> owner = jdbcTemplate.queryForList(
                    "SELECT bound_user_id FROM patient WHERE id = ?", patientId);
            Long boundUserId = null;
            if (!owner.isEmpty() && owner.get(0).get("bound_user_id") != null) {
                boundUserId = Long.valueOf(String.valueOf(owner.get(0).get("bound_user_id")));
            }
            if (boundUserId == null || !boundUserId.equals(UserContext.getUserId())) {
                throw new BusinessException(403, "只能对自己床位的记录反馈");
            }
        }

        // 同一条记录已有待处理请求时不重复插入
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM record_correction_request WHERE record_id = ? AND category = ? AND status = 'PENDING'",
                Integer.class, dto.getRecordId(), dto.getCategory());
        if (exists != null && exists > 0) {
            return;
        }

        Object amount = row.get("amount");
        String itemName = String.valueOf(isIntake ? row.get("item_name") : row.get("output_type"));
        Object recordTime = row.get("record_time");
        Object remark = row.get("remark");

        jdbcTemplate.update(
                "INSERT INTO record_correction_request " +
                        "(record_id, category, patient_id, amount, item_name, record_time, remark, reason, requester_id, requester_role, status, create_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)",
                dto.getRecordId(), dto.getCategory(), patientId, amount, itemName, recordTime,
                remark == null ? "" : String.valueOf(remark),
                dto.getReason() == null ? "" : dto.getReason(),
                UserContext.getUserId(), UserContext.getRole(), LocalDateTime.now());

        log.info("患者提交记错反馈: recordId={}, category={}, patientId={}, reason={}",
                dto.getRecordId(), dto.getCategory(), patientId, dto.getReason());
    }

    /**
     * 待核对列表（护士端）
     */
    public List<CorrectionRequestVO> listPending(Long wardId, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.record_id, r.category, r.patient_id, r.amount, r.item_name, " +
                        "r.record_time, r.remark, r.reason, r.status, r.create_time, " +
                        "p.patient_name, p.bed_no " +
                        "FROM record_correction_request r LEFT JOIN patient p ON r.patient_id = p.id " +
                        "WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            sql.append("AND r.status = ? ");
            args.add(status);
        }
        if (wardId != null) {
            sql.append("AND p.ward_id = ? ");
            args.add(wardId);
        }
        sql.append("ORDER BY r.create_time DESC LIMIT 50");

        return jdbcTemplate.query(sql.toString(), (rs, i) -> {
            CorrectionRequestVO vo = new CorrectionRequestVO();
            vo.setId(rs.getLong("id"));
            vo.setRecordId(rs.getLong("record_id"));
            vo.setCategory(rs.getString("category"));
            vo.setPatientId(rs.getLong("patient_id"));
            vo.setAmount(rs.getBigDecimal("amount"));
            vo.setItemName(rs.getString("item_name"));
            Timestamp rt = rs.getTimestamp("record_time");
            vo.setRecordTime(rt == null ? null : rt.toLocalDateTime());
            vo.setRemark(rs.getString("remark"));
            vo.setReason(rs.getString("reason"));
            vo.setStatus(rs.getString("status"));
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(ct == null ? null : ct.toLocalDateTime());
            vo.setPatientName(rs.getString("patient_name"));
            vo.setBedNo(rs.getString("bed_no"));
            return vo;
        }, args.toArray());
    }

    /**
     * 护士处理完（改了或确认无误）后标记已处理
     */
    public void markHandled(Long id) {
        jdbcTemplate.update(
                "UPDATE record_correction_request SET status = 'HANDLED', handler_id = ?, handle_time = ? WHERE id = ?",
                UserContext.getUserId(), LocalDateTime.now(), id);
    }
}
