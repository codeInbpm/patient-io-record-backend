package com.patient.io.service;

import com.patient.io.common.UserContext;
import com.patient.io.entity.SysAuditLog;
import com.patient.io.mapper.SysAuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * 操作审计。只增不改：没有 update / delete 方法。
 * 审计写入失败不能影响业务主流程，因此统一 catch 后打日志。
 */
@Slf4j
@Service
public class AuditService {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAIL = "FAIL";

    @Autowired
    private SysAuditLogMapper auditLogMapper;

    public void success(String action, String targetType, String targetId, String detail) {
        write(action, targetType, targetId, detail, SUCCESS);
    }

    public void fail(String action, String targetType, String targetId, String detail) {
        write(action, targetType, targetId, detail, FAIL);
    }

    public void write(String action, String targetType, String targetId, String detail, String result) {
        try {
            SysAuditLog entry = new SysAuditLog();
            entry.setUserType(UserContext.getUserType());
            entry.setUserId(UserContext.getUserId());
            entry.setRoleCode(UserContext.getRole());
            entry.setHospitalId(com.patient.io.common.TenantContext.getHospitalId());
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(trim(detail, 990));
            entry.setResult(result);
            entry.setCreateTime(new Date());

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entry.setIp(clientIp(request));
                String ua = request.getHeader("User-Agent");
                entry.setUserAgent(trim(ua, 250));
            }
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("写审计日志失败 action={} detail={}", action, detail, e);
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String h : headers) {
            String v = request.getHeader(h);
            if (v != null && !v.isEmpty() && !"unknown".equalsIgnoreCase(v)) {
                int idx = v.indexOf(',');
                return idx > 0 ? v.substring(0, idx).trim() : v.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String trim(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}
