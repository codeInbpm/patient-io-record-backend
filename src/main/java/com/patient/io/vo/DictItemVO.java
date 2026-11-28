package com.patient.io.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 下拉选项通用结构，前端 useDict 直接消费
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String value;
    private String label;
    private String extra;
    private Integer sortOrder;
}
