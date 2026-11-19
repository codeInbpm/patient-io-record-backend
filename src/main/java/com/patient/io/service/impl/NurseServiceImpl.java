package com.patient.io.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.BusinessException;
import com.patient.io.common.TenantContext;
import com.patient.io.common.UserContext;
import com.patient.io.dto.ClaimDutyBedsDTO;
import com.patient.io.dto.NurseAssignBedsDTO;
import com.patient.io.dto.NurseSaveDTO;
import com.patient.io.entity.*;
import com.patient.io.mapper.*;
import com.patient.io.service.NurseService;
import com.patient.io.vo.NurseAssignmentVO;
import com.patient.io.vo.NurseManageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NurseServiceImpl implements NurseService {

    @Autowired
    private NurseMapper nurseMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private WardMapper wardMapper;

    @Autowired
    private BedMapper bedMapper;

    @Autowired
    private PatientMapper patientMapper;

    @Autowired
    private NursePatientRelationMapper relationMapper;

    @Autowired
    private HospitalMapper hospitalMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    private void checkHeadNursePermission() {
        String role = UserContext.getRole();
        if (!"HEAD_NURSE".equals(role) && !"SUPER_ADMIN".equals(role) && !"HOSPITAL_ADMIN".equals(role)) {
            throw new BusinessException(403, "权限不足，仅护士长或系统管理员可进行医护管理与排班分配");
        }
    }

    @Override
    public List<NurseManageVO> listManageNurses() {
        checkHeadNursePermission();

        // 查询所有护士与护士长
        LambdaQueryWrapper<Nurse> nWrapper = new LambdaQueryWrapper<>();
        nWrapper.orderByDesc(Nurse::getRole) // 护士长排在前面
                .orderByAsc(Nurse::getJobNumber);
        List<Nurse> nurseList = nurseMapper.selectList(nWrapper);

        // 查询所有分配关系
        List<NursePatientRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, List<NursePatientRelation>> relationMap = relations.stream()
                .collect(Collectors.groupingBy(NursePatientRelation::getNurseId));

        // 预查所有病房名称
        List<Ward> wards = wardMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> wardNameMap = wards.stream()
                .collect(Collectors.toMap(Ward::getId, Ward::getWardName, (a, b) -> a));

        // 预查所有在院病人（供展开查看负责的病患明细）
        List<Patient> inPatients = patientMapper.selectList(new LambdaQueryWrapper<Patient>().eq(Patient::getStatus, 1));
        Map<Long, Patient> patientMap = inPatients.stream().collect(Collectors.toMap(Patient::getId, p -> p, (a, b) -> a));

        List<NurseManageVO> voList = new ArrayList<>();
        for (Nurse nurse : nurseList) {
            List<NursePatientRelation> nRels = relationMap.getOrDefault(nurse.getId(), Collections.emptyList());

            // 负责床位集合
            Set<Long> bedSet = nRels.stream().map(NursePatientRelation::getBedId).filter(Objects::nonNull).collect(Collectors.toSet());
            // 负责病患集合
            Set<Long> patientSet = nRels.stream().map(NursePatientRelation::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
            // 负责病房集合
            List<String> wardNames = nRels.stream()
                    .map(r -> wardNameMap.get(r.getWardId()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // 构造负责病患详细列表
            List<NurseManageVO.AssignedPatientSimpleVO> patientDetails = new ArrayList<>();
            for (NursePatientRelation r : nRels) {
                if (r.getPatientId() != null) {
                    Patient p = patientMap.get(r.getPatientId());
                    if (p != null) {
                        patientDetails.add(NurseManageVO.AssignedPatientSimpleVO.builder()
                                .patientId(p.getId())
                                .patientName(p.getPatientName())
                                .hospitalNo(p.getHospitalNo())
                                .bedNo(p.getBedNo())
                                .wardName(wardNameMap.getOrDefault(p.getWardId(), ""))
                                .diagnosis(p.getDiagnosis())
                                .build());
                    }
                }
            }

            NurseManageVO vo = NurseManageVO.builder()
                    .id(nurse.getId())
                    .jobNumber(nurse.getJobNumber())
                    .realName(nurse.getRealName())
                    .role(nurse.getRole())
                    .hospitalId(nurse.getHospitalId())
                    .hospitalName(nurse.getHospitalName())
                    .deptId(nurse.getDeptId())
                    .department(nurse.getDepartment())
                    .building(nurse.getBuilding() != null ? nurse.getBuilding() : nurse.getDepartment())
                    .wardId(nurse.getWardId())
                    .wardName(nurse.getWardName())
                    .phone(nurse.getPhone())
                    .isBound(nurse.getBoundUserId() != null)
                    .status(nurse.getStatus())
                    .assignedWardNames(wardNames)
                    .assignedBedCount(bedSet.size())
                    .assignedPatientCount(patientSet.size())
                    .assignedPatients(patientDetails)
                    .createTime(nurse.getCreateTime())
                    .build();
            voList.add(vo);
        }
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NurseManageVO saveNurse(NurseSaveDTO dto) {
        checkHeadNursePermission();

        String jobNumber = dto.getJobNumber().trim();
        if (jobNumber.length() != 6 || !jobNumber.matches("\\d{6}")) {
            throw new BusinessException("工号必须为6位纯数字");
        }

        Nurse nurse;
        if (dto.getId() != null) {
            nurse = nurseMapper.selectById(dto.getId());
            if (nurse == null) {
                throw new BusinessException("护士档案不存在");
            }
            // 校验工号修改是否与其他人冲突
            LambdaQueryWrapper<Nurse> chk = new LambdaQueryWrapper<>();
            chk.eq(Nurse::getJobNumber, jobNumber).ne(Nurse::getId, dto.getId());
            if (nurseMapper.selectCount(chk) > 0) {
                throw new BusinessException("工号 " + jobNumber + " 已被其他医护人员使用");
            }
        } else {
            // 新增校验唯一性
            LambdaQueryWrapper<Nurse> chk = new LambdaQueryWrapper<>();
            chk.eq(Nurse::getJobNumber, jobNumber);
            if (nurseMapper.selectCount(chk) > 0) {
                throw new BusinessException("工号 " + jobNumber + " 已存在，请使用其他工号");
            }
            nurse = new Nurse();
            nurse.setRole(dto.getRole() != null ? dto.getRole() : "NURSE");
            nurse.setStatus(1);
        }

        if (dto.getRole() != null) {
            nurse.setRole(dto.getRole());
        }
        nurse.setJobNumber(jobNumber);
        nurse.setRealName(dto.getRealName().trim());
        nurse.setPhone(dto.getPhone());

        // 医院科室与病区信息设置
        Long targetHospitalId = dto.getHospitalId() != null ? dto.getHospitalId() : TenantContext.getHospitalId();
        if (targetHospitalId != null) {
            Hospital h = hospitalMapper.selectById(targetHospitalId);
            if (h != null) {
                nurse.setHospitalId(h.getId());
                nurse.setHospitalName(h.getHospitalName());
            }
        }
        if (dto.getDeptId() != null) {
            Department d = departmentMapper.selectById(dto.getDeptId());
            if (d != null) {
                nurse.setDeptId(d.getId());
                nurse.setDepartment(d.getDeptName());
                nurse.setBuilding(d.getDeptName());
            }
        } else if (dto.getDepartment() != null) {
            nurse.setDepartment(dto.getDepartment().trim());
            nurse.setBuilding(dto.getDepartment().trim());
        }

        // 重点：处理病区绑定
        if (dto.getWardId() != null) {
            Ward w = wardMapper.selectById(dto.getWardId());
            if (w != null) {
                nurse.setWardId(w.getId());
                nurse.setWardName(w.getWardName());
                if (nurse.getHospitalId() == null && w.getHospitalId() != null) {
                    nurse.setHospitalId(w.getHospitalId());
                    nurse.setHospitalName(w.getHospitalName());
                }
                if (nurse.getDeptId() == null && w.getDeptId() != null) {
                    nurse.setDeptId(w.getDeptId());
                    nurse.setDepartment(w.getDepartment());
                    nurse.setBuilding(w.getBuilding() != null ? w.getBuilding() : w.getDepartment());
                }
            }
        } else if (dto.getWardName() != null) {
            nurse.setWardName(dto.getWardName().trim());
        }

        if (nurse.getId() != null) {
            nurseMapper.updateById(nurse);
        } else {
            nurseMapper.insert(nurse);
        }

        return NurseManageVO.builder()
                .id(nurse.getId())
                .jobNumber(nurse.getJobNumber())
                .realName(nurse.getRealName())
                .role(nurse.getRole())
                .hospitalId(nurse.getHospitalId())
                .hospitalName(nurse.getHospitalName())
                .deptId(nurse.getDeptId())
                .department(nurse.getDepartment())
                .building(nurse.getBuilding() != null ? nurse.getBuilding() : nurse.getDepartment())
                .wardId(nurse.getWardId())
                .wardName(nurse.getWardName())
                .phone(nurse.getPhone())
                .isBound(nurse.getBoundUserId() != null)
                .status(nurse.getStatus())
                .createTime(nurse.getCreateTime())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleNurseStatus(Long nurseId) {
        checkHeadNursePermission();
        Nurse nurse = nurseMapper.selectById(nurseId);
        if (nurse == null) {
            throw new BusinessException("护士档案不存在");
        }
        if ("HEAD_NURSE".equals(nurse.getRole())) {
            throw new BusinessException("不能停用护士长账号");
        }
        int newStatus = (nurse.getStatus() != null && nurse.getStatus() == 1) ? 0 : 1;
        nurse.setStatus(newStatus);
        nurseMapper.updateById(nurse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindNurseWechat(Long nurseId) {
        checkHeadNursePermission();
        Nurse nurse = nurseMapper.selectById(nurseId);
        if (nurse == null) {
            throw new BusinessException("护士档案不存在");
        }

        Long boundUserId = nurse.getBoundUserId();
        if (boundUserId != null) {
            SysUser user = sysUserMapper.selectById(boundUserId);
            if (user != null) {
                user.setRole("NONE");
                user.setBindStatus(0);
                user.setBindRefId(null);
                sysUserMapper.updateById(user);
            }
            nurse.setBoundUserId(null);
            nurseMapper.updateById(nurse);
        }
    }

    private Long getCurrentNurseId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getBindRefId() == null) {
            throw new BusinessException(403, "当前账号尚未绑定护士档案，请先绑定工号");
        }
        return user.getBindRefId();
    }

    @Override
    public NurseAssignmentVO getMyDutyBedAssignments(Long wardId) {
        Long currentNurseId = getCurrentNurseId();
        return getNurseWardAssignments(currentNurseId, wardId);
    }

    @Override
    public NurseAssignmentVO getNurseWardAssignments(Long nurseId, Long wardId) {
        String role = UserContext.getRole();
        Long userId = UserContext.getUserId();
        // 如果是普通护士，只允许查询自己的分配情况；护士长与管理员可查询任意护士
        if (!"HEAD_NURSE".equals(role) && !"SUPER_ADMIN".equals(role) && !"HOSPITAL_ADMIN".equals(role)) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null || !Objects.equals(user.getBindRefId(), nurseId)) {
                throw new BusinessException(403, "权限不足，只能查询本人的床位分配情况");
            }
        }

        Nurse nurse = nurseMapper.selectById(nurseId);
        if (nurse == null) {
            throw new BusinessException("护士不存在");
        }
        Ward ward = wardMapper.selectById(wardId);
        if (ward == null) {
            throw new BusinessException("病房不存在");
        }

        // 查出该病房所有有效床位
        LambdaQueryWrapper<Bed> bWrapper = new LambdaQueryWrapper<>();
        bWrapper.eq(Bed::getWardId, wardId).orderByAsc(Bed::getSortOrder).orderByAsc(Bed::getBedNo);
        List<Bed> beds = bedMapper.selectList(bWrapper);
        List<Long> wardBedIds = beds.stream().map(Bed::getId).collect(Collectors.toList());

        // 查出该病房的所有在院病人
        LambdaQueryWrapper<Patient> pWrapper = new LambdaQueryWrapper<>();
        pWrapper.eq(Patient::getWardId, wardId).eq(Patient::getStatus, 1);
        List<Patient> patients = patientMapper.selectList(pWrapper);
        Map<Long, Patient> bedPatientMap = patients.stream()
                .collect(Collectors.toMap(Patient::getBedId, p -> p, (a, b) -> a));

        // 查出该病房床位当前所有的分配关系，按 id DESC 倒序排序（最新打勾交班记录在最前）
        List<NursePatientRelation> currentRelations = Collections.emptyList();
        if (!wardBedIds.isEmpty()) {
            LambdaQueryWrapper<NursePatientRelation> rWrapper = new LambdaQueryWrapper<>();
            rWrapper.in(NursePatientRelation::getBedId, wardBedIds)
                    .orderByDesc(NursePatientRelation::getId);
            currentRelations = relationMapper.selectList(rWrapper);
        }

        // 每张床只保留最后交班（在该床位打勾）的那位护士ID
        Map<Long, Long> bedDutyNurseIdMap = new HashMap<>();
        for (NursePatientRelation r : currentRelations) {
            if (r.getBedId() != null && r.getNurseId() != null) {
                // 因按 id 倒序，第一次遇到即为最新打勾的记录
                bedDutyNurseIdMap.putIfAbsent(r.getBedId(), r.getNurseId());
            }
        }

        // 预查所有护士姓名
        List<Nurse> allNurses = nurseMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> nurseNameMap = allNurses.stream()
                .collect(Collectors.toMap(Nurse::getId, Nurse::getRealName, (a, b) -> a));

        List<NurseAssignmentVO.BedAssignItemVO> bedItems = new ArrayList<>();
        for (Bed bed : beds) {
            Patient p = bedPatientMap.get(bed.getId());
            Long dutyNurseId = bedDutyNurseIdMap.get(bed.getId());
            // 只有最后交班打勾的护士才判定为在管
            boolean isMine = dutyNurseId != null && dutyNurseId.equals(nurseId);
            // 责任护士只显示一个，显示最后交班打勾的人
            String dutyNurseName = dutyNurseId != null ? nurseNameMap.get(dutyNurseId) : null;

            bedItems.add(NurseAssignmentVO.BedAssignItemVO.builder()
                    .bedId(bed.getId())
                    .bedNo(bed.getBedNo())
                    .patientId(p != null ? p.getId() : null)
                    .patientName(p != null ? p.getPatientName() : "（空床）")
                    .isAssignedToThisNurse(isMine)
                    .currentNurseId(dutyNurseId)
                    .currentNurseName(dutyNurseName)
                    .build());
        }

        return NurseAssignmentVO.builder()
                .nurseId(nurse.getId())
                .nurseName(nurse.getRealName())
                .jobNumber(nurse.getJobNumber())
                .wardId(ward.getId())
                .wardName(ward.getWardName())
                .beds(bedItems)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimMyDutyBeds(ClaimDutyBedsDTO dto) {
        Long nurseId = getCurrentNurseId();
        Long wardId = dto.getWardId();
        List<Long> targetBedIds = dto.getBedIds() != null ? dto.getBedIds() : Collections.emptyList();

        Nurse nurse = nurseMapper.selectById(nurseId);
        if (nurse == null || (nurse.getStatus() != null && nurse.getStatus() == 0)) {
            throw new BusinessException("护士档案不存在或已离职");
        }

        Ward ward = wardMapper.selectById(wardId);
        if (ward == null) {
            throw new BusinessException("病房不存在");
        }

        Long operatorUserId = UserContext.getUserId();

        // 1. 查出该病房所有有效床位ID
        LambdaQueryWrapper<Bed> bQ = new LambdaQueryWrapper<>();
        bQ.eq(Bed::getWardId, wardId);
        List<Bed> wardBeds = bedMapper.selectList(bQ);
        List<Long> wardBedIds = wardBeds.stream().map(Bed::getId).collect(Collectors.toList());
        if (wardBedIds.isEmpty()) {
            return;
        }

        // 2. 查出这批床位当前在院的病人映射
        LambdaQueryWrapper<Patient> pWrapper = new LambdaQueryWrapper<>();
        pWrapper.eq(Patient::getWardId, wardId)
                .in(Patient::getBedId, wardBedIds)
                .eq(Patient::getStatus, 1);
        List<Patient> inHospitalPatients = patientMapper.selectList(pWrapper);
        Map<Long, Patient> bedPatientMap = inHospitalPatients.stream()
                .collect(Collectors.toMap(Patient::getBedId, p -> p, (a, b) -> a));

        // 3. 责任护士交班接管：
        // 3.1 先清理当前护士在该病区床位上的已有管辖记录
        LambdaQueryWrapper<NursePatientRelation> delCurrentNurseWrapper = new LambdaQueryWrapper<>();
        delCurrentNurseWrapper.eq(NursePatientRelation::getNurseId, nurseId)
                .in(NursePatientRelation::getBedId, wardBedIds);
        relationMapper.delete(delCurrentNurseWrapper);

        // 3.2 针对本次最新打勾接班的床位，清除原有任何其他护士管辖记录（交班接管床位，确保每床只归最后打勾者）
        if (!targetBedIds.isEmpty()) {
            LambdaQueryWrapper<NursePatientRelation> delTargetBedsWrapper = new LambdaQueryWrapper<>();
            delTargetBedsWrapper.in(NursePatientRelation::getBedId, targetBedIds);
            relationMapper.delete(delTargetBedsWrapper);
        }

        // 4. 将本次护士最新勾选接班的床位批量写入（由最后打勾交班的护士接管负责）
        Date now = new Date();
        for (Long bedId : targetBedIds) {
            if (!wardBedIds.contains(bedId)) {
                continue;
            }
            Patient patient = bedPatientMap.get(bedId);
            NursePatientRelation rel = new NursePatientRelation();
            rel.setNurseId(nurseId);
            rel.setWardId(wardId);
            rel.setBedId(bedId);
            rel.setPatientId(patient != null ? patient.getId() : null);
            rel.setAssignBy(operatorUserId);
            rel.setCreateTime(now);
            relationMapper.insert(rel);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignBeds(NurseAssignBedsDTO dto) {
        checkHeadNursePermission();
        Long nurseId = dto.getNurseId();
        Long wardId = dto.getWardId();
        List<Long> bedIds = dto.getBedIds() != null ? dto.getBedIds() : Collections.emptyList();

        Nurse nurse = nurseMapper.selectById(nurseId);
        if (nurse == null) {
            throw new BusinessException("护士不存在");
        }

        Long operatorUserId = UserContext.getUserId();

        // 1. 查出该病房所有床位ID
        LambdaQueryWrapper<Bed> bQ = new LambdaQueryWrapper<>();
        bQ.eq(Bed::getWardId, wardId);
        List<Bed> wardBeds = bedMapper.selectList(bQ);
        List<Long> wardBedIds = wardBeds.stream().map(Bed::getId).collect(Collectors.toList());

        // 删除该护士在该病房下的原有分配关系
        if (!wardBedIds.isEmpty()) {
            LambdaQueryWrapper<NursePatientRelation> delWrapper = new LambdaQueryWrapper<>();
            delWrapper.eq(NursePatientRelation::getNurseId, nurseId)
                    .in(NursePatientRelation::getBedId, wardBedIds);
            relationMapper.delete(delWrapper);
        }

        // 对于本次指派的床位，清除原有的任何分配关系（确保每床只对应唯一责任护士）
        if (!bedIds.isEmpty()) {
            LambdaQueryWrapper<NursePatientRelation> delBedWrapper = new LambdaQueryWrapper<>();
            delBedWrapper.in(NursePatientRelation::getBedId, bedIds);
            relationMapper.delete(delBedWrapper);
        }

        // 批量插入
        if (!bedIds.isEmpty()) {
            LambdaQueryWrapper<Patient> pWrapper = new LambdaQueryWrapper<>();
            pWrapper.eq(Patient::getWardId, wardId)
                    .in(Patient::getBedId, bedIds)
                    .eq(Patient::getStatus, 1);
            List<Patient> inHospitalPatients = patientMapper.selectList(pWrapper);
            Map<Long, Patient> bedPatientMap = inHospitalPatients.stream()
                    .collect(Collectors.toMap(Patient::getBedId, p -> p, (a, b) -> a));

            Date now = new Date();
            for (Long bedId : bedIds) {
                Patient patient = bedPatientMap.get(bedId);
                NursePatientRelation rel = new NursePatientRelation();
                rel.setNurseId(nurseId);
                rel.setWardId(wardId);
                rel.setBedId(bedId);
                rel.setPatientId(patient != null ? patient.getId() : null);
                rel.setAssignBy(operatorUserId);
                rel.setCreateTime(now);
                relationMapper.insert(rel);
            }
        }
    }
}
