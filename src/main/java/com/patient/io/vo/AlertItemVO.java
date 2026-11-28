package com.patient.io.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局告警条目（由实时规则聚合生成，处置状态来自 alert_ack）
 */
@Data
public class AlertItemVO {

    /** 稳定唯一键，如 BALANCE:12:2026-11-28 */
    private String key;
    /** BALANCE / INFUSION / ASSIGN / HANDOVER */
    private String type;
    private String typeLabel;
    /** HIGH / MID / LOW */
    private String level;
    private String levelLabel;

    private String title;
    private String detail;

    private Long patientId;
    private String patientName;
    private String bedNo;
    private Long wardId;
    private String wardName;
    private Long refId;

    /** 前端定位用：patient / infusion / assignment / handover */
    private String target;

    /** OPEN / ACKED / CLOSED */
    private String status;
    private String statusLabel;
    private String ownerName;
    private LocalDateTime ackTime;
    private LocalDateTime closeTime;
    private String note;

    private LocalDateTime occurredAt;
    /** 已持续分钟 */
    private long minutes;
}
