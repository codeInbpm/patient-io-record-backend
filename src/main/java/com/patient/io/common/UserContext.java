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

    public static void clear() {
        CURRENT_USER.remove();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class CurrentUserInfo {
        private Long userId;
        private String openid;
        private String role; // NURSE, PATIENT
    }
}
