package com.patient.io.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 反馈处理中心列表项（后台统一收口三类事项）
 */
@Data
public class FeedbackItemVO {

    private Long id;
    /** 展示单号，如 FK-20261128-0011 */
    private String code;

    /** INTAKE / OUTPUT / SYMPTOM / FAMILY */
    private String category;
    private String typeLabel;

    /** HIGH / MID / LOW */
    private String level;
    private String levelLabel;

    private Long patientId;
    private String patientName;
    private String bedNo;
    private Long wardId;
    private String wardName;
    private String hospitalNo;

    private Long recordId;
    /** 原记录类型 INTAKE / OUTPUT */
    private String recordCategory;
    private BigDecimal amount;
    private BigDecimal correctedAmount;
    private String itemName;
    private LocalDateTime recordTime;
    private String remark;
    private String reason;

    private String status;
    private String handleAction;
    private String handleNote;
    private String handlerName;
    private String syncStatus;

    private String reporter;
    private String source;
    private String sourceLabel;

    private LocalDateTime createTime;
    private LocalDateTime handleTime;
    private LocalDateTime deferUntil;

    /** 已等待分钟数 */
    private long waitMinutes;
    /** 是否超时 */
    private boolean overdue;
    /** 超时分钟数 */
    private long overdueMinutes;
    /** 处理用时（分钟） */
    private Long handleMinutes;
}
