package com.patient.io.controller;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.BusinessException;
import com.patient.io.common.Result;
import com.patient.io.common.UserContext;
import com.patient.io.entity.Hospital;
import com.patient.io.entity.SysAdminUser;
import com.patient.io.mapper.HospitalMapper;
import com.patient.io.mapper.SysAdminUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/hospitals")
public class AdminHospitalController {

    @Autowired
    private HospitalMapper hospitalMapper;

    @Autowired
    private SysAdminUserMapper adminUserMapper;

    @Autowired
    private com.patient.io.service.RbacService rbacService;

    private void checkSuperAdmin() {
        if (!"SUPER_ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(403, "只有超级管理员有权限维护医院基础信息");
        }
    }

    /** 医院列表（支持搜索） */
    @GetMapping
    public Result<List<Hospital>> listAll(@RequestParam(value = "keyword", required = false) String keyword) {
        LambdaQueryWrapper<Hospital> query = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.like(Hospital::getHospitalName, keyword).or().like(Hospital::getHospitalCode, keyword);
        }
        query.orderByAsc(Hospital::getSortOrder);
        return Result.success(hospitalMapper.selectList(query));
    }

    /** 新增或编辑医院 */
    @PostMapping
    public Result<Void> saveHospital(@RequestBody Hospital hospital) {
        checkSuperAdmin();
        if (hospital.getId() == null) {
            hospitalMapper.insert(hospital);
        } else {
            hospitalMapper.updateById(hospital);
        }
        return Result.success();
    }

    /** 为医院创建/更新医院管理员账号 */
    @PostMapping("/{hospitalId}/assign-admin")
    public Result<Void> assignAdmin(
            @PathVariable("hospitalId") Long hospitalId,
            @RequestBody SysAdminUser adminDTO) {
        checkSuperAdmin();

        SysAdminUser exist = adminUserMapper.selectOne(
                new LambdaQueryWrapper<SysAdminUser>().eq(SysAdminUser::getUsername, adminDTO.getUsername())
        );
        if (exist != null && !hospitalId.equals(exist.getHospitalId())) {
            throw new BusinessException("该登录账号已被其他医院或用户占用");
        }

        if (exist == null) {
            SysAdminUser user = new SysAdminUser();
            user.setUsername(adminDTO.getUsername());
            user.setPassword(SecureUtil.md5(adminDTO.getPassword() != null && !adminDTO.getPassword().isEmpty() ? adminDTO.getPassword() : "123456"));
            user.setRealName(adminDTO.getRealName());
            user.setRole("HOSPITAL_ADMIN");
            user.setHospitalId(hospitalId);
            user.setPhone(adminDTO.getPhone());
            user.setStatus(1);
            adminUserMapper.insert(user);
            rbacService.ensurePrimaryGrant("ADMIN", user.getId(), "HOSPITAL_ADMIN",
                    user.getHospitalId(), null, null);
        } else {
            exist.setRealName(adminDTO.getRealName());
            exist.setPhone(adminDTO.getPhone());
            if (adminDTO.getPassword() != null && !adminDTO.getPassword().isEmpty()) {
                exist.setPassword(SecureUtil.md5(adminDTO.getPassword()));
            }
            adminUserMapper.updateById(exist);
        }
        return Result.success();
    }
}
