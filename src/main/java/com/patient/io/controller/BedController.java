package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.dto.BedCreateDTO;
import com.patient.io.entity.Bed;
import com.patient.io.service.BedService;
import com.patient.io.vo.BedVisualVO;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/beds")
public class BedController {

    @Autowired
    private BedService bedService;

    @GetMapping("/by-ward/{wardId}")
    public Result<List<Bed>> listBedsByWard(@PathVariable Long wardId) {
        return Result.success(bedService.listBedsByWard(wardId));
    }

    /**
     * 获取病区全景可视化床位卡片（含病患姓名、住院号、出入量与责任护士）
     */
    @GetMapping("/visual-matrix/{wardId}")
    public Result<List<BedVisualVO>> getWardVisualBeds(@PathVariable Long wardId) {
        return Result.success(bedService.getWardVisualBeds(wardId));
    }

    /**
     * 批量按房间规格快速生成床位（以上海市一医院胃肠科为例：1-12房各3床，13房2床）
     */
    @PostMapping("/batch-generate")
    public Result<Void> batchGenerate(@RequestBody BatchGenerateDTO dto) {
        bedService.batchGenerateRoomsAndBeds(
                dto.getWardId(),
                dto.getStandardRooms() != null ? dto.getStandardRooms() : 12,
                dto.getBedsPerStandardRoom() != null ? dto.getBedsPerStandardRoom() : 3,
                dto.getLastRoomBeds() != null ? dto.getLastRoomBeds() : 2
        );
        return Result.success();
    }

    @PostMapping("/create")
    public Result<Bed> createBed(@RequestBody @Validated BedCreateDTO createDTO) {
        return Result.success(bedService.createBed(createDTO));
    }

    @PutMapping("/update")
    public Result<Void> updateBed(@RequestBody Bed bed) {
        bedService.updateBed(bed);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteBed(@PathVariable Long id) {
        bedService.deleteBed(id);
        return Result.success();
    }

    @Data
    public static class BatchGenerateDTO {
        private Long wardId;
        private Integer standardRooms;
        private Integer bedsPerStandardRoom;
        private Integer lastRoomBeds;
    }
}
