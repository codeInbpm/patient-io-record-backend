package com.patient.io.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class InfusionEndDTO {
    @NotNull(message = "补液记录ID不能为空")
    private Long infusionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    /**
     * 实际滴入量(ml，默认等于原容量)
     */
    private Integer actualIntakeAmount;

    /**
     * 状态: COMPLETED(正常结束), STOPPED(异常提前中断)
     */
    private String status = "COMPLETED";

    private String remark;
}
