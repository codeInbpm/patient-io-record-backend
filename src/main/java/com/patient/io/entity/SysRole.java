package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 角色。hospitalId = 0 表示平台内置角色（8 个），其它值为医院自建角色。
 */
@Data
@TableName("sys_role")
public class SysRole implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleCode;
    private String roleName;
    private String roleDesc;
    /** ALL / HOSPITAL / DEPT / WARD / SELF */
    private String dataScope;
    private Long hospitalId;
    /** 1=内置，不可删除、不可改权限 */
    private Integer builtin;
    private Integer status;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
}
