package com.patient.io.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patient.io.entity.Patient;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {

    @Delete("DELETE FROM patient WHERE id = #{id}")
    int deletePhysicallyById(@Param("id") Long id);
}
