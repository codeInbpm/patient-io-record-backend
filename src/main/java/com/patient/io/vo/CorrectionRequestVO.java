package com.patient.io.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待核对记录（患者说记错了，护士要去看一眼的那条）
 */
@Data
public class CorrectionRequestVO {

    private Long id;
    private Long recordId;
    private String category;      // INTAKE / OUTPUT
    private Long patientId;
    private String patientName;
    private String bedNo;
    private BigDecimal amount;
    private String itemName;      // 品名 / 出量类型
    private LocalDateTime recordTime;
    private String remark;        // 原记录备注（含患者填写说明）
    private String reason;        // 患者说明哪里错了
    private String status;        // PENDING / HANDLED / REJECTED / RECORDED
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}
