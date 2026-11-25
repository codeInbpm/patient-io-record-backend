package com.patient.io.dto;

import lombok.Data;

/**
 * 补液用药明细（护士端从字典选择后提交）
 */
@Data
public class InfusionDrugDTO {
    /** 字典ID，手工录入时为空 */
    private Long id;
    /** 药品名称（通用名优先） */
    private String name;
    /** 规格，如 250ml:2.25g */
    private String spec;
    /** 剂量，如 250 */
    private String dose;
    /** 剂量单位，如 ml / 支 / g */
    private String unit;
    /** 给药途径 */
    private String route;
    /** 1-高警示药品 */
    private Integer highAlert;
    /** true-未在字典中，护士手工录入 */
    private Boolean manual;
}
