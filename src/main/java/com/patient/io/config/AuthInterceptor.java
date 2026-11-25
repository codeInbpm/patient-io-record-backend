package com.patient.io.config;

import cn.hutool.core.util.StrUtil;
import com.patient.io.common.TenantContext;
import com.patient.io.common.UserContext;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 放行 OPTIONS 跨域预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 2. 兜底放行登录与公开字典查询接口；字典的增删改仍需登录
        String uri = request.getRequestURI();
        boolean dictRead = uri.contains("/dict/") && "GET".equalsIgnoreCase(request.getMethod());
        if (uri.contains("/admin/auth/login")
                || uri.contains("/auth/wx-login")
                || uri.contains("/auth/mock-login")
                || dictRead
                || uri.contains("/swagger")
                || uri.contains("/webjars")
                || uri.contains("/doc.html")) {
            return true;
        }

        // 3. 提取 Token
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (!StringUtils.hasText(token)) {
            sendUnauthorized(response, "未登录或登录凭证已失效");
            return false;
        }

        Claims claims = jwtUtils.getClaimsFromToken(token);
        if (claims == null || !jwtUtils.validateToken(token)) {
            sendUnauthorized(response, "登录凭证无效或已过期");
            return false;
        }

        Long userId = Long.valueOf(claims.get("userId").toString());
        String role = (String) claims.get("role");
        Object hospIdObj = claims.get("hospitalId");
        Long tokenHospitalId = hospIdObj != null && !"null".equals(hospIdObj.toString()) ? Long.valueOf(hospIdObj.toString()) : null;

        // 设置用户基础上下文
        UserContext.set(new UserContext.CurrentUserInfo(userId, (String) claims.get("openid"), role));

        // 核心：设置多租户上下文
        if ("SUPER_ADMIN".equals(role)) {
            // 超管可从请求头动态切换目标医院
            String headerHospId = request.getHeader("X-Hospital-Id");
            if (StrUtil.isNotBlank(headerHospId) && !"0".equals(headerHospId) && !"null".equalsIgnoreCase(headerHospId)) {
                TenantContext.setHospitalId(Long.parseLong(headerHospId));
            } else {
                TenantContext.setHospitalId(null); // 为空则不带过滤，查看全局
            }
        } else if ("HOSPITAL_ADMIN".equals(role)) {
            // 医院管理员强行绑定其专属 hospitalId，杜绝越权
            if (tokenHospitalId == null) {
                sendForbidden(response, "当前管理员未绑定任何医院");
                return false;
            }
            TenantContext.setHospitalId(tokenHospitalId);
        } else {
            // 小程序端用户（护士长/护士/患者）如果存在关联的 hospitalId，注入租户
            TenantContext.setHospitalId(tokenHospitalId);
        }

        return true;
    }

    private void sendUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + msg + "\",\"data\":null}");
    }

    private void sendForbidden(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":403,\"message\":\"" + msg + "\",\"data\":null}");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
        TenantContext.clear(); // 务必清理 ThreadLocal，防止线程池复用污染
    }
}
