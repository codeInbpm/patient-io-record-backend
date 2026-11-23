package com.patient.io.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("infusion_image")
public class InfusionImage implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hospitalId;
    private Long infusionId;
    private String fileName;
    private String objectKey;
    private String fileUrl;
    private Long fileSize;
    private Long uploadUserId;
    private Date createTime;
}
