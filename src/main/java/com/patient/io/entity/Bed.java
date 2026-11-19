package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("bed")
public class Bed implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hospitalId;
    private Long wardId;
    private String roomNo; // 所属房间号，如 01号房间
    private String bedNo;
    private Integer status; // 0-空床, 1-已占用
    private Integer sortOrder;

    @TableLogic
    private Integer isDeleted;

    private Date createTime;
    private Date updateTime;
}
