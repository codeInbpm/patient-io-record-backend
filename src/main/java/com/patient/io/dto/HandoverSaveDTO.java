package com.patient.io.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 交班草稿保存
 */
@Data
public class HandoverSaveDTO {

    @NotNull(message = "病区不能为空")
    private Long wardId;

    /** yyyy-MM-dd，缺省为今天 */
    private String shiftDate;

    /** DAY / NIGHT，缺省 NIGHT */
    private String shiftType;

    /** 护士长叮嘱 */
    private String memo;

    /** 交班报告正文 */
    private String reportText;
}
