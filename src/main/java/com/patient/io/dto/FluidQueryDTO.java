package com.patient.io.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Data
public class FluidQueryDTO {
    /**
     * 选中的病患ID列表（支持同时查询多个客户/病患）
     */
    private List<Long> patientIds;

    /**
     * 所属病区/病房ID
     */
    private Long wardId;

    /**
     * 开始日期（含）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /**
     * 结束日期（含）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /**
     * 记录分类：ALL(全部), INTAKE(仅入量), OUTPUT(仅出量)
     */
    private String category;

    /**
     * 具体入量分类 (ORAL, IV, NASOGASTRIC, OTHER)
     */
    private String intakeType;

    /**
     * 具体出量分类 (URINE, STOOL, GASTRO_DRAINAGE, OTHER_DRAINAGE)
     */
    private String outputType;

    /**
     * 录入人身份筛选：ALL(全部), NURSE(护士/护士长录入), PATIENT(患者自录)
     */
    private String recorderRole;

    /**
     * 模糊搜索关键字（患者姓名、床号、住院号、品名、录入人姓名、备注等）
     */
    private String keyword;
}
