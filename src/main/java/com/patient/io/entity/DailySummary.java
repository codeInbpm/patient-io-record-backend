package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("daily_summary")
public class DailySummary implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long patientId;
    private Long wardId;
    private LocalDate recordDate;
    private BigDecimal totalIntake;
    private BigDecimal totalOutput;
    private BigDecimal netBalance; // totalIntake - totalOutput
    private BigDecimal oralIntake;
    private BigDecimal ivIntake;
    private BigDecimal nasogastricIntake;
    private BigDecimal urineOutput;
    private BigDecimal stoolOutput;
    private BigDecimal drainageOutput;
    private Integer isAbnormal; // 0-正常, 1-异常
    private Date updateTime;
}
