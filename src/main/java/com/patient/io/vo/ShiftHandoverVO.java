package com.patient.io.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 交班记录（PC 端只落提交人/时间/班次，签名与接班确认在小程序端）
 */
@Data
public class ShiftHandoverVO {

    private Long id;
    private Long wardId;
    private String wardName;
    private LocalDate shiftDate;
    private String shiftType;
    private String shiftTypeLabel;

    /** DRAFT / SUBMITTED / LOCKED / CONFIRMED */
    private String status;
    private String statusLabel;

    private String memo;
    private String reportText;
    private String statsJson;
    /** statsJson 的解析结果，小程序端直接渲染，避免各端重复解析 */
    private Map<String, Object> stats;
    private Integer patientCount;

    private Long submitBy;
    private String submitByName;
    private LocalDateTime submitTime;

    private Long receiverId;
    private String receiverName;
    private LocalDateTime receiveTime;
    private String signatureUrl;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 是否可编辑（草稿） */
    private boolean editable;
    /** 是否可提交 */
    private boolean submittable;
    /** 是否可撤销提交（已提交且接班人未确认） */
    private boolean reopenable;
}
