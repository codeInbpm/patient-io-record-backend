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

            // 9. 自动创建功能一：患者补液记录表 infusion_record
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `infusion_record` (" +
                        "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                        "`hospital_id` BIGINT NOT NULL DEFAULT 1 COMMENT '医院ID(租户隔离)'," +
                        "`ward_id` BIGINT NOT NULL COMMENT '病区ID'," +
                        "`bed_id` BIGINT NOT NULL COMMENT '床位ID'," +
                        "`bed_no` VARCHAR(32) NOT NULL COMMENT '床号冗余'," +
                        "`patient_id` BIGINT NOT NULL COMMENT '患者ID'," +
                        "`patient_name` VARCHAR(64) NOT NULL COMMENT '患者姓名冗余'," +
                        "`capacity` INT NOT NULL COMMENT '补液容量(ml)'," +
                        "`usage_type` VARCHAR(64) NOT NULL COMMENT '给药用法(ivg/ibn/iv drip/自定义)'," +
                        "`start_time` DATETIME NOT NULL COMMENT '开始补液时间'," +
                        "`estimated_end_time` DATETIME NOT NULL COMMENT '预计拔针时间'," +
                        "`actual_end_time` DATETIME DEFAULT NULL COMMENT '实际结束时间'," +
                        "`status` VARCHAR(20) NOT NULL DEFAULT 'INFUSING' COMMENT '状态: INFUSING-正在补液, COMPLETED-正常结束, STOPPED-异常中断'," +
                        "`actual_intake_amount` INT DEFAULT NULL COMMENT '实际滴入量(ml)'," +
                        "`is_synced_intake` TINYINT(1) DEFAULT 1 COMMENT '是否已同步入量记录: 1-是, 0-否'," +
                        "`intake_record_id` BIGINT DEFAULT NULL COMMENT '关联的入量记录ID'," +
                        "`nurse_id` BIGINT NOT NULL COMMENT '经办护士ID'," +
                        "`nurse_name` VARCHAR(64) NOT NULL COMMENT '经办护士姓名'," +
                        "`end_nurse_id` BIGINT DEFAULT NULL COMMENT '拔针/结束护士ID'," +
                        "`end_nurse_name` VARCHAR(64) DEFAULT NULL COMMENT '拔针/结束护士姓名'," +
                        "`remark` VARCHAR(500) DEFAULT NULL COMMENT '补液备注'," +
                        "`is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除'," +
                        "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "PRIMARY KEY (`id`)," +
                        "KEY `idx_ward_status` (`ward_id`, `status`)," +
                        "KEY `idx_patient_time` (`patient_id`, `start_time`)," +
                        "KEY `idx_estimated_end` (`status`, `estimated_end_time`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者补液记录表'");
                log.info("已检查并确保 infusion_record 补液记录表创建成功");
            } catch (Exception e) {
                log.warn("创建 infusion_record 表异常: {}", e.getMessage());
            }

            // 10. 自动创建补液图片凭据表 infusion_image
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `infusion_image` (" +
                        "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                        "`hospital_id` BIGINT NOT NULL DEFAULT 1 COMMENT '医院ID'," +
                        "`infusion_id` BIGINT NOT NULL COMMENT '关联补液记录ID'," +
                        "`file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名'," +
                        "`object_key` VARCHAR(512) NOT NULL COMMENT 'MinIO存储对象Key'," +
                        "`file_url` VARCHAR(1024) NOT NULL COMMENT '图片访问全路径URL'," +
                        "`file_size` BIGINT DEFAULT NULL COMMENT '文件大小(字节)'," +
                        "`upload_user_id` BIGINT NOT NULL COMMENT '上传人ID'," +
                        "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "PRIMARY KEY (`id`)," +
                        "KEY `idx_infusion_id` (`infusion_id`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补液凭据图片表'");
                // 升级检查：检查是否存在 hospital_id 列
                try {
                    jdbcTemplate.execute("ALTER TABLE `infusion_image` ADD COLUMN `hospital_id` BIGINT NOT NULL DEFAULT 1 COMMENT '医院ID' AFTER `infusion_id`");
                } catch (Exception ignored) {}
                log.info("已检查并确保 infusion_image 补液图片表创建成功");
            } catch (Exception e) {
                log.warn("创建 infusion_image 表异常: {}", e.getMessage());
            }

            // 11. 自动创建功能二：每日交接班特殊记录表 shift_handover_special
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `shift_handover_special` (" +
                        "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                        "`hospital_id` BIGINT NOT NULL DEFAULT 1 COMMENT '医院ID'," +
                        "`ward_id` BIGINT NOT NULL COMMENT '病区ID'," +
                        "`handover_date` DATE NOT NULL COMMENT '交接日期'," +
                        "`shift_type` VARCHAR(20) NOT NULL DEFAULT 'ALL_DAY' COMMENT '班次类型(ALL_DAY-全天/DAY-白班/NIGHT-夜班)'," +
                        "`leave_hospital_json` MEDIUMTEXT DEFAULT NULL COMMENT '请假记录JSON'," +
                        "`laxative_json` MEDIUMTEXT DEFAULT NULL COMMENT '泻药记录JSON'," +
                        "`temperature_abnormal_json` MEDIUMTEXT DEFAULT NULL COMMENT '体温异常记录JSON'," +
                        "`blood_sugar_json` MEDIUMTEXT DEFAULT NULL COMMENT '血糖记录JSON'," +
                        "`blood_pressure_json` MEDIUMTEXT DEFAULT NULL COMMENT '血压记录JSON'," +
                        "`urine_volume_json` MEDIUMTEXT DEFAULT NULL COMMENT '尿量记录JSON'," +
                        "`special_cases` MEDIUMTEXT DEFAULT NULL COMMENT '特殊情况文字自由记录'," +
                        "`pipe_level1_json` MEDIUMTEXT DEFAULT NULL COMMENT 'I级护理床位清单JSON'," +
                        "`pipe_monitor_json` MEDIUMTEXT DEFAULT NULL COMMENT '监护床位清单JSON'," +
                        "`pipe_maint_fluid_json` MEDIUMTEXT DEFAULT NULL COMMENT '维持液床位清单JSON'," +
                        "`pipe_ivg_json` MEDIUMTEXT DEFAULT NULL COMMENT 'IVG床位清单JSON'," +
                        "`pipe_picc_json` MEDIUMTEXT DEFAULT NULL COMMENT 'PICC床位清单JSON'," +
                        "`pipe_cvc_json` MEDIUMTEXT DEFAULT NULL COMMENT 'CVC床位清单JSON'," +
                        "`pipe_port_json` MEDIUMTEXT DEFAULT NULL COMMENT 'PORT输液港床位清单JSON'," +
                        "`pipe_nasobiliary_json` MEDIUMTEXT DEFAULT NULL COMMENT '鼻胆管床位清单JSON'," +
                        "`pipe_gastro_decomp_json` MEDIUMTEXT DEFAULT NULL COMMENT '胃肠减压管床位清单JSON'," +
                        "`pipe_catheter_json` MEDIUMTEXT DEFAULT NULL COMMENT '导尿管床位清单JSON'," +
                        "`pipe_abdom_drain_json` MEDIUMTEXT DEFAULT NULL COMMENT '腹腔引流管床位清单JSON'," +
                        "`pipe_other_json` MEDIUMTEXT DEFAULT NULL COMMENT '其他导管床位清单JSON'," +
                        "`status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿编辑中, SUBMITTED-已交班提交, LOCKED-护士长已归档锁定'," +
                        "`last_editor_id` BIGINT DEFAULT NULL COMMENT '最后更新人护士ID'," +
                        "`last_editor_name` VARCHAR(64) DEFAULT NULL COMMENT '最后更新人姓名'," +
                        "`head_nurse_signed` TINYINT(1) DEFAULT 0 COMMENT '护士长是否核签: 0-未签, 1-已签'," +
                        "`head_nurse_signed_time` DATETIME DEFAULT NULL COMMENT '护士长核签时间'," +
                        "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "PRIMARY KEY (`id`)," +
                        "UNIQUE KEY `uk_ward_date_shift` (`ward_id`, `handover_date`, `shift_type`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日交接班特殊记录表'");
                log.info("已检查并确保 shift_handover_special 特殊交接班表创建成功");
            } catch (Exception e) {
                log.warn("创建 shift_handover_special 表异常: {}", e.getMessage());
            }

            // 11. 检查并为 infusion_record 表添加 remind_status 字段
            try {
                jdbcTemplate.execute("ALTER TABLE `infusion_record` ADD COLUMN `remind_status` INT NOT NULL DEFAULT 0 COMMENT '提醒状态: 0-未提醒, 1-即将结束已提醒, 2-超时已提醒' AFTER `remark`");
                log.info("已成功为 infusion_record 表添加 remind_status 字段");
            } catch (Exception e) {
                // 已存在忽略
            }

            // 12. 检查并为 nurse 表添加 wx_notice_openid 字段
            try {
                jdbcTemplate.execute("ALTER TABLE `nurse` ADD COLUMN `wx_notice_openid` VARCHAR(64) DEFAULT NULL COMMENT '微信测试号通知OpenID' AFTER `bound_user_id`");
                log.info("已成功为 nurse 表添加 wx_notice_openid 字段");
            } catch (Exception e) {
                // 已存在忽略
            }

            // 13. 出入量记录删除留痕表（谁删的、删了什么、为什么删）
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `record_delete_log` (" +
                        "`id` BIGINT NOT NULL AUTO_INCREMENT," +
                        "`category` VARCHAR(16) NOT NULL COMMENT 'INTAKE/OUTPUT'," +
                        "`record_id` BIGINT NOT NULL COMMENT '被删除记录ID'," +
                        "`patient_id` BIGINT DEFAULT NULL COMMENT '患者ID'," +
                        "`amount` DECIMAL(10,2) DEFAULT NULL COMMENT '删除时的量(ml)'," +
                        "`item_name` VARCHAR(128) DEFAULT NULL COMMENT '品名/类型'," +
                        "`record_time` DATETIME DEFAULT NULL COMMENT '原记录时间'," +
                        "`reason` VARCHAR(255) DEFAULT NULL COMMENT '删除原因'," +
                        "`operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID'," +
                        "`operator_role` VARCHAR(32) DEFAULT NULL COMMENT '操作人角色'," +
                        "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '删除时间'," +
                        "PRIMARY KEY (`id`)," +
                        "KEY `idx_patient` (`patient_id`)," +
                        "KEY `idx_time` (`create_time`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出入量删除留痕'");
                log.info("已检查并确保 record_delete_log 删除留痕表创建成功");
            } catch (Exception e) {
                log.warn("创建 record_delete_log 表异常: {}", e.getMessage());
            }

            // 7. 患者「记错了」待核对请求表
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `record_correction_request` (" +
                        "`id` BIGINT NOT NULL AUTO_INCREMENT," +
                        "`record_id` BIGINT NOT NULL COMMENT '反馈的记录ID'," +
                        "`category` VARCHAR(16) NOT NULL COMMENT 'INTAKE/OUTPUT'," +
                        "`patient_id` BIGINT NOT NULL COMMENT '患者ID'," +
                        "`amount` DECIMAL(10,2) DEFAULT NULL COMMENT '记录量快照'," +
                        "`item_name` VARCHAR(128) DEFAULT NULL COMMENT '品名快照'," +
                        "`record_time` DATETIME DEFAULT NULL COMMENT '原记录时间'," +
                        "`remark` VARCHAR(255) DEFAULT NULL COMMENT '原记录备注'," +
                        "`reason` VARCHAR(255) DEFAULT NULL COMMENT '患者说明哪里错了'," +
                        "`requester_id` BIGINT DEFAULT NULL COMMENT '提交人ID'," +
                        "`requester_role` VARCHAR(32) DEFAULT NULL COMMENT '提交人角色'," +
                        "`status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/HANDLED'," +
                        "`handler_id` BIGINT DEFAULT NULL COMMENT '处理人ID'," +
                        "`handle_time` DATETIME DEFAULT NULL COMMENT '处理时间'," +
                        "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间'," +
                        "PRIMARY KEY (`id`)," +
                        "KEY `idx_status` (`status`)," +
                        "KEY `idx_record` (`record_id`, `category`)," +
                        "KEY `idx_patient` (`patient_id`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者记错反馈（待核对）'");
                log.info("已检查并确保 record_correction_request 待核对表创建成功");
            } catch (Exception e) {
                log.warn("创建 record_correction_request 表异常: {}", e.getMessage());
            }

            log.info(">>> 数据库表结构与索引升级检查完成！");
        } catch (Exception e) {
            log.error("数据库升级 runner 执行失败", e);
        }
    }
}
