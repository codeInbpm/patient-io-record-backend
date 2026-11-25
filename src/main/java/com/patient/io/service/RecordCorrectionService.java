package com.patient.io.service;

import com.patient.io.common.UserContext;
import com.patient.io.common.BusinessException;
import com.patient.io.dto.CorrectionRequestDTO;
import com.patient.io.dto.SymptomAlertDTO;
import com.patient.io.vo.CorrectionRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private FluidRecordService fluidRecordService;

    @Autowired
    private RecordAuditService recordAuditService;

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
        checkPatientOwner(patientId);

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
     * 患者上报异常症状（血尿/黑便/水样便）→ 护士端待处理。
     *
     * 复用 record_correction_request 表，category = SYMPTOM、record_id = 0：
     * 症状可能发生在保存记录之前，没有 record_id 可挂；单开一张表只是多一次建表运维。
     * record_id = 0 不会被流水的状态回传查询命中（那里按真实 record_id 拼 IN）。
     */
    public void reportSymptom(SymptomAlertDTO dto) {
        checkPatientOwner(dto.getPatientId());

        // 同一患者同一条症状还挂着就不重复插，避免患者连点把护士的列表刷满
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM record_correction_request " +
                        "WHERE patient_id = ? AND category = 'SYMPTOM' AND status = 'PENDING' AND item_name = ?",
                Integer.class, dto.getPatientId(), dto.getSymptom());
        if (exists != null && exists > 0) {
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO record_correction_request " +
                        "(record_id, category, patient_id, amount, item_name, record_time, remark, reason, " +
                        "requester_id, requester_role, status, create_time) " +
                        "VALUES (0, 'SYMPTOM', ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)",
                dto.getPatientId(),
                dto.getAmount(),
                dto.getSymptom(),
                LocalDateTime.now(),
                dto.getRemark(),
                (dto.getCategory() == null ? "OUTPUT" : dto.getCategory()) + "|" + dto.getSymptom(),
                UserContext.getUserId(),
                UserContext.getRole(),
                LocalDateTime.now());
        log.warn("患者上报异常症状: patientId={}, symptom={}", dto.getPatientId(), dto.getSymptom());
    }

    /**
     * 患者只能对自己的床位操作，避免家属点到别人的床
     */
    private void checkPatientOwner(Long patientId) {
        if (!"PATIENT".equals(UserContext.getRole())) {
            return;
        }
        List<Map<String, Object>> owner = jdbcTemplate.queryForList(
                "SELECT bound_user_id FROM patient WHERE id = ?", patientId);
        Long boundUserId = null;
        if (!owner.isEmpty() && owner.get(0).get("bound_user_id") != null) {
            boundUserId = Long.valueOf(String.valueOf(owner.get(0).get("bound_user_id")));
        }
        if (boundUserId == null || !boundUserId.equals(UserContext.getUserId())) {
            throw new BusinessException(403, "只能对自己床位的情况反馈");
        }
    }

    /**
     * 待核对列表（护士端）
     */
    public List<CorrectionRequestVO> listPending(Long wardId, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.record_id, r.category, r.patient_id, r.amount, r.item_name, " +
                        "r.record_time, r.remark, r.reason, r.status, r.create_time, r.handle_time, " +
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

        return jdbcTemplate.query(sql.toString(), CORRECTION_ROW, args.toArray());
    }

    /**
     * 护士驳回：这条数据不可信，不留在今天的出入量里，退回给患者重新记。
     * 护士只做「对不对」的判断，不做代改——谁录的谁负责，这条线不能断。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT record_id, category, status FROM record_correction_request WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new BusinessException("这条反馈不存在");
        }
        Map<String, Object> row = rows.get(0);
        if (!"PENDING".equals(String.valueOf(row.get("status")))) {
            throw new BusinessException("这条反馈已经处理过了");
        }
        Long recordId = Long.valueOf(String.valueOf(row.get("record_id")));
        String category = String.valueOf(row.get("category"));

        // 必须先改成 REJECTED 再删记录：删除会自动把 PENDING 收口成 HANDLED，顺序反了状态就错了
        jdbcTemplate.update(
                "UPDATE record_correction_request SET status = 'REJECTED', handler_id = ?, handle_time = ? WHERE id = ?",
                UserContext.getUserId(), LocalDateTime.now(), id);

        String reason = "护士驳回：患者反馈记错，待重新记录（反馈单号 " + id + "）";
        if ("INTAKE".equals(category)) {
            recordAuditService.logIntakeDelete(recordId, reason);
            fluidRecordService.deleteIntakeRecord(recordId);
        } else {
            recordAuditService.logOutputDelete(recordId, reason);
            fluidRecordService.deleteOutputRecord(recordId);
        }
        log.info("护士驳回记错反馈: id={}, recordId={}, category={}", id, recordId, category);
    }

    /**
     * 患者端：被护士退回、还没重记的条目
     */
    public List<CorrectionRequestVO> listMyRejected() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Collections.emptyList();
        }
        List<Long> patientIds = jdbcTemplate.queryForList(
                "SELECT id FROM patient WHERE bound_user_id = ?", Long.class, userId);
        if (patientIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = patientIds.stream().map(p -> "?").collect(Collectors.joining(","));
        String sql = "SELECT r.id, r.record_id, r.category, r.patient_id, r.amount, r.item_name, " +
                "r.record_time, r.remark, r.reason, r.status, r.create_time, r.handle_time, " +
                "p.patient_name, p.bed_no " +
                "FROM record_correction_request r LEFT JOIN patient p ON r.patient_id = p.id " +
                "WHERE r.status = 'REJECTED' AND r.patient_id IN (" + placeholders + ") " +
                "ORDER BY r.handle_time DESC LIMIT 20";
        return jdbcTemplate.query(sql, CORRECTION_ROW, patientIds.toArray());
    }

    /**
     * 患者按驳回要求重新记完后，把这条从「待重记」里去掉
     */
    public void markReRecorded(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT patient_id, status FROM record_correction_request WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new BusinessException("这条反馈不存在");
        }
        Map<String, Object> row = rows.get(0);
        if ("PATIENT".equals(UserContext.getRole())) {
            Long patientId = Long.valueOf(String.valueOf(row.get("patient_id")));
            List<Map<String, Object>> owner = jdbcTemplate.queryForList(
                    "SELECT bound_user_id FROM patient WHERE id = ?", patientId);
            Long boundUserId = null;
            if (!owner.isEmpty() && owner.get(0).get("bound_user_id") != null) {
                boundUserId = Long.valueOf(String.valueOf(owner.get(0).get("bound_user_id")));
            }
            if (boundUserId == null || !boundUserId.equals(UserContext.getUserId())) {
                throw new BusinessException(403, "只能处理自己床位的记录");
            }
        }
        jdbcTemplate.update(
                "UPDATE record_correction_request SET status = 'RECORDED', handle_time = ? WHERE id = ? AND status = 'REJECTED'",
                LocalDateTime.now(), id);
    }

    private static final org.springframework.jdbc.core.RowMapper<CorrectionRequestVO> CORRECTION_ROW = (rs, i) -> {
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
        Timestamp ht = rs.getTimestamp("handle_time");
        vo.setHandleTime(ht == null ? null : ht.toLocalDateTime());
        vo.setPatientName(rs.getString("patient_name"));
        vo.setBedNo(rs.getString("bed_no"));
        return vo;
    };

    /**
     * 护士处理完（改了或确认无误）后标记已处理
     */
    public void markHandled(Long id) {
        jdbcTemplate.update(
                "UPDATE record_correction_request SET status = 'HANDLED', handler_id = ?, handle_time = ? WHERE id = ?",
                UserContext.getUserId(), LocalDateTime.now(), id);
    }
}
