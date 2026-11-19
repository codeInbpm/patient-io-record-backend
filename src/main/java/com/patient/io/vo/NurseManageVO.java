package com.patient.io.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NurseManageVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String jobNumber;
    private String realName;
    private String role;
    private Long hospitalId;
    private String hospitalName;
    private Long deptId;
    private String department;
    private String building;
    private Long wardId;
    private String wardName;
    private String phone;
    private Boolean isBound;
    private Integer status; // 1-在职, 0-离职
    private List<String> assignedWardNames;
    private Integer assignedBedCount;
    private Integer assignedPatientCount;
    private List<AssignedPatientSimpleVO> assignedPatients;
    private Date createTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedPatientSimpleVO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long patientId;
        private String patientName;
        private String hospitalNo;
        private String bedNo;
        private String wardName;
        private String diagnosis;
    }
}
