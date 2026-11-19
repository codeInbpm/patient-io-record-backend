package com.patient.io.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.BusinessException;
import com.patient.io.common.SimpleExcelExporter;
import com.patient.io.common.UserContext;
import com.patient.io.dto.FluidQueryDTO;
import com.patient.io.dto.RecordIntakeDTO;
import com.patient.io.dto.RecordOutputDTO;
import com.patient.io.entity.*;
import com.patient.io.mapper.*;
import com.patient.io.service.FluidRecordService;
import com.patient.io.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FluidRecordServiceImpl implements FluidRecordService {

    @Autowired
    private IntakeRecordMapper intakeRecordMapper;

    @Autowired
    private OutputRecordMapper outputRecordMapper;

    @Autowired
    private DailySummaryMapper dailySummaryMapper;

    @Autowired
    private PatientMapper patientMapper;

    @Autowired
    private NursePatientRelationMapper relationMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private NurseMapper nurseMapper;

    @Autowired
    private WardMapper wardMapper;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private void checkRecordPermission(Patient patient) {
        String role = UserContext.getRole();
        Long userId = UserContext.getUserId();

        if ("HEAD_NURSE".equals(role) || "SUPER_ADMIN".equals(role) || "HOSPITAL_ADMIN".equals(role)) {
            return;
        }
        if ("PATIENT".equals(role)) {
            if (!userId.equals(patient.getBoundUserId())) {
                throw new BusinessException(403, "患者只能录入本人的出入量数据");
            }
            return;
        }
        if ("NURSE".equals(role)) {
            SysUser user = userMapper.selectById(userId);
            if (user == null || user.getBindRefId() == null) {
                throw new BusinessException(403, "请先绑定护士工号");
            }
            Long nurseId = user.getBindRefId();
            // 校验当前护士是否勾选管辖了该床位/病人（手动取消勾选后，设置为不能继续给病人记录）
            LambdaQueryWrapper<NursePatientRelation> query = new LambdaQueryWrapper<>();
            query.eq(NursePatientRelation::getNurseId, nurseId)
                    .and(w -> w.eq(NursePatientRelation::getPatientId, patient.getId())
                            .or().eq(NursePatientRelation::getBedId, patient.getBedId()));
            if (relationMapper.selectCount(query) == 0) {
                throw new BusinessException(403, "您当前未管辖该床位（或已交班取消勾选），无法继续为其录入记录，仅支持查询");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordIntake(RecordIntakeDTO dto) {
        Patient patient = patientMapper.selectById(dto.getPatientId());
        if (patient == null) {
            throw new BusinessException("病人不存在");
        }
        checkRecordPermission(patient);

        IntakeRecord record = new IntakeRecord();
        record.setPatientId(patient.getId());
        record.setWardId(patient.getWardId());
        record.setIntakeType(dto.getIntakeType());
        record.setSubType(dto.getSubType());
        record.setItemName(dto.getItemName());
        record.setAmount(dto.getAmount());
        record.setRecordTime(dto.getRecordTime());
        record.setRecordDate(dto.getRecordTime().toLocalDate());
        Long currentUserId = UserContext.getUserId();
        String currentRole = UserContext.getRole();
        boolean isSelfPatient = "PATIENT".equalsIgnoreCase(currentRole)
                || (currentUserId != null && currentUserId.equals(patient.getBoundUserId()));
        record.setRecorderId(currentUserId != null ? currentUserId : 0L);
        record.setRecorderRole(isSelfPatient ? "PATIENT" : (StrUtil.isNotBlank(currentRole) ? currentRole : "NURSE"));
        record.setRemark(dto.getRemark());

        intakeRecordMapper.insert(record);

        // 刷新当日汇总
        refreshDailySummary(patient.getId(), record.getRecordDate());
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordOutput(RecordOutputDTO dto) {
        Patient patient = patientMapper.selectById(dto.getPatientId());
        if (patient == null) {
            throw new BusinessException("病人不存在");
        }
        checkRecordPermission(patient);

        OutputRecord record = new OutputRecord();
        record.setPatientId(patient.getId());
        record.setWardId(patient.getWardId());
        record.setOutputType(dto.getOutputType());
        record.setAmount(dto.getAmount());
        record.setNatureOrColor(dto.getNatureOrColor());
        record.setRecordTime(dto.getRecordTime());
        record.setRecordDate(dto.getRecordTime().toLocalDate());
        Long currentUserId = UserContext.getUserId();
        String currentRole = UserContext.getRole();
        boolean isSelfPatient = "PATIENT".equalsIgnoreCase(currentRole)
                || (currentUserId != null && currentUserId.equals(patient.getBoundUserId()));
        record.setRecorderId(currentUserId != null ? currentUserId : 0L);
        record.setRecorderRole(isSelfPatient ? "PATIENT" : (StrUtil.isNotBlank(currentRole) ? currentRole : "NURSE"));
        record.setRemark(dto.getRemark());

        outputRecordMapper.insert(record);

        refreshDailySummary(patient.getId(), record.getRecordDate());
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteIntakeRecord(Long recordId) {
        IntakeRecord record = intakeRecordMapper.selectById(recordId);
        if (record != null) {
            intakeRecordMapper.deleteById(recordId);
            refreshDailySummary(record.getPatientId(), record.getRecordDate());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOutputRecord(Long recordId) {
        OutputRecord record = outputRecordMapper.selectById(recordId);
        if (record != null) {
            outputRecordMapper.deleteById(recordId);
            refreshDailySummary(record.getPatientId(), record.getRecordDate());
        }
    }

    @Override
    public DailyBalanceVO getDailyBalance(Long patientId, LocalDate date) {
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) {
            throw new BusinessException("病人档案不存在");
        }

        // 查询当日入量明细
        LambdaQueryWrapper<IntakeRecord> inWrapper = new LambdaQueryWrapper<>();
        inWrapper.eq(IntakeRecord::getPatientId, patientId)
                .eq(IntakeRecord::getRecordDate, date)
                .orderByAsc(IntakeRecord::getRecordTime);
        List<IntakeRecord> inList = intakeRecordMapper.selectList(inWrapper);

        // 查询当日出量明细
        LambdaQueryWrapper<OutputRecord> outWrapper = new LambdaQueryWrapper<>();
        outWrapper.eq(OutputRecord::getPatientId, patientId)
                .eq(OutputRecord::getRecordDate, date)
                .orderByAsc(OutputRecord::getRecordTime);
        List<OutputRecord> outList = outputRecordMapper.selectList(outWrapper);

        // 收集所有录入人ID，统一解析其姓名与展示文本
        Set<Long> userIds = new HashSet<>();
        inList.forEach(r -> { if (r.getRecorderId() != null) userIds.add(r.getRecorderId()); });
        outList.forEach(r -> { if (r.getRecorderId() != null) userIds.add(r.getRecorderId()); });
        Map<Long, RecorderInfo> recorderMap = resolveRecorders(userIds);

        BigDecimal totalIntake = BigDecimal.ZERO;
        BigDecimal oralIntake = BigDecimal.ZERO;
        BigDecimal ivIntake = BigDecimal.ZERO;
        BigDecimal nasogastricIntake = BigDecimal.ZERO;
        BigDecimal otherIntake = BigDecimal.ZERO;

        List<RecordItemVO> timeline = new ArrayList<>();

        for (IntakeRecord r : inList) {
            totalIntake = totalIntake.add(r.getAmount());
            if ("ORAL".equalsIgnoreCase(r.getIntakeType())) {
                oralIntake = oralIntake.add(r.getAmount());
            } else if ("IV".equalsIgnoreCase(r.getIntakeType())) {
                ivIntake = ivIntake.add(r.getAmount());
            } else if ("NASOGASTRIC".equalsIgnoreCase(r.getIntakeType())) {
                nasogastricIntake = nasogastricIntake.add(r.getAmount());
            } else {
                otherIntake = otherIntake.add(r.getAmount());
            }

            RecorderInfo info = recorderMap.get(r.getRecorderId());
            boolean isPatientRecord = "PATIENT".equalsIgnoreCase(r.getRecorderRole())
                    || (patient.getBoundUserId() != null && patient.getBoundUserId().equals(r.getRecorderId()))
                    || (info != null && "PATIENT".equalsIgnoreCase(info.role));

            RecordItemVO item = new RecordItemVO();
            item.setId(r.getId());
            item.setCategory("INTAKE");
            item.setType(r.getIntakeType());
            item.setTypeText("入量 - " + formatIntakeType(r.getIntakeType(), r.getItemName()));
            item.setItemName(r.getItemName());
            item.setAmount(r.getAmount());
            item.setRecordTime(r.getRecordTime());
            item.setRecorderId(r.getRecorderId());
            item.setRecorderRole(isPatientRecord ? "PATIENT" : r.getRecorderRole());
            if (isPatientRecord) {
                item.setRecorderName("本人");
                item.setRecorderText("本人录入");
            } else if (info != null) {
                item.setRecorderName(info.name);
                item.setRecorderText(info.displayText);
            } else {
                item.setRecorderName("护士");
                item.setRecorderText("护士录入");
            }
            item.setRemark(r.getRemark());
            timeline.add(item);
        }

        BigDecimal totalOutput = BigDecimal.ZERO;
        BigDecimal urineOutput = BigDecimal.ZERO;
        BigDecimal stoolOutput = BigDecimal.ZERO;
        BigDecimal drainageOutput = BigDecimal.ZERO;
        BigDecimal otherOutput = BigDecimal.ZERO;

        for (OutputRecord r : outList) {
            totalOutput = totalOutput.add(r.getAmount());
            if ("URINE".equalsIgnoreCase(r.getOutputType())) {
                urineOutput = urineOutput.add(r.getAmount());
            } else if ("STOOL".equalsIgnoreCase(r.getOutputType())) {
                stoolOutput = stoolOutput.add(r.getAmount());
            } else if ("GASTRO_DRAINAGE".equalsIgnoreCase(r.getOutputType()) || "CHEST_DRAINAGE".equalsIgnoreCase(r.getOutputType()) || "OTHER_DRAINAGE".equalsIgnoreCase(r.getOutputType())) {
                drainageOutput = drainageOutput.add(r.getAmount());
            } else {
                otherOutput = otherOutput.add(r.getAmount());
            }

            RecorderInfo info = recorderMap.get(r.getRecorderId());
            boolean isPatientRecord = "PATIENT".equalsIgnoreCase(r.getRecorderRole())
                    || (patient.getBoundUserId() != null && patient.getBoundUserId().equals(r.getRecorderId()))
                    || (info != null && "PATIENT".equalsIgnoreCase(info.role));

            RecordItemVO item = new RecordItemVO();
            item.setId(r.getId());
            item.setCategory("OUTPUT");
            item.setType(r.getOutputType());
            item.setTypeText("出量 - " + formatOutputType(r.getOutputType()));
            item.setItemName(formatOutputType(r.getOutputType()));
            item.setAmount(r.getAmount());
            item.setNatureOrColor(r.getNatureOrColor());
            item.setRecordTime(r.getRecordTime());
            item.setRecorderId(r.getRecorderId());
            item.setRecorderRole(isPatientRecord ? "PATIENT" : r.getRecorderRole());
            if (isPatientRecord) {
                item.setRecorderName("本人");
                item.setRecorderText("本人录入");
            } else if (info != null) {
                item.setRecorderName(info.name);
                item.setRecorderText(info.displayText);
            } else {
                item.setRecorderName("护士");
                item.setRecorderText("护士录入");
            }
            item.setRemark(r.getRemark());
            timeline.add(item);
        }

        // 时间倒序排列流水
        timeline.sort(Comparator.comparing(RecordItemVO::getRecordTime).reversed());

        BigDecimal netBalance = totalIntake.subtract(totalOutput);

        DailyBalanceVO vo = new DailyBalanceVO();
        vo.setPatientId(patient.getId());
        vo.setPatientName(patient.getPatientName());
        vo.setBedNo(patient.getBedNo());
        vo.setDate(date);

        vo.setTotalIntake(totalIntake);
        vo.setTotalOutput(totalOutput);
        vo.setNetBalance(netBalance);

        vo.setOralIntake(oralIntake);
        vo.setIvIntake(ivIntake);
        vo.setNasogastricIntake(nasogastricIntake);
        vo.setOtherIntake(otherIntake);

        vo.setUrineOutput(urineOutput);
        vo.setStoolOutput(stoolOutput);
        vo.setDrainageOutput(drainageOutput);
        vo.setOtherOutput(otherOutput);

        // 临床异常预警与三大标红/标黄规则：
        // 1. 24小时 尿量<400ML，标红
        // 2. 入量-出量差值>1000ML，标红
        // 3. 静脉输液>3000ML，标黄色
        boolean isUrineAlert = false;
        boolean isBalanceAlert = false;
        boolean isIvAlert = false;
        StringBuilder reason = new StringBuilder();

        boolean hasAnyActivity = totalIntake.compareTo(BigDecimal.ZERO) > 0 || totalOutput.compareTo(BigDecimal.ZERO) > 0;
        if ((date.isBefore(LocalDate.now()) || hasAnyActivity) && urineOutput.compareTo(new BigDecimal("400")) < 0) {
            isUrineAlert = true;
            reason.append("24小时少尿(<400ml/标红); ");
        }
        if (netBalance.compareTo(new BigDecimal("1000")) > 0) {
            isBalanceAlert = true;
            reason.append("入量-出量差值>1000ml(严重正平衡/标红); ");
        }
        if (ivIntake.compareTo(new BigDecimal("3000")) > 0) {
            isIvAlert = true;
            reason.append("静脉输液>3000ml(高容量负荷/标黄); ");
        }

        boolean isAbnormal = isUrineAlert || isBalanceAlert || isIvAlert;
        vo.setIsAbnormal(isAbnormal);
        vo.setIsUrineAlert(isUrineAlert);
        vo.setIsBalanceAlert(isBalanceAlert);
        vo.setIsIvAlert(isIvAlert);
        vo.setAbnormalReason(reason.toString().trim());
        vo.setItems(timeline);

        return vo;
    }

    @Override
    public List<DailySummary> getHistoricalDailySummaries(Long patientId, Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        }
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        LambdaQueryWrapper<DailySummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DailySummary::getPatientId, patientId)
                .ge(DailySummary::getRecordDate, startDate)
                .orderByAsc(DailySummary::getRecordDate);
        return dailySummaryMapper.selectList(wrapper);
    }

    @Override
    public void refreshDailySummary(Long patientId, LocalDate date) {
        DailyBalanceVO vo = getDailyBalance(patientId, date);
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) return;

        LambdaQueryWrapper<DailySummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DailySummary::getPatientId, patientId)
                .eq(DailySummary::getRecordDate, date);
        DailySummary summary = dailySummaryMapper.selectOne(wrapper);

        boolean isNew = false;
        if (summary == null) {
            summary = new DailySummary();
            summary.setPatientId(patientId);
            summary.setWardId(patient.getWardId());
            summary.setRecordDate(date);
            isNew = true;
        }

        summary.setTotalIntake(vo.getTotalIntake());
        summary.setTotalOutput(vo.getTotalOutput());
        summary.setNetBalance(vo.getNetBalance());
        summary.setOralIntake(vo.getOralIntake());
        summary.setIvIntake(vo.getIvIntake());
        summary.setNasogastricIntake(vo.getNasogastricIntake());
        summary.setUrineOutput(vo.getUrineOutput());
        summary.setStoolOutput(vo.getStoolOutput());
        summary.setDrainageOutput(vo.getDrainageOutput());
        summary.setIsAbnormal(Boolean.TRUE.equals(vo.getIsAbnormal()) ? 1 : 0);

        if (isNew) {
            dailySummaryMapper.insert(summary);
        } else {
            dailySummaryMapper.updateById(summary);
        }
    }

    @Override
    public FluidQueryVO queryFluidRecords(FluidQueryDTO queryDTO) {
        if (queryDTO == null) {
            queryDTO = new FluidQueryDTO();
        }

        // 1. 确定日期范围
        LocalDate startDate = queryDTO.getStartDate();
        LocalDate endDate = queryDTO.getEndDate();
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(6); // 默认最近7天
        }
        if (startDate.isAfter(endDate)) {
            LocalDate tmp = startDate;
            startDate = endDate;
            endDate = tmp;
        }

        // 2. 确定病患范围
        String currentRole = UserContext.getRole();
        Long currentUserId = UserContext.getUserId();

        List<Patient> targetPatients = new ArrayList<>();
        if ("PATIENT".equals(currentRole)) {
            // 患者角色只允许查询自己
            SysUser user = userMapper.selectById(currentUserId);
            if (user != null && user.getBindRefId() != null) {
                Patient p = patientMapper.selectById(user.getBindRefId());
                if (p != null) targetPatients.add(p);
            }
        } else {
            // 护士或管理端：不做查询限制，可查询全病区或多个病患
            LambdaQueryWrapper<Patient> pWrapper = new LambdaQueryWrapper<>();
            if (queryDTO.getPatientIds() != null && !queryDTO.getPatientIds().isEmpty()) {
                pWrapper.in(Patient::getId, queryDTO.getPatientIds());
            } else if (queryDTO.getWardId() != null) {
                pWrapper.eq(Patient::getWardId, queryDTO.getWardId());
            }
            pWrapper.eq(Patient::getStatus, 1).orderByAsc(Patient::getBedNo);
            targetPatients = patientMapper.selectList(pWrapper);
        }

        FluidQueryVO resultVO = new FluidQueryVO();
        if (targetPatients.isEmpty()) {
            return resultVO;
        }

        List<Long> patientIds = targetPatients.stream().map(Patient::getId).collect(Collectors.toList());
        Map<Long, Patient> patientMap = targetPatients.stream().collect(Collectors.toMap(Patient::getId, Function.identity()));

        // 查询病区名称
        Set<Long> wardIds = targetPatients.stream().map(Patient::getWardId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> wardNameMap = new HashMap<>();
        if (!wardIds.isEmpty()) {
            wardNameMap = wardMapper.selectBatchIds(wardIds).stream()
                    .collect(Collectors.toMap(Ward::getId, Ward::getWardName, (a, b) -> a));
        }

        // 查询责任护士（只显示最后交班打勾者）
        Map<Long, String> assignedNurseMap = new HashMap<>();
        LambdaQueryWrapper<NursePatientRelation> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.in(NursePatientRelation::getPatientId, patientIds)
                .orderByDesc(NursePatientRelation::getId);
        List<NursePatientRelation> relations = relationMapper.selectList(relWrapper);
        if (!relations.isEmpty()) {
            Set<Long> nurseIds = relations.stream().map(NursePatientRelation::getNurseId).collect(Collectors.toSet());
            Map<Long, String> nNameMap = nurseMapper.selectBatchIds(nurseIds).stream()
                    .collect(Collectors.toMap(Nurse::getId, Nurse::getRealName, (a, b) -> a));
            for (NursePatientRelation r : relations) {
                assignedNurseMap.putIfAbsent(r.getPatientId(), nNameMap.get(r.getNurseId()));
            }
        }

        // 3. 查入量
        List<IntakeRecord> inRecords = new ArrayList<>();
        if (queryDTO.getCategory() == null || "ALL".equalsIgnoreCase(queryDTO.getCategory()) || "INTAKE".equalsIgnoreCase(queryDTO.getCategory())) {
            LambdaQueryWrapper<IntakeRecord> inW = new LambdaQueryWrapper<>();
            inW.in(IntakeRecord::getPatientId, patientIds)
               .ge(IntakeRecord::getRecordDate, startDate)
               .le(IntakeRecord::getRecordDate, endDate);
            if (StrUtil.isNotBlank(queryDTO.getIntakeType())) {
                inW.eq(IntakeRecord::getIntakeType, queryDTO.getIntakeType());
            }
            if (StrUtil.isNotBlank(queryDTO.getRecorderRole()) && !"ALL".equalsIgnoreCase(queryDTO.getRecorderRole())) {
                if ("NURSE".equalsIgnoreCase(queryDTO.getRecorderRole())) {
                    inW.in(IntakeRecord::getRecorderRole, "NURSE", "HEAD_NURSE");
                } else {
                    inW.eq(IntakeRecord::getRecorderRole, queryDTO.getRecorderRole());
                }
            }
            inRecords = intakeRecordMapper.selectList(inW);
        }

        // 4. 查出量
        List<OutputRecord> outRecords = new ArrayList<>();
        if (queryDTO.getCategory() == null || "ALL".equalsIgnoreCase(queryDTO.getCategory()) || "OUTPUT".equalsIgnoreCase(queryDTO.getCategory())) {
            LambdaQueryWrapper<OutputRecord> outW = new LambdaQueryWrapper<>();
            outW.in(OutputRecord::getPatientId, patientIds)
                .ge(OutputRecord::getRecordDate, startDate)
                .le(OutputRecord::getRecordDate, endDate);
            if (StrUtil.isNotBlank(queryDTO.getOutputType())) {
                outW.eq(OutputRecord::getOutputType, queryDTO.getOutputType());
            }
            if (StrUtil.isNotBlank(queryDTO.getRecorderRole()) && !"ALL".equalsIgnoreCase(queryDTO.getRecorderRole())) {
                if ("NURSE".equalsIgnoreCase(queryDTO.getRecorderRole())) {
                    outW.in(OutputRecord::getRecorderRole, "NURSE", "HEAD_NURSE");
                } else {
                    outW.eq(OutputRecord::getRecorderRole, queryDTO.getRecorderRole());
                }
            }
            outRecords = outputRecordMapper.selectList(outW);
        }

        // 收集所有录入人ID统一解析
        Set<Long> userIds = new HashSet<>();
        inRecords.forEach(r -> { if (r.getRecorderId() != null) userIds.add(r.getRecorderId()); });
        outRecords.forEach(r -> { if (r.getRecorderId() != null) userIds.add(r.getRecorderId()); });
        Map<Long, RecorderInfo> recorderMap = resolveRecorders(userIds);

        // 5. 组合流水明细
        List<FluidRecordDetailVO> details = new ArrayList<>();
        String kw = StrUtil.trim(queryDTO.getKeyword());

        for (IntakeRecord r : inRecords) {
            Patient p = patientMap.get(r.getPatientId());
            RecorderInfo rInfo = recorderMap.get(r.getRecorderId());
            boolean isPatientRecord = "PATIENT".equalsIgnoreCase(r.getRecorderRole())
                    || (p != null && p.getBoundUserId() != null && p.getBoundUserId().equals(r.getRecorderId()))
                    || (rInfo != null && "PATIENT".equalsIgnoreCase(rInfo.role));
            String recName = isPatientRecord ? "本人" : (rInfo != null ? rInfo.name : "护士");
            String recText = isPatientRecord ? "本人录入" : (rInfo != null ? rInfo.displayText : "护士录入");

            // 关键字模糊过滤
            if (StrUtil.isNotBlank(kw)) {
                String matchStr = (p != null ? (p.getPatientName() + p.getBedNo() + p.getHospitalNo()) : "")
                        + r.getItemName() + StrUtil.nullToEmpty(r.getRemark()) + recName + recText;
                if (!matchStr.toLowerCase().contains(kw.toLowerCase())) {
                    continue;
                }
            }

            FluidRecordDetailVO d = new FluidRecordDetailVO();
            d.setId(r.getId());
            d.setCategory("INTAKE");
            d.setPatientId(r.getPatientId());
            if (p != null) {
                d.setPatientName(p.getPatientName());
                d.setBedNo(p.getBedNo());
                d.setHospitalNo(p.getHospitalNo());
                d.setWardId(p.getWardId());
                d.setWardName(wardNameMap.getOrDefault(p.getWardId(), ""));
            }
            d.setType(r.getIntakeType());
            d.setTypeText("入量 - " + formatIntakeType(r.getIntakeType(), r.getItemName()));
            d.setItemName(r.getItemName());
            d.setAmount(r.getAmount());
            d.setRecordTime(r.getRecordTime());
            d.setRecordDate(r.getRecordDate());
            d.setRecorderId(r.getRecorderId());
            d.setRecorderRole(r.getRecorderRole());
            d.setRecorderName(recName);
            d.setRecorderText(recText);
            d.setRemark(r.getRemark());
            details.add(d);
        }

        for (OutputRecord r : outRecords) {
            Patient p = patientMap.get(r.getPatientId());
            RecorderInfo rInfo = recorderMap.get(r.getRecorderId());
            boolean isPatientRecord = "PATIENT".equalsIgnoreCase(r.getRecorderRole())
                    || (p != null && p.getBoundUserId() != null && p.getBoundUserId().equals(r.getRecorderId()))
                    || (rInfo != null && "PATIENT".equalsIgnoreCase(rInfo.role));
            String recName = isPatientRecord ? "本人" : (rInfo != null ? rInfo.name : "护士");
            String recText = isPatientRecord ? "本人录入" : (rInfo != null ? rInfo.displayText : "护士录入");

            if (StrUtil.isNotBlank(kw)) {
                String matchStr = (p != null ? (p.getPatientName() + p.getBedNo() + p.getHospitalNo()) : "")
                        + formatOutputType(r.getOutputType()) + StrUtil.nullToEmpty(r.getRemark()) + StrUtil.nullToEmpty(r.getNatureOrColor()) + recName + recText;
                if (!matchStr.toLowerCase().contains(kw.toLowerCase())) {
                    continue;
                }
            }

            FluidRecordDetailVO d = new FluidRecordDetailVO();
            d.setId(r.getId());
            d.setCategory("OUTPUT");
            d.setPatientId(r.getPatientId());
            if (p != null) {
                d.setPatientName(p.getPatientName());
                d.setBedNo(p.getBedNo());
                d.setHospitalNo(p.getHospitalNo());
                d.setWardId(p.getWardId());
                d.setWardName(wardNameMap.getOrDefault(p.getWardId(), ""));
            }
            d.setType(r.getOutputType());
            d.setTypeText("出量 - " + formatOutputType(r.getOutputType()));
            d.setItemName(formatOutputType(r.getOutputType()));
            d.setAmount(r.getAmount());
            d.setNatureOrColor(r.getNatureOrColor());
            d.setRecordTime(r.getRecordTime());
            d.setRecordDate(r.getRecordDate());
            d.setRecorderId(r.getRecorderId());
            d.setRecorderRole(r.getRecorderRole());
            d.setRecorderName(recName);
            d.setRecorderText(recText);
            d.setRemark(r.getRemark());
            details.add(d);
        }

        // 按记录时间倒序
        details.sort(Comparator.comparing(FluidRecordDetailVO::getRecordTime).reversed());

        // 6. 统计各病患指标与全局指标
        BigDecimal grandTotalIn = BigDecimal.ZERO;
        BigDecimal grandTotalOut = BigDecimal.ZERO;

        Map<Long, PatientSummaryVO> pSumMap = new LinkedHashMap<>();
        for (Patient p : targetPatients) {
            PatientSummaryVO ps = new PatientSummaryVO();
            ps.setPatientId(p.getId());
            ps.setPatientName(p.getPatientName());
            ps.setBedNo(p.getBedNo());
            ps.setHospitalNo(p.getHospitalNo());
            ps.setWardId(p.getWardId());
            ps.setWardName(wardNameMap.getOrDefault(p.getWardId(), ""));
            ps.setAssignedNurseName(assignedNurseMap.get(p.getId()));
            pSumMap.put(p.getId(), ps);
        }

        for (FluidRecordDetailVO d : details) {
            PatientSummaryVO ps = pSumMap.get(d.getPatientId());
            if ("INTAKE".equalsIgnoreCase(d.getCategory())) {
                grandTotalIn = grandTotalIn.add(d.getAmount());
                if (ps != null) {
                    ps.setTotalIntake(ps.getTotalIntake().add(d.getAmount()));
                    ps.setRecordCount(ps.getRecordCount() + 1);
                    if ("IV".equalsIgnoreCase(d.getType()) || (d.getItemName() != null && (d.getItemName().contains("输液") || d.getItemName().contains("静脉")))) {
                        ps.setIvIntake(ps.getIvIntake().add(d.getAmount()));
                    }
                }
            } else {
                grandTotalOut = grandTotalOut.add(d.getAmount());
                if (ps != null) {
                    ps.setTotalOutput(ps.getTotalOutput().add(d.getAmount()));
                    ps.setRecordCount(ps.getRecordCount() + 1);
                    if ("URINE".equalsIgnoreCase(d.getType()) || (d.getItemName() != null && d.getItemName().contains("尿"))) {
                        ps.setUrineOutput(ps.getUrineOutput().add(d.getAmount()));
                    }
                }
            }
        }

        for (PatientSummaryVO ps : pSumMap.values()) {
            ps.setNetBalance(ps.getTotalIntake().subtract(ps.getTotalOutput()));
            boolean urineAlert = ps.getRecordCount() > 0 && ps.getUrineOutput().compareTo(new BigDecimal("400")) < 0;
            boolean balanceAlert = ps.getNetBalance().compareTo(new BigDecimal("1000")) > 0;
            boolean ivAlert = ps.getIvIntake().compareTo(new BigDecimal("3000")) > 0;
            ps.setIsUrineAlert(urineAlert);
            ps.setIsBalanceAlert(balanceAlert);
            ps.setIsIvAlert(ivAlert);
            ps.setIsAbnormal(urineAlert || balanceAlert || ivAlert);
        }

        // 回填明细流水的警示标记
        for (FluidRecordDetailVO d : details) {
            PatientSummaryVO ps = pSumMap.get(d.getPatientId());
            if (ps != null) {
                if ("INTAKE".equalsIgnoreCase(d.getCategory()) && ("IV".equalsIgnoreCase(d.getType()) || (d.getItemName() != null && d.getItemName().contains("输液")))) {
                    if (ps.getIsIvAlert() || d.getAmount().compareTo(new BigDecimal("3000")) > 0) {
                        d.setAlertLevel("yellow");
                        d.setAlertTag("静脉输液>3000ml");
                    }
                } else if ("OUTPUT".equalsIgnoreCase(d.getCategory()) && ("URINE".equalsIgnoreCase(d.getType()) || (d.getItemName() != null && d.getItemName().contains("尿")))) {
                    if (ps.getIsUrineAlert()) {
                        d.setAlertLevel("red");
                        d.setAlertTag("24h尿量<400ml");
                    }
                }
            }
        }

        resultVO.setTotalIntake(grandTotalIn);
        resultVO.setTotalOutput(grandTotalOut);
        resultVO.setNetBalance(grandTotalIn.subtract(grandTotalOut));
        resultVO.setPatientCount(pSumMap.size());
        resultVO.setRecordCount(details.size());
        resultVO.setPatientSummaries(new ArrayList<>(pSumMap.values()));
        resultVO.setRecords(details);

        return resultVO;
    }

    @Override
    public void exportExcel(FluidQueryDTO queryDTO, HttpServletResponse response) {
        FluidQueryVO queryVO = queryFluidRecords(queryDTO);

        SimpleExcelExporter exporter = new SimpleExcelExporter("出入量统计与预警报表");

        // 1. 大标题与概况信息
        exporter.addRow(Collections.singletonList("住院患者出入量记录与平衡分析报表"), SimpleExcelExporter.STYLE_TITLE);
        
        LocalDate start = queryDTO.getStartDate() != null ? queryDTO.getStartDate() : LocalDate.now().minusDays(6);
        LocalDate end = queryDTO.getEndDate() != null ? queryDTO.getEndDate() : LocalDate.now();
        exporter.addRow(Arrays.asList("统计周期", start + " 至 " + end, "导出时间", LocalDateTime.now().format(TIME_FMT), "涉及患者", queryVO.getPatientCount() + " 人"));

        // 全局净平衡若 > 1000ml，将其标红
        List<String> overviewRow = Arrays.asList(
                "总入量(ml)", queryVO.getTotalIntake().toString(),
                "总出量(ml)", queryVO.getTotalOutput().toString(),
                "净平衡差值(ml)", (queryVO.getNetBalance().compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + queryVO.getNetBalance(),
                "流水总条数", queryVO.getRecordCount() + " 条"
        );
        List<Integer> overviewStyles = Arrays.asList(
                SimpleExcelExporter.STYLE_DATA, SimpleExcelExporter.STYLE_DATA,
                SimpleExcelExporter.STYLE_DATA, SimpleExcelExporter.STYLE_DATA,
                queryVO.getNetBalance().compareTo(new BigDecimal("1000")) > 0 ? SimpleExcelExporter.STYLE_ALERT_RED : SimpleExcelExporter.STYLE_DATA,
                queryVO.getNetBalance().compareTo(new BigDecimal("1000")) > 0 ? SimpleExcelExporter.STYLE_ALERT_RED : SimpleExcelExporter.STYLE_DATA,
                SimpleExcelExporter.STYLE_DATA, SimpleExcelExporter.STYLE_DATA
        );
        exporter.addRowWithStyles(overviewRow, overviewStyles);

        // 预警图例说明行
        exporter.addRow(Arrays.asList(
                "⚠️ 预警图例说明",
                "🔴 红色背景：24小时尿量<400ml(少尿) 或 入-出净差值>1000ml(严重正平衡)",
                "", "",
                "🟡 黄色背景：静脉输液>3000ml(高容量负荷)",
                "", "", ""
        ), SimpleExcelExporter.STYLE_MUTED);

        exporter.addRow(Collections.singletonList("")); // 空行隔开

        // 2. 第一部分：患者出入量平衡与预警汇总表
        exporter.addRow(Collections.singletonList("【一、 各病患24小时出入平衡与异常预警汇总】"), SimpleExcelExporter.STYLE_TITLE);
        List<String> sumHeaders = Arrays.asList(
                "序号", "床号", "患者姓名", "住院号", "所属病区", "责任护士",
                "总入量(ml)", "总出量(ml)", "净差值(ml)", "24h尿量(ml)", "静脉输液(ml)", "临床预警状态"
        );
        exporter.addRow(sumHeaders, SimpleExcelExporter.STYLE_HEADER);

        int pSeq = 1;
        for (PatientSummaryVO ps : queryVO.getPatientSummaries()) {
            String netStr = (ps.getNetBalance().compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + ps.getNetBalance();
            StringBuilder warnSb = new StringBuilder();
            if (Boolean.TRUE.equals(ps.getIsUrineAlert())) warnSb.append("少尿(<400ml/标红) ");
            if (Boolean.TRUE.equals(ps.getIsBalanceAlert())) warnSb.append("差值>1000ml(标红) ");
            if (Boolean.TRUE.equals(ps.getIsIvAlert())) warnSb.append("输液>3000ml(标黄) ");
            String warnText = warnSb.length() > 0 ? warnSb.toString().trim() : "正常";

            List<String> row = Arrays.asList(
                    String.valueOf(pSeq++),
                    StrUtil.nullToEmpty(ps.getBedNo()),
                    StrUtil.nullToEmpty(ps.getPatientName()),
                    StrUtil.nullToEmpty(ps.getHospitalNo()),
                    StrUtil.nullToEmpty(ps.getWardName()),
                    StrUtil.nullToEmpty(ps.getAssignedNurseName()),
                    ps.getTotalIntake().toString(),
                    ps.getTotalOutput().toString(),
                    netStr,
                    ps.getUrineOutput().toString(),
                    ps.getIvIntake().toString(),
                    warnText
            );

            List<Integer> styles = new ArrayList<>(Collections.nCopies(row.size(), SimpleExcelExporter.STYLE_DATA));
            styles.set(0, SimpleExcelExporter.STYLE_CENTER);
            styles.set(1, SimpleExcelExporter.STYLE_CENTER);

            // 规则2: 入量-出量差值>1000ML 标红 (列索引 8)
            if (Boolean.TRUE.equals(ps.getIsBalanceAlert())) {
                styles.set(8, SimpleExcelExporter.STYLE_ALERT_RED);
            }
            // 规则1: 24小时尿量<400ML 标红 (列索引 9)
            if (Boolean.TRUE.equals(ps.getIsUrineAlert())) {
                styles.set(9, SimpleExcelExporter.STYLE_ALERT_RED);
            }
            // 规则3: 入量-静脉输液>3000ML 标黄 (列索引 10)
            if (Boolean.TRUE.equals(ps.getIsIvAlert())) {
                styles.set(10, SimpleExcelExporter.STYLE_ALERT_YELLOW);
            }

            // 预警状态列样式
            if (Boolean.TRUE.equals(ps.getIsUrineAlert()) || Boolean.TRUE.equals(ps.getIsBalanceAlert())) {
                styles.set(11, SimpleExcelExporter.STYLE_ALERT_RED);
            } else if (Boolean.TRUE.equals(ps.getIsIvAlert())) {
                styles.set(11, SimpleExcelExporter.STYLE_ALERT_YELLOW);
            }

            exporter.addRowWithStyles(row, styles);
        }

        exporter.addRow(Collections.singletonList("")); // 空行隔开

        // 3. 第二部分：出入量明细流水
        exporter.addRow(Collections.singletonList("【二、 出入量明细流水记录】"), SimpleExcelExporter.STYLE_TITLE);
        List<String> headers = Arrays.asList(
                "序号",
                "床号",
                "患者姓名",
                "住院号",
                "所属病区",
                "记录时间",
                "出入类型",
                "分类项目",
                "品名/排泄物",
                "数量 (ml)",
                "预警提示",
                "颜色/性状",
                "录入身份",
                "具体录入人",
                "临床备注"
        );
        exporter.addRow(headers, SimpleExcelExporter.STYLE_HEADER);

        int seq = 1;
        for (FluidRecordDetailVO item : queryVO.getRecords()) {
            boolean isIntake = "INTAKE".equalsIgnoreCase(item.getCategory());
            String amtStr = (isIntake ? "+" : "-") + item.getAmount().stripTrailingZeros().toPlainString();
            String alertTag = StrUtil.nullToEmpty(item.getAlertTag());

            List<String> row = Arrays.asList(
                    String.valueOf(seq++),
                    StrUtil.nullToEmpty(item.getBedNo()),
                    StrUtil.nullToEmpty(item.getPatientName()),
                    StrUtil.nullToEmpty(item.getHospitalNo()),
                    StrUtil.nullToEmpty(item.getWardName()),
                    item.getRecordTime() != null ? item.getRecordTime().format(TIME_FMT) : "",
                    isIntake ? "入量" : "出量",
                    StrUtil.nullToEmpty(item.getTypeText()),
                    StrUtil.nullToEmpty(item.getItemName()),
                    amtStr,
                    alertTag,
                    StrUtil.nullToEmpty(item.getNatureOrColor()),
                    "PATIENT".equalsIgnoreCase(item.getRecorderRole()) ? "患者自录" : "护士录入",
                    StrUtil.nullToEmpty(item.getRecorderText()),
                    StrUtil.nullToEmpty(item.getRemark())
            );

            List<Integer> styles = new ArrayList<>(Collections.nCopies(row.size(), SimpleExcelExporter.STYLE_DATA));
            styles.set(0, SimpleExcelExporter.STYLE_CENTER);
            styles.set(1, SimpleExcelExporter.STYLE_CENTER);

            // 明细行预警背景着色
            if ("red".equalsIgnoreCase(item.getAlertLevel())) {
                styles.set(8, SimpleExcelExporter.STYLE_ALERT_RED); // 品名
                styles.set(9, SimpleExcelExporter.STYLE_ALERT_RED); // 数量
                styles.set(10, SimpleExcelExporter.STYLE_ALERT_RED); // 预警提示
            } else if ("yellow".equalsIgnoreCase(item.getAlertLevel())) {
                styles.set(8, SimpleExcelExporter.STYLE_ALERT_YELLOW); // 品名
                styles.set(9, SimpleExcelExporter.STYLE_ALERT_YELLOW); // 数量
                styles.set(10, SimpleExcelExporter.STYLE_ALERT_YELLOW); // 预警提示
            }

            exporter.addRowWithStyles(row, styles);
        }

        // 4. 合计行
        List<String> totalRow = Arrays.asList(
                "合计",
                "", "", "", "", "",
                "总入: " + queryVO.getTotalIntake() + "ml / 总出: " + queryVO.getTotalOutput() + "ml",
                "净差值: " + (queryVO.getNetBalance().compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + queryVO.getNetBalance() + "ml",
                "", "", "", "", "", "",
                "共 " + queryVO.getRecordCount() + " 笔记录"
        );
        List<Integer> totalStyles = new ArrayList<>(Collections.nCopies(totalRow.size(), SimpleExcelExporter.STYLE_DATA));
        if (queryVO.getNetBalance().compareTo(new BigDecimal("1000")) > 0) {
            totalStyles.set(7, SimpleExcelExporter.STYLE_ALERT_RED);
        }
        exporter.addRowWithStyles(totalRow, totalStyles);

        // 5. 写入响应流
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("患者出入量统计与预警报表_" + LocalDate.now() + ".xlsx", "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            exporter.writeTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new BusinessException("导出Excel失败: " + e.getMessage());
        }
    }

    private Map<Long, RecorderInfo> resolveRecorders(Set<Long> userIds) {
        Map<Long, RecorderInfo> map = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return map;
        }

        List<SysUser> users = userMapper.selectBatchIds(userIds);
        if (users == null || users.isEmpty()) {
            return map;
        }

        Set<Long> nurseIds = new HashSet<>();
        Set<Long> patientIds = new HashSet<>();

        for (SysUser u : users) {
            if (u.getBindRefId() != null) {
                if ("NURSE".equalsIgnoreCase(u.getRole()) || "HEAD_NURSE".equalsIgnoreCase(u.getRole())) {
                    nurseIds.add(u.getBindRefId());
                } else if ("PATIENT".equalsIgnoreCase(u.getRole())) {
                    patientIds.add(u.getBindRefId());
                }
            }
        }

        Map<Long, Nurse> nurseMap = nurseIds.isEmpty() ? Collections.emptyMap() :
                nurseMapper.selectBatchIds(nurseIds).stream().collect(Collectors.toMap(Nurse::getId, Function.identity(), (a, b) -> a));

        Map<Long, Patient> patientMap = patientIds.isEmpty() ? Collections.emptyMap() :
                patientMapper.selectBatchIds(patientIds).stream().collect(Collectors.toMap(Patient::getId, Function.identity(), (a, b) -> a));

        for (SysUser u : users) {
            RecorderInfo info = new RecorderInfo();
            info.userId = u.getId();
            info.role = u.getRole();

            if ("HEAD_NURSE".equalsIgnoreCase(u.getRole()) || "NURSE".equalsIgnoreCase(u.getRole())) {
                Nurse n = u.getBindRefId() != null ? nurseMap.get(u.getBindRefId()) : null;
                if (n != null && StrUtil.isNotBlank(n.getRealName())) {
                    info.name = n.getRealName();
                    info.displayText = n.getRealName() + "录入";
                } else {
                    String base = StrUtil.isNotBlank(u.getNickname()) ? u.getNickname() : "护士";
                    info.name = base;
                    info.displayText = base + "录入";
                }
            } else if ("PATIENT".equalsIgnoreCase(u.getRole())) {
                Patient p = u.getBindRefId() != null ? patientMap.get(u.getBindRefId()) : null;
                if (p != null && StrUtil.isNotBlank(p.getPatientName())) {
                    info.name = "本人";
                    info.displayText = "本人录入";
                } else {
                    info.name = "本人";
                    info.displayText = "本人录入";
                }
            } else {
                info.name = StrUtil.isNotBlank(u.getNickname()) ? u.getNickname() : "管理员";
                info.displayText = info.name + "录入";
            }
            map.put(u.getId(), info);
        }
        return map;
    }

    private static class RecorderInfo {
        Long userId;
        String name;
        String role;
        String displayText;
    }

    private String formatIntakeType(String type, String itemName) {
        if ("ORAL".equalsIgnoreCase(type)) return "口服(" + itemName + ")";
        if ("IV".equalsIgnoreCase(type)) return "静脉输液(" + itemName + ")";
        if ("NASOGASTRIC".equalsIgnoreCase(type)) return "鼻饲(" + itemName + ")";
        return itemName;
    }

    private String formatOutputType(String type) {
        if ("URINE".equalsIgnoreCase(type)) return "尿量";
        if ("STOOL".equalsIgnoreCase(type)) return "大便量";
        if ("GASTRO_DRAINAGE".equalsIgnoreCase(type)) return "胃肠减压引流";
        if ("CHEST_DRAINAGE".equalsIgnoreCase(type)) return "胸腹水引流";
        if ("OTHER_DRAINAGE".equalsIgnoreCase(type)) return "引流液";
        return "排泄物";
    }
}
