package com.patient.io.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class WxLoginDTO {
    @NotBlank(message = "微信授权code不能为空")
    private String code;
    private String nickname;
    private String avatarUrl;
}
