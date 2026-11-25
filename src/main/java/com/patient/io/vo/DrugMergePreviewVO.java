package com.patient.io.vo;

import lombok.Data;

import java.util.List;

/**
 * 重复药品归并预览
 */
@Data
public class DrugMergePreviewVO {

    /** 存在重复的分组数 */
    private int groupCount;

    /** 会被停用的重复条数 */
    private int duplicateCount;

    /** 明细，按重复条数从多到少排序 */
    private List<DrugMergeGroupVO> groups;
}
