package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 数据字典类型（字典分组）
 * hospital_id = 0 表示平台内置通用字典，各医院可直接复用；
 * 医院自建字典写入自己的 hospital_id，同 code 时优先使用本院的配置。
 */
@Data
@TableName("sys_dict_type")
public class SysDictType implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 0-平台通用，其他为医院ID */
    private Long hospitalId;

    /** 字典编码，如 nursing_level，程序内引用依据 */
    private String dictCode;

    /** 字典名称，如 护理级别 */
    private String dictName;

    private String remark;

    /** 1-启用, 0-停用 */
    private Integer status;

    private Date createTime;
    private Date updateTime;

    /** 列表展示用：字典项数量 */
    @TableField(exist = false)
    private Integer itemCount;

    /** 列表展示用：是否为平台内置字典 */
    @TableField(exist = false)
    private Boolean builtin;
}
