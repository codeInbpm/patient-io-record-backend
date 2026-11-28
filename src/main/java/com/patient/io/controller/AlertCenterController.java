package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.service.AlertCenterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 全局告警中心
 */
@RestController
@RequestMapping("/v1/admin/alerts")
public class AlertCenterController {

    @Autowired
    private AlertCenterService alertCenterService;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) Long wardId,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String status) {
        return Result.success(alertCenterService.list(wardId, type, status));
    }

    /** 顶栏铃铛未读数 */
    @GetMapping("/badge")
    public Result<Map<String, Object>> badge() {
        return Result.success(alertCenterService.badge());
    }

    @PostMapping("/ack")
    public Result<Void> ack(@RequestBody Map<String, String> body) {
        alertCenterService.ack(body.get("key"));
        return Result.success();
    }

    @PostMapping("/close")
    public Result<Void> close(@RequestBody Map<String, String> body) {
        alertCenterService.close(body.get("key"), body.get("note"));
        return Result.success();
    }
}
