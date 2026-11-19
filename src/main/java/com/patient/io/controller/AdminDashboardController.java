package com.patient.io.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.Result;
import com.patient.io.common.TenantContext;
import com.patient.io.entity.*;
import com.patient.io.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/admin/dashboard")
public class AdminDashboardController {

    @Autowired private HospitalMapper hospitalMapper;
    @Autowired private WardMapper wardMapper;
    @Autowired private BedMapper bedMapper;
    @Autowired private PatientMapper patientMapper;
    @Autowired private NurseMapper nurseMapper;
    @Autowired private IntakeRecordMapper intakeRecordMapper;
    @Autowired private OutputRecordMapper outputRecordMapper;
    @Autowired private DailySummaryMapper dailySummaryMapper;

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        Map<String, Object> data = new HashMap<>();

        // 若当前未选择医院（超管全景），统计医院总数；若选择医院，显示当前医院信息
        Long hospId = TenantContext.getHospitalId();
        data.put("currentHospitalId", hospId);
        data.put("hospitalCount", hospitalMapper.selectCount(new LambdaQueryWrapper<Hospital>().eq(Hospital::getStatus, 1)));

        // 下面全部由租户插件自动过滤当前医院
        Long wardCount = wardMapper.selectCount(new LambdaQueryWrapper<Ward>().eq(Ward::getStatus, 1));
        Long totalBeds = bedMapper.selectCount(null);
        Long occupiedBeds = bedMapper.selectCount(new LambdaQueryWrapper<Bed>().eq(Bed::getStatus, 1));
        Long inPatients = patientMapper.selectCount(new LambdaQueryWrapper<Patient>().eq(Patient::getStatus, 1));
        Long nurseCount = nurseMapper.selectCount(new LambdaQueryWrapper<Nurse>().eq(Nurse::getStatus, 1));

        data.put("wardCount", wardCount);
        data.put("bedCount", totalBeds);
        data.put("occupiedBedCount", occupiedBeds);
        data.put("inPatientCount", inPatients);
        data.put("nurseCount", nurseCount);

        // 床位使用率
        double bedUsageRate = 0.0;
        if (totalBeds != null && totalBeds > 0 && occupiedBeds != null) {
            bedUsageRate = BigDecimal.valueOf(occupiedBeds * 100.0 / totalBeds)
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
        data.put("bedUsageRate", bedUsageRate);

        // 查在院病患列表以提取更多指标
        List<Patient> currentInPatients = patientMapper.selectList(new LambdaQueryWrapper<Patient>().eq(Patient::getStatus, 1));
        LocalDate today = LocalDate.now();

        long routineCount = currentInPatients.stream().filter(p -> today.equals(p.getAdmissionDate()) && !"EMERGENCY".equalsIgnoreCase(p.getAdmissionType())).count();
        long emergencyCount = currentInPatients.stream().filter(p -> today.equals(p.getAdmissionDate()) && "EMERGENCY".equalsIgnoreCase(p.getAdmissionType())).count();
        long needIoCount = currentInPatients.stream().filter(p -> p.getNeedIoRecord() == null || p.getNeedIoRecord() == 1).count();
        long tubeCount = currentInPatients.stream().filter(p -> p.getDrainageTubes() != null && !p.getDrainageTubes().trim().isEmpty()).count();

        data.put("routineAdmissionCount", routineCount);
        data.put("emergencyAdmissionCount", emergencyCount);
        data.put("needIoPatientCount", needIoCount);
        data.put("tubePatientCount", tubeCount);

        // 今日出入量记录
        List<IntakeRecord> todayIntakes = intakeRecordMapper.selectList(new LambdaQueryWrapper<IntakeRecord>().eq(IntakeRecord::getRecordDate, today));
        List<OutputRecord> todayOutputs = outputRecordMapper.selectList(new LambdaQueryWrapper<OutputRecord>().eq(OutputRecord::getRecordDate, today));

        long todayIntakeCount = todayIntakes.size();
        long todayOutputCount = todayOutputs.size();
        data.put("todayIntakeRecordCount", todayIntakeCount);
        data.put("todayOutputRecordCount", todayOutputCount);
        data.put("todayTotalRecordCount", todayIntakeCount + todayOutputCount);

        // 今日入液量 & 出液量总量 (ml)
        BigDecimal todayIntakeTotalMl = todayIntakes.stream()
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal todayOutputTotalMl = todayOutputs.stream()
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal todayNetBalance = todayIntakeTotalMl.subtract(todayOutputTotalMl);

        data.put("todayIntakeTotalAmount", todayIntakeTotalMl.setScale(0, RoundingMode.HALF_UP).intValue());
        data.put("todayOutputTotalAmount", todayOutputTotalMl.setScale(0, RoundingMode.HALF_UP).intValue());
        data.put("todayNetBalance", todayNetBalance.setScale(0, RoundingMode.HALF_UP).intValue());

        // 今日入量类型分布
        Map<String, String> intakeNameMap = new LinkedHashMap<>();
        intakeNameMap.put("IV", "静脉输液");
        intakeNameMap.put("ORAL", "口服饮水/饮食");
        intakeNameMap.put("NASOGASTRIC", "鼻饲/管饲");
        intakeNameMap.put("OTHER", "其他入量");

        List<Map<String, Object>> intakeTypeStats = new ArrayList<>();
        for (Map.Entry<String, String> entry : intakeNameMap.entrySet()) {
            String key = entry.getKey();
            List<IntakeRecord> matched = todayIntakes.stream()
                    .filter(r -> key.equalsIgnoreCase(r.getIntakeType()) || (key.equals("OTHER") && (r.getIntakeType() == null || !intakeNameMap.containsKey(r.getIntakeType().toUpperCase()))))
                    .collect(Collectors.toList());
            BigDecimal amount = matched.stream().map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> item = new HashMap<>();
            item.put("type", key);
            item.put("name", entry.getValue());
            item.put("count", matched.size());
            item.put("amount", amount.setScale(0, RoundingMode.HALF_UP).intValue());
            intakeTypeStats.add(item);
        }
        data.put("intakeTypeStats", intakeTypeStats);

        // 今日出量类型分布
        Map<String, String> outputNameMap = new LinkedHashMap<>();
        outputNameMap.put("URINE", "尿液");
        outputNameMap.put("GASTRO_DRAINAGE", "胃肠减压");
        outputNameMap.put("CHEST_DRAINAGE", "胸腹腔引流");
        outputNameMap.put("STOOL", "大便/排泄");
        outputNameMap.put("OTHER_DRAINAGE", "其他引流/排出");

        List<Map<String, Object>> outputTypeStats = new ArrayList<>();
        for (Map.Entry<String, String> entry : outputNameMap.entrySet()) {
            String key = entry.getKey();
            List<OutputRecord> matched = todayOutputs.stream()
                    .filter(r -> key.equalsIgnoreCase(r.getOutputType()) || (key.equals("OTHER_DRAINAGE") && (r.getOutputType() == null || !outputNameMap.containsKey(r.getOutputType().toUpperCase()))))
                    .collect(Collectors.toList());
            BigDecimal amount = matched.stream().map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> item = new HashMap<>();
            item.put("type", key);
            item.put("name", entry.getValue());
            item.put("count", matched.size());
            item.put("amount", amount.setScale(0, RoundingMode.HALF_UP).intValue());
            outputTypeStats.add(item);
        }
        data.put("outputTypeStats", outputTypeStats);

        // 近7天出入量趋势
        DateTimeFormatter df = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> trend7Days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Long inCount = intakeRecordMapper.selectCount(new LambdaQueryWrapper<IntakeRecord>().eq(IntakeRecord::getRecordDate, d));
            Long outCount = outputRecordMapper.selectCount(new LambdaQueryWrapper<OutputRecord>().eq(OutputRecord::getRecordDate, d));

            Map<String, Object> point = new HashMap<>();
            point.put("date", d.format(df));
            point.put("fullDate", d.toString());
            point.put("intakeCount", inCount != null ? inCount : 0L);
            point.put("outputCount", outCount != null ? outCount : 0L);
            point.put("totalCount", (inCount != null ? inCount : 0L) + (outCount != null ? outCount : 0L));
            trend7Days.add(point);
        }
        data.put("trend7Days", trend7Days);

        // 各病区实时监测矩阵（只查已启用的病区 status = 1）
        List<Ward> wardList = wardMapper.selectList(new LambdaQueryWrapper<Ward>()
                .eq(Ward::getStatus, 1)
                .orderByAsc(Ward::getSortOrder)
                .orderByDesc(Ward::getId));
        List<Bed> allBeds = bedMapper.selectList(null);
        List<Map<String, Object>> wardStatsList = new ArrayList<>();
        for (Ward w : wardList) {
            long wTotalBeds = allBeds.stream().filter(b -> w.getId().equals(b.getWardId())).count();
            long wOccBeds = allBeds.stream().filter(b -> w.getId().equals(b.getWardId()) && Integer.valueOf(1).equals(b.getStatus())).count();
            long wPatients = currentInPatients.stream().filter(p -> w.getId().equals(p.getWardId())).count();
            long wNeedIo = currentInPatients.stream().filter(p -> w.getId().equals(p.getWardId()) && (p.getNeedIoRecord() == null || p.getNeedIoRecord() == 1)).count();
            long wInRecords = todayIntakes.stream().filter(r -> w.getId().equals(r.getWardId())).count();
            long wOutRecords = todayOutputs.stream().filter(r -> w.getId().equals(r.getWardId())).count();

            double rate = wTotalBeds > 0 ? BigDecimal.valueOf(wOccBeds * 100.0 / wTotalBeds).setScale(1, RoundingMode.HALF_UP).doubleValue() : 0.0;

            Map<String, Object> wMap = new HashMap<>();
            wMap.put("wardId", w.getId());
            wMap.put("wardName", w.getWardName());
            wMap.put("building", w.getBuilding() != null ? w.getBuilding() : "综合楼");
            wMap.put("totalBeds", wTotalBeds);
            wMap.put("occupiedBeds", wOccBeds);
            wMap.put("bedUsageRate", rate);
            wMap.put("patientCount", wPatients);
            wMap.put("needIoCount", wNeedIo);
            wMap.put("todayIntakeCount", wInRecords);
            wMap.put("todayOutputCount", wOutRecords);
            wMap.put("todayTotalRecords", wInRecords + wOutRecords);
            wardStatsList.add(wMap);
        }
        data.put("wardStatsList", wardStatsList);

        // 今日出入量失衡或异常预警患者
        List<DailySummary> abnormalSummaries = dailySummaryMapper.selectList(
                new LambdaQueryWrapper<DailySummary>()
                        .eq(DailySummary::getRecordDate, today)
                        .eq(DailySummary::getIsAbnormal, 1)
        );
        data.put("todayAbnormalCount", abnormalSummaries.size());

        Map<Long, Patient> patientMap = currentInPatients.stream().collect(Collectors.toMap(Patient::getId, p -> p, (k1, k2) -> k1));
        Map<Long, Ward> wardEntityMap = wardList.stream().collect(Collectors.toMap(Ward::getId, w -> w, (k1, k2) -> k1));

        List<Map<String, Object>> abnormalPatientList = new ArrayList<>();
        for (DailySummary s : abnormalSummaries) {
            Patient p = patientMap.get(s.getPatientId());
            Ward w = p != null && p.getWardId() != null ? wardEntityMap.get(p.getWardId()) : null;
            Map<String, Object> aItem = new HashMap<>();
            aItem.put("patientId", s.getPatientId());
            aItem.put("patientName", p != null ? p.getPatientName() : ("患者#" + s.getPatientId()));
            aItem.put("bedNo", p != null ? p.getBedNo() : "-");
            aItem.put("wardName", w != null ? w.getWardName() : "-");
            aItem.put("totalIntake", s.getTotalIntake());
            aItem.put("totalOutput", s.getTotalOutput());
            aItem.put("netBalance", s.getNetBalance());
            abnormalPatientList.add(aItem);
        }
        data.put("abnormalPatientList", abnormalPatientList);

        return Result.success(data);
    }

    /**
     * 护士长夜查房/交班统计数据（直击每日19:00上报痛点）
     */
    @GetMapping("/night-rounds-summary")
    public Result<Map<String, Object>> getNightRoundsSummary(@RequestParam(value = "wardId", required = false) Long wardId) {
        Map<String, Object> data = new HashMap<>();

        Ward targetWard = null;
        if (wardId != null) {
            targetWard = wardMapper.selectById(wardId);
        } else {
            List<Ward> wards = wardMapper.selectList(new LambdaQueryWrapper<Ward>()
                    .eq(Ward::getStatus, 1)
                    .orderByAsc(Ward::getSortOrder)
                    .last("limit 1"));
            if (!wards.isEmpty()) {
                targetWard = wards.get(0);
                wardId = targetWard.getId();
            }
        }

        if (targetWard == null) {
            data.put("building", "暂无病区");
            data.put("wardName", "暂无病区");
            return Result.success(data);
        }

        data.put("wardId", targetWard.getId());
        data.put("building", targetWard.getBuilding() != null ? targetWard.getBuilding() : "未设置楼宇");
        data.put("wardName", targetWard.getWardName());

        // 1. 查询当前在院病患
        LambdaQueryWrapper<Patient> pQuery = new LambdaQueryWrapper<Patient>()
                .eq(Patient::getWardId, wardId)
                .eq(Patient::getStatus, 1);
        List<Patient> inPatients = patientMapper.selectList(pQuery);

        long adultCount = inPatients.stream().filter(p -> !"INFANT".equalsIgnoreCase(p.getPatientType())).count();
        long infantCount = inPatients.stream().filter(p -> "INFANT".equalsIgnoreCase(p.getPatientType())).count();

        // 2. 今日平诊入院 vs 急诊入院 (根据 admissionDate == today)
        LocalDate today = LocalDate.now();
        long routineCount = inPatients.stream().filter(p -> today.equals(p.getAdmissionDate()) && !"EMERGENCY".equalsIgnoreCase(p.getAdmissionType())).count();
        long emergencyCount = inPatients.stream().filter(p -> today.equals(p.getAdmissionDate()) && "EMERGENCY".equalsIgnoreCase(p.getAdmissionType())).count();

        // 3. 今日出院人数 (status == 2 且 updateTime 在今日)
        LambdaQueryWrapper<Patient> dQuery = new LambdaQueryWrapper<Patient>()
                .eq(Patient::getWardId, wardId)
                .eq(Patient::getStatus, 2)
                .apply("DATE(update_time) = {0}", today);
        Long dischargeCount = patientMapper.selectCount(dQuery);

        // 4. 重点出入量监测人数
        long needIoCount = inPatients.stream().filter(p -> p.getNeedIoRecord() == null || p.getNeedIoRecord() == 1).count();

        // 5. 今日出入量异常人数 (DailySummary isAbnormal == 1)
        LambdaQueryWrapper<DailySummary> abQuery = new LambdaQueryWrapper<DailySummary>()
                .eq(DailySummary::getWardId, wardId)
                .eq(DailySummary::getRecordDate, today)
                .eq(DailySummary::getIsAbnormal, 1);
        Long abnormalCount = dailySummaryMapper.selectCount(abQuery);

        // 6. 留置管道统计
        long tubeCatheterCount = inPatients.stream().filter(p -> p.getDrainageTubes() != null && p.getDrainageTubes().contains("CATHER")).count();
        long tubeGastricCount = inPatients.stream().filter(p -> p.getDrainageTubes() != null && p.getDrainageTubes().contains("GASTRIC")).count();
        long tubeDrainageCount = inPatients.stream().filter(p -> p.getDrainageTubes() != null && (p.getDrainageTubes().contains("ABDOMINAL") || p.getDrainageTubes().contains("DRAINAGE"))).count();

        // 7. 护理级别统计
        long specialCareCount = inPatients.stream().filter(p -> "SPECIAL".equalsIgnoreCase(p.getNursingLevel())).count();
        long level1CareCount = inPatients.stream().filter(p -> "LEVEL_1".equalsIgnoreCase(p.getNursingLevel())).count();
        long level2CareCount = inPatients.stream().filter(p -> "LEVEL_2".equalsIgnoreCase(p.getNursingLevel())).count();
        long level3CareCount = inPatients.stream().filter(p -> "LEVEL_3".equalsIgnoreCase(p.getNursingLevel())).count();

        // 8. 床位情况
        Long totalBeds = bedMapper.selectCount(new LambdaQueryWrapper<Bed>().eq(Bed::getWardId, wardId));
        Long occupiedBeds = bedMapper.selectCount(new LambdaQueryWrapper<Bed>().eq(Bed::getWardId, wardId).eq(Bed::getStatus, 1));
        double bedUsageRate = 0.0;
        if (totalBeds != null && totalBeds > 0 && occupiedBeds != null) {
            bedUsageRate = BigDecimal.valueOf(occupiedBeds * 100.0 / totalBeds).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        // 9. 组装在床病患巡查与出入量明细
        List<IntakeRecord> wardIntakes = intakeRecordMapper.selectList(new LambdaQueryWrapper<IntakeRecord>()
                .eq(IntakeRecord::getWardId, wardId)
                .eq(IntakeRecord::getRecordDate, today));
        List<OutputRecord> wardOutputs = outputRecordMapper.selectList(new LambdaQueryWrapper<OutputRecord>()
                .eq(OutputRecord::getWardId, wardId)
                .eq(OutputRecord::getRecordDate, today));
        List<DailySummary> wardSummaries = dailySummaryMapper.selectList(new LambdaQueryWrapper<DailySummary>()
                .eq(DailySummary::getWardId, wardId)
                .eq(DailySummary::getRecordDate, today));

        Map<Long, BigDecimal> patientIntakeMap = wardIntakes.stream()
                .collect(Collectors.groupingBy(IntakeRecord::getPatientId,
                        Collectors.reducing(BigDecimal.ZERO, r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO, BigDecimal::add)));
        Map<Long, BigDecimal> patientOutputMap = wardOutputs.stream()
                .collect(Collectors.groupingBy(OutputRecord::getPatientId,
                        Collectors.reducing(BigDecimal.ZERO, r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO, BigDecimal::add)));
        Map<Long, DailySummary> summaryMap = wardSummaries.stream()
                .collect(Collectors.toMap(DailySummary::getPatientId, s -> s, (k1, k2) -> k1));

        List<Map<String, Object>> patientDetails = new ArrayList<>();
        List<Map<String, Object>> riskPatients = new ArrayList<>();

        for (Patient p : inPatients) {
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("id", p.getId());
            pMap.put("bedNo", p.getBedNo() != null ? p.getBedNo() : "-");
            pMap.put("patientName", p.getPatientName());
            pMap.put("gender", p.getGender());
            pMap.put("age", p.getAge());
            pMap.put("patientType", p.getPatientType());
            pMap.put("admissionType", p.getAdmissionType());
            pMap.put("nursingLevel", p.getNursingLevel());
            pMap.put("diagnosis", p.getDiagnosis() != null ? p.getDiagnosis() : "无明确诊断");
            pMap.put("needIoRecord", p.getNeedIoRecord() == null || p.getNeedIoRecord() == 1);
            pMap.put("drainageTubes", p.getDrainageTubes() != null ? p.getDrainageTubes() : "");

            BigDecimal inMl = patientIntakeMap.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal outMl = patientOutputMap.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal netMl = inMl.subtract(outMl);
            DailySummary s = summaryMap.get(p.getId());
            boolean isAbnormal = s != null && Integer.valueOf(1).equals(s.getIsAbnormal());

            pMap.put("todayIntakeMl", inMl.setScale(0, RoundingMode.HALF_UP).intValue());
            pMap.put("todayOutputMl", outMl.setScale(0, RoundingMode.HALF_UP).intValue());
            pMap.put("todayNetBalanceMl", netMl.setScale(0, RoundingMode.HALF_UP).intValue());
            pMap.put("isAbnormal", isAbnormal);

            patientDetails.add(pMap);

            // 风险患者判定：出入量异常、或特级/一级护理、或带管道、或急诊入院
            boolean hasTube = p.getDrainageTubes() != null && !p.getDrainageTubes().trim().isEmpty();
            boolean isSevereCare = "SPECIAL".equalsIgnoreCase(p.getNursingLevel()) || "LEVEL_1".equalsIgnoreCase(p.getNursingLevel());
            if (isAbnormal || hasTube || isSevereCare || "EMERGENCY".equalsIgnoreCase(p.getAdmissionType())) {
                List<String> tags = new ArrayList<>();
                if (isAbnormal) tags.add("出入量失衡");
                if (hasTube) tags.add("留置管道");
                if (isSevereCare) tags.add("重症护理");
                if ("EMERGENCY".equalsIgnoreCase(p.getAdmissionType())) tags.add("急诊入院");
                pMap.put("riskTags", tags);
                riskPatients.add(pMap);
            }
        }

        // 按床号排序
        patientDetails.sort((a, b) -> String.valueOf(a.get("bedNo")).compareTo(String.valueOf(b.get("bedNo"))));
        riskPatients.sort((a, b) -> String.valueOf(a.get("bedNo")).compareTo(String.valueOf(b.get("bedNo"))));

        data.put("totalAdultCount", adultCount);
        data.put("totalInfantCount", infantCount);
        data.put("totalPatientCount", inPatients.size());
        data.put("routineAdmissionCount", routineCount);
        data.put("emergencyAdmissionCount", emergencyCount);
        data.put("dischargeCount", dischargeCount);
        data.put("needIoCount", needIoCount);
        data.put("abnormalIoCount", abnormalCount);
        data.put("tubeCatheterCount", tubeCatheterCount);
        data.put("tubeGastricCount", tubeGastricCount);
        data.put("tubeDrainageCount", tubeDrainageCount);

        data.put("specialCareCount", specialCareCount);
        data.put("level1CareCount", level1CareCount);
        data.put("level2CareCount", level2CareCount);
        data.put("level3CareCount", level3CareCount);
        data.put("totalBeds", totalBeds != null ? totalBeds : 0L);
        data.put("occupiedBeds", occupiedBeds != null ? occupiedBeds : 0L);
        data.put("bedUsageRate", bedUsageRate);

        data.put("patientList", patientDetails);
        data.put("riskPatients", riskPatients);

        return Result.success(data);
    }
}
