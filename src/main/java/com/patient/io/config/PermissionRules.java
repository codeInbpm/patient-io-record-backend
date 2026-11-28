package com.patient.io.config;

import org.springframework.util.StringUtils;

/**
 * URI -> 权限码 的集中映射。
 *
 * 为什么不逐个 Controller 加注解：后台接口分散在 16 个 Controller、140 多个方法上，
 * 注解容易漏；把规则收到一处，评审时能一眼看完「哪个接口要什么权限」。
 * 规则按「越具体越靠前」的顺序匹配。
 */
public final class PermissionRules {

    private PermissionRules() {
    }

    private static final String[][] RULES = new String[][]{
            // pathPrefix, GET 权限, 写权限
            {"/v1/admin/roles", "role:view", "role:manage"},
            {"/v1/admin/permissions", "role:view", "role:manage"},
            {"/v1/admin/accounts", "account:view", "account:manage"},
            {"/v1/admin/audit", "audit:view", "audit:view"},
            {"/v1/admin/hospitals", "hospital:view", "hospital:manage"},
            {"/v1/admin/departments", "dept:view", "dept:manage"},
            {"/v1/admin/dict", "dict:view", "dict:manage"},
            {"/v1/admin/feedback", "feedback:view", "feedback:handle"},
            {"/v1/admin/handover", "handover:view", "handover:submit"},
            {"/v1/admin/alerts", "alert:view", "alert:ack"},
            {"/v1/admin/dashboard", "dashboard:view", "dashboard:view"},
            {"/v1/admin/patients", "patient:view", "patient:view"},
            {"/v1/dict/drugs", "drug:view", "drug:manage"},
            {"/v1/dict", "dict:view", "dict:manage"},
            {"/v1/patients", "patient:view", "patient:update"},
            {"/v1/nurses", "nurse:view", "nurse:manage"},
            {"/v1/wards", "dept:view", "dept:manage"},
            {"/v1/beds", "bed:view", "bed:manage"},
            {"/v1/infusion", "infusion:view", "infusion:create"},
            {"/v1/fluid-records", "record:view", "record:create"},
            {"/v1/handover/special", "handover:view", "handover:submit"}
    };

    /**
     * @return 需要的权限码；null 表示该接口不在管控范围（放行）
     */
    public static String resolve(String method, String uri) {
        if (!StringUtils.hasText(uri)) {
            return null;
        }
        boolean get = "GET".equalsIgnoreCase(method);

        // 高危/特例接口优先
        if (uri.startsWith("/v1/admin/accounts") && uri.contains("/reset-password")) {
            return "account:reset-pwd";
        }
        if (uri.startsWith("/v1/admin/audit") && uri.contains("/export")) {
            return "audit:export";
        }
        if (uri.startsWith("/v1/admin/feedback") && uri.contains("/fix")) {
            return "feedback:fix";
        }
        if (uri.startsWith("/v1/admin/handover")
                && (uri.endsWith("/confirm") || uri.endsWith("/receive"))) {
            return "handover:confirm";
        }
        if (uri.startsWith("/v1/patients")) {
            if (uri.endsWith("/discharge")) {
                return "patient:discharge";
            }
            if (uri.contains("/bind") || uri.contains("/unbind")) {
                return "patient:bind";
            }
            if (uri.contains("/assign-nurse")) {
                return "assignment:manage";
            }
            if (get) {
                return "patient:view";
            }
            return uri.endsWith("/create") ? "patient:create" : "patient:update";
        }
        if (uri.startsWith("/v1/nurses")) {
            if (uri.contains("/assign-beds") || uri.contains("/claim-my-beds")) {
                return "assignment:manage";
            }
            if (get) {
                return "nurse:view";
            }
            return "nurse:manage";
        }
        if (uri.startsWith("/v1/infusion")) {
            if (uri.contains("/end")) {
                return "infusion:end";
            }
            return get ? "infusion:view" : "infusion:create";
        }
        if (uri.startsWith("/v1/fluid-records")) {
            if (uri.contains("/export-excel")) {
                return "export:record";
            }
            if (uri.contains("/correct")) {
                return "record:correct";
            }
            return get ? "record:view" : "record:create";
        }
        if (uri.startsWith("/v1/handover/special")) {
            if (get) {
                return "handover:view";
            }
            if (uri.contains("/confirm") || uri.contains("/lock")) {
                return "handover:confirm";
            }
            return "handover:submit";
        }

        for (String[] rule : RULES) {
            if (uri.startsWith(rule[0])) {
                return get ? rule[1] : rule[2];
            }
        }
        return null;
    }
}
