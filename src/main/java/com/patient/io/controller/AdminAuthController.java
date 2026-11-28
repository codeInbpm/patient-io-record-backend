package com.patient.io.controller;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.BusinessException;
import com.patient.io.common.Result;
import com.patient.io.common.UserContext;
import com.patient.io.config.JwtUtils;
import com.patient.io.entity.Hospital;
import com.patient.io.entity.Nurse;
import com.patient.io.entity.SysAdminUser;
import com.patient.io.mapper.HospitalMapper;
import com.patient.io.mapper.NurseMapper;
import com.patient.io.mapper.SysAdminUserMapper;
import com.patient.io.service.RbacService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/admin/auth")
public class AdminAuthController {

    @Autowired
    private SysAdminUserMapper adminUserMapper;

    @Autowired
    private NurseMapper nurseMapper;

    @Autowired
    private HospitalMapper hospitalMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RbacService rbacService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        // 1. 先尝试通过系统管理员表登录
        SysAdminUser user = adminUserMapper.selectOne(
                new LambdaQueryWrapper<SysAdminUser>()
                        .eq(SysAdminUser::getUsername, req.getUsername())
        );

        if (user != null) {
            if (user.getStatus() != 1) {
                throw new BusinessException("账号已被禁用，请联系平台管理员");
            }

            // 密码比对：支持 MD5 加密或明文比对
            String encryptPwd = SecureUtil.md5(req.getPassword());
            if (!encryptPwd.equals(user.getPassword()) && !req.getPassword().equals(user.getPassword())) {
                throw new BusinessException("账号或密码错误");
            }

            // 构造 Claims 并生成 Token
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", user.getId());
            claims.put("role", user.getRole());
            claims.put("username", user.getUsername());
            claims.put("hospitalId", user.getHospitalId());
            claims.put("utype", "ADMIN");

            String token = jwtUtils.generateToken(claims);

            Map<String, Object> resp = new HashMap<>();
            resp.put("token", token);
            resp.put("userId", user.getId());
            resp.put("role", user.getRole());
            resp.put("username", user.getUsername());
            resp.put("realName", user.getRealName());
            resp.put("hospitalId", user.getHospitalId());
            attachRbac(resp, "ADMIN", user.getId());

            if (user.getHospitalId() != null) {
                Hospital h = hospitalMapper.selectById(user.getHospitalId());
                resp.put("hospitalName", h != null ? h.getHospitalName() : "");
            } else {
                resp.put("hospitalName", "平台全网总览");
            }

            return Result.success(resp);
        }

        // 2. 尝试通过医护名册表登录（工号登录）
        Nurse nurse = nurseMapper.selectOne(
                new LambdaQueryWrapper<Nurse>()
                        .eq(Nurse::getJobNumber, req.getUsername().trim())
        );

        if (nurse != null) {
            if (nurse.getStatus() != 1) {
                throw new BusinessException("该医护人员档案已停用");
            }

            // 护士初始密码默认为 123456 或手机号后6位
            String pwd = req.getPassword() != null ? req.getPassword().trim() : "";
            boolean pwdMatch = "123456".equals(pwd) 
                    || (nurse.getPhone() != null && nurse.getPhone().length() >= 6 && nurse.getPhone().endsWith(pwd))
                    || nurse.getJobNumber().equals(pwd);

            if (!pwdMatch) {
                throw new BusinessException("工号或密码错误（默认初始密码为 123456）");
            }

            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", nurse.getId());
            claims.put("role", nurse.getRole());
            claims.put("username", nurse.getJobNumber());
            claims.put("hospitalId", nurse.getHospitalId());
            claims.put("wardId", nurse.getWardId());
            claims.put("utype", "NURSE");

            String token = jwtUtils.generateToken(claims);

            Map<String, Object> resp = new HashMap<>();
            resp.put("token", token);
            resp.put("userId", nurse.getId());
            resp.put("role", nurse.getRole());
            resp.put("username", nurse.getJobNumber());
            resp.put("realName", nurse.getRealName());
            resp.put("hospitalId", nurse.getHospitalId());
            resp.put("hospitalName", nurse.getHospitalName() != null ? nurse.getHospitalName() : "第一人民医院（示范本部）");
            resp.put("deptId", nurse.getDeptId());
            resp.put("department", nurse.getDepartment());
            resp.put("building", nurse.getBuilding() != null ? nurse.getBuilding() : nurse.getDepartment());
            resp.put("wardId", nurse.getWardId());
            resp.put("wardName", nurse.getWardName());
            attachRbac(resp, "NURSE", nurse.getId());

            return Result.success(resp);
        }

        throw new BusinessException("账号或密码错误");
    }

    /**
     * 当前登录人的权限快照。前端路由守卫、菜单渲染、按钮显隐都以这里为准。
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return Result.success(rbacService.me(UserContext.getUserType(), userId));
    }

    /**
     * 登录响应带上权限快照，避免前端登录后立刻再发一次 /me 请求。
     * RBAC 表尚未建立时静默降级：前端会回落到角色映射。
     */
    private void attachRbac(Map<String, Object> resp, String userType, Long userId) {
        try {
            Map<String, Object> me = rbacService.me(userType, userId);
            resp.put("permissions", me.get("permissions"));
            resp.put("roleCodes", me.get("roleCodes"));
            resp.put("dataScope", me.get("dataScope"));
            resp.put("wardIds", me.get("wardIds"));
            resp.put("userType", userType);
        } catch (Exception e) {
            resp.put("permissions", java.util.Collections.emptyList());
            resp.put("roleCodes", java.util.Collections.emptyList());
            resp.put("userType", userType);
        }
    }

    /**
     * 管理员修改个人密码
     */
    @PostMapping("/update-password")
    public Result<Void> updatePassword(@RequestBody UpdatePasswordRequest req) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            throw new BusinessException("未登录或登录已失效");
        }

        if (!"ADMIN".equals(UserContext.getUserType())) {
            throw new BusinessException("护士账号请在小程序端或联系管理员重置密码");
        }

        if (req.getOldPassword() == null || req.getOldPassword().trim().isEmpty()) {
            throw new BusinessException("请输入原密码");
        }
        if (req.getNewPassword() == null || req.getNewPassword().trim().length() < 6) {
            throw new BusinessException("新密码长度不能少于6位");
        }
        if (req.getNewPassword().trim().equals(req.getOldPassword().trim())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        SysAdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("管理员账号不存在");
        }

        // 校验原密码（兼容 MD5 与 历史可能存在的明文）
        String encryptOld = SecureUtil.md5(req.getOldPassword().trim());
        if (!encryptOld.equals(user.getPassword()) && !req.getOldPassword().trim().equals(user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        // 更新为新密码（MD5 加密存储）
        user.setPassword(SecureUtil.md5(req.getNewPassword().trim()));
        adminUserMapper.updateById(user);

        return Result.success();
    }

    @Data
    public static class UpdatePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
