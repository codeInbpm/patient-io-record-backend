package com.patient.io.service;

import com.patient.io.dto.DrugSaveDTO;
import com.patient.io.entity.DrugDict;
import com.patient.io.vo.DrugPageVO;
import com.patient.io.vo.DrugVO;

import java.util.List;

public interface DrugService {

    /**
     * 补液用药检索：中文名 / 拼音全拼 / 首字母，纯本地字典，无外部依赖
     *
     * @param keyword 关键词，空则返回常用药
     * @param route   给药途径过滤，可为空
     */
    List<DrugVO> search(Long hospitalId, String keyword, String route, int limit);

    /** 新增或编辑个人药品库条目，保存后即为已确认状态 */
    DrugVO save(Long hospitalId, DrugSaveDTO dto);

    /** 从药品库删除（物理删除，历史记录已存药名快照不受影响） */
    void remove(Long hospitalId, Long id);

    /** 启用 / 停用：1-启用 2-停用 */
    void updateStatus(Long hospitalId, Long id, Integer status);

    /** 我的药品库，含待确认与停用项；status 为空表示不过滤 */
    DrugPageVO library(Long hospitalId, String keyword, Integer status, int page, int size);

    /** 药品库状态汇总：总数 / 启用 / 待确认 / 停用 / 高警示 */
    java.util.Map<String, Object> stats(Long hospitalId);

    /** 批量改状态，供管理端勾选后一次确认或停用 */
    int batchUpdateStatus(Long hospitalId, List<Long> ids, Integer status);

    /** 重复药品归并预览：同通用名 + 归一化规格，返回保留项与待停用项，不落库 */
    com.patient.io.vo.DrugMergePreviewVO previewMerge(Long hospitalId);

    /** 执行归并：重复项停用，使用次数合并到保留项，返回停用条数 */
    int mergeDuplicates(Long hospitalId);

    /** 批量导入候选字典，自动补算拼音 */
    int importBatch(Long hospitalId, List<DrugDict> list);

    /** 为拼音缺失的记录补算拼音 */
    int backfillPinyin();
}
