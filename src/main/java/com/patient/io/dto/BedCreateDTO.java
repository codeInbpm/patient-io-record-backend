package com.patient.io.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class BedCreateDTO {
    @NotNull(message = "所属病房ID不能为空")
    private Long wardId;

    private String roomNo;

    @NotBlank(message = "床位号不能为空")
    private String bedNo;

    private Integer sortOrder;
}
