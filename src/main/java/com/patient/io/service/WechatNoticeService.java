package com.patient.io.service;

import java.util.Collection;

public interface WechatNoticeService {

    /**
     * 发送微信测试号模板通知到指定 OpenID 列表
     *
     * @param title       预警标题（如：拔针/换药预提醒、出入量失衡警示）
     * @param bedNo       床号（如：12床）
     * @param patientName 患者姓名（如：xxsf）
     * @param content     预警内容详情
     * @param remark      处理备注建议
     * @param openids     接收者 openid 集合
     * @return 是否成功
     */
    boolean sendTemplateNotice(String title, String bedNo, String patientName, String content, String remark, Collection<String> openids);

    /**
     * 发送通知给指定护士，若护士未绑定微信则自动发给科室兜底/默认接收人
     *
     * @param nurseId     责任护士ID（可传 null）
     * @param title       预警标题
     * @param bedNo       床号
     * @param patientName 患者姓名
     * @param content     预警详情
     * @param remark      备注
     * @return 是否成功
     */
    boolean sendToNurseOrAdmins(Long nurseId, String title, String bedNo, String patientName, String content, String remark);
}
