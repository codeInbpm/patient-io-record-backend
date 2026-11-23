package com.patient.io.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class SpecialHandoverSaveDTO {
    private Long id;

    @NotNull(message = "病区ID不能为空")
    private Long wardId;

    @NotNull(message = "交接日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate handoverDate;

    private String shiftType = "ALL_DAY";

    // 左侧关注项 (半结构化 JSON 字符串)
    private String leaveHospitalJson;
    private String laxativeJson;
    private String temperatureAbnormalJson;
    private String bloodSugarJson;
    private String bloodPressureJson;
    private String urineVolumeJson;
    private String specialCases;

    // 右侧管路巡视项 (JSON 字符串)
    private String pipeLevel1Json;
    private String pipeMonitorJson;
    private String pipeMaintFluidJson;
    private String pipeIvgJson;
    private String pipePiccJson;
    private String pipeCvcJson;
    private String pipePortJson;
    private String pipeNasobiliaryJson;
    private String pipeGastroDecompJson;
    private String pipeCatheterJson;
    private String pipeAbdomDrainJson;
    private String pipeOtherJson;

    /**
     * 状态: DRAFT(草稿), SUBMITTED(交班提交)
     */
    private String status;

    // 指定接班人
    private Long successorNurseId;
    private String successorNurseName;
    private String handoverNotes;
}
