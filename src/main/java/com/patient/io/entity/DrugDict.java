package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 药品字典（补液用药）
 * 数据来源：院内维护 / 离线导入的候选池
 */
@Data
@TableName("drug_dict")
public class DrugDict implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hospitalId;

    /** 数据来源: 0-手工维护, 1-候选池导入, 2-医保目录 */
    private Integer srcType;
    /** 外部数据源主键，便于二次同步对齐 */
    private String srcId;

    /** 通用名，如 氯化钠注射液 */
    private String genericName;
    /** 商品名，如 罗氏芬 */
    private String tradeName;
    /** 别名/俗名，英文逗号分隔 */
    private String aliasNames;
    /** 规格原文，如 250ml:2.25g */
    private String spec;
    /** 归一后的装量(ml)，便于与补液容量校验 */
    private Integer specVolume;
    /** 归一后的含药量(g)，仅主药有值 */
    private String specDose;
    /** 剂型，如 注射剂 */
    private String dosageForm;
    /** 计量单位，如 支/瓶/袋/ml */
    private String unit;
    /** 默认单次剂量，如 250 */
    private String defaultDose;
    /** 给药途径: ivg/ibn/ivdrip/po/... */
    private String route;
    /** 是否高警示药品(氯化钾/胰岛素/化疗药等) */
    private Integer isHighAlert;

    private String pinyinFull;
    private String pinyinInitial;

    /** 0-待审核, 1-已启用, 2-停用 */
    private Integer status;
    /** 使用频次，用于搜索排序 */
    private Integer useCount;
    private Integer sortOrder;

    @TableLogic
    private Integer isDeleted;

    private Date createTime;
    private Date updateTime;
}
