package com.patient.io.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class PatientBindDTO {
    @NotBlank(message = "真实姓名不能为空")
    private String patientName;

    @NotNull(message = "所属楼宇/病区不能为空")
    private Long wardId;

    @NotBlank(message = "床位号不能为空")
    private String bedNo;

    private Long bedId;

    @NotBlank(message = "住院号不能为空")
    private String hospitalNo;
}
