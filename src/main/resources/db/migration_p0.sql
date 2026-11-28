-- =========================================================
-- P0 闭环增强迁移（migration_p0）
-- 说明：
--   1. 本脚本由 P0SchemaInitializer 在应用启动时按「表/列是否存在」逐项执行，
--      与手工执行完全等价，重复执行安全。
--   2. 若由 DBA 手工执行，MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，
--      请先确认列不存在，或直接交给应用启动时自动补齐。
-- =========================================================

-- ---------------------------------------------------------
-- 1. 反馈单扩展：处理动作、处理说明、修正值、严重度、回传状态
-- ---------------------------------------------------------
ALTER TABLE `record_correction_request`
  ADD COLUMN `level` varchar(16) NOT NULL DEFAULT 'MID' COMMENT '严重度 HIGH/MID/LOW',
  ADD COLUMN `source` varchar(32) NOT NULL DEFAULT 'MINIAPP' COMMENT '来源 MINIAPP/FAMILY',
  ADD COLUMN `ward_id` bigint DEFAULT NULL COMMENT '病区ID冗余（便于跨病区筛选）',
  ADD COLUMN `handle_action` varchar(32) DEFAULT NULL COMMENT 'FIX/CONFIRM/REJECT/REFER/REPLY/DEFER',
  ADD COLUMN `handle_note` varchar(500) DEFAULT NULL COMMENT '处理说明（驳回原因/转交意见/回复内容）',
  ADD COLUMN `handler_name` varchar(64) DEFAULT NULL COMMENT '处理人姓名快照',
  ADD COLUMN `corrected_amount` decimal(10,2) DEFAULT NULL COMMENT '修正后数值',
  ADD COLUMN `defer_until` datetime DEFAULT NULL COMMENT '延后处理截止时间',
  ADD COLUMN `sync_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SYNCED/FAILED 结果回传小程序端状态';

-- ---------------------------------------------------------
-- 2. 交班记录（PC 只落提交人/时间/班次；签名与接班确认在小程序端）
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shift_handover` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '医院ID',
  `ward_id` bigint NOT NULL COMMENT '病区ID',
  `shift_date` date NOT NULL COMMENT '交班日期',
  `shift_type` varchar(20) NOT NULL DEFAULT 'NIGHT' COMMENT 'DAY-白班, NIGHT-夜班',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT-草稿, SUBMITTED-已提交, LOCKED-已锁定, CONFIRMED-接班人已确认',
  `memo` text COMMENT '护士长叮嘱（交接重点）',
  `report_text` mediumtext COMMENT '交班报告正文',
  `stats_json` mediumtext COMMENT '提交时的病区统计快照',
  `patient_count` int DEFAULT '0' COMMENT '交班时在院人数快照',
  `submit_by` bigint DEFAULT NULL COMMENT '提交人ID',
  `submit_by_name` varchar(64) DEFAULT NULL COMMENT '提交人姓名',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `receiver_id` bigint DEFAULT NULL COMMENT '接班护士ID（小程序端）',
  `receiver_name` varchar(64) DEFAULT NULL COMMENT '接班护士姓名',
  `receive_time` datetime DEFAULT NULL COMMENT '接班确认时间',
  `signature_url` varchar(500) DEFAULT NULL COMMENT '电子签名图片地址（小程序端上传）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ward_date_shift` (`ward_id`,`shift_date`,`shift_type`),
  KEY `idx_hospital_date` (`hospital_id`,`shift_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交班记录（含状态机）';

-- ---------------------------------------------------------
-- 3. 告警处理状态（告警本身由实时规则聚合生成，此表只存处置轨迹）
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `alert_ack` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '医院ID',
  `alert_key` varchar(128) NOT NULL COMMENT '告警唯一键 BALANCE:{patientId}:{date} 等',
  `alert_type` varchar(32) NOT NULL COMMENT 'BALANCE/INFUSION/ASSIGN/HANDOVER',
  `ref_id` bigint DEFAULT NULL COMMENT '关联业务ID',
  `patient_id` bigint DEFAULT NULL COMMENT '关联患者',
  `ward_id` bigint DEFAULT NULL COMMENT '关联病区',
  `level` varchar(16) NOT NULL DEFAULT 'MID' COMMENT 'HIGH/MID/LOW',
  `status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN-待处理, ACKED-处理中, CLOSED-已关闭',
  `owner_id` bigint DEFAULT NULL COMMENT '认领人ID',
  `owner_name` varchar(64) DEFAULT NULL COMMENT '认领人姓名',
  `ack_time` datetime DEFAULT NULL COMMENT '认领时间',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `note` varchar(500) DEFAULT NULL COMMENT '处理说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alert_key` (`alert_key`),
  KEY `idx_status` (`hospital_id`,`status`),
  KEY `idx_ward` (`ward_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='告警处置状态';
