package com.patient.io.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedVisualVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long bedId;
    private Long wardId;
    private String wardName;
    private String roomNo; // 所属房间号，如 01号房间、02号房间
    private String bedNo; // 床位编号，如 01床、02床
    private Integer status; // 0-空床, 1-占用
    private Integer sortOrder;

    // 占用人病患详情
    private Long patientId;
    private String patientName;
    private String hospitalNo;
    private Integer gender; // 1-男, 2-女
    private Integer age;
    private String patientType; // ADULT, INFANT
    private String nursingLevel; // SPECIAL, LEVEL_1, LEVEL_2, LEVEL_3
    private String diagnosis;
    private Integer needIoRecord; // 1-监测中, 0-不测
    private String drainageTubes; // 留置管路

    // 责任护士
    private Long assignedNurseId;
    private String assignedNurseName;

    // 今日出入量指标
    private BigDecimal todayIntake;
    private BigDecimal todayOutput;
    private BigDecimal todayBalance;
    private Boolean isAbnormal;
}
