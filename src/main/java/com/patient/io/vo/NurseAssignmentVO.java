package com.patient.io.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NurseAssignmentVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long nurseId;
    private String nurseName;
    private String jobNumber;
    private Long wardId;
    private String wardName;
    private List<BedAssignItemVO> beds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BedAssignItemVO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long bedId;
        private String bedNo;
        private Long patientId;
        private String patientName;
        private Boolean isAssignedToThisNurse; // 是否当前分配给该护士
        private String currentNurseName;        // 当前在管护士姓名
        private Long currentNurseId;
    }
}
