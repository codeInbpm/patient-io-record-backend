package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("patient")
public class Patient implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hospitalId;
    private Long wardId;
    private Long bedId;
    private String bedNo;
    private String patientName;
    private String hospitalNo;
    private Integer gender; // 1-男, 2-女
    private Integer age;
    private String patientType; // ADULT(成人), INFANT(婴儿)
    private String admissionType; // ROUTINE(平诊), EMERGENCY(急诊)
    private Double weight; // 体重(kg)
    private String nursingLevel; // SPECIAL(特级), LEVEL_1(一级), LEVEL_2(二级), LEVEL_3(三级)
    private Integer needIoRecord; // 1-监测出入量, 0-不监测
    private String drainageTubes; // 留置管路，逗号分隔，如：CATHER,GASTRIC
    private LocalDate admissionDate;
    private String diagnosis;
    private Long boundUserId;
    private Integer status; // 1-在院, 2-已出院

    @TableLogic
    private Integer isDeleted;

    private Date createTime;
    private Date updateTime;
}
