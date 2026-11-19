package com.patient.io.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.patient.io.dto.BedCreateDTO;
import com.patient.io.entity.Bed;
import com.patient.io.vo.BedVisualVO;

import java.util.List;

public interface BedService extends IService<Bed> {
    List<Bed> listBedsByWard(Long wardId);
    Bed createBed(BedCreateDTO createDTO);
    void updateBed(Bed bed);
    void deleteBed(Long bedId);

    /**
     * 获取病区可视化床位矩阵（携带占用病患姓名、住院号、出入量与责任护士）
     */
    List<BedVisualVO> getWardVisualBeds(Long wardId);

    /**
     * 批量按房间规格快速生成床位（以上海市一医院胃肠科为例：1-12房各3床，13房2床）
     */
    void batchGenerateRoomsAndBeds(Long wardId, int standardRooms, int bedsPerStandardRoom, int lastRoomBeds);
}
