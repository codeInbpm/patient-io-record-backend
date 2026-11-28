package com.patient.io.config;

import com.patient.io.common.ScopeContext;
import com.patient.io.common.UserContext;
import com.patient.io.service.AuditService;
import com.patient.io.service.RbacService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 后台权限闸门（RBAC）。
 *
 * 相比旧实现的三个硬编码角色，这里改成「URI -> 权限码」判定：
 *  - 功能权限：PermissionRules 给出接口所需权限码，按账号授权聚合结果裁决；
 *  - 数据范围：WARD 范围下校验 wardId 参数，跨病区直接 403；
 *  - 影子模式：rbac.enforce=false 时只记日志不拦截，便于上线前比对差异；
 *  - 兼容：脚本未执行、账号未回填授权时，RbacService 会按主角色套用内置模板（fail-open），
 *    避免一开开关全站 403。
 *
 * 执行顺序在 AuthInterceptor 之后（UserContext / TenantContext 已就绪）。
 */
@Slf4j
@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    /** 旧版后台角色，仅用于判断「是否能进后台」 */
    private static final Set<String> LEGACY_BACKEND_ROLES = new HashSet<>(Arrays.asList(
            "SUPER_ADMIN", "HOSPITAL_ADMIN", "HEAD_NURSE"));

    @Autowired
    private RbacService rbacService;

    @Autowired
    private AuditService auditService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 注意：应用配置了 context-path=/api，getRequestURI() 会带前缀，
        // 规则表按 /v1/** 编写，这里必须先剥掉上下文路径。
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        String method = request.getMethod();

        // 登录、匿名可读字典、上传：不参与鉴权
        if (uri == null || uri.contains("/admin/auth/") || uri.contains("/v1/auth/")
                || uri.contains("/swagger") || uri.contains("/webjars") || uri.contains("/doc.html")) {
            return true;
        }
        if (uri.startsWith("/v1/dict/") && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        if (uri.startsWith("/v1/file/")) {
            return true;
        }
        // 小程序端接班确认：护士在病床边完成签名，必须放行（登录已由 AuthInterceptor 校验）
        if (uri.startsWith("/v1/admin/handover/") && uri.endsWith("/receive")) {
            return true;
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            send(response, 401, "未登录或登录凭证已失效");
            return false;
        }
        String userType = UserContext.getUserType();
        String role = UserContext.getRole();

        Set<String> perms;
        try {
            perms = rbacService.permissionsOf(userType, userId);
        } catch (Exception e) {
            // RBAC 表缺失或数据库异常时不阻断业务，回落到旧闸门
            log.warn("RBAC 权限读取失败，回落旧闸门：{}", e.getMessage());
            perms = new HashSet<>();
        }
        boolean backendUser = !perms.isEmpty() || LEGACY_BACKEND_ROLES.contains(role);
        String need = PermissionRules.resolve(method, uri);

        if (!rbacService.enforced()) {
            // 影子模式：保持原有行为，只记录「本应拦截」的差异，供上线前核对
            if (uri.startsWith("/v1/admin/") && !backendUser) {
                auditService.fail("PERM_DENY", "API", uri, "后台不开放给 " + role + "（旧闸门）");
                send(response, 403, "责任护士与家属请使用小程序端，后台不开放");
                return false;
            }
            if (uri.contains("/v1/admin/hospitals") && !"GET".equalsIgnoreCase(method)
                    && !"SUPER_ADMIN".equals(role)) {
                send(response, 403, "仅平台超级管理员可维护医院租户");
                return false;
            }
            if (need != null && !perms.contains(need)) {
                log.info("[RBAC-SHADOW] need={} allow=false user={}:{} role={} uri={}",
                        need, userType, userId, role, uri);
            }
            return true;
        }

        if (need == null) {
            if (uri.startsWith("/v1/admin/") && !backendUser) {
                send(response, 403, "责任护士与家属请使用小程序端，后台不开放");
                return false;
            }
            return true;
        }

        if (!backendUser) {
            auditService.fail("PERM_DENY", "API", uri, "非后台账号访问后台接口：" + role);
            send(response, 403, "责任护士与家属请使用小程序端，后台不开放");
            return false;
        }
        if (!perms.contains(need)) {
            auditService.fail("PERM_DENY", "API", uri, "缺权限 " + need + "（role=" + role + "）");
            send(response, 403, "无权限执行该操作：" + need);
            return false;
        }

        // 数据范围：WARD 范围只允许访问授权病区
        String wardParam = request.getParameter("wardId");
        if (wardParam != null && !wardParam.trim().isEmpty()
                && ScopeContext.WARD.equals(ScopeContext.getScope())) {
            try {
                Long wardId = Long.parseLong(wardParam.trim());
                if (!ScopeContext.canAccessWard(wardId)) {
                    auditService.fail("SCOPE_DENY", "WARD", wardParam, "越权访问病区 " + wardId);
                    send(response, 403, "无权访问该病区数据");
                    return false;
                }
            } catch (NumberFormatException ignored) {
                // 非法参数交给业务层处理
            }
        }
        return true;
    }

    private void send(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + msg + "\",\"data\":null}");
    }
}
