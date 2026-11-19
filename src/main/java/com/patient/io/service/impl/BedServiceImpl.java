package com.patient.io.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.patient.io.common.BusinessException;
import com.patient.io.common.UserContext;
import com.patient.io.dto.BedCreateDTO;
import com.patient.io.entity.*;
import com.patient.io.mapper.*;
import com.patient.io.service.BedService;
import com.patient.io.vo.BedVisualVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BedServiceImpl extends ServiceImpl<BedMapper, Bed> implements BedService {

    @Autowired private WardMapper wardMapper;
    @Autowired private PatientMapper patientMapper;
    @Autowired private NurseMapper nurseMapper;
    @Autowired private NursePatientRelationMapper relationMapper;
    @Autowired private DailySummaryMapper dailySummaryMapper;

    private void checkManagePermission() {
        String role = UserContext.getRole();
        if (!"HEAD_NURSE".equals(role) && !"SUPER_ADMIN".equals(role) && !"HOSPITAL_ADMIN".equals(role)) {
            throw new BusinessException(403, "权限不足，仅护士长或系统管理员可维护床位");
        }
    }

    @Override
    public List<Bed> listBedsByWard(Long wardId) {
        LambdaQueryWrapper<Bed> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bed::getWardId, wardId)
                .orderByAsc(Bed::getSortOrder)
                .orderByAsc(Bed::getBedNo);
        return list(wrapper);
    }

    @Override
    public Bed createBed(BedCreateDTO createDTO) {
        checkManagePermission();

        LambdaQueryWrapper<Bed> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(Bed::getWardId, createDTO.getWardId())
                    .eq(Bed::getBedNo, createDTO.getBedNo().trim());
        if (count(checkWrapper) > 0) {
            throw new BusinessException("该病房下已存在同名床位");
        }

        Bed bed = new Bed();
        bed.setWardId(createDTO.getWardId());
        bed.setRoomNo(createDTO.getRoomNo() != null ? createDTO.getRoomNo().trim() : null);
        bed.setBedNo(createDTO.getBedNo().trim());
        bed.setStatus(0); // 默认空床
        bed.setSortOrder(createDTO.getSortOrder() != null ? createDTO.getSortOrder() : 0);
        save(bed);
        return bed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBed(Bed bed) {
        checkManagePermission();
        if (bed.getId() == null) {
            throw new BusinessException("床位ID不能为空");
        }
        Bed exist = getById(bed.getId());
        if (exist == null) {
            throw new BusinessException("床位档案不存在");
        }

        String targetBedNo = bed.getBedNo() != null ? bed.getBedNo().trim() : exist.getBedNo();
        Long targetWardId = bed.getWardId() != null ? bed.getWardId() : exist.getWardId();

        // 校验修改后的床位号在该病区内是否已存在（排除自身）
        LambdaQueryWrapper<Bed> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(Bed::getWardId, targetWardId)
                    .eq(Bed::getBedNo, targetBedNo)
                    .ne(Bed::getId, bed.getId());
        if (count(checkWrapper) > 0) {
            throw new BusinessException("该病区下已存在同名床位【" + targetBedNo + "】，不可重复命名");
        }

        exist.setBedNo(targetBedNo);
        if (bed.getRoomNo() != null) {
            exist.setRoomNo(bed.getRoomNo().trim());
        }
        if (bed.getSortOrder() != null) {
            exist.setSortOrder(bed.getSortOrder());
        }
        updateById(exist);

        // 如果当前床位有在院患者，同步更新患者的冗余床位编号
        LambdaQueryWrapper<Patient> pQuery = new LambdaQueryWrapper<Patient>()
                .eq(Patient::getBedId, exist.getId())
                .eq(Patient::getStatus, 1);
        Patient patient = patientMapper.selectOne(pQuery);
        if (patient != null) {
            patient.setBedNo(targetBedNo);
            patientMapper.updateById(patient);
        }
    }

    @Override
    public void deleteBed(Long bedId) {
        checkManagePermission();
        Bed bed = getById(bedId);
        if (bed != null && bed.getStatus() == 1) {
            throw new BusinessException("该床位当前有患者占用，无法删除");
        }
        removeById(bedId);
    }

    @Override
    public List<BedVisualVO> getWardVisualBeds(Long wardId) {
        Ward ward = wardMapper.selectById(wardId);
        String wardName = ward != null ? ward.getWardName() : "";

        // 1. 获取该病区所有床位
        List<Bed> bedList = listBedsByWard(wardId);
        if (bedList.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 获取该病区所有在院患者
        LambdaQueryWrapper<Patient> pQuery = new LambdaQueryWrapper<Patient>()
                .eq(Patient::getWardId, wardId)
                .eq(Patient::getStatus, 1);
        List<Patient> inPatients = patientMapper.selectList(pQuery);
        Map<Long, Patient> bedPatientMap = inPatients.stream()
                .filter(p -> p.getBedId() != null)
                .collect(Collectors.toMap(Patient::getBedId, p -> p, (a, b) -> a));

        // 3. 预查今日出入量汇总
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<DailySummary> sQuery = new LambdaQueryWrapper<DailySummary>()
                .eq(DailySummary::getWardId, wardId)
                .eq(DailySummary::getRecordDate, today);
        List<DailySummary> summaryList = dailySummaryMapper.selectList(sQuery);
        Map<Long, DailySummary> patientSummaryMap = summaryList.stream()
                .collect(Collectors.toMap(DailySummary::getPatientId, s -> s, (a, b) -> a));

        // 4. 预查负责护士信息（按 id DESC 倒序，取最后交班打勾者）
        List<NursePatientRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<NursePatientRelation>()
                .eq(NursePatientRelation::getWardId, wardId)
                .orderByDesc(NursePatientRelation::getId));
        Map<Long, Long> patientNurseIdMap = new HashMap<>();
        Map<Long, Long> bedNurseIdMap = new HashMap<>();
        for (NursePatientRelation r : relations) {
            if (r.getNurseId() != null) {
                if (r.getPatientId() != null) {
                    patientNurseIdMap.putIfAbsent(r.getPatientId(), r.getNurseId());
                }
                if (r.getBedId() != null) {
                    bedNurseIdMap.putIfAbsent(r.getBedId(), r.getNurseId());
                }
            }
        }

        List<Nurse> nurses = nurseMapper.selectList(new LambdaQueryWrapper<Nurse>().eq(Nurse::getStatus, 1));
        Map<Long, String> nurseNameMap = nurses.stream().collect(Collectors.toMap(Nurse::getId, Nurse::getRealName, (a, b) -> a));

        // 5. 遍历组装可视化数据，并按房间自动归集
        List<BedVisualVO> visualList = new ArrayList<>();
        int index = 0;
        for (Bed bed : bedList) {
            index++;
            Patient p = bedPatientMap.get(bed.getId());

            // 优先读取持久化的房间号，如未设定则智能推算
            String roomNo = bed.getRoomNo();
            if (roomNo == null || roomNo.trim().isEmpty()) {
                if (bed.getBedNo().contains("-")) {
                    roomNo = bed.getBedNo().split("-")[0] + "号房";
                } else {
                    int roomIdx = ((index - 1) / 3) + 1;
                    roomNo = String.format("%02d号房间", roomIdx);
                }
            }

            BedVisualVO.BedVisualVOBuilder voBuilder = BedVisualVO.builder()
                    .bedId(bed.getId())
                    .wardId(wardId)
                    .wardName(wardName)
                    .roomNo(roomNo)
                    .bedNo(bed.getBedNo())
                    .status(bed.getStatus())
                    .sortOrder(bed.getSortOrder());

            if (p != null) {
                DailySummary sum = patientSummaryMap.get(p.getId());
                Long nurseId = patientNurseIdMap.get(p.getId());
                if (nurseId == null && bed.getId() != null) {
                    nurseId = bedNurseIdMap.get(bed.getId());
                }
                String nurseName = nurseId != null ? nurseNameMap.get(nurseId) : null;

                voBuilder.patientId(p.getId())
                        .patientName(p.getPatientName())
                        .hospitalNo(p.getHospitalNo())
                        .gender(p.getGender())
                        .age(p.getAge())
                        .patientType(p.getPatientType())
                        .nursingLevel(p.getNursingLevel())
                        .diagnosis(p.getDiagnosis())
                        .needIoRecord(p.getNeedIoRecord())
                        .drainageTubes(p.getDrainageTubes())
                        .assignedNurseId(nurseId)
                        .assignedNurseName(nurseName)
                        .todayIntake(sum != null ? sum.getTotalIntake() : BigDecimal.ZERO)
                        .todayOutput(sum != null ? sum.getTotalOutput() : BigDecimal.ZERO)
                        .todayBalance(sum != null ? sum.getNetBalance() : BigDecimal.ZERO)
                        .isAbnormal(sum != null && sum.getIsAbnormal() == 1);
            }

            visualList.add(voBuilder.build());
        }

        return visualList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchGenerateRoomsAndBeds(Long wardId, int standardRooms, int bedsPerStandardRoom, int lastRoomBeds) {
        checkManagePermission();

        int bedSeq = 1;
        // 1. 生成 1 到 standardRooms 号房间（每间 bedsPerStandardRoom 张床）
        for (int r = 1; r <= standardRooms; r++) {
            String roomName = String.format("%02d号房间", r);
            for (int b = 1; b <= bedsPerStandardRoom; b++) {
                String bedNo = String.format("%d床", bedSeq++);
                createSingleBedIfNotExists(wardId, roomName, bedNo, bedSeq);
            }
        }

        // 2. 如果存在最后一间特殊床位数的房间（例如第13号房有2张床）
        if (lastRoomBeds > 0) {
            int lastRoomNum = standardRooms + 1;
            String roomName = String.format("%02d号房间", lastRoomNum);
            for (int b = 1; b <= lastRoomBeds; b++) {
                String bedNo = String.format("%d床", bedSeq++);
                createSingleBedIfNotExists(wardId, roomName, bedNo, bedSeq);
            }
        }
    }

    private void createSingleBedIfNotExists(Long wardId, String roomNo, String bedNo, int sortOrder) {
        LambdaQueryWrapper<Bed> check = new LambdaQueryWrapper<Bed>()
                .eq(Bed::getWardId, wardId)
                .eq(Bed::getBedNo, bedNo);
        if (count(check) == 0) {
            Bed bed = new Bed();
            bed.setWardId(wardId);
            bed.setRoomNo(roomNo);
            bed.setBedNo(bedNo);
            bed.setStatus(0);
            bed.setSortOrder(sortOrder);
            save(bed);
        }
    }
}
