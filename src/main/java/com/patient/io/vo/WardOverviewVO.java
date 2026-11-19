package com.patient.io.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WardOverviewVO {
    private Long wardId;
    private String wardName;
    private Integer totalPatients;
    private Integer recordedPatients; // 今日已记录病人数
    private Integer abnormalCount;    // 今日出入量异常预警人数
    private BigDecimal wardTotalIntake;
    private BigDecimal wardTotalOutput;
    private List<PatientVO> patients;
}
