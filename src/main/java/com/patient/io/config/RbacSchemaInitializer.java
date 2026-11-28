package com.patient.io.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * RBAC 表结构与内置角色的自愈初始化。
 *
 * 直接执行 db/migration_rbac.sql（幂等：建表 IF NOT EXISTS、权限码 ON DUPLICATE KEY、
 * 内置角色权限先删后建、账号授权回填带 NOT EXISTS 守卫）。
 * 这么做的好处是「SQL 脚本就是唯一事实来源」，不需要在 Java 里再维护一份 46 项权限码。
 *
 * 失败只告警不阻断启动：现场数据库账号常常没有 DDL 权限，此时后台角色页会提示执行脚本。
 */
@Slf4j
@Component
@Order(2)
public class RbacSchemaInitializer implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration_rbac.sql"));
            Integer perms = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_permission", Integer.class);
            Integer roles = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role WHERE hospital_id = 0", Integer.class);
            ensureRoleCodeUniqueIndex();
            log.info("RBAC 初始化完成：权限码 {} 项，内置角色 {} 个", perms, roles);
        } catch (Exception e) {
            log.warn("RBAC 初始化未完成（可能缺少 DDL 权限或表已存在冲突），请手工执行 db/migration_rbac.sql：{}", e.getMessage());
        }
    }

    /**
     * 老库补 role_code 唯一索引：CREATE TABLE IF NOT EXISTS 不会改动已存在的表，
     * 所以这里单独探测一次。已有重复数据时只告警不阻断（由应用层查重兜底）。
     */
    private void ensureRoleCodeUniqueIndex() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() AND table_name = 'sys_role' " +
                            "AND index_name = 'uk_role_code'", Integer.class);
            if (exists != null && exists > 0) {
                return;
            }
            Integer dup = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM (SELECT role_code FROM sys_role GROUP BY role_code " +
                            "HAVING COUNT(1) > 1) t", Integer.class);
            if (dup != null && dup > 0) {
                log.warn("sys_role 存在 {} 组重复 role_code，已跳过唯一索引创建，请先人工合并", dup);
                return;
            }
            jdbcTemplate.execute("ALTER TABLE sys_role ADD UNIQUE KEY uk_role_code (role_code)");
            log.info("已为 sys_role.role_code 补建唯一索引");
        } catch (Exception e) {
            log.warn("role_code 唯一索引创建跳过：{}", e.getMessage());
        }
    }
}
