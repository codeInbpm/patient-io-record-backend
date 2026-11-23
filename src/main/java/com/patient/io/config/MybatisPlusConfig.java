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
    private static final List<String> IGNORE_TABLES = Arrays.asList(
            "hospital",
            "sys_admin_user",
            "sys_user",
            "infusion_image"
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
