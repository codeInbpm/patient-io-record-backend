package com.patient.io.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.patient.io.dto.InfusionCreateDTO;
import com.patient.io.dto.InfusionEndDTO;
import com.patient.io.vo.InfusionVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface InfusionService {

    /**
     * 新增补液记录
     */
    Long createInfusion(InfusionCreateDTO dto);

    /**
     * 结束补液（拔针）
     */
    void endInfusion(InfusionEndDTO dto);

    /**
     * 查询病区当前正在补液的患者列表
     */
    List<InfusionVO> listActiveInfusions(Long wardId);

    /**
     * 查询病区指定日期已结束的补液记录列表
     */
    List<InfusionVO> listCompletedInfusions(Long wardId, LocalDate date);

    /**
     * 查询患者的历史补液记录
     */
    List<InfusionVO> listPatientHistory(Long patientId);

    /**
     * PC端分页查询补液记录
     */
    Page<InfusionVO> pageInfusions(Long wardId, String bedNo, String patientName, String keyword,
                                   String drugName, String nurseName, String status,
                                   LocalDate startDate, LocalDate endDate,
                                   String usageType, Boolean hasDrug, Boolean syncedIntake,
                                   long current, long size);

    /**
     * 获取单条补液详情
     */
    InfusionVO getInfusionDetail(Long id);

    /**
     * 药品结构化覆盖率：total-总记录数, withDrug-有结构化用药的记录数, coverage-覆盖率百分比
     */
    Map<String, Object> drugCoverage();
}
