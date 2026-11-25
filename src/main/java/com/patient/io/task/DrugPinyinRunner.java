package com.patient.io.task;

import com.patient.io.service.DrugService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时补齐药品字典拼音（导入的种子数据拼音列为空时自动计算）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrugPinyinRunner implements ApplicationRunner {

    private final DrugService drugService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int count = drugService.backfillPinyin();
            if (count > 0) {
                log.info("药品字典拼音补算完成: {} 条", count);
            }
        } catch (Exception e) {
            // 表未建或字段缺失时不能阻断启动
            log.warn("药品字典拼音补算跳过: {}", e.getMessage());
        }
    }
}
