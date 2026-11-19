package com.patient.io.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FluidRecordDetailVO {
    private Long id;
    private String category;      // INTAKE(入量), OUTPUT(出量)
    private Long patientId;
    private String patientName;
    private String bedNo;
    private String hospitalNo;
    private Long wardId;
    private String wardName;

    private String type;          // ORAL, IV, URINE, STOOL 等
    private String typeText;      // 口服-温水, 尿量 等
    private String itemName;      // 品名/排泄物名
    private BigDecimal amount;    // 量(ml)
    private String natureOrColor; // 颜色/性状
    private LocalDateTime recordTime;
    private LocalDate recordDate;

    private Long recorderId;      // 录入人sys_user.id
    private String recorderRole;  // HEAD_NURSE, NURSE, PATIENT
    private String recorderName;  // 录入人具体姓名 (如 李丽护士、张洁护士长、李建国)
    private String recorderText;  // 人性化展示文本 (如 李丽护士录入、张洁护士长录入、患者本人录入)

    private String remark;        // 备注

    // 预警标识
    private String alertLevel;    // "red", "yellow", null
    private String alertTag;      // 预警标签文本，如 "少尿<400ml", "静脉输液>3000ml"
}
