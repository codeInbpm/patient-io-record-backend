package com.patient.io.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.Result;
import com.patient.io.entity.Department;
import com.patient.io.entity.Hospital;
import com.patient.io.mapper.DepartmentMapper;
import com.patient.io.mapper.HospitalMapper;
import com.patient.io.vo.HospitalTreeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/dict")
public class DictController {

    @Autowired
    private HospitalMapper hospitalMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    /**
     * 获取医院及科室级联列表（供护士长/护士绑定、筛选时使用）
     */
    @GetMapping("/hospitals")
    public Result<List<HospitalTreeVO>> listHospitals() {
        LambdaQueryWrapper<Hospital> hWrapper = new LambdaQueryWrapper<>();
        hWrapper.eq(Hospital::getStatus, 1).orderByAsc(Hospital::getSortOrder);
        List<Hospital> hospitals = hospitalMapper.selectList(hWrapper);

        LambdaQueryWrapper<Department> dWrapper = new LambdaQueryWrapper<>();
        dWrapper.eq(Department::getStatus, 1).orderByAsc(Department::getSortOrder);
        List<Department> departments = departmentMapper.selectList(dWrapper);

        Map<Long, List<Department>> deptMap = departments.stream()
                .collect(Collectors.groupingBy(Department::getHospitalId));

        List<HospitalTreeVO> result = new ArrayList<>();
        for (Hospital h : hospitals) {
            List<Department> dList = deptMap.getOrDefault(h.getId(), new ArrayList<>());
            List<HospitalTreeVO.DepartmentVO> dVos = dList.stream().map(d ->
                    HospitalTreeVO.DepartmentVO.builder()
                            .id(d.getId())
                            .hospitalId(d.getHospitalId())
                            .deptName(d.getDeptName())
                            .deptCode(d.getDeptCode())
                            .build()
            ).collect(Collectors.toList());

            result.add(HospitalTreeVO.builder()
                    .id(h.getId())
                    .hospitalName(h.getHospitalName())
                    .hospitalCode(h.getHospitalCode())
                    .departments(dVos)
                    .build());
        }
        return Result.success(result);
    }

    /**
     * 按医院ID获取科室列表
     */
    @GetMapping("/departments")
    public Result<List<Department>> listDepartments(@RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Department::getStatus, 1);
        if (hospitalId != null) {
            wrapper.eq(Department::getHospitalId, hospitalId);
        }
        wrapper.orderByAsc(Department::getSortOrder);
        return Result.success(departmentMapper.selectList(wrapper));
    }
}
