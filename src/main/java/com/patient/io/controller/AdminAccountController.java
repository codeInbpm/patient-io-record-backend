package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.common.TenantContext;
import com.patient.io.dto.RbacDtos;
import com.patient.io.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 账号与授权。管理员与护士共用一套视图：userType 区分 ADMIN / NURSE。
 */
@RestController
@RequestMapping("/v1/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final RbacService rbacService;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "roleCode", required = false) String roleCode,
                                            @RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "userType", required = false) String userType,
                                            @RequestParam(value = "page", defaultValue = "1") int page,
                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(rbacService.listAccounts(keyword, roleCode, status, userType, page, size));
    }

    @GetMapping("/{userType}/{userId}/roles")
    public Result<List<Map<String, Object>>> roles(@PathVariable("userType") String userType,
                                                   @PathVariable("userId") Long userId) {
        return Result.success(rbacService.accountRoles(userType.toUpperCase(), userId));
    }

    @PostMapping("/{userType}/{userId}/roles")
    public Result<Void> assign(@PathVariable("userType") String userType,
                               @PathVariable("userId") Long userId,
                               @RequestBody RbacDtos.AssignRoles dto) {
        rbacService.assignRoles(userType.toUpperCase(), userId, dto, TenantContext.getHospitalId());
        return Result.success();
    }

    @PostMapping("/{userType}/{userId}/reset-password")
    public Result<Map<String, Object>> resetPassword(@PathVariable("userType") String userType,
                                                     @PathVariable("userId") Long userId) {
        return Result.success(rbacService.resetPassword(userType.toUpperCase(), userId));
    }

    @PostMapping("/{userType}/{userId}/toggle-status")
    public Result<Void> toggleStatus(@PathVariable("userType") String userType,
                                     @PathVariable("userId") Long userId,
                                     @RequestBody RbacDtos.StatusBody body) {
        rbacService.toggleAccountStatus(userType.toUpperCase(), userId, body.getStatus() == null ? 1 : body.getStatus());
        return Result.success();
    }
}
