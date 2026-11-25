package com.patient.io.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patient.io.entity.DrugDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DrugDictMapper extends BaseMapper<DrugDict> {

    /**
     * 补液用药检索：中文包含 + 拼音包含（不要求从头匹配，护士常省略「注射用/盐酸」前缀）。
     * 排序：精确命中 > 从头命中 > 其它命中，再按使用频次降序。
     * 全部走 #{} 绑定，like/prefix 由服务层转义后传入；字典量级为千百条，包含匹配无需索引。
     */
    @Select("<script>"
            + "SELECT * FROM drug_dict "
            + "WHERE hospital_id = #{hospitalId} AND status IN (0, 1) AND is_deleted = 0 "
            + "<if test=\"route != null and route != ''\">"
            + "  AND (route = #{route} OR route IS NULL OR route = '') "
            + "</if>"
            + "AND (generic_name LIKE #{likeKw} "
            + "  OR IFNULL(trade_name,'') LIKE #{likeKw} "
            + "  OR IFNULL(alias_names,'') LIKE #{likeKw} "
            + "  OR pinyin_full LIKE #{likeKwLower} "
            + "  OR pinyin_initial LIKE #{likeKwLower}) "
            + "ORDER BY CASE "
            + "  WHEN generic_name = #{kw} OR pinyin_initial = #{kwLower} THEN 0 "
            + "  WHEN generic_name LIKE #{prefixExact} OR pinyin_initial LIKE #{prefix} THEN 1 "
            + "  WHEN IFNULL(alias_names,'') LIKE #{likeKw} THEN 2 "
            + "  ELSE 3 END, "
            + "status DESC, COALESCE(use_count, 0) DESC, COALESCE(sort_order, 9999) ASC, id ASC "
            + "LIMIT #{limit}"
            + "</script>")
    List<DrugDict> search(@Param("hospitalId") Long hospitalId,
                          @Param("route") String route,
                          @Param("kw") String kw,
                          @Param("kwLower") String kwLower,
                          @Param("likeKw") String likeKw,
                          @Param("likeKwLower") String likeKwLower,
                          @Param("prefix") String prefix,
                          @Param("prefixExact") String prefixExact,
                          @Param("limit") int limit);

    /**
     * 我的药品库列表：包含未启用项，供个人自查自改
     */
    @Select("<script>"
            + "SELECT * FROM drug_dict "
            + "WHERE hospital_id = #{hospitalId} AND is_deleted = 0 "
            + "<if test=\"kw != null and kw != ''\">"
            + "  AND (generic_name LIKE #{likeKw} OR IFNULL(alias_names,'') LIKE #{likeKw}"
            + "       OR pinyin_initial LIKE #{likeKw}) "
            + "</if>"
            + "<if test=\"status != null\">"
            + "  AND status = #{status} "
            + "</if>"
            + "ORDER BY CASE status WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE 2 END,"
            + " COALESCE(use_count, 0) DESC, generic_name ASC, id ASC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<DrugDict> listLibrary(@Param("hospitalId") Long hospitalId,
                               @Param("kw") String kw,
                               @Param("likeKw") String likeKw,
                               @Param("status") Integer status,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    @Select("<script>"
            + "SELECT COUNT(*) FROM drug_dict WHERE hospital_id = #{hospitalId} AND is_deleted = 0 "
            + "<if test=\"kw != null and kw != ''\">"
            + "  AND (generic_name LIKE #{likeKw} OR IFNULL(alias_names,'') LIKE #{likeKw}"
            + "       OR pinyin_initial LIKE #{likeKw}) "
            + "</if>"
            + "<if test=\"status != null\">"
            + "  AND status = #{status} "
            + "</if>"
            + "</script>")
    int countLibrary(@Param("hospitalId") Long hospitalId,
                     @Param("kw") String kw,
                     @Param("likeKw") String likeKw,
                     @Param("status") Integer status);

    /**
     * 药品库按状态汇总，供管理端顶部统计胶囊使用
     */
    @Select("SELECT COUNT(*) AS total,"
            + " SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS activeCount,"
            + " SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS pendingCount,"
            + " SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS disabledCount,"
            + " SUM(CASE WHEN is_high_alert = 1 THEN 1 ELSE 0 END) AS alertCount"
            + " FROM drug_dict WHERE hospital_id = #{hospitalId} AND is_deleted = 0")
    java.util.Map<String, Object> stats(@Param("hospitalId") Long hospitalId);

    /**
     * 批量改状态，管理端勾选多条「确认/停用」一次提交
     */
    @Update("<script>"
            + "UPDATE drug_dict SET status = #{status} WHERE hospital_id = #{hospitalId}"
            + " AND id IN <foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach>"
            + "</script>")
    int batchUpdateStatus(@Param("hospitalId") Long hospitalId,
                          @Param("ids") List<Long> ids,
                          @Param("status") Integer status);

    /**
     * 取出全部有效记录，供「同通用名+规格」归并分析（只取分组/决策所需字段）
     */
    @Select("SELECT id, generic_name, trade_name, alias_names, spec, status, use_count "
            + "FROM drug_dict WHERE hospital_id = #{hospitalId} AND is_deleted = 0 "
            + "ORDER BY id ASC")
    List<DrugDict> listForMerge(@Param("hospitalId") Long hospitalId);

    /**
     * 归并时把重复项的使用次数合并到保留项，保证排序仍反映真实使用频次
     */
    @Update("UPDATE drug_dict SET use_count = COALESCE(use_count,0) + #{delta} WHERE id = #{id}")
    int addUseCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 同通用名同规格去重，避免个人重复添加同一种药
     */
    @Select("SELECT * FROM drug_dict WHERE hospital_id = #{hospitalId} AND is_deleted = 0 "
            + "AND generic_name = #{name} AND IFNULL(spec,'') = #{spec} LIMIT 1")
    DrugDict findSame(@Param("hospitalId") Long hospitalId,
                      @Param("name") String name,
                      @Param("spec") String spec);

    /**
     * 空关键词时返回高频用药
     */
    @Select("SELECT * FROM drug_dict WHERE hospital_id = #{hospitalId} AND status = 1 AND is_deleted = 0 "
            + "ORDER BY COALESCE(use_count,0) DESC, COALESCE(sort_order, 9999) ASC, id ASC LIMIT #{limit}")
    List<DrugDict> listHot(@Param("hospitalId") Long hospitalId, @Param("limit") int limit);

    /**
     * 取出拼音为空的记录，供启动时补算
     */
    @Select("SELECT id, generic_name, trade_name, alias_names FROM drug_dict "
            + "WHERE (pinyin_initial IS NULL OR pinyin_initial = '') AND is_deleted = 0 LIMIT 500")
    List<DrugDict> listMissingPinyin();

    @Update("UPDATE drug_dict SET pinyin_full = #{full}, pinyin_initial = #{initial} WHERE id = #{id}")
    int updatePinyin(@Param("id") Long id, @Param("full") String full, @Param("initial") String initial);

    @Update("UPDATE drug_dict SET use_count = COALESCE(use_count,0) + 1 WHERE id = #{id}")
    int increaseUseCount(@Param("id") Long id);

    @Update("UPDATE drug_dict SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
