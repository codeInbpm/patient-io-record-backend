package com.patient.io.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.patient.io.common.BusinessException;
import com.patient.io.common.UserContext;
import com.patient.io.dto.NurseAssignDTO;
import com.patient.io.dto.PatientCreateDTO;
import com.patient.io.dto.PatientUpdateDTO;
import com.patient.io.entity.*;
import com.patient.io.mapper.*;
import com.patient.io.service.PatientService;
import com.patient.io.vo.PatientVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PatientServiceImpl extends ServiceImpl<PatientMapper, Patient> implements PatientService {

    @Autowired
    private WardMapper wardMapper;

    @Autowired
    private BedMapper bedMapper;

    @Autowired
    private NurseMapper nurseMapper;

    @Autowired
    private NursePatientRelationMapper relationMapper;

    @Autowired
    private DailySummaryMapper dailySummaryMapper;

    @Autowired
    private OutputRecordMapper outputRecordMapper;

    @Autowired
    private IntakeRecordMapper intakeRecordMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientVO createPatient(PatientCreateDTO createDTO) {
        String currentRole = UserContext.getRole();
        if (!"NURSE".equals(currentRole) && !"HEAD_NURSE".equals(currentRole) && !"SUPER_ADMIN".equals(currentRole) && !"HOSPITAL_ADMIN".equals(currentRole)) {
            throw new BusinessException(403, "无创建病患档案权限");
        }

        Ward ward = wardMapper.selectById(createDTO.getWardId());
        if (ward == null) {
            throw new BusinessException("指定病房不存在");
        }

        Bed bed = bedMapper.selectById(createDTO.getBedId());
        if (bed == null || !bed.getWardId().equals(createDTO.getWardId())) {
            throw new BusinessException("床位不存在或与病房不匹配");
        }
        if (bed.getStatus() == 1) {
            throw new BusinessException("该床位已被占用，请先选择其他空床");
        }

        // 检查住院号是否重复
        LambdaQueryWrapper<Patient> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(Patient::getHospitalNo, createDTO.getHospitalNo().trim());
        if (count(checkWrapper) > 0) {
            throw new BusinessException("该住院号已存在");
        }

        Patient patient = new Patient();
        BeanUtils.copyProperties(createDTO, patient);
        patient.setBedNo(bed.getBedNo());
        patient.setStatus(1); // 在院
        if (patient.getPatientType() == null) {
            patient.setPatientType("ADULT");
        }
        if (patient.getNeedIoRecord() == null) {
            patient.setNeedIoRecord(1);
        }
        if (patient.getAdmissionType() == null) {
            patient.setAdmissionType("ROUTINE");
        }
        if (patient.getNursingLevel() == null) {
            patient.setNursingLevel("LEVEL_2");
        }
        save(patient);

        // 将床位标记为占用
        bed.setStatus(1);
        bedMapper.updateById(bed);

        return convertToVO(patient, ward.getWardName(), null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientVO updatePatient(PatientUpdateDTO updateDTO) {
        String currentRole = UserContext.getRole();
        if (!"NURSE".equals(currentRole) && !"HEAD_NURSE".equals(currentRole) && !"SUPER_ADMIN".equals(currentRole) && !"HOSPITAL_ADMIN".equals(currentRole)) {
            throw new BusinessException(403, "无修改病患档案权限");
        }

        Patient patient = getById(updateDTO.getId());
        if (patient == null) {
            throw new BusinessException("病患档案不存在");
        }

        // 校验住院号是否与其他在院病患重复（排除自己）
        LambdaQueryWrapper<Patient> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(Patient::getHospitalNo, updateDTO.getHospitalNo().trim())
                    .ne(Patient::getId, updateDTO.getId());
        if (count(checkWrapper) > 0) {
            throw new BusinessException("该住院号已被其他患者使用，不可重复");
        }

        // 处理床位变更（如换床或换病区）
        Long oldBedId = patient.getBedId();
        Long newBedId = updateDTO.getBedId();
        if (newBedId != null && !newBedId.equals(oldBedId)) {
            Bed newBed = bedMapper.selectById(newBedId);
            if (newBed == null) {
                throw new BusinessException("指定新床位不存在");
            }
            if (newBed.getStatus() == 1) {
                throw new BusinessException("新床位【" + newBed.getBedNo() + "】已被其他患者占用，无法调整");
            }
            // 释放原床位
            if (oldBedId != null) {
                Bed oldBed = bedMapper.selectById(oldBedId);
                if (oldBed != null) {
                    oldBed.setStatus(0);
                    bedMapper.updateById(oldBed);
                }
            }
            // 占用新床位
            newBed.setStatus(1);
            bedMapper.updateById(newBed);

            patient.setBedId(newBed.getId());
            patient.setBedNo(newBed.getBedNo());
            patient.setWardId(newBed.getWardId());

            // 同步更新护士分配记录关联的床位与病区
            LambdaUpdateWrapper<NursePatientRelation> relUpdate = new LambdaUpdateWrapper<>();
            relUpdate.eq(NursePatientRelation::getPatientId, patient.getId())
                     .set(NursePatientRelation::getBedId, newBed.getId())
                     .set(NursePatientRelation::getWardId, newBed.getWardId());
            relationMapper.update(null, relUpdate);
        }

        // 更新基本信息
        patient.setPatientName(updateDTO.getPatientName().trim());
        patient.setHospitalNo(updateDTO.getHospitalNo().trim());
        if (updateDTO.getGender() != null) patient.setGender(updateDTO.getGender());
        if (updateDTO.getAge() != null) patient.setAge(updateDTO.getAge());
        if (updateDTO.getWeight() != null) patient.setWeight(updateDTO.getWeight());
        if (updateDTO.getPatientType() != null) patient.setPatientType(updateDTO.getPatientType());
        if (updateDTO.getAdmissionType() != null) patient.setAdmissionType(updateDTO.getAdmissionType());
        if (updateDTO.getNursingLevel() != null) patient.setNursingLevel(updateDTO.getNursingLevel());
        if (updateDTO.getNeedIoRecord() != null) patient.setNeedIoRecord(updateDTO.getNeedIoRecord());
        if (updateDTO.getDrainageTubes() != null) patient.setDrainageTubes(updateDTO.getDrainageTubes());
        if (updateDTO.getAdmissionDate() != null) patient.setAdmissionDate(updateDTO.getAdmissionDate());
        if (updateDTO.getDiagnosis() != null) patient.setDiagnosis(updateDTO.getDiagnosis());

        updateById(patient);

        Ward ward = wardMapper.selectById(patient.getWardId());
        String wardName = ward != null ? ward.getWardName() : "";
        return convertToVO(patient, wardName, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignNurseToPatient(NurseAssignDTO assignDTO) {
        String currentRole = UserContext.getRole();
        if (!"HEAD_NURSE".equals(currentRole) && !"SUPER_ADMIN".equals(currentRole) && !"HOSPITAL_ADMIN".equals(currentRole)) {
            throw new BusinessException(403, "只有护士长或系统管理员拥有分配责任护士权限");
        }

        Patient patient = getById(assignDTO.getPatientId());
        if (patient == null) {
            throw new BusinessException("病人不存在");
        }

        Nurse nurse = nurseMapper.selectById(assignDTO.getNurseId());
        if (nurse == null || nurse.getStatus() == 0) {
            throw new BusinessException("被分配护士不存在或已离职");
        }

        // 删除已有分配，更新为新分配
        LambdaQueryWrapper<NursePatientRelation> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(NursePatientRelation::getPatientId, patient.getId());
        relationMapper.delete(delWrapper);

        NursePatientRelation rel = new NursePatientRelation();
        rel.setNurseId(nurse.getId());
        rel.setWardId(patient.getWardId());
        rel.setPatientId(patient.getId());
        rel.setBedId(patient.getBedId());
        rel.setAssignBy(UserContext.getUserId());
        relationMapper.insert(rel);
    }

    @Override
    public List<PatientVO> listPatientsForCurrentRole(Long wardId) {
        return listPatientsForCurrentRole(wardId, false);
    }

    @Override
    public List<PatientVO> listPatientsForCurrentRole(Long wardId, Boolean myOnly) {
        String role = UserContext.getRole();
        Long userId = UserContext.getUserId();

        List<Patient> patients = new ArrayList<>();

        // 数据范围：非 SELF 的账号按范围取数；WARD 范围未指定病区时自动收窄到授权病区，
        // 避免护士长不选病区就拿到全院患者（旧实现正是这个口子）。
        java.util.List<Long> scopeWards = com.patient.io.common.ScopeContext.getWardIds();
        boolean wardScoped = com.patient.io.common.ScopeContext.WARD
                .equals(com.patient.io.common.ScopeContext.getScope())
                && scopeWards != null && !scopeWards.isEmpty();

        if (!com.patient.io.common.ScopeContext.SELF.equals(com.patient.io.common.ScopeContext.getScope())
                || "HEAD_NURSE".equals(role) || "SUPER_ADMIN".equals(role) || "HOSPITAL_ADMIN".equals(role)) {
            // 护士长与管理员：按病区查看在院病人
            LambdaQueryWrapper<Patient> wrapper = new LambdaQueryWrapper<>();
            if (wardId != null) {
                wrapper.eq(Patient::getWardId, wardId);
            } else if (wardScoped) {
                wrapper.in(Patient::getWardId, scopeWards);
            }
            wrapper.eq(Patient::getStatus, 1)
                   .orderByAsc(Patient::getBedNo);
            patients = list(wrapper);
        } else if ("NURSE".equals(role)) {
            // 普通护士：不做查询限制，支持查看所有在院病患！
            // 只有当显式指定 myOnly=true 时，才过滤为分配给自己的病患
            if (Boolean.TRUE.equals(myOnly)) {
                SysUser user = userMapper.selectById(userId);
                if (user == null || user.getBindRefId() == null) {
                    return new ArrayList<>();
                }
                Long nurseId = user.getBindRefId();

                LambdaQueryWrapper<NursePatientRelation> relWrapper = new LambdaQueryWrapper<>();
                relWrapper.eq(NursePatientRelation::getNurseId, nurseId);
                List<NursePatientRelation> relations = relationMapper.selectList(relWrapper);

                if (relations.isEmpty()) {
                    return new ArrayList<>();
                }

                Set<Long> patientIds = relations.stream().map(NursePatientRelation::getPatientId).filter(id -> id != null && id > 0).collect(Collectors.toSet());
                Set<Long> bedIds = relations.stream().map(NursePatientRelation::getBedId).filter(Objects::nonNull).collect(Collectors.toSet());

                if (patientIds.isEmpty() && bedIds.isEmpty()) {
                    return new ArrayList<>();
                }

                LambdaQueryWrapper<Patient> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Patient::getStatus, 1);
                if (wardId != null) {
                    wrapper.eq(Patient::getWardId, wardId);
                }
                wrapper.and(w -> {
                    if (!patientIds.isEmpty() && !bedIds.isEmpty()) {
                        w.in(Patient::getId, patientIds).or().in(Patient::getBedId, bedIds);
                    } else if (!patientIds.isEmpty()) {
                        w.in(Patient::getId, patientIds);
                    } else {
                        w.in(Patient::getBedId, bedIds);
                    }
                });
                wrapper.orderByAsc(Patient::getBedNo);
                patients = list(wrapper);
            } else {
                // 默认不做限制，可查看该病区或全院所有在院病患
                LambdaQueryWrapper<Patient> wrapper = new LambdaQueryWrapper<>();
                if (wardId != null) {
                    wrapper.eq(Patient::getWardId, wardId);
                }
                wrapper.eq(Patient::getStatus, 1)
                       .orderByAsc(Patient::getBedNo);
                patients = list(wrapper);
            }
        } else if ("PATIENT".equals(role)) {
            // 患者：只看自己
            Patient my = getMyBoundPatientEntity();
            if (my != null) {
                patients.add(my);
            }
        }

        if (patients.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询今日出入量与分配护士
        LocalDate today = LocalDate.now();
        List<Long> patientIds = patients.stream().map(Patient::getId).collect(Collectors.toList());

        LambdaQueryWrapper<DailySummary> sWrapper = new LambdaQueryWrapper<>();
        sWrapper.in(DailySummary::getPatientId, patientIds)
                .eq(DailySummary::getRecordDate, today);
        Map<Long, DailySummary> summaryMap = dailySummaryMapper.selectList(sWrapper).stream()
                .collect(Collectors.toMap(DailySummary::getPatientId, s -> s));

        // 查询过去24小时（从当前时刻倒推24小时）动态流水统计
        Map<Long, Patient24hStat> stat24hMap = query24hStats(patientIds);

        // 漏记哨兵：批量取每位患者最近一次记录时间 + 今日记录笔数（一次查询，避免 N+1）
        RecordPulse pulse = queryRecordPulse(patientIds, today);

        // 查出所有关联的护士（责任护士只显示一个，显示最后交班在该病患床位打勾的人）
        List<Long> bedIds = patients.stream().map(Patient::getBedId).filter(Objects::nonNull).collect(Collectors.toList());
        LambdaQueryWrapper<NursePatientRelation> rWrapper = new LambdaQueryWrapper<>();
        rWrapper.and(w -> {
            w.in(NursePatientRelation::getPatientId, patientIds);
            if (!bedIds.isEmpty()) {
                w.or().in(NursePatientRelation::getBedId, bedIds);
            }
        }).orderByDesc(NursePatientRelation::getId);
        List<NursePatientRelation> relList = relationMapper.selectList(rWrapper);
        
        // 每个病人及床位对应的最后交班打勾的护士ID（按 id 倒序遍历，首次出现即为最新交班人）
        Map<Long, Long> patientDutyNurseIdMap = new HashMap<>();
        Map<Long, Long> bedDutyNurseIdMap = new HashMap<>();
        for (NursePatientRelation r : relList) {
            if (r.getNurseId() != null) {
                if (r.getPatientId() != null) {
                    patientDutyNurseIdMap.putIfAbsent(r.getPatientId(), r.getNurseId());
                }
                if (r.getBedId() != null) {
                    bedDutyNurseIdMap.putIfAbsent(r.getBedId(), r.getNurseId());
                }
            }
        }

        Map<Long, String> nurseNameMap = nurseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Nurse::getId, Nurse::getRealName, (k1, k2) -> k1));

        Map<Long, String> wardNameMap = wardMapper.selectList(null).stream()
                .collect(Collectors.toMap(Ward::getId, Ward::getWardName, (k1, k2) -> k1));

        // 判断当前登录人员是否为护士长/管理员，或获取当前护士的nurseId
        boolean isSuperOrHead = "HEAD_NURSE".equals(role) || "SUPER_ADMIN".equals(role) || "HOSPITAL_ADMIN".equals(role);
        Long currentNurseId = null;
        if ("NURSE".equals(role)) {
            SysUser currentUser = userMapper.selectById(userId);
            if (currentUser != null) {
                currentNurseId = currentUser.getBindRefId();
            }
        }

        List<PatientVO> voList = new ArrayList<>();
        for (Patient p : patients) {
            String wName = wardNameMap.getOrDefault(p.getWardId(), "");
            DailySummary s = summaryMap.get(p.getId());
            
            // 责任护士：优先取病人关联，若无则取床位关联，只取最后交班打勾的一位
            Long dutyNurseId = patientDutyNurseIdMap.get(p.getId());
            if (dutyNurseId == null && p.getBedId() != null) {
                dutyNurseId = bedDutyNurseIdMap.get(p.getBedId());
            }

            String nName = dutyNurseId != null ? nurseNameMap.get(dutyNurseId) : null;
            List<String> assignedNames = nName != null ? Collections.singletonList(nName) : Collections.emptyList();

            // 是否属于当前护士当班管辖：由最后交班打勾的护士管辖负责
            boolean isMyDuty = isSuperOrHead || (currentNurseId != null && Objects.equals(currentNurseId, dutyNurseId));

            Patient24hStat stat24h = stat24hMap.get(p.getId());
            PatientVO vo = convertToVO(p, wName, s, stat24h, nName, assignedNames, isMyDuty);
            applyRecordPulse(vo, p, pulse);
            voList.add(vo);
        }

        return voList;
    }

    @Override
    public PatientVO getPatientDetail(Long patientId) {
        Patient patient = getById(patientId);
        if (patient == null) {
            throw new BusinessException("病人档案不存在");
        }
        checkPermission(patient);

        Ward ward = wardMapper.selectById(patient.getWardId());
        String wardName = ward != null ? ward.getWardName() : "";

        LambdaQueryWrapper<DailySummary> summaryWrapper = new LambdaQueryWrapper<>();
        summaryWrapper.eq(DailySummary::getPatientId, patientId)
                      .eq(DailySummary::getRecordDate, LocalDate.now());
        DailySummary summary = dailySummaryMapper.selectOne(summaryWrapper);

        // 查负责护士：责任护士只显示一个，显示最后交班（在该病患床位打勾）的人
        LambdaQueryWrapper<NursePatientRelation> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.and(w -> {
            w.eq(NursePatientRelation::getPatientId, patientId);
            if (patient.getBedId() != null) {
                w.or().eq(NursePatientRelation::getBedId, patient.getBedId());
            }
        }).orderByDesc(NursePatientRelation::getId);
        List<NursePatientRelation> rels = relationMapper.selectList(relWrapper);
        Long dutyNurseId = rels.isEmpty() ? null : rels.get(0).getNurseId();

        Map<Long, String> nurseNameMap = nurseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Nurse::getId, Nurse::getRealName, (k1, k2) -> k1));

        String nurseName = dutyNurseId != null ? nurseNameMap.get(dutyNurseId) : null;
        List<String> assignedNames = nurseName != null ? Collections.singletonList(nurseName) : Collections.emptyList();

        String role = UserContext.getRole();
        Long userId = UserContext.getUserId();
        boolean isSuperOrHead = "HEAD_NURSE".equals(role) || "SUPER_ADMIN".equals(role) || "HOSPITAL_ADMIN".equals(role);
        Long currentNurseId = null;
        if ("NURSE".equals(role)) {
            SysUser currentUser = userMapper.selectById(userId);
            if (currentUser != null) {
                currentNurseId = currentUser.getBindRefId();
            }
        }
        boolean isMyDuty = isSuperOrHead || (currentNurseId != null && Objects.equals(currentNurseId, dutyNurseId));

        Map<Long, Patient24hStat> stat24hMap = query24hStats(Collections.singletonList(patientId));
        Patient24hStat stat24h = stat24hMap.get(patientId);

        PatientVO detailVO = convertToVO(patient, wardName, summary, stat24h, nurseName, assignedNames, isMyDuty);
        applyRecordPulse(detailVO, patient, queryRecordPulse(Collections.singletonList(patientId), LocalDate.now()));
        return detailVO;
    }

    @Override
    public PatientVO getMyBoundPatient() {
        Patient p = getMyBoundPatientEntity();
        if (p == null) return null;
        return getPatientDetail(p.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dischargePatient(Long patientId) {
        String currentRole = UserContext.getRole();
        if (!"HEAD_NURSE".equals(currentRole) && !"SUPER_ADMIN".equals(currentRole) && !"HOSPITAL_ADMIN".equals(currentRole)) {
            throw new BusinessException(403, "无办理出院权限");
        }
        Patient patient = getById(patientId);
        if (patient == null) {
            throw new BusinessException("病人不存在");
        }

        // 1. 释放原床位恢复为空闲状态
        if (patient.getBedId() != null) {
            Bed bed = bedMapper.selectById(patient.getBedId());
            if (bed != null) {
                bed.setStatus(0); // 恢复空床
                bedMapper.updateById(bed);
            }
        }

        // 2. 清理护士分配关联
        LambdaQueryWrapper<NursePatientRelation> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(NursePatientRelation::getPatientId, patientId);
        relationMapper.delete(relWrapper);

        // 3. 解除绑定的微信小程序用户
        if (patient.getBoundUserId() != null) {
            SysUser user = userMapper.selectById(patient.getBoundUserId());
            if (user != null) {
                user.setRole("NONE");
                user.setBindStatus(0);
                user.setBindRefId(null);
                userMapper.updateById(user);
            }
        }

        // 4. 清理每日出入量汇总记录
        LambdaQueryWrapper<DailySummary> summaryWrapper = new LambdaQueryWrapper<>();
        summaryWrapper.eq(DailySummary::getPatientId, patientId);
        dailySummaryMapper.delete(summaryWrapper);

        // 5. 按照用户明确需求：出院的数据直接物理删除！
        baseMapper.deletePhysicallyById(patientId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindPatientWechat(Long patientId) {
        Patient patient = getById(patientId);
        if (patient == null) {
            throw new BusinessException("病患档案不存在");
        }
        Long boundUserId = patient.getBoundUserId();
        if (boundUserId != null) {
            SysUser user = userMapper.selectById(boundUserId);
            if (user != null) {
                user.setRole("NONE");
                user.setBindStatus(0);
                user.setBindRefId(null);
                userMapper.updateById(user);
            }
            patient.setBoundUserId(null);
            updateById(patient);
        }
    }

    private Patient getMyBoundPatientEntity() {
        Long userId = UserContext.getUserId();
        if (userId == null) return null;

        LambdaQueryWrapper<Patient> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Patient::getBoundUserId, userId)
               .eq(Patient::getStatus, 1)
               .last("limit 1");
        return getOne(wrapper);
    }

    private void checkPermission(Patient patient) {
        String role = UserContext.getRole();
        Long userId = UserContext.getUserId();

        if ("HEAD_NURSE".equals(role) || "SUPER_ADMIN".equals(role) || "HOSPITAL_ADMIN".equals(role)) {
            return; // 护士长和管理员拥有全部查看权限
        }
        if ("NURSE".equals(role)) {
            SysUser user = userMapper.selectById(userId);
            if (user == null || user.getBindRefId() == null) {
                throw new BusinessException(403, "未绑定护士工号");
            }
            // 护士可全量查阅病区病患档案及出入量流水（满足换班后仍可查询的需求）
            return;
        }
        if ("PATIENT".equals(role)) {
            if (!userId.equals(patient.getBoundUserId())) {
                throw new BusinessException(403, "只能查看本人的出入量数据");
            }
        }
    }

    private static class Patient24hStat {
        BigDecimal urine = BigDecimal.ZERO;
        BigDecimal iv = BigDecimal.ZERO;
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        boolean hasRecords = false;
    }

    private Map<Long, Patient24hStat> query24hStats(List<Long> patientIds) {
        Map<Long, Patient24hStat> map = new HashMap<>();
        if (patientIds == null || patientIds.isEmpty()) {
            return map;
        }
        for (Long pid : patientIds) {
            map.put(pid, new Patient24hStat());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime past24h = now.minusHours(24);

        LambdaQueryWrapper<OutputRecord> outQuery = new LambdaQueryWrapper<>();
        outQuery.in(OutputRecord::getPatientId, patientIds)
                .ge(OutputRecord::getRecordTime, past24h)
                .le(OutputRecord::getRecordTime, now);
        List<OutputRecord> outList = outputRecordMapper.selectList(outQuery);
        for (OutputRecord r : outList) {
            Patient24hStat stat = map.get(r.getPatientId());
            if (stat != null && r.getAmount() != null) {
                stat.hasRecords = true;
                stat.totalOut = stat.totalOut.add(r.getAmount());
                if ("URINE".equalsIgnoreCase(r.getOutputType())) {
                    stat.urine = stat.urine.add(r.getAmount());
                }
            }
        }

        LambdaQueryWrapper<IntakeRecord> inQuery = new LambdaQueryWrapper<>();
        inQuery.in(IntakeRecord::getPatientId, patientIds)
                .ge(IntakeRecord::getRecordTime, past24h)
                .le(IntakeRecord::getRecordTime, now);
        List<IntakeRecord> inList = intakeRecordMapper.selectList(inQuery);
        for (IntakeRecord r : inList) {
            Patient24hStat stat = map.get(r.getPatientId());
            if (stat != null && r.getAmount() != null) {
                stat.hasRecords = true;
                stat.totalIn = stat.totalIn.add(r.getAmount());
                if ("IV".equalsIgnoreCase(r.getIntakeType()) || (r.getItemName() != null && (r.getItemName().contains("输液") || r.getItemName().contains("静脉")))) {
                    stat.iv = stat.iv.add(r.getAmount());
                }
            }
        }

        return map;
    }

    /** 记录活跃度聚合结果（漏记哨兵） */
    private static class RecordPulse {
        Map<Long, LocalDateTime> lastTime = new HashMap<>();
        Map<Long, Integer> todayCount = new HashMap<>();

        void accept(Long patientId, LocalDateTime recordTime, LocalDate recordDate, LocalDate today) {
            if (patientId == null || recordTime == null) {
                return;
            }
            LocalDateTime current = lastTime.get(patientId);
            if (current == null || recordTime.isAfter(current)) {
                lastTime.put(patientId, recordTime);
            }
            if (today.equals(recordDate)) {
                todayCount.merge(patientId, 1, Integer::sum);
            }
        }
    }

    /**
     * 一次查出近 3 天记录时间，算出「最近一次记录」与「今日笔数」
     */
    private RecordPulse queryRecordPulse(List<Long> patientIds, LocalDate today) {
        RecordPulse pulse = new RecordPulse();
        if (patientIds == null || patientIds.isEmpty()) {
            return pulse;
        }
        LocalDate from = today.minusDays(2);

        LambdaQueryWrapper<IntakeRecord> iw = new LambdaQueryWrapper<>();
        iw.select(IntakeRecord::getPatientId, IntakeRecord::getRecordTime, IntakeRecord::getRecordDate)
                .in(IntakeRecord::getPatientId, patientIds)
                .ge(IntakeRecord::getRecordDate, from);
        for (IntakeRecord r : intakeRecordMapper.selectList(iw)) {
            pulse.accept(r.getPatientId(), r.getRecordTime(), r.getRecordDate(), today);
        }

        LambdaQueryWrapper<OutputRecord> ow = new LambdaQueryWrapper<>();
        ow.select(OutputRecord::getPatientId, OutputRecord::getRecordTime, OutputRecord::getRecordDate)
                .in(OutputRecord::getPatientId, patientIds)
                .ge(OutputRecord::getRecordDate, from);
        for (OutputRecord r : outputRecordMapper.selectList(ow)) {
            pulse.accept(r.getPatientId(), r.getRecordTime(), r.getRecordDate(), today);
        }
        return pulse;
    }

    /**
     * 把「距上次记录多久 / 是否需要补记」写入 VO
     */
    private void applyRecordPulse(PatientVO vo, Patient p, RecordPulse pulse) {
        LocalDateTime last = pulse.lastTime.get(p.getId());
        int todayCount = pulse.todayCount.getOrDefault(p.getId(), 0);
        vo.setTodayRecordCount(todayCount);
        vo.setLastRecordTime(last);

        boolean needIo = p.getNeedIoRecord() == null || p.getNeedIoRecord() == 1;
        if (!needIo) {
            vo.setIsMissed(false);
            vo.setHoursSinceLastRecord(null);
            return;
        }

        if (last == null) {
            vo.setHoursSinceLastRecord(null);
            // 非今日新入院的患者，至今没有任何记录 -> 需要补记
            boolean admittedBeforeToday = p.getAdmissionDate() != null && p.getAdmissionDate().isBefore(LocalDate.now());
            vo.setIsMissed(admittedBeforeToday);
            return;
        }

        long minutes = Duration.between(last, LocalDateTime.now()).toMinutes();
        double hours = Math.max(0, minutes / 60.0);
        vo.setHoursSinceLastRecord(Math.round(hours * 10) / 10.0);
        vo.setIsMissed(hours >= 4);
    }

    private PatientVO convertToVO(Patient p, String wardName, DailySummary summary, String assignedNurseName) {
        return convertToVO(p, wardName, summary, null, assignedNurseName, null, false);
    }

    private PatientVO convertToVO(Patient p, String wardName, DailySummary summary, Patient24hStat stat24h, String assignedNurseName, List<String> assignedNurseNames, Boolean isMyDuty) {
        PatientVO vo = new PatientVO();
        BeanUtils.copyProperties(p, vo);
        vo.setWardName(wardName);
        vo.setIsBound(p.getBoundUserId() != null);
        vo.setAssignedNurseName(assignedNurseName);
        vo.setAssignedNurseNames(assignedNurseNames);
        vo.setIsMyDuty(isMyDuty);

        if (summary != null) {
            vo.setTodayIntake(summary.getTotalIntake() != null ? summary.getTotalIntake() : BigDecimal.ZERO);
            vo.setTodayOutput(summary.getTotalOutput() != null ? summary.getTotalOutput() : BigDecimal.ZERO);
            vo.setTodayBalance(summary.getNetBalance() != null ? summary.getNetBalance() : BigDecimal.ZERO);
            vo.setTodayUrine(summary.getUrineOutput() != null ? summary.getUrineOutput() : BigDecimal.ZERO);
            vo.setTodayIv(summary.getIvIntake() != null ? summary.getIvIntake() : BigDecimal.ZERO);
        } else {
            vo.setTodayIntake(BigDecimal.ZERO);
            vo.setTodayOutput(BigDecimal.ZERO);
            vo.setTodayBalance(BigDecimal.ZERO);
            vo.setTodayUrine(BigDecimal.ZERO);
            vo.setTodayIv(BigDecimal.ZERO);
        }

        // 滚动24小时统计指标（从当前时间往前倒推24小时）
        BigDecimal urine24h = (stat24h != null && stat24h.hasRecords) ? stat24h.urine : vo.getTodayUrine();
        BigDecimal iv24h = (stat24h != null && stat24h.hasRecords) ? stat24h.iv : vo.getTodayIv();
        BigDecimal in24h = (stat24h != null && stat24h.hasRecords) ? stat24h.totalIn : vo.getTodayIntake();
        BigDecimal out24h = (stat24h != null && stat24h.hasRecords) ? stat24h.totalOut : vo.getTodayOutput();
        BigDecimal balance24h = in24h.subtract(out24h);

        vo.setLast24hUrine(urine24h);
        vo.setLast24hIntake(in24h);
        vo.setLast24hOutput(out24h);
        vo.setLast24hBalance(balance24h);

        // 规则1：24小时内，（从当前时间开始算）尿量 < 400ml，标红
        // 用户明确需求：数据为0 也要纳入统计给出警示（0ml为临床无尿/极重度少尿）
        boolean needIo = p.getNeedIoRecord() == null || p.getNeedIoRecord() == 1;
        boolean urineAlert = needIo && urine24h.compareTo(new BigDecimal("400")) < 0;

        // 规则2：入量-出量差值 > 1000ml，标红
        boolean balanceAlert = vo.getTodayBalance().compareTo(new BigDecimal("1000")) > 0
                || balance24h.compareTo(new BigDecimal("1000")) > 0;

        // 规则3：入量中静脉输液 > 3000ml，标黄色
        boolean ivAlert = vo.getTodayIv().compareTo(new BigDecimal("3000")) > 0
                || iv24h.compareTo(new BigDecimal("3000")) > 0;

        vo.setIsUrineAlert(urineAlert);
        vo.setIsBalanceAlert(balanceAlert);
        vo.setIsIvAlert(ivAlert);
        vo.setIsAbnormal(urineAlert || balanceAlert || ivAlert || (summary != null && summary.getIsAbnormal() != null && summary.getIsAbnormal() == 1));

        StringBuilder reason = new StringBuilder();
        if (urineAlert) {
            if (urine24h.compareTo(BigDecimal.ZERO) == 0) {
                reason.append("24h无尿/少尿(尿量:0ml<400ml/标红) ");
            } else {
                reason.append("24h少尿(尿量:").append(urine24h).append("ml<400ml/标红) ");
            }
        }
        if (balanceAlert) {
            BigDecimal dispBal = vo.getTodayBalance().compareTo(new BigDecimal("1000")) > 0 ? vo.getTodayBalance() : balance24h;
            reason.append("差值过大(+").append(dispBal).append("ml>1000ml/标红) ");
        }
        if (ivAlert) {
            BigDecimal dispIv = vo.getTodayIv().compareTo(new BigDecimal("3000")) > 0 ? vo.getTodayIv() : iv24h;
            reason.append("静脉输液过量(").append(dispIv).append("ml>3000ml/标黄) ");
        }
        vo.setAbnormalReason(reason.toString().trim());

        return vo;
    }
}
