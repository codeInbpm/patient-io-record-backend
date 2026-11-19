package com.patient.io.common;

/**
 * 多租户上下文，基于 ThreadLocal 绑定当前线程的医院租户状态
 */
public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_HOSPITAL_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE_TENANT = new ThreadLocal<>();

    public static void setHospitalId(Long hospitalId) {
        CURRENT_HOSPITAL_ID.set(hospitalId);
    }

    public static Long getHospitalId() {
        return CURRENT_HOSPITAL_ID.get();
    }

    public static void setIgnoreTenant(boolean ignore) {
        IGNORE_TENANT.set(ignore);
    }

    public static boolean isIgnoreTenant() {
        Boolean ignore = IGNORE_TENANT.get();
        return ignore != null && ignore;
    }

    public static void clear() {
        CURRENT_HOSPITAL_ID.remove();
        IGNORE_TENANT.remove();
    }
}
