package com.patient.io.service;

import com.patient.io.dto.FluidQueryDTO;
import com.patient.io.dto.RecordIntakeDTO;
import com.patient.io.dto.RecordOutputDTO;
import com.patient.io.entity.DailySummary;
import com.patient.io.vo.DailyBalanceVO;
import com.patient.io.vo.FluidQueryVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

public interface FluidRecordService {
    Long recordIntake(RecordIntakeDTO dto);
    Long recordOutput(RecordOutputDTO dto);
    void deleteIntakeRecord(Long recordId);
    void deleteOutputRecord(Long recordId);
    DailyBalanceVO getDailyBalance(Long patientId, LocalDate date);
    List<DailySummary> getHistoricalDailySummaries(Long patientId, Integer days);
    void refreshDailySummary(Long patientId, LocalDate date);

    /**
     * 多患者、时间范围、多筛选条件出入量查询
     */
    FluidQueryVO queryFluidRecords(FluidQueryDTO queryDTO);

    /**
     * 导出 Excel
     */
    void exportExcel(FluidQueryDTO queryDTO, HttpServletResponse response);
}
