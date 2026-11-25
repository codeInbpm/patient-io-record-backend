package com.patient.io.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InfusionCreateDTO {
    @NotNull(message = "患者ID不能为空")
    private Long patientId;

    @NotNull(message = "补液容量不能为空")
    private Integer capacity;

    @NotNull(message = "给药用法不能为空")
    private String usageType;

    @NotNull(message = "开始补液时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "预计拔针时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedEndTime;

    private String remark;

    /**
     * 结构化用药明细（护士端从药品字典选择）。为空时降级为 remark 自由文本。
     */
    private List<InfusionDrugDTO> drugs;

    /**
     * 上传的药袋/穿刺部位图片URL列表
     */
    private List<String> imageUrls;

    /**
     * 是否同步写入入量记录（默认是）
     */
    private Boolean syncToIntake = true;
}
