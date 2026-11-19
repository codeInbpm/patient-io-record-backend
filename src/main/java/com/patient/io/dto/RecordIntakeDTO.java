package com.patient.io.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecordIntakeDTO {
    @NotNull(message = "病人ID不能为空")
    private Long patientId;

    @NotBlank(message = "入量类型不能为空")
    private String intakeType; // ORAL, IV, NASOGASTRIC, OTHER

    private String subType;    // WATER, CONGEE, NOODLES, RICE_SOUP, LOTUS_STARCH, CUSTOM

    @NotBlank(message = "品名不能为空")
    private String itemName;   // 如：温水、米汤、生理盐水

    @NotNull(message = "摄入量不能为空")
    @DecimalMin(value = "0.1", message = "摄入量必须大于0")
    private BigDecimal amount;

    @NotNull(message = "记录时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;

    private String remark;
}
