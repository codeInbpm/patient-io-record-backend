package com.patient.io.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class PatientCreateDTO {
    @NotNull(message = "所属病房不能为空")
    private Long wardId;

    @NotNull(message = "所属床位不能为空")
    private Long bedId;

    private String bedNo;

    @NotBlank(message = "病人姓名不能为空")
    private String patientName;

    @NotBlank(message = "住院号不能为空")
    private String hospitalNo;

    private Integer gender; // 1-男, 2-女
    private Integer age;
    private String patientType; // ADULT, INFANT
    private String admissionType; // ROUTINE(平诊), EMERGENCY(急诊)
    private Double weight;
    private String nursingLevel; // SPECIAL, LEVEL_1, LEVEL_2, LEVEL_3
    private Integer needIoRecord; // 1-监测出入量, 0-不监测
    private String drainageTubes; // 留置管路

    @NotNull(message = "入院日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate admissionDate;

    private String diagnosis;
}
