package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 账号角色授权：userType = ADMIN / NURSE */
@Data
@TableName("sys_user_role")
public class SysUserRole implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userType;
    private Long userId;
    private Long roleId;
    private Long hospitalId;
    private Long deptId;
    /** 病区 ID 逗号分隔，空=按角色默认范围 */
    private String wardIds;
    private Date effectiveFrom;
    private Date effectiveTo;
    private Integer status;
    private Long grantBy;
    private Date grantTime;
}
