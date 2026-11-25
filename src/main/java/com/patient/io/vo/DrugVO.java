package com.patient.io.vo;

import lombok.Data;

@Data
public class DrugVO {
    /** 本地字典ID；在线兜底结果为空，前端按 name+spec 去重 */
    private Long id;
    private String genericName;
    private String tradeName;
    private String aliasNames;
    private String spec;
    private String dosageForm;
    private String unit;
    private String defaultDose;
    private String route;
    /** 1-高警示药品，前端标红并二次确认 */
    private Integer highAlert;
    private String pinyinInitial;
    /** 0-待确认 1-已确认，个人库里两者都参与检索 */
    private Integer status;
    /** 待确认：来自候选池且规格未核对过，选中时提示核对 */
    private Boolean pending;
    /** 使用频次，管理端展示与排序参考 */
    private Integer useCount;
}
