package com.patient.io.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class NurseAssignBedsDTO {
    @NotNull(message = "护士ID不能为空")
    private Long nurseId;

    @NotNull(message = "病房ID不能为空")
    private Long wardId;

    /**
     * 该护士在所属病房下负责的床位ID列表
     */
    private List<Long> bedIds;
}
