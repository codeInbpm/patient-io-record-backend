package com.patient.io.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.patient.io.common.BusinessException;
import com.patient.io.common.UserContext;
import com.patient.io.dto.InfusionCreateDTO;
import com.patient.io.dto.InfusionDrugDTO;
import com.patient.io.dto.InfusionEndDTO;
import com.patient.io.entity.*;
import com.patient.io.mapper.*;
import com.patient.io.service.InfusionService;
import com.patient.io.vo.InfusionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InfusionServiceImpl implements InfusionService {

    private final InfusionRecordMapper infusionRecordMapper;
    private final InfusionImageMapper infusionImageMapper;
    private final IntakeRecordMapper intakeRecordMapper;
    private final PatientMapper patientMapper;
    private final NurseMapper nurseMapper;
    private final SysUserMapper sysUserMapper;
    private final DrugDictMapper drugDictMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInfusion(InfusionCreateDTO dto) {
        Patient patient = patientMapper.selectById(dto.getPatientId());
        if (patient == null || patient.getStatus() != 1) {
            throw new BusinessException("患者不存在或已出院");
        }

        // 获取操作护士信息
        Long currentUserId = UserContext.getUserId();
        String nurseName = "责任护士";
        Long nurseId = 1L;
        if (currentUserId != null) {
            SysUser user = sysUserMapper.selectById(currentUserId);
            if (user != null && user.getBindRefId() != null) {
                Nurse nurse = nurseMapper.selectById(user.getBindRefId());
                if (nurse != null) {
                    nurseId = nurse.getId();
                    nurseName = nurse.getRealName();
                }
            }
        }

        // 结构化用药：药品字典选择结果，同时拼出可读文本兼容历史报表
        // 药品文本在前，自由备注（如滴速）在后；都为空时保持为空，兼容旧客户端
        String drugJson = buildDrugJson(dto.getDrugs());
        String drugsText = buildDrugText(dto.getDrugs());
        String extraRemark = StrUtil.trimToNull(dto.getRemark());
        String drugText;
        if (drugsText != null) {
            drugText = extraRemark != null ? drugsText + " | " + extraRemark : drugsText;
        } else {
            drugText = extraRemark;
        }

        // 1. 如果勾选了同步写入入量记录，先写入 intake_record
        Long intakeRecordId = null;
        if (Boolean.TRUE.equals(dto.getSyncToIntake())) {
            IntakeRecord intake = new IntakeRecord();
            intake.setPatientId(patient.getId());
            intake.setWardId(patient.getWardId());
            intake.setIntakeType("IV");
            intake.setSubType("CUSTOM");
            intake.setItemName((StrUtil.isNotBlank(dto.getUsageType()) ? dto.getUsageType() : "ivg") + " (补液)");
            intake.setAmount(BigDecimal.valueOf(dto.getCapacity()));
            intake.setRecordTime(dto.getStartTime() != null ? dto.getStartTime() : LocalDateTime.now());
            intake.setRecordDate(intake.getRecordTime().toLocalDate());
            intake.setRecorderId(currentUserId != null ? currentUserId : 1L);
            intake.setRecorderRole("NURSE");
            intake.setRemark("由补液记录单自动生成" + (StrUtil.isNotBlank(drugText) ? ": " + drugText : ""));
            intakeRecordMapper.insert(intake);
            intakeRecordId = intake.getId();
        }

        // 2. 插入补液记录
        InfusionRecord record = new InfusionRecord();
        record.setHospitalId(patient.getHospitalId() != null ? patient.getHospitalId() : 1L);
        record.setWardId(patient.getWardId());
        record.setBedId(patient.getBedId());
        record.setBedNo(patient.getBedNo());
        record.setPatientId(patient.getId());
        record.setPatientName(patient.getPatientName());
        record.setCapacity(dto.getCapacity());
        record.setUsageType(dto.getUsageType());
        LocalDateTime start = dto.getStartTime() != null ? dto.getStartTime() : LocalDateTime.now();
        LocalDateTime estEnd = dto.getEstimatedEndTime();
        if (start != null && estEnd != null && estEnd.isBefore(start)) {
            if (Duration.between(estEnd, start).toHours() < 24) {
                estEnd = estEnd.plusDays(1);
            }
        }
        record.setStartTime(start);
        record.setEstimatedEndTime(estEnd);
        record.setStatus("INFUSING");
        record.setActualIntakeAmount(dto.getCapacity());
        record.setIsSyncedIntake(intakeRecordId != null ? 1 : 0);
        record.setIntakeRecordId(intakeRecordId);
        record.setNurseId(nurseId);
        record.setNurseName(nurseName);
        record.setRemark(drugText);
        record.setDrugJson(drugJson);
        infusionRecordMapper.insert(record);

        // 3. 关联保存图片
        if (CollUtil.isNotEmpty(dto.getImageUrls())) {
            for (String url : dto.getImageUrls()) {
                if (StrUtil.isNotBlank(url)) {
                    InfusionImage img = new InfusionImage();
                    img.setHospitalId(record.getHospitalId());
                    img.setInfusionId(record.getId());
                    img.setFileName("infusion_proof.jpg");
                    img.setObjectKey(url);
                    img.setFileUrl(url);
                    img.setUploadUserId(currentUserId != null ? currentUserId : 1L);
                    infusionImageMapper.insert(img);
                }
            }
        }

        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endInfusion(InfusionEndDTO dto) {
        InfusionRecord record = infusionRecordMapper.selectById(dto.getInfusionId());
        if (record == null) {
            throw new BusinessException("补液记录不存在");
        }
        if (!"INFUSING".equals(record.getStatus())) {
            throw new BusinessException("该补液记录已结束，请勿重复操作");
        }

        Long currentUserId = UserContext.getUserId();
        String endNurseName = "当班护士";
        Long endNurseId = 1L;
        if (currentUserId != null) {
            SysUser user = sysUserMapper.selectById(currentUserId);
            if (user != null && user.getBindRefId() != null) {
                Nurse nurse = nurseMapper.selectById(user.getBindRefId());
                if (nurse != null) {
                    endNurseId = nurse.getId();
                    endNurseName = nurse.getRealName();
                }
            }
        }

        LocalDateTime actualEnd = dto.getActualEndTime() != null ? dto.getActualEndTime() : LocalDateTime.now();
        record.setActualEndTime(actualEnd);
        record.setStatus(StrUtil.isNotBlank(dto.getStatus()) ? dto.getStatus() : "COMPLETED");
        record.setEndNurseId(endNurseId);
        record.setEndNurseName(endNurseName);

        if (dto.getActualIntakeAmount() != null) {
            record.setActualIntakeAmount(dto.getActualIntakeAmount());

            // 同步调整关联的入量记录
            if (record.getIntakeRecordId() != null) {
                IntakeRecord intake = intakeRecordMapper.selectById(record.getIntakeRecordId());
                if (intake != null) {
                    intake.setAmount(BigDecimal.valueOf(dto.getActualIntakeAmount()));
                    if (StrUtil.isNotBlank(dto.getRemark())) {
                        intake.setRemark(intake.getRemark() + " (拔针调整: " + dto.getRemark() + ")");
                    }
                    intakeRecordMapper.updateById(intake);
                }
            }
        }

        if (StrUtil.isNotBlank(dto.getRemark())) {
            record.setRemark(StrUtil.isNotBlank(record.getRemark()) ? record.getRemark() + " | " + dto.getRemark() : dto.getRemark());
        }

        infusionRecordMapper.updateById(record);
    }

    @Override
    public List<InfusionVO> listActiveInfusions(Long wardId) {
        LambdaQueryWrapper<InfusionRecord> wrapper = new LambdaQueryWrapper<>();
        if (wardId != null && wardId > 0) {
            wrapper.eq(InfusionRecord::getWardId, wardId);
        }
        wrapper.eq(InfusionRecord::getStatus, "INFUSING")
                .orderByAsc(InfusionRecord::getEstimatedEndTime);

        List<InfusionRecord> list = infusionRecordMapper.selectList(wrapper);
        return convertToVOList(list);
    }

    @Override
    public List<InfusionVO> listCompletedInfusions(Long wardId, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        LambdaQueryWrapper<InfusionRecord> wrapper = new LambdaQueryWrapper<>();
        if (wardId != null && wardId > 0) {
            wrapper.eq(InfusionRecord::getWardId, wardId);
        }
        wrapper.in(InfusionRecord::getStatus, Arrays.asList("COMPLETED", "STOPPED"))
                .ge(InfusionRecord::getStartTime, date.atStartOfDay())
                .le(InfusionRecord::getStartTime, date.atTime(23, 59, 59))
                .orderByDesc(InfusionRecord::getActualEndTime);

        List<InfusionRecord> list = infusionRecordMapper.selectList(wrapper);
        return convertToVOList(list);
    }

    @Override
    public List<InfusionVO> listPatientHistory(Long patientId) {
        LambdaQueryWrapper<InfusionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InfusionRecord::getPatientId, patientId)
                .orderByDesc(InfusionRecord::getStartTime);

        List<InfusionRecord> list = infusionRecordMapper.selectList(wrapper);
        return convertToVOList(list);
    }

    @Override
    public Page<InfusionVO> pageInfusions(Long wardId, String bedNo, String patientName, String status, LocalDate startDate, LocalDate endDate, long current, long size) {
        LambdaQueryWrapper<InfusionRecord> wrapper = new LambdaQueryWrapper<>();
        if (wardId != null && wardId > 0) {
            wrapper.eq(InfusionRecord::getWardId, wardId);
        }
        if (StrUtil.isNotBlank(bedNo)) {
            wrapper.like(InfusionRecord::getBedNo, bedNo.trim());
        }
        if (StrUtil.isNotBlank(patientName)) {
            wrapper.like(InfusionRecord::getPatientName, patientName.trim());
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(InfusionRecord::getStatus, status.trim());
        }
        if (startDate != null) {
            wrapper.ge(InfusionRecord::getStartTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(InfusionRecord::getStartTime, endDate.atTime(23, 59, 59));
        }
        wrapper.orderByDesc(InfusionRecord::getStartTime);

        Page<InfusionRecord> page = new Page<>(current, size);
        Page<InfusionRecord> recordPage = infusionRecordMapper.selectPage(page, wrapper);

        Page<InfusionVO> voPage = new Page<>(current, size, recordPage.getTotal());
        voPage.setRecords(convertToVOList(recordPage.getRecords()));
        return voPage;
    }

    @Override
    public InfusionVO getInfusionDetail(Long id) {
        InfusionRecord record = infusionRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        List<InfusionVO> voList = convertToVOList(Collections.singletonList(record));
        return CollUtil.isNotEmpty(voList) ? voList.get(0) : null;
    }

    private List<InfusionVO> convertToVOList(List<InfusionRecord> records) {
        if (CollUtil.isEmpty(records)) {
            return Collections.emptyList();
        }

        List<Long> ids = records.stream().map(InfusionRecord::getId).collect(Collectors.toList());
        LambdaQueryWrapper<InfusionImage> imgWrapper = new LambdaQueryWrapper<>();
        imgWrapper.in(InfusionImage::getInfusionId, ids);
        List<InfusionImage> images = infusionImageMapper.selectList(imgWrapper);

        Map<Long, List<String>> imageMap = images.stream().collect(
                Collectors.groupingBy(InfusionImage::getInfusionId,
                        Collectors.mapping(InfusionImage::getFileUrl, Collectors.toList()))
        );

        LocalDateTime now = LocalDateTime.now();

        return records.stream().map(record -> {
            InfusionVO vo = new InfusionVO();
            BeanUtils.copyProperties(record, vo);
            vo.setImageUrls(imageMap.getOrDefault(record.getId(), Collections.emptyList()));
            vo.setDrugs(parseDrugJson(record.getDrugJson()));

            if ("INFUSING".equals(record.getStatus())) {
                LocalDateTime start = record.getStartTime();
                LocalDateTime estEnd = record.getEstimatedEndTime();

                if (estEnd != null) {
                    if (start != null && estEnd.isBefore(start) && Duration.between(estEnd, start).toHours() < 24) {
                        estEnd = estEnd.plusDays(1);
                    }
                    long remainingMin = Duration.between(now, estEnd).toMinutes();
                    vo.setRemainingMinutes(remainingMin);
                    vo.setIsTimeout(remainingMin < 0);

                    if (start != null && estEnd.isAfter(start)) {
                        long totalSec = Duration.between(start, estEnd).getSeconds();
                        long elapsedSec = Duration.between(start, now).getSeconds();
                        if (totalSec > 0) {
                            int percent = (int) Math.min(100, Math.max(0, (elapsedSec * 100) / totalSec));
                            vo.setProgressPercent(percent);
                        } else {
                            vo.setProgressPercent(0);
                        }
                    }
                }
            } else {
                vo.setProgressPercent(100);
                vo.setRemainingMinutes(0L);
                vo.setIsTimeout(false);
            }

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 解析持久化的用药 JSON；历史记录没有该字段时返回空集合，由前端回退到 remark 文本
     */
    private List<InfusionDrugDTO> parseDrugJson(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            List<InfusionDrugDTO> list = JSON.parseArray(json, InfusionDrugDTO.class);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 结构化用药 -> JSON（用于持久化与后续统计）
     */
    private String buildDrugJson(List<InfusionDrugDTO> drugs) {        if (CollUtil.isEmpty(drugs)) {
            return null;
        }
        List<InfusionDrugDTO> valid = drugs.stream()
                .filter(d -> d != null && StrUtil.isNotBlank(d.getName()))
                .collect(Collectors.toList());
        return valid.isEmpty() ? null : JSON.toJSONString(valid);
    }

    /**
     * 结构化用药 -> 可读文本，如「0.9%氯化钠注射液 250ml:2.25g 250ml + 注射用头孢曲松钠 1.0g 1支」
     * 同时累加字典使用频次，越常用的药在联想列表越靠前
     */
    private String buildDrugText(List<InfusionDrugDTO> drugs) {
        if (CollUtil.isEmpty(drugs)) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (InfusionDrugDTO d : drugs) {
            if (d == null || StrUtil.isBlank(d.getName())) {
                continue;
            }
            StringBuilder sb = new StringBuilder(d.getName().trim());
            if (StrUtil.isNotBlank(d.getSpec())) {
                sb.append(' ').append(d.getSpec().trim());
            }
            if (StrUtil.isNotBlank(d.getDose())) {
                sb.append(' ').append(d.getDose().trim());
                if (StrUtil.isNotBlank(d.getUnit())) {
                    sb.append(d.getUnit().trim());
                }
            }
            parts.add(sb.toString());
            if (d.getId() != null && !Boolean.TRUE.equals(d.getManual())) {
                try {
                    drugDictMapper.increaseUseCount(d.getId());
                } catch (Exception ignored) {
                    // 频次累加失败不影响主流程
                }
            }
        }
        return parts.isEmpty() ? null : String.join(" + ", parts);
    }
}
