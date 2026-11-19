package com.patient.io.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PatientSummaryVO {
    private Long patientId;
    private String patientName;
    private String bedNo;
    private String hospitalNo;
    private Long wardId;
    private String wardName;
    private String assignedNurseName;

    private BigDecimal totalIntake = BigDecimal.ZERO;
    private BigDecimal totalOutput = BigDecimal.ZERO;
    private BigDecimal netBalance = BigDecimal.ZERO;
    private BigDecimal urineOutput = BigDecimal.ZERO;
    private BigDecimal ivIntake = BigDecimal.ZERO;
    private Integer recordCount = 0;

    // 预警状态
    private Boolean isUrineAlert = false;   // 24小时尿量 < 400ml (红)
    private Boolean isBalanceAlert = false; // 入量-出量差值 > 1000ml (红)
    private Boolean isIvAlert = false;      // 静脉输液 > 3000ml (黄)
    private Boolean isAbnormal = false;
}
