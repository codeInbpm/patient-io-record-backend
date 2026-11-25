package com.patient.io.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 个人药品库新增/编辑入参
 */
@Data
public class DrugSaveDTO {

    /** 为空表示新增，有值表示编辑 */
    private Long id;

    @NotBlank(message = "药品名称不能为空")
    @Size(max = 128, message = "药品名称过长")
    private String genericName;

    /** 规格，如 0.9% 250ml:2.25g、0.5g */
    @Size(max = 128, message = "规格过长")
    private String spec;

    /** 别名/俗名，英文逗号分隔，用于拼音与中文检索 */
    @Size(max = 255, message = "别名过长")
    private String aliasNames;

    /** 商品名 */
    @Size(max = 128, message = "商品名过长")
    private String tradeName;

    @Size(max = 16, message = "单位过长")
    private String unit;

    @Size(max = 64, message = "剂型过长")
    private String dosageForm;

    @Size(max = 32, message = "默认剂量过长")
    private String defaultDose;

    @Size(max = 32, message = "给药途径过长")
    private String route;

    /** 0-否 1-是，为空则按药名关键词自动识别 */
    private Integer isHighAlert;
}
