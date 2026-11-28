package com.patient.io.controller;

import com.patient.io.common.BusinessException;
import com.patient.io.common.Result;
import com.patient.io.common.UserContext;
import com.patient.io.dto.HandoverSaveDTO;
import com.patient.io.service.ShiftHandoverService;
import com.patient.io.vo.ShiftHandoverVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 交班工作台
 *
 * PC：护士长填写、提交、撤销；医院管理员只读。
 * 小程序：接班护士确认接班并上传签名（/receive）。
 */
@RestController
@RequestMapping("/v1/admin/handover")
public class ShiftHandoverController {

    @Autowired
    private ShiftHandoverService shiftHandoverService;

    @GetMapping("/current")
    public Result<ShiftHandoverVO> current(@RequestParam Long wardId,
                                           @RequestParam(required = false) String shiftDate,
                                           @RequestParam(required = false, defaultValue = "NIGHT") String shiftType) {
        return Result.success(shiftHandoverService.getCurrent(wardId, shiftDate, shiftType));
    }

    @PostMapping("/draft")
    public Result<ShiftHandoverVO> save(@RequestBody @Validated HandoverSaveDTO dto) {
        requireHeadNurse();
        return Result.success(shiftHandoverService.save(dto));
    }

    @PostMapping("/{id}/submit")
    public Result<ShiftHandoverVO> submit(@PathVariable Long id) {
        requireHeadNurse();
        return Result.success(shiftHandoverService.submit(id));
    }

    @PostMapping("/{id}/reopen")
    public Result<ShiftHandoverVO> reopen(@PathVariable Long id) {
        requireHeadNurse();
        return Result.success(shiftHandoverService.reopen(id));
    }

    /**
     * 接班确认（小程序端调用，护士角色也可访问，权限在拦截器里单独放行）
     */
    @PostMapping("/{id}/receive")
    public Result<ShiftHandoverVO> receive(@PathVariable Long id,
                                           @RequestParam(required = false) String receiverName,
                                           @RequestParam(required = false) String signatureUrl) {
        return Result.success(shiftHandoverService.receive(id, receiverName, signatureUrl));
    }

    @GetMapping("/history")
    public Result<Map<String, Object>> history(@RequestParam(required = false) Long wardId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(shiftHandoverService.history(wardId, status, page, size));
    }

    private void requireHeadNurse() {
        if (!"HEAD_NURSE".equals(UserContext.getRole())) {
            throw new BusinessException(403, "仅护士长可填写与提交交班，管理员仅可查看");
        }
    }
}
