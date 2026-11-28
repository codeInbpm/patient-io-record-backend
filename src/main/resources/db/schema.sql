-- ========================================================
-- 病患出入量记录助手 - 完整表结构（与线上生产库 139.224.27.132/patient_io_record 保持一致）
--
-- 说明：
--   1. 本文件为「手动执行」脚本，服务启动时不再自动建表/改表（原 DatabaseUpgradeRunner 已移除）。
--   2. 全部语句均为 CREATE TABLE IF NOT EXISTS，重复执行安全，不会影响已有数据。
--   3. 表结构有变更时，请直接改本文件并在数据库执行 ALTER，同时更新本文件的最终结构。
--   4. 生成时间：2026-11-25，MySQL 8.0.27
-- ========================================================

CREATE DATABASE IF NOT EXISTS `patient_io_record` DEFAULT CHARACTER SET utf8mb4;
USE `patient_io_record`;

-- 1. hospital
CREATE TABLE IF NOT EXISTS `hospital` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_name` varchar(100) NOT NULL COMMENT '医院名称',
  `hospital_code` varchar(50) DEFAULT NULL COMMENT '医院编码',
  `contact_person` varchar(32) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `address` varchar(255) DEFAULT NULL COMMENT '医院地址',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-启用, 0-停用',
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hospital_name` (`hospital_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医院字典表';

-- 2. department
CREATE TABLE IF NOT EXISTS `department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL COMMENT '所属医院ID',
  `dept_name` varchar(100) NOT NULL COMMENT '科室名称',
  `dept_code` varchar(50) DEFAULT NULL COMMENT '科室编码',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-启用, 0-停用',
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hospital_dept` (`hospital_id`,`dept_name`),
  KEY `idx_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室字典表';

-- 3. ward
CREATE TABLE IF NOT EXISTS `ward` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '病房ID',
  `ward_name` varchar(64) NOT NULL COMMENT '病房名称（如：心内科三病区、骨科一病区）',
  `building` varchar(64) DEFAULT '2号楼' COMMENT '所在楼宇(如:1号楼、2号楼)',
  `hospital_id` bigint DEFAULT NULL,
  `hospital_name` varchar(100) DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL,
  `department` varchar(64) NOT NULL COMMENT '科室名称',
  `room_no` varchar(32) DEFAULT NULL COMMENT '房间号（如：301室）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序权重',
  `create_by` bigint DEFAULT NULL COMMENT '创建护士长ID',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-启用, 0-停用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='病房表';

-- 4. bed
CREATE TABLE IF NOT EXISTS `bed` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '床位ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '所属医院ID',
  `ward_id` bigint NOT NULL COMMENT '所属病房ID',
  `room_no` varchar(32) DEFAULT NULL COMMENT '所属房间',
  `bed_no` varchar(20) NOT NULL COMMENT '床位编号（如：01床、02床）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '床位状态：0-空床, 1-占用',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ward_bed` (`ward_id`,`bed_no`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='床位表';

-- 5. sys_user
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `openid` varchar(64) NOT NULL COMMENT '微信openid',
  `unionid` varchar(64) DEFAULT NULL COMMENT '微信unionid',
  `nickname` varchar(64) DEFAULT '微信用户' COMMENT '微信昵称',
  `avatar_url` varchar(255) DEFAULT '' COMMENT '头像链接',
  `role` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '当前角色：HEAD_NURSE(护士长), NURSE(护士), PATIENT(患者), NONE(未绑定)',
  `bind_status` tinyint NOT NULL DEFAULT '0' COMMENT '绑定状态：0-未绑定, 1-已绑定',
  `bind_ref_id` bigint DEFAULT NULL COMMENT '绑定的实体ID（nurse.id 或 patient.id）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常, 0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信用户表';

-- 6. sys_admin
CREATE TABLE IF NOT EXISTS `sys_admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL COMMENT '管理员账号',
  `password` varchar(128) NOT NULL COMMENT '密码',
  `real_name` varchar(64) NOT NULL DEFAULT '超级管理员' COMMENT '真实姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `bound_user_id` bigint DEFAULT NULL COMMENT '绑定的微信sys_user.id',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-正常, 0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_bound_user` (`bound_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='超级管理员表';

-- 7. sys_admin_user
CREATE TABLE IF NOT EXISTS `sys_admin_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password` varchar(128) NOT NULL COMMENT '登录密码(加密)',
  `real_name` varchar(32) NOT NULL COMMENT '真实姓名',
  `role` varchar(32) NOT NULL COMMENT '角色：SUPER_ADMIN(平台超管), HOSPITAL_ADMIN(医院管理员)',
  `hospital_id` bigint DEFAULT NULL COMMENT '所属医院ID (超管为NULL)',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系手机',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常, 0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_hospital_id` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台管理员用户表';

-- 8. nurse
CREATE TABLE IF NOT EXISTS `nurse` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_number` varchar(6) NOT NULL COMMENT '6位纯数字唯一工号(如100001)',
  `real_name` varchar(32) NOT NULL COMMENT '医护姓名',
  `role` varchar(20) NOT NULL DEFAULT 'NURSE' COMMENT '角色：HEAD_NURSE(护士长), NURSE(护士)',
  `hospital_id` bigint DEFAULT NULL,
  `hospital_name` varchar(100) DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL,
  `ward_id` bigint DEFAULT NULL COMMENT '所属病区ID',
  `ward_name` varchar(64) DEFAULT NULL COMMENT '所属病区名称',
  `building` varchar(64) DEFAULT NULL COMMENT '所在楼宇',
  `department` varchar(64) NOT NULL COMMENT '所属科室',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `bound_user_id` bigint DEFAULT NULL COMMENT '绑定的微信sys_user.id',
  `wx_notice_openid` varchar(64) DEFAULT NULL COMMENT '微信测试号通知OpenID',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-在职, 0-离职',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_number` (`job_number`),
  KEY `idx_bound_user` (`bound_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医护人员名册表';

-- 9. patient
CREATE TABLE IF NOT EXISTS `patient` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '病人ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '所属医院ID',
  `ward_id` bigint NOT NULL COMMENT '所属病房ID',
  `bed_id` bigint NOT NULL COMMENT '所属床位ID',
  `bed_no` varchar(20) NOT NULL COMMENT '床号冗余',
  `patient_name` varchar(32) NOT NULL COMMENT '病人真实姓名',
  `hospital_no` varchar(32) NOT NULL COMMENT '住院号',
  `gender` tinyint DEFAULT '1' COMMENT '性别：1-男, 2-女',
  `age` int DEFAULT NULL COMMENT '年龄',
  `patient_type` varchar(20) DEFAULT 'ADULT' COMMENT 'ADULT(成人), INFANT(婴儿)',
  `admission_type` varchar(20) DEFAULT 'ROUTINE' COMMENT 'ROUTINE(平诊), EMERGENCY(急诊)',
  `weight` decimal(5,2) DEFAULT '60.00' COMMENT '体重(kg)',
  `nursing_level` varchar(20) DEFAULT 'LEVEL_2' COMMENT 'SPECIAL(特级), LEVEL_1(一级), LEVEL_2(二级), LEVEL_3(三级)',
  `need_io_record` tinyint NOT NULL DEFAULT '1' COMMENT '1-监测出入量, 0-不监测',
  `drainage_tubes` varchar(128) DEFAULT NULL COMMENT '留置管路类型，逗号分隔',
  `admission_date` date NOT NULL COMMENT '入院日期',
  `diagnosis` varchar(128) DEFAULT NULL COMMENT '临床诊断',
  `bound_user_id` bigint DEFAULT NULL COMMENT '已绑定的微信sys_user.id',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '住院状态：1-在院, 2-已出院',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hospital_no` (`hospital_no`,`is_deleted`),
  KEY `idx_match_patient` (`patient_name`,`ward_id`,`bed_no`),
  KEY `idx_bound_user` (`bound_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='住院病人表';

-- 10. nurse_patient_relation
CREATE TABLE IF NOT EXISTS `nurse_patient_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '所属医院ID',
  `nurse_id` bigint NOT NULL COMMENT '护士nurse.id',
  `ward_id` bigint DEFAULT NULL,
  `patient_id` bigint DEFAULT NULL COMMENT '负责的病人patient.id',
  `bed_id` bigint NOT NULL COMMENT '床位bed.id',
  `assign_by` bigint NOT NULL COMMENT '分配人(护士长ID)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nurse_bed` (`nurse_id`,`bed_id`),
  KEY `idx_patient` (`patient_id`),
  KEY `idx_nurse` (`nurse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='护士与患者分配关系表';

-- 11. intake_record
CREATE TABLE IF NOT EXISTS `intake_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '入量记录ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '所属医院ID',
  `patient_id` bigint NOT NULL COMMENT '病人ID',
  `ward_id` bigint NOT NULL COMMENT '病房ID',
  `intake_type` varchar(32) NOT NULL COMMENT 'ORAL(口服), IV(输液), NASOGASTRIC(鼻饲), OTHER(其他)',
  `sub_type` varchar(32) DEFAULT NULL COMMENT 'WATER, CONGEE, NOODLES, RICE_SOUP, LOTUS_STARCH, CUSTOM',
  `item_name` varchar(64) NOT NULL COMMENT '品名',
  `amount` decimal(8,2) NOT NULL COMMENT '量(ml)',
  `record_time` datetime NOT NULL COMMENT '摄入时间(精确到分)',
  `record_date` date NOT NULL COMMENT '归属自然日',
  `recorder_id` bigint NOT NULL COMMENT '录入人user_id',
  `recorder_role` varchar(20) NOT NULL COMMENT 'HEAD_NURSE, NURSE, PATIENT',
  `remark` varchar(255) DEFAULT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_patient_date` (`patient_id`,`record_date`),
  KEY `idx_ward_date` (`ward_id`,`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入量记录表';

-- 12. output_record
CREATE TABLE IF NOT EXISTS `output_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '出量记录ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '所属医院ID',
  `patient_id` bigint NOT NULL COMMENT '病人ID',
  `ward_id` bigint NOT NULL COMMENT '病房ID',
  `output_type` varchar(32) NOT NULL COMMENT 'URINE(尿量), STOOL(大便量), GASTRO_DRAINAGE(胃肠减压引流), OTHER_DRAINAGE(其他引流)',
  `amount` decimal(8,2) NOT NULL COMMENT '排出量(ml)',
  `nature_or_color` varchar(64) DEFAULT NULL COMMENT '性状颜色',
  `record_time` datetime NOT NULL COMMENT '排出时间(精确到分)',
  `record_date` date NOT NULL COMMENT '归属自然日',
  `recorder_id` bigint NOT NULL COMMENT '录入人user_id',
  `recorder_role` varchar(20) NOT NULL COMMENT 'HEAD_NURSE, NURSE, PATIENT',
  `remark` varchar(255) DEFAULT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_patient_date` (`patient_id`,`record_date`),
  KEY `idx_ward_date` (`ward_id`,`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='出量记录表';

-- 13. daily_summary
CREATE TABLE IF NOT EXISTS `daily_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '汇总ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '所属医院ID',
  `patient_id` bigint NOT NULL,
  `ward_id` bigint NOT NULL,
  `record_date` date NOT NULL,
  `total_intake` decimal(8,2) NOT NULL DEFAULT '0.00',
  `total_output` decimal(8,2) NOT NULL DEFAULT '0.00',
  `net_balance` decimal(8,2) NOT NULL DEFAULT '0.00',
  `oral_intake` decimal(8,2) NOT NULL DEFAULT '0.00',
  `iv_intake` decimal(8,2) NOT NULL DEFAULT '0.00',
  `nasogastric_intake` decimal(8,2) NOT NULL DEFAULT '0.00',
  `urine_output` decimal(8,2) NOT NULL DEFAULT '0.00',
  `stool_output` decimal(8,2) NOT NULL DEFAULT '0.00',
  `drainage_output` decimal(8,2) NOT NULL DEFAULT '0.00',
  `is_abnormal` tinyint NOT NULL DEFAULT '0' COMMENT '1-异常预警(如24h尿量<400ml), 0-正常',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_patient_date` (`patient_id`,`record_date`),
  KEY `idx_ward_date` (`ward_id`,`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日出入平衡汇总表';

-- 14. infusion_record
CREATE TABLE IF NOT EXISTS `infusion_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '医院ID(租户隔离)',
  `ward_id` bigint NOT NULL COMMENT '病区ID',
  `bed_id` bigint NOT NULL COMMENT '床位ID',
  `bed_no` varchar(32) NOT NULL COMMENT '床号冗余',
  `patient_id` bigint NOT NULL COMMENT '患者ID',
  `patient_name` varchar(64) NOT NULL COMMENT '患者姓名冗余',
  `capacity` int NOT NULL COMMENT '补液容量(ml)',
  `usage_type` varchar(64) NOT NULL COMMENT '给药用法(ivg/ibn/iv drip/自定义)',
  `start_time` datetime NOT NULL COMMENT '开始补液时间',
  `estimated_end_time` datetime NOT NULL COMMENT '预计拔针时间',
  `actual_end_time` datetime DEFAULT NULL COMMENT '实际结束时间',
  `status` varchar(20) NOT NULL DEFAULT 'INFUSING' COMMENT '状态: INFUSING-正在补液, COMPLETED-正常结束, STOPPED-异常中断',
  `actual_intake_amount` int DEFAULT NULL COMMENT '实际滴入量(ml)',
  `is_synced_intake` tinyint(1) DEFAULT '1' COMMENT '是否已同步入量记录: 1-是, 0-否',
  `intake_record_id` bigint DEFAULT NULL COMMENT '关联的入量记录ID',
  `nurse_id` bigint NOT NULL COMMENT '经办护士ID',
  `nurse_name` varchar(64) NOT NULL COMMENT '经办护士姓名',
  `end_nurse_id` bigint DEFAULT NULL COMMENT '拔针/结束护士ID',
  `end_nurse_name` varchar(64) DEFAULT NULL COMMENT '拔针/结束护士姓名',
  `remark` varchar(500) DEFAULT NULL COMMENT '补液备注',
  `remind_status` int NOT NULL DEFAULT '0' COMMENT '提醒状态: 0-未提醒, 1-即将结束已提醒, 2-超时已提醒',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常, 1-删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ward_status` (`ward_id`,`status`),
  KEY `idx_patient_time` (`patient_id`,`start_time`),
  KEY `idx_estimated_end` (`status`,`estimated_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='患者补液记录表';

-- 15. infusion_image
CREATE TABLE IF NOT EXISTS `infusion_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `infusion_id` bigint NOT NULL COMMENT '关联补液记录ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '医院ID',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `object_key` varchar(512) NOT NULL COMMENT 'MinIO存储对象Key',
  `file_url` varchar(1024) NOT NULL COMMENT '图片访问全路径URL',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `upload_user_id` bigint NOT NULL COMMENT '上传人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_infusion_id` (`infusion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='补液凭据图片表';

-- 16. shift_handover_special
CREATE TABLE IF NOT EXISTS `shift_handover_special` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '医院ID',
  `ward_id` bigint NOT NULL COMMENT '病区ID',
  `handover_date` date NOT NULL COMMENT '交接日期',
  `shift_type` varchar(20) NOT NULL DEFAULT 'ALL_DAY' COMMENT '班次类型(ALL_DAY-全天/DAY-白班/NIGHT-夜班)',
  `leave_hospital_json` mediumtext COMMENT '请假记录JSON',
  `laxative_json` mediumtext COMMENT '泻药记录JSON',
  `temperature_abnormal_json` mediumtext COMMENT '体温异常记录JSON',
  `blood_sugar_json` mediumtext COMMENT '血糖记录JSON',
  `blood_pressure_json` mediumtext COMMENT '血压记录JSON',
  `urine_volume_json` mediumtext COMMENT '尿量记录JSON',
  `special_cases` mediumtext COMMENT '特殊情况文字自由记录',
  `pipe_level1_json` mediumtext COMMENT 'I级护理床位清单JSON',
  `pipe_monitor_json` mediumtext COMMENT '监护床位清单JSON',
  `pipe_maint_fluid_json` mediumtext COMMENT '维持液床位清单JSON',
  `pipe_ivg_json` mediumtext COMMENT 'IVG床位清单JSON',
  `pipe_picc_json` mediumtext COMMENT 'PICC床位清单JSON',
  `pipe_cvc_json` mediumtext COMMENT 'CVC床位清单JSON',
  `pipe_port_json` mediumtext COMMENT 'PORT输液港床位清单JSON',
  `pipe_nasobiliary_json` mediumtext COMMENT '鼻胆管床位清单JSON',
  `pipe_gastro_decomp_json` mediumtext COMMENT '胃肠减压管床位清单JSON',
  `pipe_catheter_json` mediumtext COMMENT '导尿管床位清单JSON',
  `pipe_abdom_drain_json` mediumtext COMMENT '腹腔引流管床位清单JSON',
  `pipe_other_json` mediumtext COMMENT '其他导管床位清单JSON',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿编辑中, SUBMITTED-已交班提交, LOCKED-护士长已归档锁定',
  `last_editor_id` bigint DEFAULT NULL COMMENT '最后更新人护士ID',
  `last_editor_name` varchar(64) DEFAULT NULL COMMENT '最后更新人姓名',
  `handover_nurse_id` bigint DEFAULT NULL COMMENT '交班护士ID',
  `handover_nurse_name` varchar(64) DEFAULT NULL COMMENT '交班护士姓名',
  `successor_nurse_id` bigint DEFAULT NULL COMMENT '接班护士ID',
  `successor_nurse_name` varchar(64) DEFAULT NULL COMMENT '接班护士姓名',
  `handover_time` datetime DEFAULT NULL COMMENT '交班时间',
  `successor_signed` tinyint DEFAULT '0' COMMENT '接班护士是否确认接班',
  `successor_signed_time` datetime DEFAULT NULL COMMENT '接班护士确认时间',
  `head_nurse_signed` tinyint(1) DEFAULT '0' COMMENT '护士长是否核签: 0-未签, 1-已签',
  `head_nurse_signed_time` datetime DEFAULT NULL COMMENT '护士长核签时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `successor_confirm_time` datetime DEFAULT NULL COMMENT '接班确认时间',
  `handover_notes` text COMMENT '交班备忘',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ward_date_shift` (`ward_id`,`handover_date`,`shift_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日交接班特殊记录表';

-- 17. record_delete_log
CREATE TABLE IF NOT EXISTS `record_delete_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(16) NOT NULL COMMENT 'INTAKE/OUTPUT',
  `record_id` bigint NOT NULL COMMENT '被删除记录ID',
  `patient_id` bigint DEFAULT NULL COMMENT '患者ID',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '删除时的量(ml)',
  `item_name` varchar(128) DEFAULT NULL COMMENT '品名/类型',
  `record_time` datetime DEFAULT NULL COMMENT '原记录时间',
  `reason` varchar(255) DEFAULT NULL COMMENT '删除原因',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_role` varchar(32) DEFAULT NULL COMMENT '操作人角色',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_patient` (`patient_id`),
  KEY `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='出入量删除留痕';

-- 18. record_correction_request
CREATE TABLE IF NOT EXISTS `record_correction_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `record_id` bigint NOT NULL COMMENT '反馈的记录ID',
  `category` varchar(16) NOT NULL COMMENT 'INTAKE/OUTPUT',
  `patient_id` bigint NOT NULL COMMENT '患者ID',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '记录量快照',
  `item_name` varchar(128) DEFAULT NULL COMMENT '品名快照',
  `record_time` datetime DEFAULT NULL COMMENT '原记录时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '原记录备注',
  `reason` varchar(255) DEFAULT NULL COMMENT '患者说明哪里错了',
  `requester_id` bigint DEFAULT NULL COMMENT '提交人ID',
  `requester_role` varchar(32) DEFAULT NULL COMMENT '提交人角色',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/HANDLED',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人ID',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_record` (`record_id`,`category`),
  KEY `idx_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='患者记错反馈（待核对）';

-- ========================================================
-- 可选：演示种子数据（仅全新空库初始化时执行，已有数据的环境用 INSERT IGNORE 也不会重复写入）
-- ========================================================

-- 初始业务种子数据

-- 预置医护名册：护士长工号 100001，普通护士工号 100002、100003，绑定所属医院、楼宇与病区
INSERT IGNORE INTO `nurse` (`id`, `job_number`, `real_name`, `role`, `hospital_id`, `hospital_name`, `dept_id`, `department`, `building`, `ward_id`, `ward_name`, `phone`) VALUES
(1, '100001', '张洁护士长', 'HEAD_NURSE', 1, '第一人民医院（示范本部）', 11, '7号楼', '7号楼', 36, '7#19B消化内科三病区', '13800000001'),
(2, '100002', '李丽护士', 'NURSE', 1, '第一人民医院（示范本部）', 11, '7号楼', '7号楼', 36, '7#19B消化内科三病区', '13800000002'),
(3, '100003', '王芳护士', 'NURSE', 1, '第一人民医院（示范本部）', 11, '7号楼', '7号楼', 21, '7#7A眼科病房', '13800000003');

-- 预置楼宇字典
INSERT IGNORE INTO `department` (`id`, `hospital_id`, `dept_name`, `dept_code`, `status`, `sort_order`) VALUES
(10, 1, '2号楼', 'BUILDING_2', 1, 1),
(11, 1, '7号楼', 'BUILDING_7', 1, 2),
(12, 1, '12号楼', 'BUILDING_12', 1, 3),
(13, 1, '急诊楼', 'BUILDING_EMERGENCY', 1, 4);

-- 预置病区（图3、图4、图5各楼宇病区字典，共49个）
INSERT IGNORE INTO `ward` (`id`, `ward_name`, `building`, `hospital_id`, `hospital_name`, `dept_id`, `department`, `sort_order`, `status`) VALUES
-- 2号楼 (18个)
(3, '2#18F干细胞移植病房', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 1, 1),
(4, '2#17F血液科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 2, 1),
(5, '2#16F肿瘤/COC/放疗科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 3, 1),
(6, '2#15F脊柱外科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 4, 1),
(7, '2#14F骨科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 5, 1),
(8, '2#13F内分泌', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 6, 1),
(9, '2#13F胆道外科病房', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 7, 1),
(10, '2#12F甲乳血管外科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 8, 1),
(11, '2#11F五官/胸外/整形外科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 9, 1),
(12, '2#10F胸外科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 10, 1),
(13, '2#9F呼吸科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 11, 1),
(14, '2#8F 妇 科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 12, 1),
(15, '2#7F 产 科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 13, 1),
(16, '2#6F重症监护室', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 14, 1),
(17, '2#5F心内/中医科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 15, 1),
(18, '2#4F心内/综合', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 16, 1),
(19, '2#3F心外科', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 17, 1),
(20, '2#2F ICU', '2号楼', 1, '第一人民医院（示范本部）', 10, '2号楼', 18, 1),
-- 7号楼 (16个)
(21, '7#7A眼科病房', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 1, 1),
(22, '7#7B眼科病房', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 2, 1),
(23, '7#8A眼科病房', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 3, 1),
(24, '7#8B眼科日间病房', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 4, 1),
(25, '7#14A肾移植病房', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 5, 1),
(26, '7#14B肾内科', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 6, 1),
(27, '7#15A泌尿肿瘤一科', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 7, 1),
(28, '7#15B泌尿结石/泌尿肿瘤二科', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 8, 1),
(29, '7#16A男科病房', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 9, 1),
(30, '7#16B前列腺尿控外科', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 10, 1),
(31, '7#17A肝胆外科', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 11, 1),
(32, '7#17B胃肠外科/结直肠外科', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 12, 1),
(33, '7#18A胰腺外科/胃肠外科', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 13, 1),
(34, '7#18B消化内科一病区', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 14, 1),
(35, '7#19A消化内科二病区', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 15, 1),
(36, '7#19B消化内科三病区', '7号楼', 1, '第一人民医院（示范本部）', 11, '7号楼', 16, 1),
-- 12号楼 (9个)
(37, '干保15F老年医学科', '12号楼', 1, '第一人民医院（示范本部）', 12, '12号楼', 1, 1),
(38, '干保14F老年医学科', '12号楼', 1, '第一人民医院（示范本部）', 12, '12号楼', 2, 1),
(39, '干保13F老年医学科', '12号楼', 1, '第一人民医院（示范本部）', 12, '12号楼', 3, 1),
(40, '干保12F老年医学科', '12号楼', 1, '第一人民医院（示范本部）', 12, '12号楼', 4, 1),
(41, '干保11F骨科', '12号楼', 1, '第一人民医院（示范本部）', 12, '12号楼', 5, 1),
(42, '干保10FIMCC', '12号楼', 1, '第一人民医院（示范本部）', 12, '12号楼', 6, 1),
(43, '干保9FIMCC', '12号楼', 1, '第一人民医院（示范本部）', 12, '12号楼', 7, 1),
(44, '干保8F神经内科', '12号楼', 1, '第一人民医院（示范本部）', 12, '12号楼', 8, 1),
(45, '干保7F神经外科+监护室', '12号楼', 1, '第一人民医院（示范本部）', 12, '12号楼', 9, 1),
-- 急诊楼 (6个)
(46, '4#日间化疗部', '急诊楼', 1, '第一人民医院（示范本部）', 13, '急诊楼', 1, 1),
(47, '急诊病房', '急诊楼', 1, '第一人民医院（示范本部）', 13, '急诊楼', 2, 1),
(48, '急诊EICU', '急诊楼', 1, '第一人民医院（示范本部）', 13, '急诊楼', 3, 1),
(49, '急诊抢救室', '急诊楼', 1, '第一人民医院（示范本部）', 13, '急诊楼', 4, 1),
(50, '急诊输液留观', '急诊楼', 1, '第一人民医院（示范本部）', 13, '急诊楼', 5, 1),
(51, '其他', '急诊楼', 1, '第一人民医院（示范本部）', 13, '急诊楼', 6, 1);

-- 预置床位
INSERT IGNORE INTO `bed` (`id`, `ward_id`, `bed_no`, `status`, `sort_order`) VALUES
(1, 3, '01床', 1, 1),
(2, 3, '02床', 1, 2),
(3, 3, '03床', 0, 3);

-- 预置病患档案（2#18F干细胞移植病房，01床李建国，02床王秀英）
INSERT IGNORE INTO `patient` (`id`, `ward_id`, `bed_id`, `bed_no`, `patient_name`, `hospital_no`, `gender`, `age`, `admission_date`, `diagnosis`, `status`) VALUES
(1, 3, 1, '01床', '李建国', 'ZY202611001', 1, 62, '2026-11-15', '急性心肌梗死合并心力衰竭', 1),
(2, 3, 2, '02床', '王秀英', 'ZY202611002', 2, 58, '2026-11-16', '高血压脑出血恢复期', 1);

-- 预置护士分配关系：李丽护士(nurse_id=2)负责01床李建国，王芳护士(nurse_id=3)负责02床王秀英
INSERT IGNORE INTO `nurse_patient_relation` (`id`, `nurse_id`, `patient_id`, `bed_id`, `assign_by`) VALUES
(1, 2, 1, 1, 1),
(2, 3, 2, 2, 1);

-- ========================================================
-- 已废弃：db_schema_version 版本标记表
-- 原先由 DatabaseUpgradeRunner 在启动时自动建表/改表并记录版本，现改为手动执行 SQL，
-- 该表及启动检查逻辑均已移除（线上库中的 db_schema_version 已删除）。
-- ========================================================

-- ========================================================
-- 药品字典（补液用药）与补液记录结构化用药
-- 2026-11-25
-- ========================================================

CREATE TABLE IF NOT EXISTS `drug_dict` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` bigint NOT NULL DEFAULT '1' COMMENT '医院ID(租户隔离)',
  `src_type` tinyint NOT NULL DEFAULT '0' COMMENT '来源: 0-手工维护, 1-ShowAPI候选, 2-医保目录',
  `src_id` varchar(64) DEFAULT NULL COMMENT '外部数据源主键',
  `generic_name` varchar(128) NOT NULL COMMENT '通用名',
  `trade_name` varchar(128) DEFAULT NULL COMMENT '商品名',
  `alias_names` varchar(255) DEFAULT NULL COMMENT '别名/俗名，英文逗号分隔',
  `spec` varchar(128) DEFAULT NULL COMMENT '规格原文',
  `spec_volume` int DEFAULT NULL COMMENT '归一装量(ml)',
  `spec_dose` varchar(64) DEFAULT NULL COMMENT '归一含药量',
  `dosage_form` varchar(64) DEFAULT NULL COMMENT '剂型',
  `unit` varchar(16) DEFAULT NULL COMMENT '计量单位',
  `default_dose` varchar(32) DEFAULT NULL COMMENT '默认单次剂量',
  `route` varchar(32) DEFAULT NULL COMMENT '给药途径',
  `is_high_alert` tinyint NOT NULL DEFAULT '0' COMMENT '高警示药品: 0-否, 1-是',
  `pinyin_full` varchar(255) DEFAULT NULL COMMENT '全拼',
  `pinyin_initial` varchar(128) DEFAULT NULL COMMENT '首字母',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0-待审核, 1-已启用, 2-停用',
  `use_count` int NOT NULL DEFAULT '0' COMMENT '使用频次(排序用)',
  `sort_order` int NOT NULL DEFAULT '9999' COMMENT '排序',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_hosp_status` (`hospital_id`,`status`,`is_deleted`),
  KEY `idx_pinyin_initial` (`pinyin_initial`),
  KEY `idx_generic_name` (`generic_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品字典(补液用药)';

-- 补液记录：结构化用药明细（JSON 数组），与 remark 文本并存以兼容历史报表
-- 重复执行会报 Duplicate column，已存在则忽略
ALTER TABLE `infusion_record`
  ADD COLUMN `drug_json` text DEFAULT NULL COMMENT '结构化用药明细JSON' AFTER `remark`;

-- ========================================================
-- 通用数据字典（后台下拉选项统一来源）
-- 2026-11-28
-- 说明：hospital_id = 0 为平台内置通用字典，所有租户共享；
--       医院自建字典写自己的 hospital_id，同 dict_code + item_value 时覆盖平台项。
-- ========================================================

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` bigint NOT NULL DEFAULT '0' COMMENT '归属：0-平台通用，其他为医院ID',
  `dict_code` varchar(64) NOT NULL COMMENT '字典编码，程序引用依据',
  `dict_name` varchar(64) NOT NULL COMMENT '字典名称',
  `remark` varchar(255) DEFAULT NULL COMMENT '用途说明',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-启用, 0-停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hosp_code` (`hospital_id`,`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据字典类型';

CREATE TABLE IF NOT EXISTS `sys_dict_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` bigint NOT NULL DEFAULT '0' COMMENT '归属：0-平台通用，其他为医院ID',
  `dict_code` varchar(64) NOT NULL COMMENT '所属字典编码',
  `item_value` varchar(64) NOT NULL COMMENT '存储值（写入业务表的值）',
  `item_label` varchar(128) NOT NULL COMMENT '显示名',
  `extra` varchar(255) DEFAULT NULL COMMENT '扩展字段（图标/单位/备注）',
  `sort_order` int NOT NULL DEFAULT '99' COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-启用, 0-停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hosp_code_value` (`hospital_id`,`dict_code`,`item_value`),
  KEY `idx_code` (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据字典项';

-- 内置字典类型
INSERT IGNORE INTO `sys_dict_type` (`hospital_id`, `dict_code`, `dict_name`, `remark`) VALUES
(0, 'patient_type', '人群类别', '住院病患人群划分，影响出入量参考值'),
(0, 'admission_type', '入院方式', '平诊/急诊，急诊患者在看板高亮'),
(0, 'nursing_level', '护理级别', '护理分级，特级/一级计入重症护理'),
(0, 'drainage_tube', '留置管路', '病患留置管路类型，可多选'),
(0, 'nurse_role', '医护岗位角色', '护士档案岗位'),
(0, 'infusion_usage', '补液给药用法', '补液登记的给药途径，可自定义扩展'),
(0, 'blood_sugar_type', '血糖测量时点', '交接班血糖记录时点'),
(0, 'laxative_drug', '常用泻药', '交接班泻药记录的常用药名'),
(0, 'drug_dosage_form', '药品剂型', '补液药品字典的剂型'),
(0, 'drug_unit', '药品单位', '补液药品字典的计量单位'),
(0, 'drug_route', '给药途径', '补液药品字典默认给药途径'),
(0, 'diagnosis', '常用临床诊断', '病患建档时的诊断联想词，可直接输入其它诊断');

-- 内置字典项
INSERT IGNORE INTO `sys_dict_item` (`hospital_id`, `dict_code`, `item_value`, `item_label`, `sort_order`) VALUES
(0,'patient_type','ADULT','成人',1),
(0,'patient_type','INFANT','婴儿',2),
(0,'admission_type','ROUTINE','平诊',1),
(0,'admission_type','EMERGENCY','急诊',2),
(0,'nursing_level','SPECIAL','特级护理',1),
(0,'nursing_level','LEVEL_1','一级护理',2),
(0,'nursing_level','LEVEL_2','二级护理',3),
(0,'nursing_level','LEVEL_3','三级护理',4),
(0,'drainage_tube','CATHER','留置导尿管',1),
(0,'drainage_tube','GASTRIC','胃肠减压管',2),
(0,'drainage_tube','DRAINAGE','引流管(胸/腹腔)',3),
(0,'drainage_tube','PICC','PICC置管',4),
(0,'drainage_tube','CVC','中心静脉导管',5),
(0,'drainage_tube','PORT','输液港',6),
(0,'drainage_tube','NASOBILIARY','鼻胆管',7),
(0,'drainage_tube','ABDOM_DRAIN','腹腔引流管',8),
(0,'drainage_tube','OTHER','其他管道',9),
(0,'nurse_role','HEAD_NURSE','护士长',1),
(0,'nurse_role','NURSE','责任护士',2),
(0,'infusion_usage','ivg','静滴',1),
(0,'infusion_usage','ibn','静推',2),
(0,'infusion_usage','ivdrip','静脉滴注',3),
(0,'infusion_usage','iv','静脉注射',4),
(0,'infusion_usage','im','肌注',5),
(0,'infusion_usage','sc','皮下注射',6),
(0,'infusion_usage','po','口服',7),
(0,'blood_sugar_type','空腹','空腹',1),
(0,'blood_sugar_type','餐后2h','餐后2h',2),
(0,'blood_sugar_type','随机','随机',3),
(0,'blood_sugar_type','睡前','睡前',4),
(0,'blood_sugar_type','凌晨','凌晨3点',5),
(0,'laxative_drug','复方聚乙二醇电解质散','复方聚乙二醇电解质散',1),
(0,'laxative_drug','聚乙二醇4000散','聚乙二醇4000散',2),
(0,'laxative_drug','乳果糖口服溶液','乳果糖口服溶液',3),
(0,'laxative_drug','硫酸镁','硫酸镁',4),
(0,'laxative_drug','番泻叶','番泻叶',5),
(0,'laxative_drug','开塞露','开塞露',6),
(0,'laxative_drug','甘油灌肠剂','甘油灌肠剂',7),
(0,'drug_dosage_form','注射剂','注射剂',1),
(0,'drug_dosage_form','粉针剂','粉针剂',2),
(0,'drug_dosage_form','片剂','片剂',3),
(0,'drug_dosage_form','胶囊剂','胶囊剂',4),
(0,'drug_dosage_form','颗粒剂','颗粒剂',5),
(0,'drug_dosage_form','口服溶液','口服溶液',6),
(0,'drug_dosage_form','乳膏剂','乳膏剂',7),
(0,'drug_dosage_form','滴眼液','滴眼液',8),
(0,'drug_dosage_form','栓剂','栓剂',9),
(0,'drug_unit','ml','ml',1),
(0,'drug_unit','g','g',2),
(0,'drug_unit','mg','mg',3),
(0,'drug_unit','支','支',4),
(0,'drug_unit','瓶','瓶',5),
(0,'drug_unit','袋','袋',6),
(0,'drug_unit','片','片',7),
(0,'drug_unit','粒','粒',8),
(0,'drug_unit','IU','IU',9),
(0,'drug_route','ivg','静滴',1),
(0,'drug_route','ibn','静推',2),
(0,'drug_route','ivdrip','静脉滴注',3),
(0,'drug_route','iv','静脉注射',4),
(0,'drug_route','im','肌注',5),
(0,'drug_route','sc','皮下注射',6),
(0,'drug_route','po','口服',7),
(0,'drug_route','外用','外用',8),
(0,'diagnosis','胃癌术后','胃癌术后',1),
(0,'diagnosis','胆囊结石伴急性胆囊炎','胆囊结石伴急性胆囊炎',2),
(0,'diagnosis','急性心肌梗死合并心力衰竭','急性心肌梗死合并心力衰竭',3),
(0,'diagnosis','高血压脑出血恢复期','高血压脑出血恢复期',4),
(0,'diagnosis','2型糖尿病','2型糖尿病',5),
(0,'diagnosis','慢性阻塞性肺疾病急性加重','慢性阻塞性肺疾病急性加重',6),
(0,'diagnosis','急性胰腺炎','急性胰腺炎',7),
(0,'diagnosis','肠梗阻','肠梗阻',8),
(0,'diagnosis','肝硬化失代偿期','肝硬化失代偿期',9),
(0,'diagnosis','泌尿系结石','泌尿系结石',10),
(0,'diagnosis','肺部感染','肺部感染',11),
(0,'diagnosis','脑梗死','脑梗死',12);
