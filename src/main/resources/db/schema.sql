-- ========================================================
-- 微信小程序：病患出入量记录助手 (Patient IO Record)
-- 严格角色体系：护士长(HEAD_NURSE) / 护士(NURSE) / 患者(PATIENT)
-- ========================================================

CREATE DATABASE IF NOT EXISTS `patient_io_record` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `patient_io_record`;

-- 1. 微信用户表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `openid` VARCHAR(64) NOT NULL COMMENT '微信openid',
  `unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信unionid',
  `nickname` VARCHAR(64) DEFAULT '微信用户' COMMENT '微信昵称',
  `avatar_url` VARCHAR(255) DEFAULT '' COMMENT '头像链接',
  `role` VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '当前角色：HEAD_NURSE(护士长), NURSE(护士), PATIENT(患者), NONE(未绑定)',
  `bind_status` TINYINT NOT NULL DEFAULT 0 COMMENT '绑定状态：0-未绑定, 1-已绑定',
  `bind_ref_id` BIGINT DEFAULT NULL COMMENT '绑定的实体ID（nurse.id 或 patient.id）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常, 0-禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信用户表';

-- 2. 医护人员名册表（预置护士长和护士档案，与病区深度绑定）
DROP TABLE IF EXISTS `nurse`;
CREATE TABLE `nurse` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_number` VARCHAR(6) NOT NULL COMMENT '6位纯数字唯一工号(如100001)',
  `real_name` VARCHAR(32) NOT NULL COMMENT '医护姓名',
  `role` VARCHAR(20) NOT NULL DEFAULT 'NURSE' COMMENT '角色：HEAD_NURSE(护士长), NURSE(护士)',
  `hospital_id` BIGINT DEFAULT 1 COMMENT '所属医院ID',
  `hospital_name` VARCHAR(128) DEFAULT '第一人民医院（示范本部）' COMMENT '所属医院名称',
  `dept_id` BIGINT DEFAULT NULL COMMENT '所属楼宇/科室ID',
  `department` VARCHAR(64) NOT NULL COMMENT '所属科室/楼宇',
  `building` VARCHAR(64) DEFAULT NULL COMMENT '所在楼宇',
  `ward_id` BIGINT DEFAULT NULL COMMENT '所属病区ID',
  `ward_name` VARCHAR(64) DEFAULT NULL COMMENT '所属病区名称',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `bound_user_id` BIGINT DEFAULT NULL COMMENT '绑定的微信sys_user.id',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-在职, 0-离职',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_number` (`job_number`),
  KEY `idx_ward` (`ward_id`),
  KEY `idx_bound_user` (`bound_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医护人员名册表';

-- 3. 病房/病区表（护士长统一管理）
DROP TABLE IF EXISTS `ward`;
CREATE TABLE `ward` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '病房ID',
  `ward_name` VARCHAR(64) NOT NULL COMMENT '病房名称（如：心内科三病区、骨科一病区）',
  `department` VARCHAR(64) NOT NULL COMMENT '科室名称',
  `room_no` VARCHAR(32) DEFAULT NULL COMMENT '房间号（如：301室）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `create_by` BIGINT DEFAULT NULL COMMENT '创建护士长ID',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用, 0-停用',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病房表';

-- 4. 床位表（护士长在病房下统一管理）
DROP TABLE IF EXISTS `bed`;
CREATE TABLE `bed` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '床位ID',
  `ward_id` BIGINT NOT NULL COMMENT '所属病房ID',
  `bed_no` VARCHAR(20) NOT NULL COMMENT '床位编号（如：01床、02床）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '床位状态：0-空床, 1-占用',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ward_bed` (`ward_id`, `bed_no`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='床位表';

-- 5. 住院病人档案表（护士长建档，患者通过 姓名+病房+床位 绑定）
DROP TABLE IF EXISTS `patient`;
CREATE TABLE `patient` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '病人ID',
  `ward_id` BIGINT NOT NULL COMMENT '所属病房ID',
  `bed_id` BIGINT NOT NULL COMMENT '所属床位ID',
  `bed_no` VARCHAR(20) NOT NULL COMMENT '床号冗余',
  `patient_name` VARCHAR(32) NOT NULL COMMENT '病人真实姓名',
  `hospital_no` VARCHAR(32) NOT NULL COMMENT '住院号',
  `gender` TINYINT DEFAULT 1 COMMENT '性别：1-男, 2-女',
  `age` INT DEFAULT NULL COMMENT '年龄',
  `admission_date` DATE NOT NULL COMMENT '入院日期',
  `diagnosis` VARCHAR(128) DEFAULT NULL COMMENT '临床诊断',
  `bound_user_id` BIGINT DEFAULT NULL COMMENT '已绑定的微信sys_user.id',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '住院状态：1-在院, 2-已出院',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hospital_no` (`hospital_no`, `is_deleted`),
  KEY `idx_match_patient` (`patient_name`, `ward_id`, `bed_no`),
  KEY `idx_bound_user` (`bound_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='住院病人表';

-- 6. 护士与病人/床位的分配关系表（护士长为床位分配专属护士）
DROP TABLE IF EXISTS `nurse_patient_relation`;
CREATE TABLE `nurse_patient_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `nurse_id` BIGINT NOT NULL COMMENT '护士nurse.id',
  `patient_id` BIGINT NOT NULL COMMENT '负责的病人patient.id',
  `bed_id` BIGINT NOT NULL COMMENT '床位bed.id',
  `assign_by` BIGINT NOT NULL COMMENT '分配人(护士长ID)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nurse_patient` (`nurse_id`, `patient_id`),
  KEY `idx_patient` (`patient_id`),
  KEY `idx_nurse` (`nurse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='护士与患者分配关系表';

-- 7. 入量记录表
DROP TABLE IF EXISTS `intake_record`;
CREATE TABLE `intake_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '入量记录ID',
  `patient_id` BIGINT NOT NULL COMMENT '病人ID',
  `ward_id` BIGINT NOT NULL COMMENT '病房ID',
  `intake_type` VARCHAR(32) NOT NULL COMMENT 'ORAL(口服), IV(输液), NASOGASTRIC(鼻饲), OTHER(其他)',
  `sub_type` VARCHAR(32) DEFAULT NULL COMMENT 'WATER, CONGEE, NOODLES, RICE_SOUP, LOTUS_STARCH, CUSTOM',
  `item_name` VARCHAR(64) NOT NULL COMMENT '品名',
  `amount` DECIMAL(8,2) NOT NULL COMMENT '量(ml)',
  `record_time` DATETIME NOT NULL COMMENT '摄入时间(精确到分)',
  `record_date` DATE NOT NULL COMMENT '归属自然日',
  `recorder_id` BIGINT NOT NULL COMMENT '录入人user_id',
  `recorder_role` VARCHAR(20) NOT NULL COMMENT 'HEAD_NURSE, NURSE, PATIENT',
  `remark` VARCHAR(255) DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_patient_date` (`patient_id`, `record_date`),
  KEY `idx_ward_date` (`ward_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入量记录表';

-- 8. 出量记录表
DROP TABLE IF EXISTS `output_record`;
CREATE TABLE `output_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '出量记录ID',
  `patient_id` BIGINT NOT NULL COMMENT '病人ID',
  `ward_id` BIGINT NOT NULL COMMENT '病房ID',
  `output_type` VARCHAR(32) NOT NULL COMMENT 'URINE(尿量), STOOL(大便量), GASTRO_DRAINAGE(胃肠减压引流), OTHER_DRAINAGE(其他引流)',
  `amount` DECIMAL(8,2) NOT NULL COMMENT '排出量(ml)',
  `nature_or_color` VARCHAR(64) DEFAULT NULL COMMENT '性状颜色',
  `record_time` DATETIME NOT NULL COMMENT '排出时间(精确到分)',
  `record_date` DATE NOT NULL COMMENT '归属自然日',
  `recorder_id` BIGINT NOT NULL COMMENT '录入人user_id',
  `recorder_role` VARCHAR(20) NOT NULL COMMENT 'HEAD_NURSE, NURSE, PATIENT',
  `remark` VARCHAR(255) DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_patient_date` (`patient_id`, `record_date`),
  KEY `idx_ward_date` (`ward_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出量记录表';

-- 9. 每日出入量统计汇总表
DROP TABLE IF EXISTS `daily_summary`;
CREATE TABLE `daily_summary` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '汇总ID',
  `patient_id` BIGINT NOT NULL,
  `ward_id` BIGINT NOT NULL,
  `record_date` DATE NOT NULL,
  `total_intake` DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  `total_output` DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  `net_balance` DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  `oral_intake` DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  `iv_intake` DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  `nasogastric_intake` DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  `urine_output` DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  `stool_output` DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  `drainage_output` DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  `is_abnormal` TINYINT NOT NULL DEFAULT 0 COMMENT '1-异常预警(如24h尿量<400ml), 0-正常',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_patient_date` (`patient_id`, `record_date`),
  KEY `idx_ward_date` (`ward_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日出入平衡汇总表';

-- ========================================================
-- 初始业务种子数据
-- ========================================================

-- 预置医护名册：护士长工号 100001，普通护士工号 100002、100003，绑定所属医院、楼宇与病区
INSERT INTO `nurse` (`id`, `job_number`, `real_name`, `role`, `hospital_id`, `hospital_name`, `dept_id`, `department`, `building`, `ward_id`, `ward_name`, `phone`) VALUES
(1, '100001', '张洁护士长', 'HEAD_NURSE', 1, '第一人民医院（示范本部）', 11, '7号楼', '7号楼', 36, '7#19B消化内科三病区', '13800000001'),
(2, '100002', '李丽护士', 'NURSE', 1, '第一人民医院（示范本部）', 11, '7号楼', '7号楼', 36, '7#19B消化内科三病区', '13800000002'),
(3, '100003', '王芳护士', 'NURSE', 1, '第一人民医院（示范本部）', 11, '7号楼', '7号楼', 21, '7#7A眼科病房', '13800000003');

-- 预置楼宇字典
INSERT INTO `department` (`id`, `hospital_id`, `dept_name`, `dept_code`, `status`, `sort_order`) VALUES
(10, 1, '2号楼', 'BUILDING_2', 1, 1),
(11, 1, '7号楼', 'BUILDING_7', 1, 2),
(12, 1, '12号楼', 'BUILDING_12', 1, 3),
(13, 1, '急诊楼', 'BUILDING_EMERGENCY', 1, 4);

-- 预置病区（图3、图4、图5各楼宇病区字典，共49个）
INSERT INTO `ward` (`id`, `ward_name`, `building`, `hospital_id`, `hospital_name`, `dept_id`, `department`, `sort_order`, `status`) VALUES
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
INSERT INTO `bed` (`id`, `ward_id`, `bed_no`, `status`, `sort_order`) VALUES
(1, 3, '01床', 1, 1),
(2, 3, '02床', 1, 2),
(3, 3, '03床', 0, 3);

-- 预置病患档案（2#18F干细胞移植病房，01床李建国，02床王秀英）
INSERT INTO `patient` (`id`, `ward_id`, `bed_id`, `bed_no`, `patient_name`, `hospital_no`, `gender`, `age`, `admission_date`, `diagnosis`, `status`) VALUES
(1, 3, 1, '01床', '李建国', 'ZY202611001', 1, 62, '2026-11-15', '急性心肌梗死合并心力衰竭', 1),
(2, 3, 2, '02床', '王秀英', 'ZY202611002', 2, 58, '2026-11-16', '高血压脑出血恢复期', 1);

-- 预置护士分配关系：李丽护士(nurse_id=2)负责01床李建国，王芳护士(nurse_id=3)负责02床王秀英
INSERT INTO `nurse_patient_relation` (`id`, `nurse_id`, `patient_id`, `bed_id`, `assign_by`) VALUES
(1, 2, 1, 1, 1),
(2, 3, 2, 2, 1);

-- ========================================================
-- 新增功能一：患者补液（打吊瓶）管理
-- ========================================================
CREATE TABLE IF NOT EXISTS `infusion_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` BIGINT NOT NULL DEFAULT 1 COMMENT '医院ID(租户隔离)',
  `ward_id` BIGINT NOT NULL COMMENT '病区ID',
  `bed_id` BIGINT NOT NULL COMMENT '床位ID',
  `bed_no` VARCHAR(32) NOT NULL COMMENT '床号冗余',
  `patient_id` BIGINT NOT NULL COMMENT '患者ID',
  `patient_name` VARCHAR(64) NOT NULL COMMENT '患者姓名冗余',
  `capacity` INT NOT NULL COMMENT '补液容量(ml)',
  `usage_type` VARCHAR(64) NOT NULL COMMENT '给药用法(ivg/ibn/iv drip/自定义)',
  `start_time` DATETIME NOT NULL COMMENT '开始补液时间',
  `estimated_end_time` DATETIME NOT NULL COMMENT '预计拔针时间',
  `actual_end_time` DATETIME DEFAULT NULL COMMENT '实际结束时间',
  `status` VARCHAR(20) NOT NULL DEFAULT 'INFUSING' COMMENT '状态: INFUSING-正在补液, COMPLETED-正常结束, STOPPED-异常中断',
  `actual_intake_amount` INT DEFAULT NULL COMMENT '实际滴入量(ml，默认等于capacity)',
  `is_synced_intake` TINYINT(1) DEFAULT 1 COMMENT '是否已同步入量记录: 1-是, 0-否',
  `intake_record_id` BIGINT DEFAULT NULL COMMENT '关联的入量记录ID',
  `nurse_id` BIGINT NOT NULL COMMENT '经办护士ID',
  `nurse_name` VARCHAR(64) NOT NULL COMMENT '经办护士姓名',
  `end_nurse_id` BIGINT DEFAULT NULL COMMENT '拔针/结束护士ID',
  `end_nurse_name` VARCHAR(64) DEFAULT NULL COMMENT '拔针/结束护士姓名',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '补液备注',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ward_status` (`ward_id`, `status`),
  KEY `idx_patient_time` (`patient_id`, `start_time`),
  KEY `idx_estimated_end` (`status`, `estimated_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者补液记录表';

CREATE TABLE IF NOT EXISTS `infusion_image` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `infusion_id` BIGINT NOT NULL COMMENT '关联补液记录ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `object_key` VARCHAR(512) NOT NULL COMMENT 'MinIO存储对象Key',
  `file_url` VARCHAR(1024) NOT NULL COMMENT '图片访问全路径URL',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
  `upload_user_id` BIGINT NOT NULL COMMENT '上传人ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_infusion_id` (`infusion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补液凭据图片表';

-- ========================================================
-- 新增功能二：每日交接班特殊记录表（电子化纸质交接班本）
-- ========================================================
CREATE TABLE IF NOT EXISTS `shift_handover_special` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `hospital_id` BIGINT NOT NULL DEFAULT 1 COMMENT '医院ID',
  `ward_id` BIGINT NOT NULL COMMENT '病区ID',
  `handover_date` DATE NOT NULL COMMENT '交接日期',
  `shift_type` VARCHAR(20) NOT NULL DEFAULT 'ALL_DAY' COMMENT '班次类型(ALL_DAY-全天/DAY-白班/NIGHT-夜班)',
  `leave_hospital_json` MEDIUMTEXT DEFAULT NULL COMMENT '请假记录JSON',
  `laxative_json` MEDIUMTEXT DEFAULT NULL COMMENT '泻药记录JSON',
  `temperature_abnormal_json` MEDIUMTEXT DEFAULT NULL COMMENT '体温异常记录JSON',
  `blood_sugar_json` MEDIUMTEXT DEFAULT NULL COMMENT '血糖记录JSON',
  `blood_pressure_json` MEDIUMTEXT DEFAULT NULL COMMENT '血压记录JSON',
  `urine_volume_json` MEDIUMTEXT DEFAULT NULL COMMENT '尿量记录JSON',
  `special_cases` MEDIUMTEXT DEFAULT NULL COMMENT '特殊情况文字自由记录',
  `pipe_level1_json` MEDIUMTEXT DEFAULT NULL COMMENT 'I级护理床位清单JSON',
  `pipe_monitor_json` MEDIUMTEXT DEFAULT NULL COMMENT '监护床位清单JSON',
  `pipe_maint_fluid_json` MEDIUMTEXT DEFAULT NULL COMMENT '维持液床位清单JSON',
  `pipe_ivg_json` MEDIUMTEXT DEFAULT NULL COMMENT 'IVG床位清单JSON',
  `pipe_picc_json` MEDIUMTEXT DEFAULT NULL COMMENT 'PICC床位清单JSON',
  `pipe_cvc_json` MEDIUMTEXT DEFAULT NULL COMMENT 'CVC床位清单JSON',
  `pipe_port_json` MEDIUMTEXT DEFAULT NULL COMMENT 'PORT输液港床位清单JSON',
  `pipe_nasobiliary_json` MEDIUMTEXT DEFAULT NULL COMMENT '鼻胆管床位清单JSON',
  `pipe_gastro_decomp_json` MEDIUMTEXT DEFAULT NULL COMMENT '胃肠减压管床位清单JSON',
  `pipe_catheter_json` MEDIUMTEXT DEFAULT NULL COMMENT '导尿管床位清单JSON',
  `pipe_abdom_drain_json` MEDIUMTEXT DEFAULT NULL COMMENT '腹腔引流管床位清单JSON',
  `pipe_other_json` MEDIUMTEXT DEFAULT NULL COMMENT '其他导管床位清单JSON',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿编辑中, SUBMITTED-已交班提交, LOCKED-护士长已归档锁定',
  `last_editor_id` BIGINT DEFAULT NULL COMMENT '最后更新人护士ID',
  `last_editor_name` VARCHAR(64) DEFAULT NULL COMMENT '最后更新人姓名',
  `head_nurse_signed` TINYINT(1) DEFAULT 0 COMMENT '护士长是否核签: 0-未签, 1-已签',
  `head_nurse_signed_time` DATETIME DEFAULT NULL COMMENT '护士长核签时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ward_date_shift` (`ward_id`, `handover_date`, `shift_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日交接班特殊记录表';
