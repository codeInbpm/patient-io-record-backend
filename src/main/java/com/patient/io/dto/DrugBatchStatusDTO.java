package com.patient.io.dto;

import lombok.Data;

import java.util.List;

/**
 * 药品库批量改状态入参
 */
@Data
public class DrugBatchStatusDTO {

    /** 待操作的药品ID集合 */
    private List<Long> ids;

    /** 1-启用 2-停用 */
    private Integer status;
}
