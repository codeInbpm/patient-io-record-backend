package com.patient.io.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.patient.io.entity.Nurse;
import com.patient.io.mapper.NurseMapper;
import com.patient.io.service.WechatNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatNoticeServiceImpl implements WechatNoticeService {

    private final NurseMapper nurseMapper;

    @Value("${wechat.test-account.appid:}")
    private String appId;

    @Value("${wechat.test-account.secret:}")
    private String appSecret;

    @Value("${wechat.test-account.template-id:}")
    private String templateId;

    @Value("${wechat.test-account.default-openids:}")
    private String defaultOpenids;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private volatile String cachedAccessToken = null;
    private volatile long tokenExpireTime = 0L;

    /**
     * 获取测试号 access_token (带本地2小时有效缓存，提前5分钟刷新)
     */
    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (StrUtil.isNotBlank(cachedAccessToken) && now < tokenExpireTime) {
            return cachedAccessToken;
        }

        if (StrUtil.isBlank(appId) || StrUtil.isBlank(appSecret)) {
            log.warn("微信测试号 appid 或 secret 未配置，无法获取 access_token");
            return null;
        }

        String url = String.format("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                appId.trim(), appSecret.trim());
        try {
            String respStr = HttpUtil.get(url, 5000);
            JSONObject json = JSON.parseObject(respStr);
            if (json.containsKey("access_token")) {
                cachedAccessToken = json.getString("access_token");
                int expiresIn = json.getIntValue("expires_in", 7200);
                tokenExpireTime = now + (expiresIn - 300) * 1000L;
                log.info("微信测试号 access_token 获取成功，有效期约 {} 秒", expiresIn);
                return cachedAccessToken;
            } else {
                log.error("获取微信测试号 access_token 失败: {}", respStr);
            }
        } catch (Exception e) {
            log.error("请求微信接口获取 access_token 发生网络异常", e);
        }
        return null;
    }

    @Override
    public boolean sendTemplateNotice(String title, String bedNo, String patientName, String content, String remark, Collection<String> openids) {
        if (CollUtil.isEmpty(openids)) {
            log.warn("发送通知被跳过: openids 为空");
            return false;
        }

        String token = getAccessToken();
        if (StrUtil.isBlank(token)) {
            log.warn("无法发送微信通知: access_token 为空");
            return false;
        }

        String sendUrl = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=" + token;
        String nowStr = LocalDateTime.now().format(TIME_FORMATTER);

        boolean allSuccess = true;
        for (String openid : openids) {
            if (StrUtil.isBlank(openid)) {
                continue;
            }

            try {
                // 构造消息模板请求体
                // 对应模板: 【{{title.DATA}}】床号:{{bed.DATA}} 患者:{{name.DATA}} 预警内容:{{content.DATA}} 时间:{{time.DATA}} 备注:{{remark.DATA}}
                JSONObject body = new JSONObject();
                body.put("touser", openid.trim());
                body.put("template_id", templateId.trim());

                JSONObject data = new JSONObject();
                data.put("title", createItem(title, "#E6A23C"));
                data.put("bed", createItem(StrUtil.isNotBlank(bedNo) ? bedNo : "床位", "#173177"));
                data.put("name", createItem(StrUtil.isNotBlank(patientName) ? patientName : "--", "#333333"));
                data.put("content", createItem(content, "#F56C6C"));
                data.put("time", createItem(nowStr, "#909399"));
                data.put("remark", createItem(remark, "#909399"));

                body.put("data", data);

                String result = HttpUtil.post(sendUrl, body.toJSONString(), 5000);
                JSONObject resJson = JSON.parseObject(result);
                if (resJson != null && resJson.getIntValue("errcode") == 0) {
                    log.info("微信测试号通知推送成功 -> openid: [{}], title: [{}]", openid, title);
                } else {
                    log.error("微信测试号通知推送失败 -> openid: [{}], resp: {}", openid, result);
                    allSuccess = false;
                }
            } catch (Exception e) {
                log.error("调用微信模板接口异常, openid=" + openid, e);
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    @Override
    public boolean sendToNurseOrAdmins(Long nurseId, String title, String bedNo, String patientName, String content, String remark) {
        Set<String> targetOpenids = new HashSet<>();

        // 1. 如果指定了护士，查找该护士绑定的 openid
        if (nurseId != null) {
            Nurse nurse = nurseMapper.selectById(nurseId);
            if (nurse != null && StrUtil.isNotBlank(nurse.getWxNoticeOpenid())) {
                targetOpenids.add(nurse.getWxNoticeOpenid().trim());
            }
        }

        // 2. 默认兜底接收人（如科室群管理员/测试开发者）
        if (StrUtil.isNotBlank(defaultOpenids)) {
            for (String oid : defaultOpenids.split(",")) {
                if (StrUtil.isNotBlank(oid)) {
                    targetOpenids.add(oid.trim());
                }
            }
        }

        if (targetOpenids.isEmpty()) {
            log.warn("未能定位任何接收预警消息的 OpenID, 放弃推送");
            return false;
        }

        return sendTemplateNotice(title, bedNo, patientName, content, remark, targetOpenids);
    }

    private JSONObject createItem(String value, String color) {
        JSONObject item = new JSONObject();
        item.put("value", value != null ? value : "");
        item.put("color", color != null ? color : "#333333");
        return item;
    }
}
