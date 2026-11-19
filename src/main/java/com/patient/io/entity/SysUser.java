package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("sys_user")
public class SysUser implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String unionid;
    private String nickname;
    private String avatarUrl;
    private String role; // HEAD_NURSE, NURSE, PATIENT, NONE
    private Integer bindStatus; // 0-未绑定, 1-已绑定
    private Long bindRefId;     // nurse.id 或 patient.id
    private Integer status;     // 1-启用, 0-禁用
    private Date createTime;
    private Date updateTime;
}
