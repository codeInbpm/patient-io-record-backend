package com.patient.io.vo;

import lombok.Data;

import java.util.List;

@Data
public class DrugPageVO {
    private long total;
    private List<DrugVO> list;
}
