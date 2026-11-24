package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("infusion_record")
public class InfusionRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hospitalId;
    private Long wardId;
    private Long bedId;
    private String bedNo;
    private Long patientId;
    private String patientName;
    private Integer capacity;
    private String usageType;
    private LocalDateTime startTime;
    private LocalDateTime estimatedEndTime;
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
    private Integer remindStatus; // 提醒状态: 0-未提醒, 1-即将结束已提醒, 2-超时已提醒

    @TableLogic
    private Integer isDeleted;

    private Date createTime;
    private Date updateTime;
}
