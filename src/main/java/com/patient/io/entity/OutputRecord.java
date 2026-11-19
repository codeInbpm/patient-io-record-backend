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
@TableName("output_record")
public class OutputRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long patientId;
    private Long wardId;
    private String outputType; // URINE, STOOL, GASTRO_DRAINAGE, CHEST_DRAINAGE, OTHER_DRAINAGE
    private BigDecimal amount; // ml
    private String natureOrColor; // 淡黄清亮、稀便等
    private LocalDateTime recordTime;
    private LocalDate recordDate;
    private Long recorderId;
    private String recorderRole; // NURSE, PATIENT
    private String remark;

    @TableLogic
    private Integer isDeleted;

    private Date createTime;
    private Date updateTime;
}
