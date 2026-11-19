package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.dto.ClaimDutyBedsDTO;
import com.patient.io.dto.NurseAssignBedsDTO;
import com.patient.io.dto.NurseSaveDTO;
import com.patient.io.service.NurseService;
import com.patient.io.vo.NurseAssignmentVO;
import com.patient.io.vo.NurseManageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/nurses")
public class NurseController {

    @Autowired
    private NurseService nurseService;

    /**
     * 护士长查询护士名册（包含工号、姓名、科室、微信绑定、负责床位/病患数统计）
     */
    @GetMapping("/manage-list")
    public Result<List<NurseManageVO>> listManageNurses() {
        return Result.success(nurseService.listManageNurses());
    }

    /**
     * 新增或编辑护士
     */
    @PostMapping
    public Result<NurseManageVO> saveNurse(@RequestBody @Validated NurseSaveDTO dto) {
        return Result.success(nurseService.saveNurse(dto));
    }

    /**
     * 切换在职/停用状态
     */
    @PostMapping("/{id}/toggle-status")
    public Result<Void> toggleStatus(@PathVariable("id") Long id) {
        nurseService.toggleNurseStatus(id);
        return Result.success();
    }

    /**
     * 护士长重置/解绑护士微信账号
     */
    @PostMapping("/{id}/unbind-wechat")
    public Result<Void> unbindWechat(@PathVariable("id") Long id) {
        nurseService.unbindNurseWechat(id);
        return Result.success();
    }

    /**
     * 获取指定病房下某护士的床位分配情况
     */
    @GetMapping("/{id}/assignments")
    public Result<NurseAssignmentVO> getAssignments(
            @PathVariable("id") Long id,
            @RequestParam("wardId") Long wardId) {
        return Result.success(nurseService.getNurseWardAssignments(id, wardId));
    }

    /**
     * 当前登录护士查询自己当班的床位管辖分配（支持小程序护士当班自查）
     */
    @GetMapping("/my-assignments")
    public Result<NurseAssignmentVO> getMyAssignments(@RequestParam("wardId") Long wardId) {
        return Result.success(nurseService.getMyDutyBedAssignments(wardId));
    }

    /**
     * 护士当班自主认领/变更管辖床位（勾选接班不覆盖他人，取消勾选解除自身管辖）
     */
    @PostMapping("/claim-my-beds")
    public Result<Void> claimMyBeds(@RequestBody @Validated ClaimDutyBedsDTO dto) {
        nurseService.claimMyDutyBeds(dto);
        return Result.success();
    }

    /**
     * 护士长批量为护士分配床位及病患
     */
    @PostMapping("/assign-beds")
    public Result<Void> assignBeds(@RequestBody @Validated NurseAssignBedsDTO dto) {
        nurseService.assignBeds(dto);
        return Result.success();
    }
}
