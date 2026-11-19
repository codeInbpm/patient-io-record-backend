package com.patient.io.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.Result;
import com.patient.io.common.TenantContext;
import com.patient.io.entity.Department;
import com.patient.io.mapper.DepartmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/departments")
public class AdminDepartmentController {

    @Autowired
    private DepartmentMapper departmentMapper;

    @GetMapping
    public Result<List<Department>> list() {
        // 由于配置了 TenantLineInnerInterceptor，当前已自动按 hospital_id 过滤
        LambdaQueryWrapper<Department> query = new LambdaQueryWrapper<>();
        query.orderByAsc(Department::getSortOrder);
        return Result.success(departmentMapper.selectList(query));
    }

    @PostMapping
    public Result<Void> save(@RequestBody Department dept) {
        if (dept.getId() == null) {
            if (dept.getHospitalId() == null) {
                dept.setHospitalId(TenantContext.getHospitalId());
            }
            departmentMapper.insert(dept);
        } else {
            departmentMapper.updateById(dept);
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        departmentMapper.deleteById(id);
        return Result.success();
    }
}
