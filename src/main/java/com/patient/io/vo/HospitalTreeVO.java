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
public class HospitalTreeVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String hospitalName;
    private String hospitalCode;
    private List<DepartmentVO> departments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentVO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private Long hospitalId;
        private String deptName;
        private String deptCode;
    }
}
