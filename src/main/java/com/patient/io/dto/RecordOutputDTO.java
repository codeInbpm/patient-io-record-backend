package com.patient.io.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecordOutputDTO {
    @NotNull(message = "病人ID不能为空")
    private Long patientId;

    @NotBlank(message = "出量类型不能为空")
    private String outputType; // URINE, STOOL, GASTRO_DRAINAGE, CHEST_DRAINAGE, OTHER_DRAINAGE

    @NotNull(message = "排出量不能为空")
    @DecimalMin(value = "0.1", message = "排出量必须大于0")
    private BigDecimal amount;

    private String natureOrColor; // 淡黄清亮、稀便等

    @NotNull(message = "记录时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;

    private String remark;
}
