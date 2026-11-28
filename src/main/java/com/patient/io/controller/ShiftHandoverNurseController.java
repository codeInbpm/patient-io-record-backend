package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.dto.HandoverReceiveDTO;
import com.patient.io.service.ShiftHandoverService;
import com.patient.io.vo.ShiftHandoverVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 小程序端交班接班接口。
 *
 * 为什么单独放在 /v1/handover/shift 而不是复用 /v1/admin/handover：
 * 后台接口默认只对「超管 / 医院管理员 / 护士长」开放，普通责任护士进不去；
 * 而接班签名恰恰是责任护士在病床边完成的动作，需要一个护士身份自然可用的入口。
 * 写操作仍然走同一套 Service，状态机与权限校验只有一份。
 */
@RestController
@RequestMapping("/v1/handover/shift")
@RequiredArgsConstructor
public class ShiftHandoverNurseController {

    private final ShiftHandoverService shiftHandoverService;

    /** 待接班列表（已提交待签名 + 近 7 天已确认） */
    @GetMapping("/pending")
    public Result<Map<String, Object>> pending(@RequestParam(required = false) Long wardId) {
        return Result.success(shiftHandoverService.pendingForNurse(wardId));
    }

    /** 交班详情（含叮嘱、报告、统计快照） */
    @GetMapping("/{id}")
    public Result<ShiftHandoverVO> detail(@PathVariable Long id) {
        return Result.success(shiftHandoverService.detail(id));
    }

    /** 接班确认：签名图片地址由 /v1/file/upload 上传后带回 */
    @PostMapping("/{id}/receive")
    public Result<ShiftHandoverVO> receive(@PathVariable Long id, @RequestBody HandoverReceiveDTO dto) {
        return Result.success(shiftHandoverService.receiveWithSignature(
                id, dto == null ? null : dto.getReceiverName(), dto == null ? null : dto.getSignatureUrl()));
    }
}
