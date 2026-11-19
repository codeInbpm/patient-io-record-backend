package com.patient.io.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class NurseAssignDTO {
    @NotNull(message = "病人ID不能为空")
    private Long patientId;

    @NotNull(message = "护士ID不能为空")
    private Long nurseId;
}
