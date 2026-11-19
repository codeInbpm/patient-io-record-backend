package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.dto.NurseBindDTO;
import com.patient.io.dto.PatientBindDTO;
import com.patient.io.dto.WxLoginDTO;
import com.patient.io.entity.Nurse;
import com.patient.io.service.UserService;
import com.patient.io.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * 真实微信小程序授权登录
     */
    @PostMapping("/wx-login")
    public Result<LoginVO> wxLogin(@RequestBody @Validated WxLoginDTO loginDTO) {
        return Result.success(userService.wxLogin(loginDTO));
    }

    /**
     * 医护人员（护士长/护士）通过6位纯数字工号绑定
     */
    @PostMapping("/bind-nurse")
    public Result<LoginVO> bindNurse(@RequestBody @Validated NurseBindDTO bindDTO) {
        return Result.success(userService.bindNurse(bindDTO));
    }

    /**
     * 患者/家属通过「姓名 + 病房 + 床位号」精确匹配绑定
     */
    @PostMapping("/bind-patient")
    public Result<LoginVO> bindPatient(@RequestBody @Validated PatientBindDTO bindDTO) {
        return Result.success(userService.bindPatient(bindDTO));
    }

    /**
     * 获取当前登录人与绑定身份详情
     */
    @GetMapping("/me")
    public Result<LoginVO> getCurrentUser() {
        return Result.success(userService.getCurrentUserInfo());
    }

    /**
     * 解绑当前身份
     */
    @PostMapping("/unbind")
    public Result<Void> unbind() {
        userService.unbind();
        return Result.success();
    }

    /**
     * 查询在职护士名册（护士长分配责任护士时使用）
     */
    @GetMapping("/nurses")
    public Result<List<Nurse>> listActiveNurses() {
        return Result.success(userService.listActiveNurses());
    }
}
