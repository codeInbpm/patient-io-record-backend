package com.patient.io.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class DailyBalanceVO {
    private Long patientId;
    private String patientName;
    private String bedNo;
    private LocalDate date;

    // 核心汇总
    private BigDecimal totalIntake;
    private BigDecimal totalOutput;
    private BigDecimal netBalance; // 总入量 - 总出量

    // 入量分项
    private BigDecimal oralIntake;        // 口服
    private BigDecimal ivIntake;          // 静脉
    private BigDecimal nasogastricIntake; // 鼻饲
    private BigDecimal otherIntake;       // 其他入量

    // 出量分项
    private BigDecimal urineOutput;       // 尿量
    private BigDecimal stoolOutput;       // 大便量
    private BigDecimal drainageOutput;    // 引流量/减压量
    private BigDecimal otherOutput;       // 其他出量

    // 状态报警
    private Boolean isAbnormal;
    private String abnormalReason;
    private Boolean isUrineAlert;   // 24小时尿量 < 400ml (红)
    private Boolean isBalanceAlert; // 入量-出量差值 > 1000ml (红)
    private Boolean isIvAlert;      // 静脉输液 > 3000ml (黄)

    // 当日按时间倒序的时间流记录明细
    private List<RecordItemVO> items;
}
