package com.patient.io.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.BusinessException;
import com.patient.io.common.UserContext;
import com.patient.io.dto.SpecialHandoverSaveDTO;
import com.patient.io.entity.*;
import com.patient.io.mapper.*;
import com.patient.io.service.ShiftHandoverSpecialService;
import com.patient.io.vo.SpecialHandoverVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftHandoverSpecialServiceImpl implements ShiftHandoverSpecialService {

    private final ShiftHandoverSpecialMapper handoverSpecialMapper;
    private final PatientMapper patientMapper;
    private final InfusionRecordMapper infusionRecordMapper;
    private final NurseMapper nurseMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public SpecialHandoverVO getSpecialHandover(Long wardId, LocalDate date, String shiftType) {
        if (date == null) {
            date = LocalDate.now();
        }
        if (StrUtil.isBlank(shiftType)) {
            shiftType = "ALL_DAY";
        }

        LambdaQueryWrapper<ShiftHandoverSpecial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShiftHandoverSpecial::getWardId, wardId)
                .eq(ShiftHandoverSpecial::getHandoverDate, date)
                .eq(ShiftHandoverSpecial::getShiftType, shiftType);

        ShiftHandoverSpecial record = handoverSpecialMapper.selectOne(wrapper);
        if (record != null) {
            SpecialHandoverVO vo = new SpecialHandoverVO();
            BeanUtils.copyProperties(record, vo);
            return vo;
        }

        // 若不存在，从在床患者与在滴补液自动智能生成预填草稿
        return generateAutoPreFillDraft(wardId, date, shiftType);
    }

    private SpecialHandoverVO generateAutoPreFillDraft(Long wardId, LocalDate date, String shiftType) {
        SpecialHandoverVO vo = new SpecialHandoverVO();
        vo.setWardId(wardId);
        vo.setHandoverDate(date);
        vo.setShiftType(shiftType);
        vo.setStatus("DRAFT");
        vo.setHeadNurseSigned(0);

        // 默认空数组占位
        vo.setLeaveHospitalJson("[]");
        vo.setLaxativeJson("[]");
        vo.setTemperatureAbnormalJson("[]");
        vo.setBloodSugarJson("[]");
        vo.setBloodPressureJson("[]");
        vo.setUrineVolumeJson("[]");
        vo.setSpecialCases("");

        // 查询该病区所有在院患者
        LambdaQueryWrapper<Patient> patientWrapper = new LambdaQueryWrapper<>();
        patientWrapper.eq(Patient::getWardId, wardId)
                .eq(Patient::getStatus, 1)
                .orderByAsc(Patient::getBedNo);
        List<Patient> patients = patientMapper.selectList(patientWrapper);

        JSONArray level1List = new JSONArray();
        JSONArray monitorList = new JSONArray();
        JSONArray catheterList = new JSONArray();
        JSONArray gastroList = new JSONArray();
        JSONArray piccList = new JSONArray();
        JSONArray cvcList = new JSONArray();
        JSONArray portList = new JSONArray();
        JSONArray nasobiliaryList = new JSONArray();
        JSONArray abdomList = new JSONArray();
        JSONArray otherPipeList = new JSONArray();

        for (Patient p : patients) {
            JSONObject item = new JSONObject();
            item.put("bedNo", p.getBedNo());
            item.put("patientId", p.getId());
            item.put("patientName", p.getPatientName());

            // 1. 护理级别自动映射
            if ("LEVEL_1".equalsIgnoreCase(p.getNursingLevel()) || "SPECIAL".equalsIgnoreCase(p.getNursingLevel())) {
                level1List.add(item);
            }
            if ("SPECIAL".equalsIgnoreCase(p.getNursingLevel())) {
                monitorList.add(item);
            }

            // 2. 留置管路自动映射
            String tubes = p.getDrainageTubes();
            if (StrUtil.isNotBlank(tubes)) {
                String upper = tubes.toUpperCase();
                if (upper.contains("CATHETER") || upper.contains("导尿") || upper.contains("尿管")) {
                    catheterList.add(item);
                }
                if (upper.contains("GASTRIC") || upper.contains("胃管") || upper.contains("胃肠减压")) {
                    gastroList.add(item);
                }
                if (upper.contains("PICC")) {
                    piccList.add(item);
                }
                if (upper.contains("CVC")) {
                    cvcList.add(item);
                }
                if (upper.contains("PORT") || upper.contains("输液港")) {
                    portList.add(item);
                }
                if (upper.contains("NASOBILIARY") || upper.contains("鼻胆")) {
                    nasobiliaryList.add(item);
                }
                if (upper.contains("ABDOMINAL") || upper.contains("腹腔引流")) {
                    abdomList.add(item);
                }
            }
        }

        vo.setPipeLevel1Json(level1List.toJSONString());
        vo.setPipeMonitorJson(monitorList.toJSONString());
        vo.setPipeCatheterJson(catheterList.toJSONString());
        vo.setPipeGastroDecompJson(gastroList.toJSONString());
        vo.setPipePiccJson(piccList.toJSONString());
        vo.setPipeCvcJson(cvcList.toJSONString());
        vo.setPipePortJson(portList.toJSONString());
        vo.setPipeNasobiliaryJson(nasobiliaryList.toJSONString());
        vo.setPipeAbdomDrainJson(abdomList.toJSONString());
        vo.setPipeOtherJson("[]");

        // 3. 正在滴注的静脉补液与维持液自动映射
        LambdaQueryWrapper<InfusionRecord> infusionWrapper = new LambdaQueryWrapper<>();
        infusionWrapper.eq(InfusionRecord::getWardId, wardId)
                .eq(InfusionRecord::getStatus, "INFUSING");
        List<InfusionRecord> activeInfusions = infusionRecordMapper.selectList(infusionWrapper);

        JSONArray ivgList = new JSONArray();
        JSONArray maintFluidList = new JSONArray();
        for (InfusionRecord inf : activeInfusions) {
            JSONObject item = new JSONObject();
            item.put("bedNo", inf.getBedNo());
            item.put("patientId", inf.getPatientId());
            item.put("patientName", inf.getPatientName());
            item.put("detail", inf.getUsageType() + " " + inf.getCapacity() + "ml");

            if ("维持液".equalsIgnoreCase(inf.getUsageType()) || "maint".equalsIgnoreCase(inf.getUsageType())) {
                item.put("drug", StrUtil.isNotBlank(inf.getRemark()) ? inf.getRemark() : inf.getUsageType());
                if (inf.getStartTime() != null) {
                    item.put("time", inf.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                }
                maintFluidList.add(item);
            } else {
                ivgList.add(item);
            }
        }
        vo.setPipeIvgJson(ivgList.toJSONString());
        vo.setPipeMaintFluidJson(maintFluidList.toJSONString());

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSpecialHandover(SpecialHandoverSaveDTO dto) {
        Long currentUserId = UserContext.getUserId();
        String editorName = "当班护士";
        Long editorId = 1L;
        if (currentUserId != null) {
            SysUser user = sysUserMapper.selectById(currentUserId);
            if (user != null && user.getBindRefId() != null) {
                Nurse nurse = nurseMapper.selectById(user.getBindRefId());
                if (nurse != null) {
                    editorId = nurse.getId();
                    editorName = nurse.getRealName();
                }
            }
        }

        LambdaQueryWrapper<ShiftHandoverSpecial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShiftHandoverSpecial::getWardId, dto.getWardId())
                .eq(ShiftHandoverSpecial::getHandoverDate, dto.getHandoverDate())
                .eq(ShiftHandoverSpecial::getShiftType, StrUtil.isNotBlank(dto.getShiftType()) ? dto.getShiftType() : "ALL_DAY");

        ShiftHandoverSpecial record = handoverSpecialMapper.selectOne(wrapper);
        boolean isNew = (record == null);
        if (isNew) {
            record = new ShiftHandoverSpecial();
            record.setHospitalId(1L);
            record.setWardId(dto.getWardId());
            record.setHandoverDate(dto.getHandoverDate());
            record.setShiftType(StrUtil.isNotBlank(dto.getShiftType()) ? dto.getShiftType() : "ALL_DAY");
            record.setStatus("DRAFT");
            record.setHeadNurseSigned(0);
        } else {
            if ("LOCKED".equals(record.getStatus())) {
                throw new BusinessException("该交接班表已被护士长归档锁定，不可修改");
            }
        }

        // 更新各字段
        if (dto.getLeaveHospitalJson() != null) record.setLeaveHospitalJson(dto.getLeaveHospitalJson());
        if (dto.getLaxativeJson() != null) record.setLaxativeJson(dto.getLaxativeJson());
        if (dto.getTemperatureAbnormalJson() != null) record.setTemperatureAbnormalJson(dto.getTemperatureAbnormalJson());
        if (dto.getBloodSugarJson() != null) record.setBloodSugarJson(dto.getBloodSugarJson());
        if (dto.getBloodPressureJson() != null) record.setBloodPressureJson(dto.getBloodPressureJson());
        if (dto.getUrineVolumeJson() != null) record.setUrineVolumeJson(dto.getUrineVolumeJson());
        if (dto.getSpecialCases() != null) record.setSpecialCases(dto.getSpecialCases());

        if (dto.getPipeLevel1Json() != null) record.setPipeLevel1Json(dto.getPipeLevel1Json());
        if (dto.getPipeMonitorJson() != null) record.setPipeMonitorJson(dto.getPipeMonitorJson());
        if (dto.getPipeMaintFluidJson() != null) record.setPipeMaintFluidJson(dto.getPipeMaintFluidJson());
        if (dto.getPipeIvgJson() != null) record.setPipeIvgJson(dto.getPipeIvgJson());
        if (dto.getPipePiccJson() != null) record.setPipePiccJson(dto.getPipePiccJson());
        if (dto.getPipeCvcJson() != null) record.setPipeCvcJson(dto.getPipeCvcJson());
        if (dto.getPipePortJson() != null) record.setPipePortJson(dto.getPipePortJson());
        if (dto.getPipeNasobiliaryJson() != null) record.setPipeNasobiliaryJson(dto.getPipeNasobiliaryJson());
        if (dto.getPipeGastroDecompJson() != null) record.setPipeGastroDecompJson(dto.getPipeGastroDecompJson());
        if (dto.getPipeCatheterJson() != null) record.setPipeCatheterJson(dto.getPipeCatheterJson());
        if (dto.getPipeAbdomDrainJson() != null) record.setPipeAbdomDrainJson(dto.getPipeAbdomDrainJson());
        if (dto.getPipeOtherJson() != null) record.setPipeOtherJson(dto.getPipeOtherJson());

        if (StrUtil.isNotBlank(dto.getStatus())) {
            record.setStatus(dto.getStatus());
        }
        record.setLastEditorId(editorId);
        record.setLastEditorName(editorName);

        if ("SUBMITTED".equals(dto.getStatus())) {
            record.setHandoverNurseId(editorId);
            record.setHandoverNurseName(editorName);
            if (record.getHandoverTime() == null) {
                record.setHandoverTime(LocalDateTime.now());
            }
            if (dto.getSuccessorNurseId() != null) {
                record.setSuccessorNurseId(dto.getSuccessorNurseId());
            }
            if (StrUtil.isNotBlank(dto.getSuccessorNurseName())) {
                record.setSuccessorNurseName(dto.getSuccessorNurseName());
            }
            if (dto.getHandoverNotes() != null) {
                record.setHandoverNotes(dto.getHandoverNotes());
            }
        }

        if (isNew) {
            handoverSpecialMapper.insert(record);
        } else {
            handoverSpecialMapper.updateById(record);
        }

        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmHandover(Long id) {
        ShiftHandoverSpecial record = handoverSpecialMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("交接班记录不存在");
        }
        Long currentUserId = UserContext.getUserId();
        String nurseName = "接班护士";
        Long nurseId = null;
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
        if (record.getSuccessorNurseId() == null && nurseId != null) {
            record.setSuccessorNurseId(nurseId);
            record.setSuccessorNurseName(nurseName);
        }
        record.setSuccessorConfirmTime(LocalDateTime.now());
        handoverSpecialMapper.updateById(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signSpecialHandover(Long id) {
        ShiftHandoverSpecial record = handoverSpecialMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("交接班记录不存在");
        }
        record.setStatus("LOCKED");
        record.setHeadNurseSigned(1);
        record.setHeadNurseSignedTime(LocalDateTime.now());
        handoverSpecialMapper.updateById(record);
    }

    @Override
    public List<SpecialHandoverVO> listHistory(Long wardId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<ShiftHandoverSpecial> wrapper = new LambdaQueryWrapper<>();
        if (wardId != null && wardId > 0) {
            wrapper.eq(ShiftHandoverSpecial::getWardId, wardId);
        }
        if (startDate != null) {
            wrapper.ge(ShiftHandoverSpecial::getHandoverDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(ShiftHandoverSpecial::getHandoverDate, endDate);
        }
        wrapper.orderByDesc(ShiftHandoverSpecial::getHandoverDate);

        List<ShiftHandoverSpecial> list = handoverSpecialMapper.selectList(wrapper);
        return list.stream().map(r -> {
            SpecialHandoverVO vo = new SpecialHandoverVO();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
