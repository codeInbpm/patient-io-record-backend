package com.patient.io.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * 护士长处理一条反馈
 *
 * action:
 *   FIX     通过并修正数值（仅更正类，需 correctedAmount）
 *   CONFIRM 核对无误，原记录成立
 *   REJECT  驳回，记录不成立，从当天出入量中移除
 *   REFER   转交值班医生（需 note）
 *   REPLY   已回复家属（需 note）
 *   DEFER   延后处理（默认 30 分钟）
 */
@Data
public class FeedbackHandleDTO {

    @NotBlank(message = "请选择处理方式")
    private String action;

    /** 修正后数值，FIX 时必填 */
    private BigDecimal correctedAmount;

    /** 处理说明：驳回原因 / 转交意见 / 回复内容 */
    private String note;

    /** 延后分钟数，DEFER 时使用，默认 30 */
    private Integer deferMinutes;

    /** 是否通知家属端，默认 true */
    private Boolean notifyFamily;
}
