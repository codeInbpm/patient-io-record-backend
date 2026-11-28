package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 数据字典项。同一字典编码下 item_value 唯一。
 */
@Data
@TableName("sys_dict_item")
public class SysDictItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 与 sys_dict_type.hospital_id 保持一致，0-平台通用 */
    private Long hospitalId;

    /** 所属字典编码 */
    private String dictCode;

    /** 程序存储值，如 LEVEL_1 */
    private String itemValue;

    /** 界面显示名，如 一级护理 */
    private String itemLabel;

    /** 扩展字段：图标/单位/备注等，前端按字典定义解释 */
    private String extra;

    private Integer sortOrder;

    /** 1-启用, 0-停用 */
    private Integer status;

    private Date createTime;
    private Date updateTime;
}
