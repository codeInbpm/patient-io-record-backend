package com.patient.io.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;
    private Long userId;
    private String openid;
    private String nickname;
    private String avatarUrl;
    private String role;        // HEAD_NURSE, NURSE, PATIENT, NONE
    private Boolean isBound;    // 是否已绑定真实身份
    private String realName;    // 真实姓名
    private String jobNumber;   // 6位工号(医护人员)
    private Long hospitalId;    // 医院ID
    private String hospitalName;// 医院名称
    private Long deptId;        // 科室ID
    private String department;  // 所属科室
    private String building;    // 所在楼宇
    private Long wardId;        // 绑定的病区ID
    private String wardName;    // 绑定的病区名称
    private Long boundPatientId;// 绑定的病人ID(患者角色)
    private String boundPatientName;
    private String boundBedNo;
}
