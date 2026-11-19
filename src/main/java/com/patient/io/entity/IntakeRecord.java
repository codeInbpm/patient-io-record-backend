package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("intake_record")
public class IntakeRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long patientId;
    private Long wardId;
    private String intakeType; // ORAL, IV, NASOGASTRIC, OTHER
    private String subType;    // WATER, CONGEE, NOODLES, RICE_SOUP, LOTUS_STARCH, CUSTOM
    private String itemName;   // 温水, 藕粉等
    private BigDecimal amount; // ml
    private LocalDateTime recordTime; // 精确到分钟
    private LocalDate recordDate;     // 自然日
    private Long recorderId;
    private String recorderRole;      // NURSE, PATIENT
    private String remark;

    @TableLogic
    private Integer isDeleted;

    private Date createTime;
    private Date updateTime;
}
