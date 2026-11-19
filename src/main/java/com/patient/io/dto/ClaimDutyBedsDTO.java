package com.patient.io.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class ClaimDutyBedsDTO {

    @NotNull(message = "病房ID不能为空")
    private Long wardId;

    /**
     * 当前护士选择管辖负责的床位ID列表（取消勾选的床位不包含在列表中）
     */
    private List<Long> bedIds;
}
