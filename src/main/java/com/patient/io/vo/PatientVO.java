package com.patient.io.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PatientVO {
    private Long id;
    private Long wardId;
    private String wardName;
    private Long bedId;
    private String bedNo;
    private String patientName;
    private String hospitalNo;
    private Integer gender;
    private Integer age;
    private String patientType; // ADULT, INFANT
    private String admissionType; // ROUTINE, EMERGENCY
    private Double weight;
    private String nursingLevel; // SPECIAL, LEVEL_1, LEVEL_2, LEVEL_3
    private Integer needIoRecord; // 1-监测, 0-不监测
    private String drainageTubes;
    private LocalDate admissionDate;
    private String diagnosis;
    private Boolean isBound;
    private Integer status;

    // 负责护士信息
    private Long assignedNurseId;
    private String assignedNurseName;
    private java.util.List<String> assignedNurseNames;
    private Boolean isMyDuty; // 当前登录护士是否在管辖负责（具备记录权限）

    // 当日实时统计简报
    private BigDecimal todayIntake;
    private BigDecimal todayOutput;
    private BigDecimal todayBalance;
    private BigDecimal todayUrine;
    private BigDecimal todayIv;
    private Boolean isAbnormal;
    private String abnormalReason;
    private Boolean isUrineAlert;   // 24小时尿量 < 400ml (红)
    private Boolean isBalanceAlert; // 入量-出量差值 > 1000ml (红)
    private Boolean isIvAlert;      // 静脉输液 > 3000ml (黄)

    // 滚动24小时统计指标（从当前时间往前倒推24小时）
    private BigDecimal last24hUrine;
    private BigDecimal last24hIntake;
    private BigDecimal last24hOutput;
    private BigDecimal last24hBalance;
}
