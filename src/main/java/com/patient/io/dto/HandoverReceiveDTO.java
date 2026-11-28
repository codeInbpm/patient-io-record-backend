package com.patient.io.dto;

import lombok.Data;

/**
 * 小程序端接班确认入参。
 *
 * 电子签名在护士端完成：前端把画板导出为 PNG，先调 /v1/file/upload 传到 MinIO，
 * 再把返回的图片地址带到这里落库。signatureUrl 为空时不允许确认接班。
 */
@Data
public class HandoverReceiveDTO {

    /** 接班护士姓名（默认取登录人姓名，可在页面更正） */
    private String receiverName;

    /** 电子签名图片地址（MinIO 公共读地址） */
    private String signatureUrl;
}
