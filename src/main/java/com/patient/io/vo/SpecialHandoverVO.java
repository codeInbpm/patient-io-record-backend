package com.patient.io.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SpecialHandoverVO {
    private Long id;
    private Long hospitalId;
    private Long wardId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate handoverDate;

    private String shiftType;

    // 左侧关注项
    private String leaveHospitalJson;
    private String laxativeJson;
    private String temperatureAbnormalJson;
    private String bloodSugarJson;
    private String bloodPressureJson;
    private String urineVolumeJson;
    private String specialCases;

    // 右侧管路巡视项
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

    private String status; // DRAFT, SUBMITTED, LOCKED
    private Long lastEditorId;
    private String lastEditorName;

    // 交班人与接班人追踪
    private Long handoverNurseId;
    private String handoverNurseName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handoverTime;
    private Long successorNurseId;
    private String successorNurseName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime successorConfirmTime;
    private String handoverNotes;

    private Integer headNurseSigned;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime headNurseSignedTime;
}
