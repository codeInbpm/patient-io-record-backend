package com.patient.io.common;

public class UserContext {
    private static final ThreadLocal<CurrentUserInfo> CURRENT_USER = new ThreadLocal<>();

    public static void set(CurrentUserInfo user) {
        CURRENT_USER.set(user);
    }

    public static CurrentUserInfo get() {
        return CURRENT_USER.get();
    }

    public static Long getUserId() {
        CurrentUserInfo u = get();
        return u != null ? u.getUserId() : null;
    }

    public static String getRole() {
        CurrentUserInfo u = get();
        return u != null ? u.getRole() : "PATIENT";
    }

    /** ADMIN / NURSE，用于把账号映射回 sys_admin_user 或 nurse 表 */
    public static String getUserType() {
        CurrentUserInfo u = get();
        if (u == null || u.getUserType() == null) {
            return "NURSE";
        }
        return u.getUserType();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class CurrentUserInfo {
        private Long userId;
        private String openid;
        private String role; // SUPER_ADMIN / HOSPITAL_ADMIN / HEAD_NURSE / NURSE / PATIENT ...
        private String userType; // ADMIN / NURSE

        public CurrentUserInfo(Long userId, String openid, String role) {
            this(userId, openid, role,
                    "SUPER_ADMIN".equals(role) || "HOSPITAL_ADMIN".equals(role) ? "ADMIN" : "NURSE");
        }

        public CurrentUserInfo(Long userId, String openid, String role, String userType) {
            this.userId = userId;
            this.openid = openid;
            this.role = role;
            this.userType = userType;
        }
    }
}
