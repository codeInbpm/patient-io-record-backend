package com.patient.io.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DatabaseUpgradeRunner implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info(">>> 启动数据库自动化升级与索引检查...");
        try {
            // 1. 检查 nurse_patient_relation 是否存在 ward_id 列
            try {
                jdbcTemplate.execute("ALTER TABLE `nurse_patient_relation` ADD COLUMN `ward_id` BIGINT DEFAULT NULL COMMENT '病房ID' AFTER `nurse_id`");
                log.info("已成功为 nurse_patient_relation 表添加 ward_id 字段");
            } catch (Exception e) {
                // 已存在则忽略
            }

            // 2. 检查并允许 patient_id 为 NULL（空床时支持 patient_id = NULL，避免唯一索引冲突）
            try {
                jdbcTemplate.execute("ALTER TABLE `nurse_patient_relation` MODIFY COLUMN `patient_id` BIGINT DEFAULT NULL COMMENT '负责的病人patient.id'");
                log.info("已成功修改 nurse_patient_relation.patient_id 允许为 NULL");
            } catch (Exception e) {
                log.warn("修改 patient_id 字段属性异常: {}", e.getMessage());
            }

            // 3. 将 ward_id 为空的历史数据按 bed.ward_id 自动修正
            try {
                int updated = jdbcTemplate.update(
                        "UPDATE `nurse_patient_relation` r JOIN `bed` b ON r.bed_id = b.id " +
                        "SET r.ward_id = b.ward_id WHERE r.ward_id IS NULL OR r.ward_id = 0");
                if (updated > 0) {
                    log.info("已自动补齐 {} 条分配记录的 ward_id", updated);
                }
            } catch (Exception e) {
                log.warn("回填 ward_id 异常: {}", e.getMessage());
            }

            // 4. 清理可能存在的历史重复 (nurse_id, bed_id) 数据，保留最新一条
            try {
                jdbcTemplate.execute(
                        "DELETE r1 FROM `nurse_patient_relation` r1 " +
                        "INNER JOIN `nurse_patient_relation` r2 " +
                        "WHERE r1.id < r2.id AND r1.nurse_id = r2.nurse_id AND r1.bed_id = r2.bed_id");
            } catch (Exception e) {
                log.warn("清理历史重复记录提示: {}", e.getMessage());
            }

            // 4.1 清理同一床位可能存在的历史多护士分配记录，确保每床只保留最后交班打勾的一条（id 最大的一条）
            try {
                int cleanedBeds = jdbcTemplate.update(
                        "DELETE r1 FROM `nurse_patient_relation` r1 " +
                        "INNER JOIN `nurse_patient_relation` r2 " +
                        "WHERE r1.id < r2.id AND r1.bed_id = r2.bed_id");
                if (cleanedBeds > 0) {
                    log.info("已自动清理历史同一床位多护士记录 {} 条，确保每床仅保留最后交班打勾的责任护士", cleanedBeds);
                }
            } catch (Exception e) {
                log.warn("清理床位历史重复护士记录提示: {}", e.getMessage());
            }

            // 4.2 清理同一病人可能存在的历史多护士分配记录，确保每位病患只保留最后交班打勾的一条
            try {
                int cleanedPatients = jdbcTemplate.update(
                        "DELETE r1 FROM `nurse_patient_relation` r1 " +
                        "INNER JOIN `nurse_patient_relation` r2 " +
                        "WHERE r1.id < r2.id AND r1.patient_id IS NOT NULL AND r1.patient_id = r2.patient_id");
                if (cleanedPatients > 0) {
                    log.info("已自动清理历史同一病患多护士记录 {} 条", cleanedPatients);
                }
            } catch (Exception e) {
                log.warn("清理病患历史重复护士记录提示: {}", e.getMessage());
            }

            // 5. 检查索引：如果存在 uk_nurse_patient，删除该索引
            boolean hasOldUk = false;
            boolean hasNewUk = false;
            try {
                List<Map<String, Object>> indexes = jdbcTemplate.queryForList("SHOW INDEX FROM `nurse_patient_relation`");
                for (Map<String, Object> idx : indexes) {
                    String keyName = String.valueOf(idx.get("Key_name"));
                    if ("uk_nurse_patient".equalsIgnoreCase(keyName)) {
                        hasOldUk = true;
                    }
                    if ("uk_nurse_bed".equalsIgnoreCase(keyName)) {
                        hasNewUk = true;
                    }
                }
            } catch (Exception e) {
                log.warn("查询表索引异常: {}", e.getMessage());
            }

            if (hasOldUk) {
                try {
                    jdbcTemplate.execute("ALTER TABLE `nurse_patient_relation` DROP INDEX `uk_nurse_patient`");
                    log.info("已成功移除旧的 uk_nurse_patient 索引，彻底解决多空床认领冲突问题");
                } catch (Exception e) {
                    log.warn("删除 uk_nurse_patient 索引异常: {}", e.getMessage());
                }
            }

            // 6. 添加 (nurse_id, bed_id) 唯一索引
            if (!hasNewUk) {
                try {
                    jdbcTemplate.execute("ALTER TABLE `nurse_patient_relation` ADD UNIQUE KEY `uk_nurse_bed` (`nurse_id`, `bed_id`)");
                    log.info("已成功建立核心唯一索引 uk_nurse_bed (nurse_id, bed_id)");
                } catch (Exception e) {
                    log.warn("创建 uk_nurse_bed 索引异常: {}", e.getMessage());
                }
            }

            // 7. 检查并为 nurse 表升级病区绑定与医院相关字段
            try {
                jdbcTemplate.execute("ALTER TABLE `nurse` ADD COLUMN `ward_id` BIGINT DEFAULT NULL COMMENT '所属病区ID' AFTER `dept_id`");
                log.info("已为 nurse 表添加 ward_id 字段");
            } catch (Exception ignored) {}

            try {
                jdbcTemplate.execute("ALTER TABLE `nurse` ADD COLUMN `ward_name` VARCHAR(64) DEFAULT NULL COMMENT '所属病区名称' AFTER `ward_id`");
                log.info("已为 nurse 表添加 ward_name 字段");
            } catch (Exception ignored) {}

            try {
                jdbcTemplate.execute("ALTER TABLE `nurse` ADD COLUMN `building` VARCHAR(64) DEFAULT NULL COMMENT '所在楼宇' AFTER `ward_name`");
                log.info("已为 nurse 表添加 building 字段");
            } catch (Exception ignored) {}

            try {
                jdbcTemplate.execute("ALTER TABLE `nurse` ADD COLUMN `hospital_id` BIGINT DEFAULT 1 COMMENT '所属医院ID' AFTER `role`");
                log.info("已为 nurse 表添加 hospital_id 字段");
            } catch (Exception ignored) {}

            try {
                jdbcTemplate.execute("ALTER TABLE `nurse` ADD COLUMN `hospital_name` VARCHAR(128) DEFAULT NULL COMMENT '所属医院名称' AFTER `hospital_id`");
                log.info("已为 nurse 表添加 hospital_name 字段");
            } catch (Exception ignored) {}

            // 8. 自动为未绑定病区或医院的历史护士档案补齐默认病区和医院
            try {
                // 默认医院：第一人民医院（示范本部） id=1
                jdbcTemplate.update("UPDATE `nurse` SET `hospital_id` = 1, `hospital_name` = '第一人民医院（示范本部）' WHERE `hospital_id` IS NULL OR `hospital_id` = 0");
                // 默认病区为 7#19B消化内科三病区 (id=36)，对应楼宇 7号楼 (dept_id=11)
                jdbcTemplate.update("UPDATE `nurse` SET `ward_id` = 36, `ward_name` = '7#19B消化内科三病区', `building` = '7号楼', `dept_id` = 11, `department` = '7号楼' WHERE `ward_id` IS NULL OR `ward_id` = 0");
                log.info("已自动补齐历史护士档案的医院与病区绑定数据");
            } catch (Exception e) {
                log.warn("补齐护士病区数据提示: {}", e.getMessage());
            }

            log.info(">>> 数据库表结构与索引升级检查完成！");
        } catch (Exception e) {
            log.error("数据库升级 runner 执行失败", e);
        }
    }
}
