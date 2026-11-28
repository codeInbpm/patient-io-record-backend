package com.patient.io.service;

import com.patient.io.entity.SysDictItem;
import com.patient.io.entity.SysDictType;
import com.patient.io.vo.DictItemVO;

import java.util.List;
import java.util.Map;

public interface DictService {

    /**
     * 字典类型列表（平台通用 + 本院），带字典项数量
     */
    List<SysDictType> listTypes(Long hospitalId, String keyword);

    SysDictType saveType(Long hospitalId, SysDictType type);

    void deleteType(Long hospitalId, Long id);

    /**
     * 字典项列表：平台项 + 本院项合并，本院同值覆盖平台项
     *
     * @param includeDisabled true 表示维护页使用，含停用项
     */
    List<SysDictItem> listItems(Long hospitalId, String dictCode, boolean includeDisabled);

    void saveItem(Long hospitalId, SysDictItem item);

    void deleteItem(Long hospitalId, Long id);

    void updateItemStatus(Long hospitalId, Long id, Integer status);

    /**
     * 批量取下拉数据，键为字典编码，只返回启用项
     */
    Map<String, List<DictItemVO>> batchItems(Long hospitalId, List<String> codes);

    /**
     * 补齐缺失的内置字典与字典项，返回新增条数
     */
    int initDefaults(Long hospitalId);
}
