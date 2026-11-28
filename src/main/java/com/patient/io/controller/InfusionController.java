package com.patient.io.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.patient.io.common.Result;
import com.patient.io.dto.InfusionCreateDTO;
import com.patient.io.dto.InfusionEndDTO;
import com.patient.io.service.InfusionService;
import com.patient.io.vo.InfusionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/infusion")
@RequiredArgsConstructor
public class InfusionController {

    private final InfusionService infusionService;

    /**
     * 登记补液
     */
    @PostMapping("/create")
    public Result<Long> createInfusion(@Validated @RequestBody InfusionCreateDTO dto) {
        Long id = infusionService.createInfusion(dto);
        return Result.success(id);
    }

    /**
     * 结束补液（拔针）
     */
    @PostMapping("/end")
    public Result<Void> endInfusion(@Validated @RequestBody InfusionEndDTO dto) {
        infusionService.endInfusion(dto);
        return Result.success();
    }

    /**
     * 获取病区正在补液列表
     */
    @GetMapping("/active")
    public Result<List<InfusionVO>> listActive(@RequestParam(value = "wardId", required = false) Long wardId) {
        List<InfusionVO> list = infusionService.listActiveInfusions(wardId);
        return Result.success(list);
    }

    /**
     * 获取病区今日已结束补液列表
     */
    @GetMapping("/completed")
    public Result<List<InfusionVO>> listCompleted(
            @RequestParam(value = "wardId", required = false) Long wardId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        List<InfusionVO> list = infusionService.listCompletedInfusions(wardId, date);
        return Result.success(list);
    }

    /**
     * 获取某患者历史补液记录
     */
    @GetMapping("/patient/history")
    public Result<List<InfusionVO>> listPatientHistory(@RequestParam("patientId") Long patientId) {
        List<InfusionVO> list = infusionService.listPatientHistory(patientId);
        return Result.success(list);
    }

    /**
     * PC 后台分页与条件查询
     */
    @GetMapping("/page")
    public Result<Page<InfusionVO>> pageInfusions(
            @RequestParam(value = "wardId", required = false) Long wardId,
            @RequestParam(value = "bedNo", required = false) String bedNo,
            @RequestParam(value = "patientName", required = false) String patientName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "drugName", required = false) String drugName,
            @RequestParam(value = "nurseName", required = false) String nurseName,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(value = "usageType", required = false) String usageType,
            @RequestParam(value = "hasDrug", required = false) Boolean hasDrug,
            @RequestParam(value = "syncedIntake", required = false) Boolean syncedIntake,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "10") long size) {
        Page<InfusionVO> page = infusionService.pageInfusions(wardId, bedNo, patientName, keyword, drugName, nurseName, status,
                startDate, endDate, usageType, hasDrug, syncedIntake, current, size);
        return Result.success(page);
    }

    /**
     * 药品结构化覆盖率（有多少补液记录真正记了结构化药品）
     */
    @GetMapping("/drug-coverage")
    public Result<Map<String, Object>> drugCoverage() {
        return Result.success(infusionService.drugCoverage());
    }

    /**
     * 获取补液记录详情
     */
    @GetMapping("/{id}")
    public Result<InfusionVO> getDetail(@PathVariable("id") Long id) {
        InfusionVO detail = infusionService.getInfusionDetail(id);
        return Result.success(detail);
    }
}
