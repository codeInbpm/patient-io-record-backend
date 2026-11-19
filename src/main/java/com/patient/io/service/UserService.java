package com.patient.io.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.patient.io.dto.NurseBindDTO;
import com.patient.io.dto.PatientBindDTO;
import com.patient.io.dto.WxLoginDTO;
import com.patient.io.entity.Nurse;
import com.patient.io.entity.SysUser;
import com.patient.io.vo.LoginVO;

import java.util.List;

public interface UserService extends IService<SysUser> {
    LoginVO wxLogin(WxLoginDTO loginDTO);
    LoginVO bindNurse(NurseBindDTO bindDTO);
    LoginVO bindPatient(PatientBindDTO bindDTO);
    LoginVO getCurrentUserInfo();
    void unbind();
    List<Nurse> listActiveNurses();
}
