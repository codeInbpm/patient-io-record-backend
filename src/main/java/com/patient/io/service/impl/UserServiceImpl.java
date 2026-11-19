package com.patient.io.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.patient.io.common.BusinessException;
import com.patient.io.common.UserContext;
import com.patient.io.config.JwtUtils;
import com.patient.io.dto.NurseBindDTO;
import com.patient.io.dto.PatientBindDTO;
import com.patient.io.dto.WxLoginDTO;
import com.patient.io.entity.*;
import com.patient.io.mapper.*;
import com.patient.io.service.UserService;
import com.patient.io.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    @Value("${wechat.miniapp.appid:}")
    private String appId;

    @Value("${wechat.miniapp.secret:}")
    private String appSecret;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private NurseMapper nurseMapper;

    @Autowired
    private PatientMapper patientMapper;

    @Autowired
    private WardMapper wardMapper;

    @Autowired
    private HospitalMapper hospitalMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    public LoginVO wxLogin(WxLoginDTO loginDTO) {
        String openid = getOpenidByCode(loginDTO.getCode());
        if (!StringUtils.hasText(openid)) {
            throw new BusinessException("微信登录授权失败，无法获取OpenID");
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getOpenid, openid);
        SysUser user = getOne(wrapper);

        if (user == null) {
            user = new SysUser();
            user.setOpenid(openid);
            user.setNickname(StringUtils.hasText(loginDTO.getNickname()) ? loginDTO.getNickname() : "微信用户");
            user.setAvatarUrl(loginDTO.getAvatarUrl());
            user.setRole("NONE");
            user.setBindStatus(0);
            user.setStatus(1);
            save(user);
        } else {
            if (StringUtils.hasText(loginDTO.getNickname())) {
                user.setNickname(loginDTO.getNickname());
            }
            if (StringUtils.hasText(loginDTO.getAvatarUrl())) {
                user.setAvatarUrl(loginDTO.getAvatarUrl());
            }
            updateById(user);
        }

        return buildLoginVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO bindNurse(NurseBindDTO bindDTO) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先微信登录");
        }

        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 校验 6 位纯数字工号
        String jobNumber = bindDTO.getJobNumber().trim();
        LambdaQueryWrapper<Nurse> nWrapper = new LambdaQueryWrapper<>();
        nWrapper.eq(Nurse::getJobNumber, jobNumber)
                .eq(Nurse::getStatus, 1);
        Nurse nurse = nurseMapper.selectOne(nWrapper);

        if (nurse == null) {
            throw new BusinessException("工号不存在或该人员已离职，请核对6位工号");
        }

        if (nurse.getBoundUserId() != null && !nurse.getBoundUserId().equals(userId)) {
            throw new BusinessException("该工号已被其他微信绑定，不可重复绑定");
        }

        // 如果用户选择了医院和科室，做匹配或同步
        if (bindDTO.getHospitalId() != null) {
            if (nurse.getHospitalId() != null && !nurse.getHospitalId().equals(bindDTO.getHospitalId())) {
                throw new BusinessException("所选医院与工号登记医院不一致");
            }
            if (nurse.getHospitalId() == null) {
                Hospital h = hospitalMapper.selectById(bindDTO.getHospitalId());
                if (h != null) {
                    nurse.setHospitalId(h.getId());
                    nurse.setHospitalName(h.getHospitalName());
                }
            }
        }
        if (bindDTO.getDeptId() != null) {
            if (nurse.getDeptId() != null && !nurse.getDeptId().equals(bindDTO.getDeptId())) {
                throw new BusinessException("所选科室与工号登记科室不一致");
            }
            if (nurse.getDeptId() == null) {
                Department d = departmentMapper.selectById(bindDTO.getDeptId());
                if (d != null) {
                    nurse.setDeptId(d.getId());
                    nurse.setDepartment(d.getDeptName());
                }
            }
        }

        // 绑定更新
        nurse.setBoundUserId(userId);
        nurseMapper.updateById(nurse);

        user.setRole(nurse.getRole()); // HEAD_NURSE 或 NURSE
        user.setBindStatus(1);
        user.setBindRefId(nurse.getId());
        updateById(user);

        return buildLoginVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO bindPatient(PatientBindDTO bindDTO) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先微信登录");
        }

        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 严格按「真实姓名 + 所属楼宇/病区 + 床位号 + 住院号」与后台在院病患档案进行全项校验，全部一致为通过
        LambdaQueryWrapper<Patient> pWrapper = new LambdaQueryWrapper<>();
        pWrapper.eq(Patient::getHospitalNo, bindDTO.getHospitalNo().trim())
                .eq(Patient::getPatientName, bindDTO.getPatientName().trim())
                .eq(Patient::getWardId, bindDTO.getWardId())
                .eq(Patient::getStatus, 1);

        if (bindDTO.getBedId() != null) {
            pWrapper.eq(Patient::getBedId, bindDTO.getBedId());
        } else {
            pWrapper.eq(Patient::getBedNo, bindDTO.getBedNo().trim());
        }
        Patient patient = patientMapper.selectOne(pWrapper);

        // 容错：若床号存在格式差异（如 1床 vs 01床），按去"床"字符再做兜底校验
        if (patient == null) {
            LambdaQueryWrapper<Patient> fallbackWrapper = new LambdaQueryWrapper<>();
            fallbackWrapper.eq(Patient::getHospitalNo, bindDTO.getHospitalNo().trim())
                    .eq(Patient::getPatientName, bindDTO.getPatientName().trim())
                    .eq(Patient::getWardId, bindDTO.getWardId())
                    .eq(Patient::getStatus, 1);
            List<Patient> candidates = patientMapper.selectList(fallbackWrapper);
            String inputBed = bindDTO.getBedNo().trim().replace("床", "");
            for (Patient c : candidates) {
                if (c.getBedNo() != null && c.getBedNo().replace("床", "").equals(inputBed)) {
                    patient = c;
                    break;
                }
            }
        }

        if (patient == null) {
            throw new BusinessException("身份核验失败：真实姓名、所属楼宇/病区、床位号及住院号不一致，请仔细核对后台病患档案！");
        }

        if (patient.getBoundUserId() != null && !patient.getBoundUserId().equals(userId)) {
            throw new BusinessException("该病患档案已被其他微信账号绑定");
        }

        patient.setBoundUserId(userId);
        patientMapper.updateById(patient);

        user.setRole("PATIENT");
        user.setBindStatus(1);
        user.setBindRefId(patient.getId());
        updateById(user);

        return buildLoginVO(user);
    }

    @Override
    public LoginVO getCurrentUserInfo() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return buildLoginVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind() {
        Long userId = UserContext.getUserId();
        if (userId == null) return;
        SysUser user = getById(userId);
        if (user == null) return;

        if ("HEAD_NURSE".equals(user.getRole()) || "NURSE".equals(user.getRole())) {
            if (user.getBindRefId() != null) {
                Nurse nurse = nurseMapper.selectById(user.getBindRefId());
                if (nurse != null) {
                    nurse.setBoundUserId(null);
                    nurseMapper.updateById(nurse);
                }
            }
        } else if ("PATIENT".equals(user.getRole())) {
            if (user.getBindRefId() != null) {
                Patient patient = patientMapper.selectById(user.getBindRefId());
                if (patient != null) {
                    patient.setBoundUserId(null);
                    patientMapper.updateById(patient);
                }
            }
        }

        user.setRole("NONE");
        user.setBindStatus(0);
        user.setBindRefId(null);
        updateById(user);
    }

    @Override
    public List<Nurse> listActiveNurses() {
        LambdaQueryWrapper<Nurse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Nurse::getStatus, 1)
                .orderByAsc(Nurse::getJobNumber);
        return nurseMapper.selectList(wrapper);
    }

    private LoginVO buildLoginVO(SysUser user) {
        String token = jwtUtils.generateToken(user.getId(), user.getOpenid(), user.getRole());

        String realName = null;
        String jobNumber = null;
        String department = null;
        Long hospitalId = null;
        String hospitalName = null;
        Long deptId = null;
        String building = null;
        Long wardId = null;
        String wardName = null;
        Long boundPatientId = null;
        String boundPatientName = null;
        String boundBedNo = null;

        if (user.getBindStatus() == 1 && user.getBindRefId() != null) {
            if ("HEAD_NURSE".equals(user.getRole()) || "NURSE".equals(user.getRole())) {
                Nurse nurse = nurseMapper.selectById(user.getBindRefId());
                if (nurse != null) {
                    realName = nurse.getRealName();
                    jobNumber = nurse.getJobNumber();
                    department = nurse.getDepartment();
                    hospitalId = nurse.getHospitalId();
                    hospitalName = nurse.getHospitalName();
                    deptId = nurse.getDeptId();
                    building = nurse.getBuilding() != null ? nurse.getBuilding() : nurse.getDepartment();
                    wardId = nurse.getWardId();
                    wardName = nurse.getWardName();
                }
            } else if ("PATIENT".equals(user.getRole())) {
                Patient patient = patientMapper.selectById(user.getBindRefId());
                if (patient != null) {
                    boundPatientId = patient.getId();
                    boundPatientName = patient.getPatientName();
                    boundBedNo = patient.getBedNo();
                    realName = patient.getPatientName();
                    Ward ward = wardMapper.selectById(patient.getWardId());
                    department = ward != null ? ward.getWardName() : "";
                    if (ward != null) {
                        hospitalId = ward.getHospitalId();
                        hospitalName = ward.getHospitalName();
                        deptId = ward.getDeptId();
                        building = ward.getBuilding() != null ? ward.getBuilding() : ward.getDepartment();
                        wardId = ward.getId();
                        wardName = ward.getWardName();
                    }
                }
            }
        }

        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .openid(user.getOpenid())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .isBound(user.getBindStatus() == 1)
                .realName(realName)
                .jobNumber(jobNumber)
                .hospitalId(hospitalId)
                .hospitalName(hospitalName)
                .deptId(deptId)
                .department(department)
                .building(building)
                .wardId(wardId)
                .wardName(wardName)
                .boundPatientId(boundPatientId)
                .boundPatientName(boundPatientName)
                .boundBedNo(boundBedNo)
                .build();
    }

    private String getOpenidByCode(String code) {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new BusinessException("微信小程序AppID或Secret未配置");
        }
        try {
            String url = String.format("https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    appId.trim(), appSecret.trim(), code.trim());
            String response = HttpUtil.get(url, 5000);
            JSONObject json = JSONUtil.parseObj(response);
            if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
                // 如果是开发工具测试号或者非法code，给明确提示
                throw new BusinessException("微信换取OpenID失败：" + json.getStr("errmsg"));
            }
            return json.getStr("openid");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("连接微信登录接口失败: " + e.getMessage());
        }
    }
}
