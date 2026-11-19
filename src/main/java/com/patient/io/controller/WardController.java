package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.entity.Ward;
import com.patient.io.service.WardService;
import com.patient.io.vo.WardOverviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/wards")
public class WardController {

    @Autowired
    private WardService wardService;

    @GetMapping("/list")
    public Result<List<Ward>> listWards(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "all", required = false, defaultValue = "false") Boolean all) {
        return Result.success(wardService.listWards(status, all));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateWardStatus(@PathVariable Long id, @RequestParam Integer status) {
        Ward ward = new Ward();
        ward.setId(id);
        ward.setStatus(status);
        wardService.updateWard(ward);
        return Result.success();
    }

    @PostMapping("/create")
    public Result<Ward> createWard(@RequestBody Ward ward) {
        return Result.success(wardService.createWard(ward));
    }

    @PostMapping("/save")
    public Result<Void> saveWard(@RequestBody Ward ward) {
        if (ward.getId() == null) {
            wardService.createWard(ward);
        } else {
            wardService.updateWard(ward);
        }
        return Result.success();
    }

    @PutMapping("/update")
    public Result<Void> updateWard(@RequestBody Ward ward) {
        wardService.updateWard(ward);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteWard(@PathVariable Long id) {
        wardService.deleteWard(id);
        return Result.success();
    }

    @GetMapping("/{id}/overview")
    public Result<WardOverviewVO> getWardTodayOverview(@PathVariable Long id) {
        return Result.success(wardService.getWardTodayOverview(id));
    }
}
