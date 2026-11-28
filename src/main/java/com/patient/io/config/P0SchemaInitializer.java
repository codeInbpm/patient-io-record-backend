package com.patient.io.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P0 闭环增强的表结构自愈。
 *
 * 为什么不只用 SQL 脚本：现场部署时运维常常忘记执行迁移，页面一打开就报
 * 「Unknown column 'handle_action'」。这里在启动时按「表在不在 / 列在不在」逐项补齐，
 * 与 migration_p0.sql 完全等价且可重复执行；DDL 失败只记录告警，不阻断应用启动，
 * 避免因为数据库账号没有 DDL 权限导致整个系统起不来。
 */
@Slf4j
@Component
@Order(1)
public class P0SchemaInitializer implements ApplicationRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 反馈单需要补齐的列：列名 -> 列定义 */
    private static final Map<String, String> FEEDBACK_COLUMNS = new LinkedHashMap<>();

    static {
        FEEDBACK_COLUMNS.put("level", "varchar(16) NOT NULL DEFAULT 'MID' COMMENT '严重度 HIGH/MID/LOW'");
        FEEDBACK_COLUMNS.put("source", "varchar(32) NOT NULL DEFAULT 'MINIAPP' COMMENT '来源 MINIAPP/FAMILY'");
        FEEDBACK_COLUMNS.put("ward_id", "bigint DEFAULT NULL COMMENT '病区ID冗余'");
        FEEDBACK_COLUMNS.put("handle_action", "varchar(32) DEFAULT NULL COMMENT 'FIX/CONFIRM/REJECT/REFER/REPLY/DEFER'");
        FEEDBACK_COLUMNS.put("handle_note", "varchar(500) DEFAULT NULL COMMENT '处理说明'");
        FEEDBACK_COLUMNS.put("handler_name", "varchar(64) DEFAULT NULL COMMENT '处理人姓名快照'");
        FEEDBACK_COLUMNS.put("corrected_amount", "decimal(10,2) DEFAULT NULL COMMENT '修正后数值'");
        FEEDBACK_COLUMNS.put("defer_until", "datetime DEFAULT NULL COMMENT '延后处理截止时间'");
        FEEDBACK_COLUMNS.put("sync_status", "varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '结果回传状态'");
    }

    private static final String SHIFT_HANDOVER_DDL =
            "CREATE TABLE IF NOT EXISTS `shift_handover` (" +
                    "`id` bigint NOT NULL AUTO_INCREMENT," +
                    "`hospital_id` bigint NOT NULL DEFAULT '1'," +
                    "`ward_id` bigint NOT NULL," +
                    "`shift_date` date NOT NULL," +
                    "`shift_type` varchar(20) NOT NULL DEFAULT 'NIGHT'," +
                    "`status` varchar(20) NOT NULL DEFAULT 'DRAFT'," +
                    "`memo` text," +
                    "`report_text` mediumtext," +
                    "`stats_json` mediumtext," +
                    "`patient_count` int DEFAULT '0'," +
                    "`submit_by` bigint DEFAULT NULL," +
                    "`submit_by_name` varchar(64) DEFAULT NULL," +
                    "`submit_time` datetime DEFAULT NULL," +
                    "`receiver_id` bigint DEFAULT NULL," +
                    "`receiver_name` varchar(64) DEFAULT NULL," +
                    "`receive_time` datetime DEFAULT NULL," +
                    "`signature_url` varchar(500) DEFAULT NULL," +
                    "`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (`id`)," +
                    "UNIQUE KEY `uk_ward_date_shift` (`ward_id`,`shift_date`,`shift_type`)," +
                    "KEY `idx_hospital_date` (`hospital_id`,`shift_date`)," +
                    "KEY `idx_status` (`status`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交班记录（含状态机）'";

    private static final String ALERT_ACK_DDL =
            "CREATE TABLE IF NOT EXISTS `alert_ack` (" +
                    "`id` bigint NOT NULL AUTO_INCREMENT," +
                    "`hospital_id` bigint NOT NULL DEFAULT '1'," +
                    "`alert_key` varchar(128) NOT NULL," +
                    "`alert_type` varchar(32) NOT NULL," +
                    "`ref_id` bigint DEFAULT NULL," +
                    "`patient_id` bigint DEFAULT NULL," +
                    "`ward_id` bigint DEFAULT NULL," +
                    "`level` varchar(16) NOT NULL DEFAULT 'MID'," +
                    "`status` varchar(16) NOT NULL DEFAULT 'OPEN'," +
                    "`owner_id` bigint DEFAULT NULL," +
                    "`owner_name` varchar(64) DEFAULT NULL," +
                    "`ack_time` datetime DEFAULT NULL," +
                    "`close_time` datetime DEFAULT NULL," +
                    "`note` varchar(500) DEFAULT NULL," +
                    "`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (`id`)," +
                    "UNIQUE KEY `uk_alert_key` (`alert_key`)," +
                    "KEY `idx_status` (`hospital_id`,`status`)," +
                    "KEY `idx_ward` (`ward_id`,`status`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警处置状态'";

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureTable("shift_handover", SHIFT_HANDOVER_DDL);
            ensureTable("alert_ack", ALERT_ACK_DDL);
            ensureFeedbackColumns();
            log.info("P0 表结构自检完成");
        } catch (Exception e) {
            log.warn("P0 表结构自检未完成（可能是数据库账号缺少 DDL 权限），请手工执行 db/migration_p0.sql：{}", e.getMessage());
        }
    }

    private void ensureTable(String table, String ddl) {
        if (!tableExists(table)) {
            jdbcTemplate.execute(ddl);
            log.info("已创建表 {}", table);
        }
    }

    private boolean tableExists(String table) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return c != null && c > 0;
    }

    private void ensureFeedbackColumns() {
        List<String> existing = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'record_correction_request'",
                String.class);
        if (existing.isEmpty()) {
            log.warn("record_correction_request 表不存在，跳过反馈字段补齐");
            return;
        }
        for (Map.Entry<String, String> entry : FEEDBACK_COLUMNS.entrySet()) {
            if (!existing.contains(entry.getKey())) {
                jdbcTemplate.execute("ALTER TABLE `record_correction_request` ADD COLUMN `"
                        + entry.getKey() + "` " + entry.getValue());
                log.info("已为 record_correction_request 补齐列 {}", entry.getKey());
            }
        }
        // 历史数据回填：把病区从患者表补到反馈单上，避免跨病区筛选时漏数据
        try {
            jdbcTemplate.update("UPDATE record_correction_request r JOIN patient p ON r.patient_id = p.id " +
                    "SET r.ward_id = p.ward_id WHERE r.ward_id IS NULL");
        } catch (Exception e) {
            log.debug("反馈单病区回填跳过: {}", e.getMessage());
        }
    }

    /** 供单元测试与运维检查使用 */
    public static List<String> requiredFeedbackColumns() {
        return Arrays.asList(FEEDBACK_COLUMNS.keySet().toArray(new String[0]));
    }
}
