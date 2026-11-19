package com.patient.io.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class FluidQueryVO {
    /**
     * 筛选条件下的全部总入量 (ml)
     */
    private BigDecimal totalIntake = BigDecimal.ZERO;

    /**
     * 筛选条件下的全部总出量 (ml)
     */
    private BigDecimal totalOutput = BigDecimal.ZERO;

    /**
     * 净平衡 (ml)
     */
    private BigDecimal netBalance = BigDecimal.ZERO;

    /**
     * 涉及患者总数
     */
    private Integer patientCount = 0;

    /**
     * 流水总记录笔数
     */
    private Integer recordCount = 0;

    /**
     * 各病患出入量汇总统计
     */
    private List<PatientSummaryVO> patientSummaries = new ArrayList<>();

    /**
     * 出入量流水明细列表（按时间倒序）
     */
    private List<FluidRecordDetailVO> records = new ArrayList<>();
}
