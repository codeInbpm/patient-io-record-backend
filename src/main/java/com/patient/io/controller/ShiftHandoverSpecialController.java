package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.dto.SpecialHandoverSaveDTO;
import com.patient.io.service.ShiftHandoverSpecialService;
import com.patient.io.vo.SpecialHandoverVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/handover/special")
@RequiredArgsConstructor
public class ShiftHandoverSpecialController {

    private final ShiftHandoverSpecialService handoverSpecialService;

    /**
     * 获取指定病区、日期的每日特殊交接班表（支持自动预填）
     */
    @GetMapping
    public Result<SpecialHandoverVO> getSpecialHandover(
            @RequestParam("wardId") Long wardId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(value = "shiftType", defaultValue = "ALL_DAY") String shiftType) {
        SpecialHandoverVO vo = handoverSpecialService.getSpecialHandover(wardId, date, shiftType);
        return Result.success(vo);
    }

    /**
     * 保存交接班表草稿或提交
     */
    @PostMapping("/save")
    public Result<Long> saveSpecialHandover(@Validated @RequestBody SpecialHandoverSaveDTO dto) {
        Long id = handoverSpecialService.saveSpecialHandover(dto);
        return Result.success(id);
    }

    /**
     * 护士长核签锁定
     */
    @PostMapping("/sign/{id}")
    public Result<Void> signSpecialHandover(@PathVariable("id") Long id) {
        handoverSpecialService.signSpecialHandover(id);
        return Result.success();
    }

    /**
     * 接班护士确认接班
     */
    @PostMapping("/confirm/{id}")
    public Result<Void> confirmHandover(@PathVariable("id") Long id) {
        handoverSpecialService.confirmHandover(id);
        return Result.success();
    }

    /**
     * 查询历史交接列表
     */
    @GetMapping("/history")
    public Result<List<SpecialHandoverVO>> listHistory(
            @RequestParam(value = "wardId", required = false) Long wardId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        List<SpecialHandoverVO> list = handoverSpecialService.listHistory(wardId, startDate, endDate);
        return Result.success(list);
    }
}
