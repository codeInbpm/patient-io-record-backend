package com.patient.io.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 患者提交「这条记错了」的核对请求
 * 患者端不直接改数据，只发起请求，由护士核对后修正，保证临床数据留痕
 */
@Data
public class CorrectionRequestDTO {

    @NotNull(message = "记录ID不能为空")
    private Long recordId;

    @NotBlank(message = "记录类型不能为空")
    private String category; // INTAKE / OUTPUT

    private String reason;
}
