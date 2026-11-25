package com.patient.io.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.patient.io.common.BusinessException;
import com.patient.io.common.UserContext;
import com.patient.io.entity.Ward;
import com.patient.io.mapper.WardMapper;
import com.patient.io.service.PatientService;
import com.patient.io.service.WardService;
import com.patient.io.vo.PatientVO;
import com.patient.io.vo.WardOverviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WardServiceImpl extends ServiceImpl<WardMapper, Ward> implements WardService {

    @Autowired
    @Lazy
    private PatientService patientService;

    @Override
    public List<Ward> listActiveWards() {
        return listWards(1, false);
    }

    @Override
    public List<Ward> listWards(Integer status, Boolean all) {
        LambdaQueryWrapper<Ward> wrapper = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(all)) {
            // 仅在明确传入 all=true 时（楼宇与病区管理维护界面），才返回包括停用在内的全部病区
            if (status != null) {
                wrapper.eq(Ward::getStatus, status);
            }
        } else {
            // 其他所有业务场景（前后台录入病患、出入量工作台、床位分配等）：一律强制只返回已启用的病区！
            wrapper.eq(Ward::getStatus, 1);
        }
        wrapper.orderByAsc(Ward::getSortOrder)
                .orderByDesc(Ward::getId);
        return list(wrapper);
    }

    private void checkManagePermission() {
        String role = UserContext.getRole();
        if (!"HEAD_NURSE".equals(role) && !"SUPER_ADMIN".equals(role) && !"HOSPITAL_ADMIN".equals(role)) {
            throw new BusinessException(403, "权限不足，仅护士长或系统管理员可维护病房");
        }
    }

    @Override
    public Ward createWard(Ward ward) {
        checkManagePermission();
        ward.setCreateBy(UserContext.getUserId());
        if (ward.getStatus() == null) {
            ward.setStatus(1);
        }
        if (ward.getSortOrder() == null) {
            ward.setSortOrder(0);
        }
        save(ward);
        return ward;
    }

    @Override
    public void updateWard(Ward ward) {
        checkManagePermission();
        if (ward.getId() == null) {
            throw new BusinessException("病房ID不能为空");
        }
        updateById(ward);
    }

    @Override
    public void deleteWard(Long wardId) {
        checkManagePermission();
        removeById(wardId);
    }

    @Override
    public WardOverviewVO getWardTodayOverview(Long wardId) {
        Ward ward = getById(wardId);
        if (ward == null) {
            throw new BusinessException("病房不存在");
        }

        List<PatientVO> patients = patientService.listPatientsForCurrentRole(wardId);

        BigDecimal totalIntake = BigDecimal.ZERO;
        BigDecimal totalOutput = BigDecimal.ZERO;
        int recordedCount = 0;
        int abnormalCount = 0;
        int missedCount = 0;

        for (PatientVO p : patients) {
            if (p.getTodayIntake() != null) {
                totalIntake = totalIntake.add(p.getTodayIntake());
            }
            if (p.getTodayOutput() != null) {
                totalOutput = totalOutput.add(p.getTodayOutput());
            }
            if ((p.getTodayIntake() != null && p.getTodayIntake().compareTo(BigDecimal.ZERO) > 0)
                    || (p.getTodayOutput() != null && p.getTodayOutput().compareTo(BigDecimal.ZERO) > 0)) {
                recordedCount++;
            }
            if (Boolean.TRUE.equals(p.getIsAbnormal())) {
                abnormalCount++;
            }
            if (Boolean.TRUE.equals(p.getIsMissed())) {
                missedCount++;
            }
        }

        WardOverviewVO vo = new WardOverviewVO();
        vo.setWardId(ward.getId());
        vo.setWardName(ward.getWardName());
        vo.setTotalPatients(patients.size());
        vo.setRecordedPatients(recordedCount);
        vo.setAbnormalCount(abnormalCount);
        vo.setMissedCount(missedCount);
        vo.setWardTotalIntake(totalIntake);
        vo.setWardTotalOutput(totalOutput);
        vo.setPatients(patients);

        return vo;
    }
}
