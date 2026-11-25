package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.dto.CorrectionRequestDTO;
import com.patient.io.dto.FluidQueryDTO;
import com.patient.io.dto.RecordIntakeDTO;
import com.patient.io.dto.RecordOutputDTO;
import com.patient.io.entity.DailySummary;
import com.patient.io.service.FluidRecordService;
import com.patient.io.service.RecordAuditService;
import com.patient.io.service.RecordCorrectionService;
import com.patient.io.vo.CorrectionRequestVO;
import com.patient.io.vo.DailyBalanceVO;
import com.patient.io.vo.FluidQueryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/fluid-records")
public class FluidRecordController {

    @Autowired
    private FluidRecordService fluidRecordService;

    @Autowired
    private RecordAuditService recordAuditService;

    @Autowired
    private RecordCorrectionService recordCorrectionService;

    @PostMapping("/intake")
    public Result<Long> recordIntake(@RequestBody @Validated RecordIntakeDTO dto) {
        return Result.success(fluidRecordService.recordIntake(dto));
    }

    @PostMapping("/output")
    public Result<Long> recordOutput(@RequestBody @Validated RecordOutputDTO dto) {
        return Result.success(fluidRecordService.recordOutput(dto));
    }

    @DeleteMapping("/intake/{id}")
    public Result<Void> deleteIntakeRecord(@PathVariable Long id,
                                           @RequestParam(required = false) String reason) {
        recordAuditService.logIntakeDelete(id, reason);
        fluidRecordService.deleteIntakeRecord(id);
        return Result.success();
    }

    @DeleteMapping("/output/{id}")
    public Result<Void> deleteOutputRecord(@PathVariable Long id,
                                           @RequestParam(required = false) String reason) {
        recordAuditService.logOutputDelete(id, reason);
        fluidRecordService.deleteOutputRecord(id);
        return Result.success();
    }

    /**
     * 患者反馈「这条记错了」，由护士核对后修正
     */
    @PostMapping("/correction-request")
    public Result<Void> submitCorrectionRequest(@RequestBody @Validated CorrectionRequestDTO dto) {
        recordCorrectionService.submit(dto);
        return Result.success();
    }

    /**
     * 护士端待核对列表
     */
    @GetMapping("/correction-requests")
    public Result<List<CorrectionRequestVO>> listCorrectionRequests(
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        return Result.success(recordCorrectionService.listPending(wardId, status));
    }

    /**
     * 护士处理完标记已处理
     */
    @PostMapping("/correction-requests/{id}/handled")
    public Result<Void> handleCorrectionRequest(@PathVariable Long id) {
        recordCorrectionService.markHandled(id);
        return Result.success();
    }

    @GetMapping("/daily-balance")
    public Result<DailyBalanceVO> getDailyBalance(
            @RequestParam Long patientId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return Result.success(fluidRecordService.getDailyBalance(patientId, date));
    }

    @GetMapping("/history-summaries")
    public Result<List<DailySummary>> getHistoricalSummaries(
            @RequestParam Long patientId,
            @RequestParam(defaultValue = "7") Integer days) {
        return Result.success(fluidRecordService.getHistoricalDailySummaries(patientId, days));
    }

    /**
     * 多患者、时间范围、多筛选条件出入量查询 (支持GET和POST)
     */
    @PostMapping("/query")
    public Result<FluidQueryVO> queryFluidRecordsPost(@RequestBody(required = false) FluidQueryDTO queryDTO) {
        return Result.success(fluidRecordService.queryFluidRecords(queryDTO));
    }

    @GetMapping("/query")
    public Result<FluidQueryVO> queryFluidRecordsGet(FluidQueryDTO queryDTO) {
        return Result.success(fluidRecordService.queryFluidRecords(queryDTO));
    }

    /**
     * 导出 Excel 报表
     */
    @GetMapping("/export-excel")
    public void exportExcelGet(FluidQueryDTO queryDTO, HttpServletResponse response) {
        fluidRecordService.exportExcel(queryDTO, response);
    }

    @PostMapping("/export-excel")
    public void exportExcelPost(@RequestBody(required = false) FluidQueryDTO queryDTO, HttpServletResponse response) {
        fluidRecordService.exportExcel(queryDTO, response);
    }
}
