package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("shift_handover_special")
public class ShiftHandoverSpecial implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hospitalId;
    private Long wardId;
    private LocalDate handoverDate;
    private String shiftType; // ALL_DAY, DAY, NIGHT

    // 左侧关注项
    private String leaveHospitalJson;
    private String laxativeJson;
    private String temperatureAbnormalJson;
    private String bloodSugarJson;
    private String bloodPressureJson;
    private String urineVolumeJson;
    private String specialCases;

    // 右侧管路与巡视项
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
    private LocalDateTime handoverTime;
    private Long successorNurseId;
    private String successorNurseName;
    private LocalDateTime successorConfirmTime;
    private String handoverNotes;

    private Integer headNurseSigned;
    private LocalDateTime headNurseSignedTime;

    private Date createTime;
    private Date updateTime;
}
