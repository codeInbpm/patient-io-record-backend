package com.patient.io.config;

import cn.hutool.core.util.StrUtil;
import com.patient.io.common.ScopeContext;
import com.patient.io.common.TenantContext;
import com.patient.io.common.UserContext;
import com.patient.io.service.RbacService;
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

    @Autowired
    private RbacService rbacService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 放行 OPTIONS 跨域预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 2. 兜底放行登录与公开字典查询接口；字典的增删改仍需登录
        String uri = request.getRequestURI();
        boolean dictRead = uri.contains("/dict/") && "GET".equalsIgnoreCase(request.getMethod());
        if (dictRead) {
            // 字典读取对小程序匿名开放，但后台带了 Token 时要还原租户上下文，
            // 否则医院自建的字典项永远读不到（TenantContext 为空只能返回平台字典）
            tryApplyContext(request);
            return true;
        }
        if (uri.contains("/admin/auth/login")
                || uri.contains("/auth/wx-login")
                || uri.contains("/auth/mock-login")
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

        // 设置用户基础上下文（utype 决定账号回查 sys_admin_user 还是 nurse）
        String userType = (String) claims.get("utype");
        UserContext.set(new UserContext.CurrentUserInfo(userId, (String) claims.get("openid"), role, userType));

        // 核心：设置多租户上下文
        applyTenantContext(request, role, tokenHospitalId);
        applyScopeContext(UserContext.getUserType(), userId);

        if ("HOSPITAL_ADMIN".equals(role) && TenantContext.getHospitalId() == null) {
            sendForbidden(response, "当前管理员未绑定任何医院");
            return false;
        }

        return true;
    }

    /**
     * 设置多租户上下文
     */
    private void applyTenantContext(HttpServletRequest request, String role, Long tokenHospitalId) {
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
            TenantContext.setHospitalId(tokenHospitalId);
        } else {
            // 小程序端用户（护士长/护士/患者）如果存在关联的 hospitalId，注入租户
            TenantContext.setHospitalId(tokenHospitalId);
        }
    }

    /**
     * 匿名可读接口上的尽力而为：带了有效 Token 就还原上下文，无效则按匿名处理
     */
    private void tryApplyContext(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            return;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            if (!jwtUtils.validateToken(token)) {
                return;
            }
            Claims claims = jwtUtils.getClaimsFromToken(token);
            if (claims == null || claims.get("userId") == null) {
                return;
            }
            String role = (String) claims.get("role");
            Object hospIdObj = claims.get("hospitalId");
            Long tokenHospitalId = hospIdObj != null && !"null".equals(hospIdObj.toString())
                    ? Long.valueOf(hospIdObj.toString()) : null;
            Long uid = Long.valueOf(claims.get("userId").toString());
            UserContext.set(new UserContext.CurrentUserInfo(
                    uid, (String) claims.get("openid"), role, (String) claims.get("utype")));
            applyTenantContext(request, role, tokenHospitalId);
            applyScopeContext(UserContext.getUserType(), uid);
        } catch (Exception ignored) {
            // 匿名访问字典时 Token 异常不影响查询
        }
    }

    /**
     * 数据范围注入：解析角色授权得到 ALL/HOSPITAL/DEPT/WARD/SELF 与病区白名单。
     * 失败不阻断（RBAC 表未建时按「未收窄」处理），由权限拦截器兜底。
     */
    private void applyScopeContext(String userType, Long userId) {
        try {
            java.util.Map<String, Object> scope = rbacService.scopeOf(userType, userId);
            Object wardIds = scope.get("wardIds");
            java.util.List<Long> ids = new java.util.ArrayList<>();
            if (wardIds instanceof java.util.List) {
                for (Object o : (java.util.List<?>) wardIds) {
                    if (o instanceof Number) {
                        ids.add(((Number) o).longValue());
                    }
                }
            }
            Object deptId = scope.get("deptId");
            ScopeContext.set((String) scope.get("scope"), ids,
                    deptId instanceof Number ? ((Number) deptId).longValue() : null);
        } catch (Exception e) {
            ScopeContext.set(ScopeContext.HOSPITAL, new java.util.ArrayList<>(), null);
        }
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
        ScopeContext.clear();
    }
}
