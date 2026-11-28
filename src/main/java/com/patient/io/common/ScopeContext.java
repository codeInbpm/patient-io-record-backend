package com.patient.io.common;

/**
 * 数据范围上下文（由 AuthInterceptor 在登录后注入，请求结束清理）。
 *
 * scope 取值：ALL / HOSPITAL / DEPT / WARD / SELF
 * wardIds 为 scope=WARD 时的病区白名单；为空表示「尚未收窄」，由业务查询自行兜底。
 */
public class ScopeContext {

    private static final ThreadLocal<String> SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<java.util.List<Long>> WARD_IDS = new ThreadLocal<>();
    private static final ThreadLocal<Long> DEPT_ID = new ThreadLocal<>();

    public static final String ALL = "ALL";
    public static final String HOSPITAL = "HOSPITAL";
    public static final String DEPT = "DEPT";
    public static final String WARD = "WARD";
    public static final String SELF = "SELF";

    public static void set(String scope, java.util.List<Long> wardIds, Long deptId) {
        SCOPE.set(scope);
        WARD_IDS.set(wardIds);
        DEPT_ID.set(deptId);
    }

    public static String getScope() {
        return SCOPE.get();
    }

    public static java.util.List<Long> getWardIds() {
        return WARD_IDS.get();
    }

    public static Long getDeptId() {
        return DEPT_ID.get();
    }

    /** scope=WARD 且配置了病区白名单时，判断目标病区是否可访问 */
    public static boolean canAccessWard(Long wardId) {
        if (wardId == null) {
            return true;
        }
        java.util.List<Long> ids = WARD_IDS.get();
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        return ids.contains(wardId);
    }

    public static void clear() {
        SCOPE.remove();
        WARD_IDS.remove();
        DEPT_ID.remove();
    }
}
