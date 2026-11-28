package com.patient.io.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patient.io.entity.SysDictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {

    /**
     * 各字典编码下的启用项数量，用于维护页展示
     */
    @Select("<script>"
            + "SELECT dict_code AS dictCode, COUNT(1) AS cnt FROM sys_dict_item "
            + "WHERE status = 1 AND hospital_id IN (0, #{hospitalId}) "
            + "GROUP BY dict_code"
            + "</script>")
    List<Map<String, Object>> countItemsByCode(@Param("hospitalId") Long hospitalId);
}
