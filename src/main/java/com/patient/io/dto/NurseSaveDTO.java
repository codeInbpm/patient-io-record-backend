package com.patient.io.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class NurseSaveDTO {
    private Long id; // 编辑时传入

    @NotBlank(message = "工号不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "工号必须为6位纯数字")
    private String jobNumber;

    @NotBlank(message = "护士姓名不能为空")
    private String realName;

    private String role; // HEAD_NURSE, NURSE
    private Long hospitalId;
    private String hospitalName;
    private Long deptId;
    private String department;
    private String building;
    private Long wardId;
    private String wardName;
    private String phone;
}
