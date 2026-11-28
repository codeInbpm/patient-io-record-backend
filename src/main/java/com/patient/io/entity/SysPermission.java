package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/** 权限码字典（模块:动作） */
@Data
@TableName("sys_permission")
public class SysPermission implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String permCode;
    private String permName;
    private String module;
    private String permType;
    /** 1 普通 / 2 敏感 / 3 高危 */
    private Integer riskLevel;
    private Integer sortOrder;
}
