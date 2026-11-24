package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("nurse")
public class Nurse implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobNumber;   // 6位纯数字唯一工号
    private String realName;
    private String role;        // HEAD_NURSE, NURSE
    private Long hospitalId;
    private String hospitalName;
    private Long deptId;
    private String department;
    private Long wardId;        // 所属病区ID
    private String wardName;    // 所属病区名称
    private String building;    // 所在楼宇
    private String phone;
    private Long boundUserId;   // 关联sys_user.id
    private String wxNoticeOpenid; // 微信测试号 OpenID (用于接收模板消息强提醒)
    private Integer status;     // 1-在职, 0-离职
    private Date createTime;
    private Date updateTime;
}
