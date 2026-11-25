package com.patient.io.vo;

import lombok.Data;

import java.util.List;

/**
 * 一组「同通用名 + 同规格」的重复药品
 */
@Data
public class DrugMergeGroupVO {

    /** 通用名，展示用 */
    private String genericName;

    /** 原始规格，展示用（取保留项或首条的规格原文） */
    private String spec;

    /** 归一化后的规格，用来解释"为什么判定为重复" */
    private String normSpec;

    /** 保留项 */
    private DrugVO keep;

    /** 将被停用的重复项 */
    private List<DrugVO> duplicates;

    /** 本组累计使用次数（归并后会合并到保留项） */
    private int totalUseCount;
}
