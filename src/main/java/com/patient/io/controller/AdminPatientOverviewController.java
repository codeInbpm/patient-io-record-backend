package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.service.PatientOverviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 患者 360 抽屉数据
 */
@RestController
@RequestMapping("/v1/admin/patients")
public class AdminPatientOverviewController {

    @Autowired
    private PatientOverviewService patientOverviewService;

    @GetMapping("/{id}/overview")
    public Result<Map<String, Object>> overview(@PathVariable Long id) {
        return Result.success(patientOverviewService.overview(id));
    }
}
