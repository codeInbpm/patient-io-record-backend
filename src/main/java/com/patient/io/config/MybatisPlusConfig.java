package com.patient.io.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.patient.io.common.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class MybatisPlusConfig {

    // 平台级白名单表，不追加 hospital_id 过滤
    // sys_dict_* 需要同时读到平台通用(hospital_id=0)与本院数据，过滤条件在 Service 内显式拼装
    private static final List<String> IGNORE_TABLES = Arrays.asList(
            "hospital",
            "sys_admin_user",
            "sys_user",
            "infusion_image",
            "sys_dict_type",
            "sys_dict_item",
            // RBAC 表由 RbacService 显式控制 hospital_id 条件：
            // sys_role 需同时读平台内置(hospital_id=0)与本院自建；
            // 关联表与审计表没有 hospital_id 列，绝不能被租户插件拼条件。
            "sys_role",
            "sys_permission",
            "sys_role_permission",
            "sys_user_role",
            "sys_audit_log"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 注入多租户插件
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long hospitalId = TenantContext.getHospitalId();
                if (hospitalId == null || hospitalId <= 0) {
                    return new NullValue();
                }
                return new LongValue(hospitalId);
            }

            @Override
            public String getTenantIdColumn() {
                return "hospital_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 如果标记了全局忽略租户，或属于平台白名单表，则不拦截
                if (TenantContext.isIgnoreTenant()) {
                    return true;
                }
                // 超级管理员在未选定具体医院时（hospitalId == null），不拼过滤条件
                if (TenantContext.getHospitalId() == null) {
                    return true;
                }
                return IGNORE_TABLES.stream().anyMatch(t -> t.equalsIgnoreCase(tableName));
            }
        }));

        // 2. 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
