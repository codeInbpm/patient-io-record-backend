package com.patient.io.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class NurseBindDTO {
    @NotBlank(message = "工号不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "工号必须为6位纯数字")
    private String jobNumber;

    private Long hospitalId;
    private Long deptId;
}
