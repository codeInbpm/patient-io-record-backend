package com.patient.io.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.entity.InfusionRecord;
import com.patient.io.mapper.InfusionRecordMapper;
import com.patient.io.service.WechatNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InfusionMonitorTask {

    private final InfusionRecordMapper infusionRecordMapper;
    private final WechatNoticeService wechatNoticeService;

    /**
     * 每 1 分钟检查一次正在输液的患者，触发拔针/换药及超时微信通知
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void checkInfusionReminders() {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<InfusionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InfusionRecord::getStatus, "INFUSING")
                .isNotNull(InfusionRecord::getEstimatedEndTime);

        List<InfusionRecord> activeList = infusionRecordMapper.selectList(wrapper);
        if (activeList.isEmpty()) {
            return;
        }

        for (InfusionRecord record : activeList) {
            LocalDateTime estEnd = record.getEstimatedEndTime();
            Integer remindStatus = record.getRemindStatus() != null ? record.getRemindStatus() : 0;

            String drugInfo = (record.getUsageType() != null ? record.getUsageType() : "补液") +
                    " (" + (record.getCapacity() != null ? record.getCapacity() : 0) + "ml)";

            // 1. 检查是否已经超时未拔针（优先级高）
            if (now.isAfter(estEnd)) {
                if (remindStatus < 2) {
                    long overMinutes = Duration.between(estEnd, now).toMinutes();
                    String content = String.format("已超时 %d 分钟未拔针，请立即前往床旁巡视处理！", Math.max(overMinutes, 1));
                    String remark = "药物: " + drugInfo + "，经办护士: " + (record.getNurseName() != null ? record.getNurseName() : "--");

                    log.warn("触发超时未拔针微信提醒: 床号={}, 患者={}, 超时={}分", record.getBedNo(), record.getPatientName(), overMinutes);
                    wechatNoticeService.sendToNurseOrAdmins(
                            record.getNurseId(),
                            "超时未拔针告警",
                            record.getBedNo(),
                            record.getPatientName(),
                            content,
                            remark
                    );

                    record.setRemindStatus(2);
                    infusionRecordMapper.updateById(record);
                }
            }
            // 2. 检查是否处于即将结束阶段（剩余 10 分钟以内）
            else {
                long remainMinutes = Duration.between(now, estEnd).toMinutes();
                if (remainMinutes <= 10 && remindStatus < 1) {
                    String content = String.format("预计剩余约 %d 分钟滴完，请提前准备接瓶或拔针", Math.max(remainMinutes, 1));
                    String remark = "药物: " + drugInfo + "，预计拔针时间: " + estEnd.toLocalTime().toString().substring(0, 5);

                    log.info("触发即将拔针预提醒: 床号={}, 患者={}, 剩余={}分", record.getBedNo(), record.getPatientName(), remainMinutes);
                    wechatNoticeService.sendToNurseOrAdmins(
                            record.getNurseId(),
                            "拔针/换药预提醒",
                            record.getBedNo(),
                            record.getPatientName(),
                            content,
                            remark
                    );

                    record.setRemindStatus(1);
                    infusionRecordMapper.updateById(record);
                }
            }
        }
    }
}
