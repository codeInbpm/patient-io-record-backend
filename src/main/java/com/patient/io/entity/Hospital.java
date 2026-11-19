package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("hospital")
public class Hospital implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String hospitalName;
    private String hospitalCode;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private Integer status;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
}
