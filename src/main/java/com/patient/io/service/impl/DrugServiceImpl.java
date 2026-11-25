package com.patient.io.service.impl;

import cn.hutool.core.util.StrUtil;
import com.patient.io.common.BusinessException;
import com.patient.io.dto.DrugSaveDTO;
import com.patient.io.entity.DrugDict;
import com.patient.io.mapper.DrugDictMapper;
import com.patient.io.service.DrugService;
import com.patient.io.util.PinyinUtil;
import com.patient.io.vo.DrugMergeGroupVO;
import com.patient.io.vo.DrugMergePreviewVO;
import com.patient.io.vo.DrugPageVO;
import com.patient.io.vo.DrugVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugServiceImpl implements DrugService {

    /** 高警示药品关键词，命中即标记并要求二次确认 */
    private static final List<String> HIGH_ALERT_KEYWORDS = Arrays.asList(
            "氯化钾", "胰岛素", "肝素", "化疗", "顺铂", "长春新碱", "甲氨蝶呤", "多巴酚丁胺",
            "去甲肾上腺素", "肾上腺素", "多巴胺", "胺碘酮", "氯化钙", "高渗", "50%葡萄糖", "10%氯化钠");

    private static final Pattern VOLUME_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:ml|毫升)", Pattern.CASE_INSENSITIVE);

    /** 规格归一用：去掉所有空白（含全角空格） */
    private static final Pattern SPEC_SPACES = Pattern.compile("[\\s\\u00A0\\u3000]+");

    private final DrugDictMapper drugDictMapper;

    @Override
    public List<DrugVO> search(Long hospitalId, String keyword, String route, int limit) {
        Long hid = hospitalId == null ? 1L : hospitalId;
        int size = limit <= 0 || limit > 30 ? 10 : limit;
        String kw = StrUtil.trimToEmpty(keyword);

        if (StrUtil.isEmpty(kw)) {
            return toVOs(drugDictMapper.listHot(hid, size));
        }

        String escaped = escapeLike(kw);
        // ü 在拼音里存为 u，习惯打 lv 时应能召回 luhuana，这里做一次等价归一
        String lower = kw.toLowerCase();
        if (lower.matches("[a-z]{2,20}") && lower.contains("v")) {
            lower = lower.replace("v", "u");
        }
        String escapedLower = escapeLike(lower);
        List<DrugDict> local = drugDictMapper.search(
                hid,
                StrUtil.trimToNull(route),
                kw,
                lower,
                "%" + escaped + "%",
                "%" + escapedLower + "%",
                escapedLower + "%",
                escaped + "%",
                size);
        return toVOs(local);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrugVO save(Long hospitalId, DrugSaveDTO dto) {
        Long hid = hospitalId == null ? 1L : hospitalId;
        String name = StrUtil.trimToEmpty(dto.getGenericName());
        if (StrUtil.isEmpty(name)) {
            throw new BusinessException("药品名称不能为空");
        }
        String spec = StrUtil.trimToEmpty(dto.getSpec());

        DrugDict target;
        if (dto.getId() != null) {
            target = drugDictMapper.selectById(dto.getId());
            if (target == null || !hid.equals(target.getHospitalId())) {
                throw new BusinessException("药品不存在或已删除");
            }
        } else {
            // 同通用名同规格视为同一条，避免个人重复添加
            DrugDict same = drugDictMapper.findSame(hid, name, spec);
            if (same != null) {
                if (!Integer.valueOf(1).equals(same.getStatus())) {
                    same.setStatus(1);
                    drugDictMapper.updateById(same);
                }
                return toVO(same);
            }
            target = new DrugDict();
            target.setHospitalId(hid);
            target.setSrcType(0);
            target.setUseCount(0);
            target.setSortOrder(100);
            target.setSrcId("");
        }

        target.setGenericName(name);
        target.setSpec(spec);
        if (dto.getAliasNames() != null) {
            target.setAliasNames(StrUtil.trimToEmpty(dto.getAliasNames()));
        }
        if (dto.getTradeName() != null) {
            target.setTradeName(StrUtil.trimToEmpty(dto.getTradeName()));
        }
        if (dto.getUnit() != null) {
            target.setUnit(StrUtil.trimToEmpty(dto.getUnit()));
        }
        if (dto.getDosageForm() != null) {
            target.setDosageForm(StrUtil.trimToEmpty(dto.getDosageForm()));
        }
        if (dto.getDefaultDose() != null) {
            target.setDefaultDose(StrUtil.trimToEmpty(dto.getDefaultDose()));
        }
        if (dto.getRoute() != null) {
            target.setRoute(StrUtil.trimToEmpty(dto.getRoute()));
        }
        // 名称/别名变了，拼音必须重算，否则检索会漏
        String source = name
                + StrUtil.blankToDefault(target.getTradeName(), "")
                + StrUtil.blankToDefault(target.getAliasNames(), "");
        target.setPinyinFull(PinyinUtil.full(source));
        target.setPinyinInitial(PinyinUtil.initials(source));
        target.setSpecVolume(parseVolume(spec));
        target.setIsHighAlert(dto.getIsHighAlert() != null
                ? dto.getIsHighAlert()
                : (isHighAlert(name) ? 1 : 0));
        // 新增或曾待确认的，保存后视为已确认；已停用的保持停用，不乱改状态
        if (target.getId() == null || Integer.valueOf(0).equals(target.getStatus())) {
            target.setStatus(1);
        }

        if (target.getId() == null) {
            drugDictMapper.insert(target);
        } else {
            drugDictMapper.updateById(target);
        }
        return toVO(target);
    }

    @Override
    public void remove(Long hospitalId, Long id) {
        Long hid = hospitalId == null ? 1L : hospitalId;
        DrugDict dict = drugDictMapper.selectById(id);
        if (dict == null || !hid.equals(dict.getHospitalId())) {
            throw new BusinessException("药品不存在或已删除");
        }
        // 逻辑删除，历史补液记录已存药名快照，不受影响
        drugDictMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long hospitalId, Long id, Integer status) {
        Long hid = hospitalId == null ? 1L : hospitalId;
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException("状态值不合法");
        }
        DrugDict dict = drugDictMapper.selectById(id);
        if (dict == null || !hid.equals(dict.getHospitalId())) {
            throw new BusinessException("药品不存在或已删除");
        }
        drugDictMapper.updateStatus(id, status);
    }

    @Override
    public DrugPageVO library(Long hospitalId, String keyword, Integer status, int page, int size) {
        Long hid = hospitalId == null ? 1L : hospitalId;
        int pageSize = size <= 0 || size > 100 ? 20 : size;
        int current = page <= 0 ? 1 : page;
        String kw = StrUtil.trimToEmpty(keyword);
        String likeKw = "%" + escapeLike(kw) + "%";
        // 只接受 0/1/2，其它值视为不过滤，避免前端传空串时查出空列表
        Integer st = (status != null && (status == 0 || status == 1 || status == 2)) ? status : null;

        DrugPageVO vo = new DrugPageVO();
        vo.setTotal(drugDictMapper.countLibrary(hid, kw, likeKw, st));
        List<DrugDict> rows = drugDictMapper.listLibrary(hid, kw, likeKw, st, pageSize, (current - 1) * pageSize);
        vo.setList(toVOs(rows));
        return vo;
    }

    @Override
    public java.util.Map<String, Object> stats(Long hospitalId) {        Long hid = hospitalId == null ? 1L : hospitalId;
        java.util.Map<String, Object> raw = drugDictMapper.stats(hid);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", num(raw, "total"));
        result.put("activeCount", num(raw, "activeCount"));
        result.put("pendingCount", num(raw, "pendingCount"));
        result.put("disabledCount", num(raw, "disabledCount"));
        result.put("alertCount", num(raw, "alertCount"));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateStatus(Long hospitalId, List<Long> ids, Integer status) {
        Long hid = hospitalId == null ? 1L : hospitalId;
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException("状态值不合法");
        }
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 去重并限制单次数量，避免拼出超长 IN
        List<Long> clean = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && !clean.contains(id) && clean.size() < 500) {
                clean.add(id);
            }
        }
        if (clean.isEmpty()) {
            return 0;
        }
        return drugDictMapper.batchUpdateStatus(hid, clean, status);
    }

    private int num(java.util.Map<String, Object> raw, String key) {
        if (raw == null) {
            return 0;
        }
        Object v = raw.get(key);
        if (v == null) {
            // MyBatis 可能把列名大小写改写，兜底再找一次
            for (java.util.Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getKey().equalsIgnoreCase(key) && e.getValue() != null) {
                    v = e.getValue();
                    break;
                }
            }
        }
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importBatch(Long hospitalId, List<DrugDict> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        Long hid = hospitalId == null ? 1L : hospitalId;
        int count = 0;
        for (DrugDict item : list) {
            if (StrUtil.isBlank(item.getGenericName())) {
                continue;
            }
            item.setId(null);
            if (item.getHospitalId() == null) {
                item.setHospitalId(hid);
            }
            fillDerived(item);
            drugDictMapper.insert(item);
            count++;
        }
        return count;
    }

    @Override
    public int backfillPinyin() {
        List<DrugDict> list = drugDictMapper.listMissingPinyin();
        int count = 0;
        for (DrugDict d : list) {
            String source = StrUtil.blankToDefault(d.getGenericName(), "")
                    + StrUtil.blankToDefault(d.getTradeName(), "")
                    + StrUtil.blankToDefault(d.getAliasNames(), "");
            if (StrUtil.isBlank(source)) {
                continue;
            }
            drugDictMapper.updatePinyin(d.getId(), PinyinUtil.full(source), PinyinUtil.initials(source));
            count++;
        }
        return count;
    }

    @Override
    public DrugMergePreviewVO previewMerge(Long hospitalId) {
        List<DrugMergeGroupVO> groups = buildMergeGroups(hospitalId);
        int duplicateCount = 0;
        for (DrugMergeGroupVO g : groups) {
            duplicateCount += g.getDuplicates().size();
        }
        DrugMergePreviewVO vo = new DrugMergePreviewVO();
        vo.setGroups(groups);
        vo.setGroupCount(groups.size());
        vo.setDuplicateCount(duplicateCount);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int mergeDuplicates(Long hospitalId) {
        Long hid = hospitalId == null ? 1L : hospitalId;
        List<DrugMergeGroupVO> groups = buildMergeGroups(hid);
        List<Long> toDisable = new ArrayList<>();
        for (DrugMergeGroupVO g : groups) {
            int extraUse = 0;
            for (DrugVO dup : g.getDuplicates()) {
                if (dup.getId() != null) {
                    toDisable.add(dup.getId());
                }
                extraUse += dup.getUseCount() == null ? 0 : dup.getUseCount();
            }
            // 同一味药的使用次数合并到保留项，排序仍反映真实使用频次
            if (extraUse > 0 && g.getKeep() != null && g.getKeep().getId() != null) {
                drugDictMapper.addUseCount(g.getKeep().getId(), extraUse);
            }
        }
        if (toDisable.isEmpty()) {
            return 0;
        }
        // 只停用不删除：护士端立刻看不到，需要时还能在管理端重新启用
        int disabled = 0;
        for (int i = 0; i < toDisable.size(); i += 500) {
            List<Long> chunk = toDisable.subList(i, Math.min(toDisable.size(), i + 500));
            disabled += drugDictMapper.batchUpdateStatus(hid, chunk, 2);
        }
        log.info("药品库归并：停用重复项 {} 条，涉及 {} 组", disabled, groups.size());
        return disabled;
    }

    /**
     * 按「通用名 + 归一化规格」分组，只保留每组排序第一的条目
     */
    private List<DrugMergeGroupVO> buildMergeGroups(Long hospitalId) {
        Long hid = hospitalId == null ? 1L : hospitalId;
        List<DrugDict> all = drugDictMapper.listForMerge(hid);
        java.util.Map<String, List<DrugDict>> buckets = new java.util.LinkedHashMap<>();
        for (DrugDict d : all) {
            String name = normName(d.getGenericName());
            if (StrUtil.isEmpty(name)) {
                continue;
            }
            // 已停用的不参与归并：护士端本来就看不到，再次停用没有意义
            if (Integer.valueOf(2).equals(d.getStatus())) {
                continue;
            }
            String key = name + "||" + normSpec(d.getSpec());
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        List<DrugMergeGroupVO> groups = new ArrayList<>();
        for (List<DrugDict> bucket : buckets.values()) {
            if (bucket.size() < 2) {
                continue;
            }
            bucket.sort(MERGE_PRIORITY);

            DrugMergeGroupVO g = new DrugMergeGroupVO();
            g.setKeep(toVO(bucket.get(0)));
            g.setGenericName(bucket.get(0).getGenericName());
            g.setSpec(bucket.get(0).getSpec());
            g.setNormSpec(normSpec(bucket.get(0).getSpec()));
            List<DrugVO> dups = new ArrayList<>();
            int totalUse = 0;
            for (int i = 1; i < bucket.size(); i++) {
                dups.add(toVO(bucket.get(i)));
                totalUse += bucket.get(i).getUseCount() == null ? 0 : bucket.get(i).getUseCount();
            }
            totalUse += bucket.get(0).getUseCount() == null ? 0 : bucket.get(0).getUseCount();
            g.setDuplicates(dups);
            g.setTotalUseCount(totalUse);
            groups.add(g);
        }
        // 重复越多越先处理
        groups.sort((a, b) -> b.getDuplicates().size() - a.getDuplicates().size());
        return groups;
    }

    /**
     * 规格归一：全角转半角、去空格、统一大小写与微克符号、修复 OCR 噪声（2m1/ml、l0ml/10ml）、
     * 中文单位转英文、克折算为毫克、去掉多余的 0 和尾部标点。
     * <p>只用于判断"是不是同一个规格"，不改写库里原文。</p>
     */
    private String normSpec(String spec) {
        if (StrUtil.isBlank(spec)) {
            return "";
        }
        String s = spec.trim()
                .replace('（', '(').replace('）', ')')
                .replace('：', ':').replace('∶', ':')
                .replace('，', ',').replace('、', ',')
                .replace('×', '*').replace('％', '%')
                .replace('．', '.').replace('。', '.')
                .replace('µ', 'u').replace('μ', 'u').replace('Μ', 'u');
        s = SPEC_SPACES.matcher(s).replaceAll("").toLowerCase();

        // 括号里是附加说明而非规格本身（如"（附专用溶媒10ml）""（静注）"），去掉后再比较
        s = s.replaceAll("\\((?:附[^)]*|静注|静滴|静脉[^)]*)\\)$", "");

        // 源数据 OCR：字母 l 被当成 1、数字 1 被当成 l
        s = s.replaceAll("m[l1]", "ml");
        s = s.replaceAll("(?<![a-z])l(?=\\d)", "1");

        // 中文单位统一成英文，20毫克 与 20mg 应视为同一规格
        s = s.replace("毫克", "mg").replace("毫升", "ml").replace("微克", "ug");

        s = s.replaceAll("[,;*]+$", "");
        // 10.0ml -> 10ml；4.50g -> 4.5g
        s = s.replaceAll("(?<=\\d)\\.0+(?=\\D|$)", "");
        s = s.replaceAll("(\\d+\\.\\d*?)0+(?=\\D|$)", "$1");
        // 克折算成毫克：0.15g 与 150mg 是同一剂量
        s = gramToMilligram(s);
        return s;
    }

    /** 把 "0.15g" 这类克剂量折算成毫克，避免同一剂量写成两种单位被当成两条 */
    private String gramToMilligram(String s) {
        java.util.regex.Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)g").matcher(s);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (m.find()) {
            found = true;
            java.math.BigDecimal mg = new java.math.BigDecimal(m.group(1))
                    .multiply(new java.math.BigDecimal("1000"));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                    mg.stripTrailingZeros().toPlainString() + "mg"));
        }
        if (!found) {
            return s;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 名称归一：去掉空格、全角括号转半角、统一小写，仅用于分组 */
    private String normName(String name) {
        if (StrUtil.isBlank(name)) {
            return "";
        }
        String s = name.trim()
                .replace('（', '(').replace('）', ')')
                .replace('　', ' ');
        s = SPEC_SPACES.matcher(s).replaceAll("").toLowerCase();
        return s;
    }

    /** 保留优先级：已启用 > 待确认 > 停用，其次书写更干净（无 OCR 噪声/中文单位），再次使用次数高者，最后取先建的那条 */
    private static final java.util.Comparator<DrugDict> MERGE_PRIORITY = (a, b) -> {
        int ra = statusRank(a.getStatus());
        int rb = statusRank(b.getStatus());
        if (ra != rb) {
            return ra - rb;
        }
        int da = specDirtyScore(a.getSpec());
        int db = specDirtyScore(b.getSpec());
        if (da != db) {
            return da - db;
        }
        int ua = a.getUseCount() == null ? 0 : a.getUseCount();
        int ub = b.getUseCount() == null ? 0 : b.getUseCount();
        if (ua != ub) {
            return ub - ua;
        }
        return Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId());
    };

    /** 规格书写脏度：含 OCR 噪声或中文单位记为 1，归并时优先保留干净的那条 */
    private static int specDirtyScore(String spec) {
        if (spec == null || spec.isEmpty()) {
            return 0;
        }
        String s = spec.toLowerCase();
        if (s.contains("m1") || s.contains("毫克") || s.contains("毫升") || s.contains("微克")
                || s.matches(".*(?<![a-z])l\\d.*")) {
            return 1;
        }
        return 0;
    }

    private static int statusRank(Integer status) {
        int s = status == null ? 1 : status;
        return s == 1 ? 0 : (s == 0 ? 1 : 2);
    }

    private List<DrugVO> toVOs(List<DrugDict> list) {
        List<DrugVO> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        for (DrugDict d : list) {
            result.add(toVO(d));
        }
        return result;
    }

    private DrugVO toVO(DrugDict d) {
        DrugVO vo = new DrugVO();
        vo.setId(d.getId());
        vo.setGenericName(d.getGenericName());
        vo.setTradeName(d.getTradeName());
        vo.setAliasNames(d.getAliasNames());
        vo.setSpec(d.getSpec());
        vo.setDosageForm(d.getDosageForm());
        vo.setUnit(d.getUnit());
        vo.setDefaultDose(d.getDefaultDose());
        vo.setRoute(d.getRoute());
        vo.setPinyinInitial(d.getPinyinInitial());
        vo.setStatus(d.getStatus() == null ? 1 : d.getStatus());
        vo.setPending(Integer.valueOf(0).equals(d.getStatus()));
        vo.setUseCount(d.getUseCount() == null ? 0 : d.getUseCount());
        int flag = (d.getIsHighAlert() != null && d.getIsHighAlert() == 1) || isHighAlert(d.getGenericName()) ? 1 : 0;
        vo.setHighAlert(flag);
        return vo;
    }

    private void fillDerived(DrugDict d) {
        if (StrUtil.isBlank(d.getPinyinInitial())) {
            String source = StrUtil.blankToDefault(d.getGenericName(), "")
                    + StrUtil.blankToDefault(d.getTradeName(), "")
                    + StrUtil.blankToDefault(d.getAliasNames(), "");
            d.setPinyinFull(PinyinUtil.full(source));
            d.setPinyinInitial(PinyinUtil.initials(source));
        }
        if (d.getSpecVolume() == null) {
            d.setSpecVolume(parseVolume(d.getSpec()));
        }
        if (d.getIsHighAlert() == null) {
            d.setIsHighAlert(isHighAlert(d.getGenericName()) ? 1 : 0);
        }
        if (d.getStatus() == null) {
            d.setStatus(1);
        }
        if (d.getUseCount() == null) {
            d.setUseCount(0);
        }
        if (d.getSrcType() == null) {
            d.setSrcType(0);
        }
    }

    private Integer parseVolume(String spec) {
        Matcher m = VOLUME_PATTERN.matcher(StrUtil.blankToDefault(spec, ""));
        if (m.find()) {
            return (int) Math.round(Double.parseDouble(m.group(1)));
        }
        return null;
    }

    private boolean isHighAlert(String name) {
        if (StrUtil.isBlank(name)) {
            return false;
        }
        for (String kw : HIGH_ALERT_KEYWORDS) {
            if (name.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
