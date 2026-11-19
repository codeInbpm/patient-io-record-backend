package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ward")
public class Ward implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String wardName;
    private String building; // 所在楼宇，如：1号楼、2号楼
    private Long hospitalId;
    private String hospitalName;
    private Long deptId;
    private String department;
    private String roomNo;
    private Integer sortOrder;
    private Long createBy;
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    private Date createTime;
    private Date updateTime;
}
