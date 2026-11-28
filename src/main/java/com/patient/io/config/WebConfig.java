package com.patient.io.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private AdminPermissionInterceptor adminPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns(
                        "/v1/auth/wx-login",
                        "/v1/auth/login",
                        "/v1/auth/mock-login",
                        "/v1/admin/auth/login",
                        // 注意：/v1/dict/** 不在此排除，交由 AuthInterceptor 内部按 GET/写操作区分，
                        // 否则药品字典的新增/删除会被匿名放行
                        "/doc.html",
                        "/swagger-resources/**",
                        "/webjars/**"
                );

        // 权限闸门必须排在 AuthInterceptor 之后（登录上下文已写入 ThreadLocal）。
        // 管辖范围从 /v1/admin/** 扩到 /v1/**：业务接口（患者、补液、交班）同样需要操作级鉴权；
        // 小程序端账号（无后台授权）由 PermissionRules 判定为 backendUser=false 时放行。
        registry.addInterceptor(adminPermissionInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns(
                        "/v1/auth/**",
                        "/v1/admin/auth/**",
                        "/v1/file/**"
                )
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
