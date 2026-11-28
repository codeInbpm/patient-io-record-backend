package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 审计日志：只增不改，应用层无删除入口 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userType;
    private Long userId;
    private String username;
    private String realName;
    private String roleCode;
    private Long hospitalId;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String result;
    private String ip;
    private String userAgent;
    private Date createTime;
}
