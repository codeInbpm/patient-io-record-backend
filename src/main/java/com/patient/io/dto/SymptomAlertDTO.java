package com.patient.io.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 患者上报异常症状（血尿、黑便、水样便等）
 *
 * 和「记错了」不是一件事：记错了是数据问题，症状是病情问题。
 * 症状必须让护士立刻看到，不能只弹一句提示就结束。
 */
@Data
public class SymptomAlertDTO {

    @NotNull(message = "患者ID不能为空")
    private Long patientId;

    /** 症状摘要，如「尿色发红」「柏油样黑便」 */
    @NotBlank(message = "症状描述不能为空")
    private String symptom;

    /** 相关记录类型：OUTPUT / INTAKE，用于护士端分类展示 */
    private String category;

    /** 本次记录量，可能为空（患者只上报症状没记量） */
    private BigDecimal amount;

    /** 品名/性状快照 */
    private String itemName;

    /** 患者补充说明 */
    private String remark;
}
