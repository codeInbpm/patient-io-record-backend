package com.patient.io.service;

import com.patient.io.dto.SpecialHandoverSaveDTO;
import com.patient.io.vo.SpecialHandoverVO;

import java.time.LocalDate;
import java.util.List;

public interface ShiftHandoverSpecialService {

    /**
     * 获取指定病区、日期的每日特殊交接班表（支持从在床患者自动生成预填数据）
     */
    SpecialHandoverVO getSpecialHandover(Long wardId, LocalDate date, String shiftType);

    /**
     * 保存或更新特殊交接班表
     */
    Long saveSpecialHandover(SpecialHandoverSaveDTO dto);

    /**
     * 护士长核签锁定
     */
    void signSpecialHandover(Long id);

    /**
     * 接班护士确认接班
     */
    void confirmHandover(Long id);

    /**
     * 查询历史交接班记录列表
     */
    List<SpecialHandoverVO> listHistory(Long wardId, LocalDate startDate, LocalDate endDate);
}
