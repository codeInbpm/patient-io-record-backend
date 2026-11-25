package com.patient.io.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecordItemVO {
    private Long id;
    private Long patientId;
    private String category;      // INTAKE (入量), OUTPUT (出量)
    private String type;          // ORAL, IV, URINE, STOOL 等
    private String typeText;      // 口服-温水, 尿量 等展示文本
    private String itemName;
    private BigDecimal amount;
    private LocalDateTime recordTime;
    private String natureOrColor; // 颜色/性状
    private Long recorderId;      // 录入人ID (sys_user.id)
    private String recorderRole;  // HEAD_NURSE, NURSE, PATIENT
    private String recorderName;  // 录入人姓名，如 李丽护士、李建国(患者)
    private String recorderText;  // 人性化展示文本，如 李丽护士录入、患者本人录入
    private String remark;
}
