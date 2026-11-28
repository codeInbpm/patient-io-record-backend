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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 角色与权限维护。
 * 权限校验由 PermissionRules 统一裁决：roles -> role:view / role:manage。
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RbacService rbacService;

    @GetMapping("/permissions")
    public Result<List<Map<String, Object>>> permissions() {
        return Result.success(rbacService.listPermissions());
    }

    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> roles() {
        return Result.success(rbacService.listRoles(TenantContext.getHospitalId()));
    }

    @GetMapping("/roles/{id}/permissions")
    public Result<List<String>> rolePermissions(@PathVariable("id") Long id) {
        return Result.success(rbacService.rolePermissions(id));
    }

    @PostMapping("/roles")
    public Result<Map<String, Object>> saveRole(@RequestBody RbacDtos.RoleSave dto) {
        return Result.success(rbacService.saveRole(TenantContext.getHospitalId(), dto));
    }

    @PostMapping("/roles/{id}/permissions")
    @SuppressWarnings("unchecked")
    public Result<Void> savePermissions(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Object codes = body.get("permCodes");
        rbacService.saveRolePermissions(id, codes instanceof List ? (List<String>) codes : null);
        return Result.success();
    }

    @PostMapping("/roles/{id}/toggle-status")
    public Result<Void> toggleStatus(@PathVariable("id") Long id, @RequestBody RbacDtos.StatusBody body) {
        rbacService.toggleRoleStatus(id, body.getStatus() == null ? 1 : body.getStatus());
        return Result.success();
    }

    /**
     * 删除角色。除内置 SUPER_ADMIN 外全部可删；
     * 角色仍绑定账号时需带 force=true（前端二次确认）。
     */
    @PostMapping("/roles/{id}/delete")
    public Result<Void> delete(@PathVariable("id") Long id,
                               @RequestBody(required = false) Map<String, Object> body) {
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"))
                || body != null && "true".equals(String.valueOf(body.get("force")));
        rbacService.deleteRole(id, force);
        return Result.success();
    }
}
