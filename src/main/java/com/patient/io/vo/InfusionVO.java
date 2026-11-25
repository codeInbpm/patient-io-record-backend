package com.patient.io.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.patient.io.dto.InfusionDrugDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InfusionVO {
    private Long id;
    private Long hospitalId;
    private Long wardId;
    private Long bedId;
    private String bedNo;
    private Long patientId;
    private String patientName;
    private Integer capacity;
    private String usageType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedEndTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    private String status; // INFUSING, COMPLETED, STOPPED
    private Integer actualIntakeAmount;
    private Integer isSyncedIntake;
    private Long intakeRecordId;
    private Long nurseId;
    private String nurseName;
    private Long endNurseId;
    private String endNurseName;
    private String remark;

    /** 结构化用药明细 JSON，前端按需解析 */
    private String drugJson;

    /**
     * 结构化用药明细（已解析），前端逐条展示；
     * 历史数据没有 drug_json 时为空数组，前端回退到 remark 文本
     */
    private List<InfusionDrugDTO> drugs;

    /**
     * 图片列表
     */
    private List<String> imageUrls;

    /**
     * 预计剩余分钟数（负数表示超时分钟数）
     */
    private Long remainingMinutes;

    /**
     * 是否超时未拔针
     */
    private Boolean isTimeout;

    /**
     * 滴注进度百分比 (0 - 100)
     */
    private Integer progressPercent;
}
