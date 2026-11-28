package com.patient.io.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.BusinessException;
import com.patient.io.common.DictDefaults;
import com.patient.io.entity.SysDictItem;
import com.patient.io.entity.SysDictType;
import com.patient.io.mapper.SysDictItemMapper;
import com.patient.io.mapper.SysDictTypeMapper;
import com.patient.io.service.DictService;
import com.patient.io.vo.DictItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    /** 平台通用字典的 hospital_id 占位值 */
    public static final long PLATFORM = 0L;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictItemMapper dictItemMapper;

    @Override
    public List<SysDictType> listTypes(Long hospitalId, String keyword) {
        long hid = hospitalId == null ? PLATFORM : hospitalId;

        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysDictType::getHospitalId, PLATFORM, hid);
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysDictType::getDictCode, kw).or().like(SysDictType::getDictName, kw));
        }
        wrapper.orderByAsc(SysDictType::getDictCode);
        List<SysDictType> types = dictTypeMapper.selectList(wrapper);

        // 同一编码下若医院自建了字典，只展示本院那条，避免列表出现重复编码
        Map<String, SysDictType> deduped = new LinkedHashMap<>();
        for (SysDictType t : types) {
            SysDictType exists = deduped.get(t.getDictCode());
            if (exists == null) {
                deduped.put(t.getDictCode(), t);
            } else {
                boolean currentIsOwn = t.getHospitalId() != null && t.getHospitalId() != PLATFORM;
                boolean existsIsOwn = exists.getHospitalId() != null && exists.getHospitalId() != PLATFORM;
                if (currentIsOwn && !existsIsOwn) {
                    deduped.put(t.getDictCode(), t);
                }
            }
        }
        types = new ArrayList<>(deduped.values());

        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : dictTypeMapper.countItemsByCode(hid)) {
            Object code = row.get("dictCode");
            Object cnt = row.get("cnt");
            if (code != null) {
                counts.put(String.valueOf(code), cnt == null ? 0 : Integer.parseInt(String.valueOf(cnt)));
            }
        }
        for (SysDictType t : types) {
            t.setItemCount(counts.getOrDefault(t.getDictCode(), 0));
            t.setBuiltin(t.getHospitalId() != null && t.getHospitalId() == PLATFORM);
        }
        return types;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysDictType saveType(Long hospitalId, SysDictType type) {
        String code = StrUtil.trimToEmpty(type.getDictCode());
        String name = StrUtil.trimToEmpty(type.getDictName());
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException("字典编码只能用小写字母、数字、下划线，且以字母开头");
        }
        if (StrUtil.isEmpty(name)) {
            throw new BusinessException("字典名称不能为空");
        }
        // 归属由服务端判定，忽略前端传入的 hospitalId，避免越权写其它租户
        long hid = defaultHospital(hospitalId);

        if (type.getId() != null) {
            SysDictType exists = dictTypeMapper.selectById(type.getId());
            if (exists == null) {
                throw new BusinessException("字典不存在或已删除");
            }
            assertWritable(exists.getHospitalId(), hospitalId);
            exists.setDictName(name);
            exists.setRemark(type.getRemark());
            if (type.getStatus() != null) {
                exists.setStatus(type.getStatus());
            }
            // 编码是程序引用依据，不允许修改
            dictTypeMapper.updateById(exists);
            return exists;
        }

        // 同一归属下编码唯一
        LambdaQueryWrapper<SysDictType> dup = new LambdaQueryWrapper<>();
        dup.eq(SysDictType::getHospitalId, hid).eq(SysDictType::getDictCode, code);
        if (dictTypeMapper.selectCount(dup) > 0) {
            throw new BusinessException("字典编码已存在：" + code);
        }

        type.setDictCode(code);
        type.setDictName(name);
        type.setHospitalId(hid);
        type.setStatus(type.getStatus() == null ? 1 : type.getStatus());
        dictTypeMapper.insert(type);
        return type;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long hospitalId, Long id) {
        SysDictType exists = dictTypeMapper.selectById(id);
        if (exists == null) {
            return;
        }
        assertWritable(exists.getHospitalId(), hospitalId);
        LambdaQueryWrapper<SysDictItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(SysDictItem::getDictCode, exists.getDictCode())
                .eq(SysDictItem::getHospitalId, exists.getHospitalId());
        dictItemMapper.delete(itemWrapper);
        dictTypeMapper.deleteById(id);
    }

    @Override
    public List<SysDictItem> listItems(Long hospitalId, String dictCode, boolean includeDisabled) {
        if (StrUtil.isBlank(dictCode)) {
            throw new BusinessException("字典编码不能为空");
        }
        long hid = hospitalId == null ? PLATFORM : hospitalId;
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictItem::getDictCode, dictCode)
                .in(SysDictItem::getHospitalId, PLATFORM, hid);
        if (!includeDisabled) {
            wrapper.eq(SysDictItem::getStatus, 1);
        }
        wrapper.orderByAsc(SysDictItem::getSortOrder).orderByAsc(SysDictItem::getId);
        return mergeItems(dictItemMapper.selectList(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItem(Long hospitalId, SysDictItem item) {
        String code = StrUtil.trimToEmpty(item.getDictCode());
        String value = StrUtil.trimToEmpty(item.getItemValue());
        String label = StrUtil.trimToEmpty(item.getItemLabel());
        if (StrUtil.isEmpty(code) || StrUtil.isEmpty(value)) {
            throw new BusinessException("字典编码和存储值不能为空");
        }
        if (StrUtil.isEmpty(label)) {
            label = value;
        }

        if (item.getId() != null) {
            SysDictItem exists = dictItemMapper.selectById(item.getId());
            if (exists == null) {
                throw new BusinessException("字典项不存在或已删除");
            }
            assertWritable(exists.getHospitalId(), hospitalId);
            if (!value.equals(exists.getItemValue())) {
                assertValueUnique(exists.getHospitalId(), exists.getDictCode(), value, exists.getId());
            }
            exists.setItemValue(value);
            exists.setItemLabel(label);
            exists.setExtra(item.getExtra());
            exists.setSortOrder(item.getSortOrder() == null ? exists.getSortOrder() : item.getSortOrder());
            exists.setStatus(item.getStatus() == null ? exists.getStatus() : item.getStatus());
            dictItemMapper.updateById(exists);
            return;
        }

        long hid = defaultHospital(hospitalId);
        assertTypeExists(hid, code);
        assertValueUnique(hid, code, value, null);

        item.setDictCode(code);
        item.setItemValue(value);
        item.setItemLabel(label);
        item.setHospitalId(hid);
        item.setStatus(item.getStatus() == null ? 1 : item.getStatus());
        item.setSortOrder(item.getSortOrder() == null ? 99 : item.getSortOrder());
        dictItemMapper.insert(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long hospitalId, Long id) {
        SysDictItem exists = dictItemMapper.selectById(id);
        if (exists == null) {
            return;
        }
        assertWritable(exists.getHospitalId(), hospitalId);
        dictItemMapper.deleteById(id);
    }

    @Override
    public void updateItemStatus(Long hospitalId, Long id, Integer status) {
        SysDictItem exists = dictItemMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException("字典项不存在或已删除");
        }
        assertWritable(exists.getHospitalId(), hospitalId);
        SysDictItem update = new SysDictItem();
        update.setId(id);
        update.setStatus(status == null ? 1 : status);
        dictItemMapper.updateById(update);
    }

    @Override
    public Map<String, List<DictItemVO>> batchItems(Long hospitalId, List<String> codes) {
        Map<String, List<DictItemVO>> result = new LinkedHashMap<>();
        if (codes == null || codes.isEmpty()) {
            return result;
        }
        long hid = hospitalId == null ? PLATFORM : hospitalId;

        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysDictItem::getDictCode, codes)
                .in(SysDictItem::getHospitalId, PLATFORM, hid)
                .eq(SysDictItem::getStatus, 1);
        wrapper.orderByAsc(SysDictItem::getSortOrder).orderByAsc(SysDictItem::getId);

        Map<String, List<SysDictItem>> grouped = new HashMap<>();
        for (SysDictItem item : mergeItems(dictItemMapper.selectList(wrapper))) {
            grouped.computeIfAbsent(item.getDictCode(), k -> new ArrayList<>()).add(item);
        }
        for (String code : codes) {
            List<SysDictItem> items = grouped.get(code);
            List<DictItemVO> vos = new ArrayList<>();
            if (items != null) {
                for (SysDictItem it : items) {
                    vos.add(DictItemVO.builder()
                            .value(it.getItemValue())
                            .label(it.getItemLabel())
                            .extra(it.getExtra())
                            .sortOrder(it.getSortOrder())
                            .build());
                }
            }
            result.put(code, vos);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int initDefaults(Long hospitalId) {
        // 平台内置字典写入口：超管写入全局(0)，医院管理员写入本院，避免跨租户改配置
        long target = isSuperAdmin() ? PLATFORM : defaultHospital(hospitalId);
        int added = 0;

        LambdaQueryWrapper<SysDictType> typeWrapper = new LambdaQueryWrapper<>();
        typeWrapper.eq(SysDictType::getHospitalId, target);
        List<SysDictType> existingTypes = dictTypeMapper.selectList(typeWrapper);
        Map<String, SysDictType> typeMap = new HashMap<>();
        for (SysDictType t : existingTypes) {
            typeMap.put(t.getDictCode(), t);
        }

        for (DictDefaults.TypeDef def : DictDefaults.ALL) {
            if (!typeMap.containsKey(def.code)) {
                SysDictType type = new SysDictType();
                type.setHospitalId(target);
                type.setDictCode(def.code);
                type.setDictName(def.name);
                type.setRemark(def.remark);
                type.setStatus(1);
                dictTypeMapper.insert(type);
                typeMap.put(def.code, type);
                added++;
            }

            LambdaQueryWrapper<SysDictItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(SysDictItem::getDictCode, def.code).eq(SysDictItem::getHospitalId, target);
            List<SysDictItem> existingItems = dictItemMapper.selectList(itemWrapper);
            Map<String, SysDictItem> valueMap = new HashMap<>();
            for (SysDictItem it : existingItems) {
                valueMap.put(it.getItemValue(), it);
            }

            int sort = 1;
            for (DictDefaults.Item defItem : def.items) {
                if (!valueMap.containsKey(defItem.value)) {
                    SysDictItem item = new SysDictItem();
                    item.setHospitalId(target);
                    item.setDictCode(def.code);
                    item.setItemValue(defItem.value);
                    item.setItemLabel(defItem.label);
                    item.setSortOrder(sort);
                    item.setStatus(1);
                    dictItemMapper.insert(item);
                    added++;
                }
                sort++;
            }
        }
        log.info("内置字典初始化完成，目标归属={}，新增 {} 条", target, added);
        return added;
    }

    private boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(com.patient.io.common.UserContext.getRole());
    }

    private long defaultHospital(Long hospitalId) {
        if (hospitalId != null && hospitalId > 0) {
            return hospitalId;
        }
        return PLATFORM;
    }

    /**
     * 平台内置字典不允许医院侧直接改，避免影响其它租户
     */
    private void assertWritable(Long ownerHospitalId, Long currentHospitalId) {
        boolean platformRow = ownerHospitalId != null && ownerHospitalId == PLATFORM;
        if (platformRow && !isSuperAdmin()) {
            throw new BusinessException("平台内置字典不可直接修改，请复制为院内字典后再调整");
        }
        if (!platformRow && currentHospitalId != null && currentHospitalId > 0
                && !currentHospitalId.equals(ownerHospitalId)) {
            throw new BusinessException("无权修改其它医院的字典");
        }
    }

    private void assertTypeExists(long hospitalId, String code) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictType::getDictCode, code).in(SysDictType::getHospitalId, PLATFORM, hospitalId);
        if (dictTypeMapper.selectCount(wrapper) == 0) {
            throw new BusinessException("字典编码不存在：" + code);
        }
    }

    private void assertValueUnique(long hospitalId, String code, String value, Long excludeId) {
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictItem::getDictCode, code)
                .eq(SysDictItem::getHospitalId, hospitalId)
                .eq(SysDictItem::getItemValue, value);
        if (excludeId != null) {
            wrapper.ne(SysDictItem::getId, excludeId);
        }
        if (dictItemMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该字典下已存在相同存储值：" + value);
        }
    }

    /**
     * 合并平台项与本院项：同 value 时本院覆盖平台，排序保持字典定义顺序
     */
    private List<SysDictItem> mergeItems(List<SysDictItem> rows) {
        Map<String, SysDictItem> merged = new LinkedHashMap<>();
        List<SysDictItem> sorted = new ArrayList<>(rows);
        // 本院项优先：先按 hospitalId 升序（0 在前），本院项后写覆盖平台项
        Collections.sort(sorted, (a, b) -> {
            long ha = a.getHospitalId() == null ? PLATFORM : a.getHospitalId();
            long hb = b.getHospitalId() == null ? PLATFORM : b.getHospitalId();
            return Long.compare(ha, hb);
        });
        for (SysDictItem item : sorted) {
            merged.put(item.getDictCode() + "|" + item.getItemValue(), item);
        }
        List<SysDictItem> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> {
            int sa = a.getSortOrder() == null ? 99 : a.getSortOrder();
            int sb = b.getSortOrder() == null ? 99 : b.getSortOrder();
            if (sa != sb) {
                return Integer.compare(sa, sb);
            }
            long ia = a.getId() == null ? 0 : a.getId();
            long ib = b.getId() == null ? 0 : b.getId();
            return Long.compare(ia, ib);
        });
        return result;
    }
}
