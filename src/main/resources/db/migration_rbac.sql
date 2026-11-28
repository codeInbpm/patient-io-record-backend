-- ============================================================
-- RBAC 角色权限体系（P0）
-- 幂等脚本：可重复执行。应用启动时 RbacSchemaInitializer 也会自动补齐。
-- 说明：
--   1. 内置角色的 hospital_id = 0（平台内置），医院自建角色为具体 hospital_id；
--      这样 unique(role_code, hospital_id) 才能真正生效（NULL 不参与唯一约束）。
--   2. 权限码格式 模块:动作，与前端菜单/按钮一一对应。
--   3. sys_audit_log 只增不改，应用层不提供删除接口。
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_code` varchar(32) NOT NULL COMMENT '角色编码，如 HEAD_NURSE',
  `role_name` varchar(64) NOT NULL COMMENT '角色名称，如 护士长',
  `role_desc` varchar(255) DEFAULT NULL,
  `data_scope` varchar(16) NOT NULL DEFAULT 'WARD' COMMENT 'ALL/HOSPITAL/DEPT/WARD/SELF',
  `hospital_id` bigint NOT NULL DEFAULT '0' COMMENT '0=平台内置；其它=医院自建',
  `builtin` tinyint NOT NULL DEFAULT '0' COMMENT '1=平台内置（仅 SUPER_ADMIN 不可删，其余可删但不可改权限）',
  `status` tinyint NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_role_code_hospital` (`role_code`,`hospital_id`),
  KEY `idx_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `perm_code` varchar(64) NOT NULL COMMENT '模块:动作',
  `perm_name` varchar(64) NOT NULL,
  `module` varchar(32) NOT NULL COMMENT '所属模块，用于前端分组',
  `perm_type` varchar(16) NOT NULL DEFAULT 'ACTION' COMMENT 'MENU/ACTION/DATA',
  `risk_level` tinyint NOT NULL DEFAULT '1' COMMENT '1普通 2敏感 3高危',
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`),
  KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限码字典';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `perm_code` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`,`perm_code`),
  KEY `idx_perm` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_type` varchar(16) NOT NULL COMMENT 'ADMIN / NURSE',
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `hospital_id` bigint NOT NULL,
  `dept_id` bigint DEFAULT NULL COMMENT '范围覆盖：科室',
  `ward_ids` varchar(255) DEFAULT NULL COMMENT '范围覆盖：病区ID逗号分隔，空=按角色默认',
  `effective_from` date DEFAULT NULL,
  `effective_to` date DEFAULT NULL COMMENT '到期自动失效',
  `status` tinyint NOT NULL DEFAULT '1',
  `grant_by` bigint DEFAULT NULL,
  `grant_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_type`,`user_id`,`role_id`),
  KEY `idx_user` (`user_type`,`user_id`),
  KEY `idx_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号角色授权';

CREATE TABLE IF NOT EXISTS `sys_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_type` varchar(16) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `real_name` varchar(32) DEFAULT NULL,
  `role_code` varchar(32) DEFAULT NULL,
  `hospital_id` bigint DEFAULT NULL,
  `action` varchar(64) NOT NULL COMMENT 'ROLE_GRANT / PERM_DENY / PATIENT_EXPORT ...',
  `target_type` varchar(32) DEFAULT NULL,
  `target_id` varchar(64) DEFAULT NULL,
  `detail` varchar(1000) DEFAULT NULL,
  `result` varchar(16) NOT NULL DEFAULT 'SUCCESS',
  `ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_action_time` (`action`,`create_time`),
  KEY `idx_hospital_time` (`hospital_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作审计日志';

-- ----------------------------
-- 权限码（46 项）
-- ----------------------------
INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`module`,`perm_type`,`risk_level`,`sort_order`) VALUES
('dashboard:view','查看数据看板','dashboard','MENU',1,10),
('patient:view','查看患者','patient','MENU',1,20),
('patient:create','新建患者','patient','ACTION',2,21),
('patient:update','编辑患者档案','patient','ACTION',2,22),
('patient:discharge','办理出院','patient','ACTION',3,23),
('patient:bind','绑定/解绑患者','patient','ACTION',3,24),
('record:view','查看出入量记录','record','MENU',1,30),
('record:create','录入出入量记录','record','ACTION',1,31),
('record:correct','更正他人记录','record','ACTION',3,32),
('infusion:view','查看补液','infusion','MENU',1,40),
('infusion:create','开立补液','infusion','ACTION',2,41),
('infusion:end','结束输液','infusion','ACTION',2,42),
('handover:view','查看交班','handover','MENU',1,50),
('handover:submit','提交交班','handover','ACTION',2,51),
('handover:confirm','接班确认','handover','ACTION',2,52),
('rounds:view','查看夜查房','rounds','MENU',1,60),
('rounds:submit','提交夜查房','rounds','ACTION',1,61),
('feedback:view','查看反馈','feedback','MENU',1,70),
('feedback:handle','处置反馈','feedback','ACTION',2,71),
('feedback:fix','直接修正原记录','feedback','ACTION',3,72),
('alert:view','查看告警','alert','MENU',1,80),
('alert:ack','认领/关闭告警','alert','ACTION',1,81),
('assignment:view','查看床位分配','assignment','MENU',1,90),
('assignment:manage','调整床位分配','assignment','ACTION',2,91),
('bed:view','查看床位','bed','MENU',1,100),
('bed:manage','维护床位','bed','ACTION',2,101),
('dept:view','查看楼宇病区','dept','MENU',1,110),
('dept:manage','维护楼宇病区','dept','ACTION',2,111),
('nurse:view','查看人员档案','nurse','MENU',1,120),
('nurse:manage','维护人员档案','nurse','ACTION',2,121),
('drug:view','查看药品字典','drug','MENU',1,130),
('drug:manage','维护药品字典','drug','ACTION',2,131),
('dict:view','查看数据字典','dict','MENU',1,140),
('dict:manage','维护数据字典','dict','ACTION',2,141),
('hospital:view','查看医院租户','hospital','MENU',1,150),
('hospital:manage','维护医院租户','hospital','ACTION',3,151),
('role:view','查看角色','system','MENU',1,160),
('role:manage','维护角色与权限','system','ACTION',3,161),
('account:view','查看账号','system','MENU',1,162),
('account:manage','维护账号与授权','system','ACTION',3,163),
('account:reset-pwd','重置账号密码','system','ACTION',3,164),
('audit:view','查看审计日志','system','MENU',1,165),
('audit:export','导出审计日志','system','ACTION',3,166),
('export:patient','导出患者清册','export','ACTION',3,170),
('export:record','导出出入量报表','export','ACTION',3,171),
('export:stat','导出统计报表','export','ACTION',2,172)
ON DUPLICATE KEY UPDATE `perm_name`=VALUES(`perm_name`),`module`=VALUES(`module`),
  `perm_type`=VALUES(`perm_type`),`risk_level`=VALUES(`risk_level`),`sort_order`=VALUES(`sort_order`);

-- ----------------------------
-- 内置角色（hospital_id = 0）
-- ----------------------------
INSERT INTO `sys_role` (`role_code`,`role_name`,`role_desc`,`data_scope`,`hospital_id`,`builtin`,`status`,`sort_order`) VALUES
('SUPER_ADMIN','平台超级管理员','平台运营。开通医院租户、创建医院管理员、维护平台字典，不介入临床数据。','ALL',0,1,1,10),
('HOSPITAL_ADMIN','医院管理员','信息科/院办。维护本院科室、病区、床位、人员、字典、药品，分配角色与账号。','HOSPITAL',0,1,1,20),
('NURSING_DIRECTOR','护理部 / 质控','护理部、质控科。全院护理数据只读，复核不良事件，导出质控报表。','HOSPITAL',0,1,1,30),
('DEPT_DIRECTOR','科主任','本科室医疗质量负责人，查看本科室患者与记录，导出报表。','DEPT',0,1,1,40),
('HEAD_NURSE','护士长','病区护理负责人。管理本病区患者、交班、异常处置与床位/人员分配。','WARD',0,1,1,50),
('CHARGE_NURSE','办公护士 / 责任组长','医嘱核对、记录录入、交班起草，不可处置反馈、不可改档。','WARD',0,1,1,60),
('NURSE','责任护士','床旁记录。全部操作在小程序端完成，后台无任何权限。','SELF',0,1,1,70),
('AUDITOR','审计员','等保三权分立中的审计角色，仅查看与导出审计日志。','HOSPITAL',0,1,1,80)
ON DUPLICATE KEY UPDATE `role_name`=VALUES(`role_name`),`role_desc`=VALUES(`role_desc`),
  `data_scope`=VALUES(`data_scope`),`builtin`=1,`sort_order`=VALUES(`sort_order`);

-- ----------------------------
-- 角色权限（先清空内置角色的旧映射，再按模板重建；自建角色不受影响）
-- ----------------------------
DELETE rp FROM `sys_role_permission` rp JOIN `sys_role` r ON rp.role_id = r.id
  WHERE r.`builtin` = 1 AND r.`hospital_id` = 0;

INSERT INTO `sys_role_permission` (`role_id`,`perm_code`)
SELECT r.id, p.perm_code FROM `sys_role` r JOIN `sys_permission` p
WHERE r.`hospital_id` = 0 AND (
  (r.`role_code`='SUPER_ADMIN' AND p.perm_code IN (
    'dashboard:view','patient:view','record:view','infusion:view','handover:view','rounds:view',
    'feedback:view','alert:view','assignment:view','bed:view','dept:view','nurse:view','drug:view',
    'dict:view','hospital:view','hospital:manage','role:view','role:manage','account:view',
    'account:manage','account:reset-pwd','audit:view','audit:export',
    'export:patient','export:record','export:stat'))
  OR (r.`role_code`='HOSPITAL_ADMIN' AND p.perm_code IN (
    'dashboard:view','patient:view','patient:create','patient:update','patient:discharge','patient:bind',
    'record:view','record:create','infusion:view','infusion:create','handover:view','rounds:view',
    'feedback:view','alert:view','alert:ack','assignment:view','assignment:manage','bed:view','bed:manage',
    'dept:view','dept:manage','nurse:view','nurse:manage','drug:view','drug:manage','dict:view','dict:manage',
    'hospital:view','role:view','role:manage','account:view','account:manage','account:reset-pwd',
    'audit:view','export:patient','export:record','export:stat'))
  OR (r.`role_code`='NURSING_DIRECTOR' AND p.perm_code IN (
    'dashboard:view','patient:view','record:view','infusion:view','handover:view','rounds:view',
    'feedback:view','alert:view','assignment:view','bed:view','dept:view','nurse:view','hospital:view',
    'audit:view','audit:export','export:patient','export:record','export:stat'))
  OR (r.`role_code`='DEPT_DIRECTOR' AND p.perm_code IN (
    'dashboard:view','patient:view','record:view','infusion:view','handover:view','rounds:view',
    'feedback:view','alert:view','nurse:view','dept:view','bed:view','export:patient','export:record','export:stat'))
  OR (r.`role_code`='HEAD_NURSE' AND p.perm_code IN (
    'dashboard:view','patient:view','patient:create','patient:update','patient:discharge','patient:bind',
    'record:view','record:create','record:correct','infusion:view','infusion:create','infusion:end',
    'handover:view','handover:submit','handover:confirm','rounds:view','rounds:submit',
    'feedback:view','feedback:handle','feedback:fix','alert:view','alert:ack',
    'assignment:view','assignment:manage','bed:view','bed:manage','dept:view','nurse:view','drug:view',
    'dict:view','export:patient','export:record','export:stat'))
  OR (r.`role_code`='CHARGE_NURSE' AND p.perm_code IN (
    'dashboard:view','patient:view','patient:create','patient:update','record:view','record:create',
    'infusion:view','infusion:create','infusion:end','handover:view','handover:submit','rounds:view',
    'rounds:submit','feedback:view','alert:view','alert:ack','assignment:view','bed:view','nurse:view',
    'dept:view','drug:view','dict:view'))
  OR (r.`role_code`='AUDITOR' AND p.perm_code IN ('audit:view','audit:export'))
);

-- ----------------------------
-- 历史账号授权回填（只在授权表为空时执行一次）
-- 目的：首次上线时让老账号立刻有权限；迁移完成后不再回填，
-- 否则管理员「清空某账号角色」后重启会被脚本重新授予，撤销失效。
INSERT INTO `sys_user_role` (`user_type`,`user_id`,`role_id`,`hospital_id`,`dept_id`,`ward_ids`,`status`)
SELECT * FROM (
  SELECT 'ADMIN' AS user_type, a.id AS user_id, r.id AS role_id,
         COALESCE(a.hospital_id, 0) AS hospital_id,
         NULL AS dept_id, NULL AS ward_ids, 1 AS status
  FROM `sys_admin_user` a
  JOIN `sys_role` r ON r.`role_code` = a.`role` AND r.`hospital_id` = 0
  UNION ALL
  SELECT 'NURSE', n.id, r.id, COALESCE(n.hospital_id, 0), n.dept_id,
         CASE WHEN n.ward_id IS NOT NULL THEN CAST(n.ward_id AS CHAR) ELSE NULL END, 1
  FROM `nurse` n
  JOIN `sys_role` r ON r.`role_code` = n.`role` AND r.`hospital_id` = 0
) src
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_user_role` LIMIT 1) t);
