package com.patient.io.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patient.io.common.BusinessException;
import com.patient.io.common.ScopeContext;
import com.patient.io.common.TenantContext;
import com.patient.io.common.UserContext;
import com.patient.io.dto.RbacDtos;
import com.patient.io.entity.Nurse;
import com.patient.io.entity.SysAdminUser;
import com.patient.io.entity.SysPermission;
import com.patient.io.entity.SysRole;
import com.patient.io.entity.SysRolePermission;
import com.patient.io.entity.SysUserRole;
import com.patient.io.mapper.NurseMapper;
import com.patient.io.mapper.SysAdminUserMapper;
import com.patient.io.mapper.SysPermissionMapper;
import com.patient.io.mapper.SysRoleMapper;
import com.patient.io.mapper.SysRolePermissionMapper;
import com.patient.io.mapper.SysUserRoleMapper;
import com.patient.io.service.AuditService;
import com.patient.io.service.RbacService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RbacServiceImpl implements RbacService {

    private static final String TYPE_ADMIN = "ADMIN";
    private static final String TYPE_NURSE = "NURSE";

    /** 后台可登录的角色，顺序用于取「主角色」展示 */
    private static final List<String> BACKEND_ROLE_ORDER = Arrays.asList(
            "SUPER_ADMIN", "HOSPITAL_ADMIN", "NURSING_DIRECTOR", "DEPT_DIRECTOR",
            "HEAD_NURSE", "CHARGE_NURSE", "AUDITOR", "NURSE");

    /** 范围宽窄：数值越大越宽 */
    private static final Map<String, Integer> SCOPE_WIDTH = new LinkedHashMap<>();

    static {
        SCOPE_WIDTH.put("SELF", 1);
        SCOPE_WIDTH.put("WARD", 2);
        SCOPE_WIDTH.put("DEPT", 3);
        SCOPE_WIDTH.put("HOSPITAL", 4);
        SCOPE_WIDTH.put("ALL", 5);
    }

    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    @Autowired
    private SysRoleMapper roleMapper;
    @Autowired
    private SysPermissionMapper permissionMapper;
    @Autowired
    private SysRolePermissionMapper rolePermissionMapper;
    @Autowired
    private SysUserRoleMapper userRoleMapper;
    @Autowired
    private SysAdminUserMapper adminUserMapper;
    @Autowired
    private NurseMapper nurseMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuditService auditService;

    @Value("${rbac.enforce:false}")
    private boolean enforce;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        Set<String> perms = new HashSet<>();
        List<String> roleCodes = new ArrayList<>();
        String scope = ScopeContext.SELF;
        List<Long> wardIds = new ArrayList<>();
        Long deptId;
        long expireAt;
    }

    // ==================== 查询与缓存 ====================

    @Override
    public Set<String> permissionsOf(String userType, Long userId) {
        return entry(userType, userId).perms;
    }

    @Override
    public Map<String, Object> scopeOf(String userType, Long userId) {
        CacheEntry e = entry(userType, userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scope", e.scope);
        m.put("wardIds", e.wardIds);
        m.put("deptId", e.deptId);
        return m;
    }

    @Override
    public boolean hasPermission(String permCode) {
        if (permCode == null) {
            return true;
        }
        return permissionsOf(UserContext.getUserType(), UserContext.getUserId()).contains(permCode);
    }

    @Override
    public boolean enforced() {
        return enforce;
    }

    @Override
    public void evictUser(String userType, Long userId) {
        cache.remove(key(userType, userId));
    }

    @Override
    public void evictAll() {
        cache.clear();
    }

    private String key(String userType, Long userId) {
        return (userType == null ? TYPE_NURSE : userType) + ":" + userId;
    }

    private CacheEntry entry(String userType, Long userId) {
        String k = key(userType, userId);
        CacheEntry e = cache.get(k);
        long now = System.currentTimeMillis();
        if (e != null && e.expireAt > now) {
            return e;
        }
        e = load(userType, userId);
        e.expireAt = now + CACHE_TTL_MS;
        cache.put(k, e);
        return e;
    }

    private CacheEntry load(String userType, Long userId) {
        CacheEntry e = new CacheEntry();
        if (userId == null) {
            return e;
        }
        List<SysUserRole> grants = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserType, userType)
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getStatus, 1));
        Date today = startOfToday();
        grants = grants.stream().filter(g -> inEffect(g, today)).collect(Collectors.toList());

        if (grants.isEmpty()) {
            return legacyEntry(userType, userId);
        }

        List<Long> roleIds = grants.stream().map(SysUserRole::getRoleId).distinct().collect(Collectors.toList());
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        roles = roles.stream().filter(r -> r.getStatus() != null && r.getStatus() == 1).collect(Collectors.toList());
        if (roles.isEmpty()) {
            return e;
        }

        List<SysRolePermission> rps = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                .in(SysRolePermission::getRoleId, roles.stream().map(SysRole::getId).collect(Collectors.toList())));
        e.perms = rps.stream().map(SysRolePermission::getPermCode).collect(Collectors.toCollection(LinkedHashSet::new));

        for (SysRole r : roles) {
            e.roleCodes.add(r.getRoleCode());
        }
        e.roleCodes.sort((a, b) -> Integer.compare(roleOrder(a), roleOrder(b)));

        String widest = ScopeContext.SELF;
        Set<Long> wards = new LinkedHashSet<>();
        for (SysRole r : roles) {
            String s = r.getDataScope() == null ? ScopeContext.SELF : r.getDataScope();
            if (SCOPE_WIDTH.getOrDefault(s, 0) > SCOPE_WIDTH.getOrDefault(widest, 0)) {
                widest = s;
            }
        }
        for (SysUserRole g : grants) {
            if (g.getWardIds() != null && !g.getWardIds().trim().isEmpty()) {
                for (String part : g.getWardIds().split(",")) {
                    try {
                        if (!part.trim().isEmpty()) {
                            wards.add(Long.parseLong(part.trim()));
                        }
                    } catch (NumberFormatException ignored) {
                        // 脏数据忽略
                    }
                }
            }
            if (e.deptId == null) {
                e.deptId = g.getDeptId();
            }
        }
        e.scope = widest;
        if (wards.isEmpty() && TypeIsNurse(userType)) {
            Nurse n = nurseMapper.selectById(userId);
            if (n != null && n.getWardId() != null) {
                wards.add(n.getWardId());
            }
            if (e.deptId == null && n != null) {
                e.deptId = n.getDeptId();
            }
        }
        e.wardIds = new ArrayList<>(wards);
        return e;
    }

    private boolean TypeIsNurse(String userType) {
        return TYPE_NURSE.equals(userType);
    }

    /**
     * 兼容兜底：脚本未执行或历史账号未回填授权时，按主角色套用内置角色模板，
     * 避免一开鉴权全站 403。回填成功后自然走标准链路。
     */
    private volatile long migratedCacheAt = 0L;
    private volatile boolean migratedCache = false;

    /**
     * RBAC 是否已初始化（内置角色是否已种下）。
     * 不能用 sys_user_role 的条数判断：管理员把一个角色的授权全部删掉时计数会归零，
     * 会被误判成「未迁移」而重新按主角色字符串放行，等于撤销失效。
     */
    private boolean migrated() {
        long now = System.currentTimeMillis();
        if (now - migratedCacheAt < 60_000L) {
            return migratedCache;
        }
        try {
            Long c = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getHospitalId, 0L));
            migratedCache = c != null && c > 0;
        } catch (Exception e) {
            migratedCache = false;
        }
        migratedCacheAt = now;
        return migratedCache;
    }

    private CacheEntry legacyEntry(String userType, Long userId) {
        CacheEntry e = new CacheEntry();
        if (migrated()) {
            // 已迁移：没有授权就是没有权限，不能再按主角色放行（否则「清空授权」会被兜底绕过）
            return e;
        }
        String primaryRole = null;
        Long hospitalId = null;
        Long wardId = null;
        Long deptId = null;
        if (TYPE_ADMIN.equals(userType)) {
            SysAdminUser a = adminUserMapper.selectById(userId);
            if (a == null) {
                return e;
            }
            primaryRole = a.getRole();
            hospitalId = a.getHospitalId();
        } else {
            Nurse n = nurseMapper.selectById(userId);
            if (n == null) {
                return e;
            }
            primaryRole = n.getRole();
            hospitalId = n.getHospitalId();
            wardId = n.getWardId();
            deptId = n.getDeptId();
        }
        String roleCode = StrUtil.blankToDefault(primaryRole, "NURSE");
        SysRole template = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode).eq(SysRole::getHospitalId, 0L).last("limit 1"));
        if (template != null) {
            List<SysRolePermission> rps = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, template.getId()));
            e.perms = rps.stream().map(SysRolePermission::getPermCode).collect(Collectors.toCollection(LinkedHashSet::new));
            e.roleCodes.add(template.getRoleCode());
            e.scope = template.getDataScope();
        }
        if (e.scope == null) {
            if ("SUPER_ADMIN".equals(roleCode)) {
                e.scope = ScopeContext.ALL;
            } else if ("HOSPITAL_ADMIN".equals(roleCode)) {
                e.scope = ScopeContext.HOSPITAL;
            } else if ("HEAD_NURSE".equals(roleCode)) {
                e.scope = ScopeContext.WARD;
            } else {
                e.scope = ScopeContext.SELF;
            }
        }
        if (wardId != null) {
            e.wardIds.add(wardId);
        }
        e.deptId = deptId;
        log.debug("RBAC 兜底：{}:{} 无有效授权，按主角色 {} 套用模板（hospital={}）",
                userType, userId, roleCode, hospitalId);
        return e;
    }

    private boolean inEffect(SysUserRole g, Date today) {
        if (g.getEffectiveFrom() != null && g.getEffectiveFrom().after(today)) {
            return false;
        }
        return g.getEffectiveTo() == null || !g.getEffectiveTo().before(today);
    }

    private Date startOfToday() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private int roleOrder(String code) {
        int idx = BACKEND_ROLE_ORDER.indexOf(code);
        return idx < 0 ? 99 : idx;
    }

    // ==================== 登录态 ====================

    @Override
    public Map<String, Object> me(String userType, Long userId) {
        Map<String, Object> account = accountRow(userType, userId);
        if (account == null) {
            throw new BusinessException(403, "账号不存在或已被删除");
        }
        if (toInt(account.get("status")) == 0) {
            throw new BusinessException(401, "账号已停用，请联系管理员");
        }
        CacheEntry e = entry(userType, userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", account.get("user_id"));
        m.put("userType", userType);
        m.put("loginName", account.get("login_name"));
        m.put("realName", account.get("real_name"));
        m.put("hospitalId", account.get("hospital_id"));
        m.put("hospitalName", account.get("hospital_name"));
        m.put("phone", account.get("phone"));
        m.put("status", toInt(account.get("status")));
        m.put("roleCodes", e.roleCodes);
        m.put("primaryRole", e.roleCodes.isEmpty() ? account.get("primary_role") : e.roleCodes.get(0));
        m.put("permissions", new ArrayList<>(e.perms));
        m.put("dataScope", e.scope);
        m.put("wardIds", e.wardIds);
        m.put("deptId", e.deptId);
        return m;
    }

    private Map<String, Object> accountRow(String userType, Long userId) {
        String sql;
        if (TYPE_ADMIN.equals(userType)) {
            sql = "SELECT a.id AS user_id, a.username AS login_name, a.real_name, a.hospital_id, " +
                    "h.hospital_name, a.phone, a.status, a.role AS primary_role " +
                    "FROM sys_admin_user a LEFT JOIN hospital h ON h.id = a.hospital_id WHERE a.id = ?";
        } else {
            sql = "SELECT n.id AS user_id, n.job_number AS login_name, n.real_name, n.hospital_id, " +
                    "COALESCE(n.hospital_name, h.hospital_name) AS hospital_name, n.phone, n.status, " +
                    "n.role AS primary_role, n.dept_id, n.ward_id, n.ward_name, n.department " +
                    "FROM nurse n LEFT JOIN hospital h ON h.id = n.hospital_id WHERE n.id = ?";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ==================== 权限与角色 ====================

    @Override
    public List<Map<String, Object>> listPermissions() {
        List<SysPermission> list = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSortOrder));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysPermission p : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("permCode", p.getPermCode());
            m.put("permName", p.getPermName());
            m.put("module", p.getModule());
            m.put("riskLevel", p.getRiskLevel());
            m.put("sortOrder", p.getSortOrder());
            result.add(m);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listRoles(Long hospitalId) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.*, " +
                        "(SELECT COUNT(1) FROM sys_role_permission rp WHERE rp.role_id = r.id) AS perm_count, " +
                        "(SELECT COUNT(1) FROM sys_user_role ur WHERE ur.role_id = r.id AND ur.status = 1) AS user_count " +
                        "FROM sys_role r WHERE (r.hospital_id = 0");
        List<Object> params = new ArrayList<>();
        if (hospitalId != null && hospitalId > 0) {
            sql.append(" OR r.hospital_id = ?");
            params.add(hospitalId);
        }
        sql.append(") ORDER BY r.hospital_id, r.sort_order, r.id");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        return rows.stream().map(this::camelRole).collect(Collectors.toList());
    }

    private Map<String, Object> camelRole(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("roleCode", row.get("role_code"));
        m.put("roleName", row.get("role_name"));
        m.put("roleDesc", row.get("role_desc"));
        m.put("dataScope", row.get("data_scope"));
        m.put("hospitalId", row.get("hospital_id"));
        m.put("builtin", toInt(row.get("builtin")));
        m.put("status", toInt(row.get("status")));
        m.put("sortOrder", row.get("sort_order"));
        m.put("permCount", row.get("perm_count"));
        m.put("userCount", row.get("user_count"));
        return m;
    }

    @Override
    public List<String> rolePermissions(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermCode).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveRole(Long hospitalId, RbacDtos.RoleSave dto) {
        SysRole role;
        if (dto.getId() != null) {
            role = roleMapper.selectById(dto.getId());
            if (role == null) {
                throw new BusinessException("角色不存在");
            }
            assertEditable(role);
        } else {
            String code = StrUtil.blankToDefault(dto.getRoleCode(), "CUSTOM_" + System.currentTimeMillis());
            code = code.trim().toUpperCase();
            if (!code.matches("^[A-Z][A-Z0-9_]{2,31}$")) {
                throw new BusinessException("角色编码需为 3-32 位大写字母、数字或下划线，且以字母开头");
            }
            Long owner = hospitalId != null ? hospitalId : TenantContext.getHospitalId();
            if (owner == null || owner <= 0) {
                throw new BusinessException("请先选择医院后再新建角色");
            }
            // 角色编码全局唯一：builtin 角色 hospital_id=0 且全院共享，
            // 若只按本院查重，允许建一个同名的 HEAD_NURSE 会让「主角色」和路由文案没法判断。
            SysRole dup = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, code).last("limit 1"));
            if (dup != null) {
                String where = dup.getHospitalId() == null || dup.getHospitalId() == 0L
                        ? "平台内置角色" : "医院 " + dup.getHospitalId() + " 的角色";
                throw new BusinessException("角色编码 " + code + " 已被" + where + "占用，请换一个编码");
            }
            role = new SysRole();
            role.setRoleCode(code);
            role.setHospitalId(owner);
            role.setBuiltin(0);
            role.setStatus(1);
            role.setSortOrder(dto.getSortOrder() == null ? 900 : dto.getSortOrder());
            role.setRoleName(StrUtil.blankToDefault(dto.getRoleName(), code));
            role.setRoleDesc(dto.getRoleDesc());
            role.setDataScope(StrUtil.blankToDefault(dto.getDataScope(), ScopeContext.WARD));
            roleMapper.insert(role);
        }
        if (dto.getId() != null) {
            if (StrUtil.isNotBlank(dto.getRoleCode())
                    && !dto.getRoleCode().trim().equalsIgnoreCase(role.getRoleCode())) {
                throw new BusinessException("角色编码创建后不可修改，如需新编码请新建角色");
            }
            role.setRoleName(StrUtil.blankToDefault(dto.getRoleName(), role.getRoleName()));
            role.setRoleDesc(dto.getRoleDesc());
            if (StrUtil.isNotBlank(dto.getDataScope())) {
                role.setDataScope(dto.getDataScope());
            }
            if (dto.getSortOrder() != null) {
                role.setSortOrder(dto.getSortOrder());
            }
            roleMapper.updateById(role);
        }
        if (dto.getPermCodes() != null) {
            saveRolePermissions(role.getId(), dto.getPermCodes());
        }
        auditService.success("ROLE_SAVE", "ROLE", String.valueOf(role.getId()),
                "角色 " + role.getRoleName() + "（" + role.getRoleCode() + "）范围=" + role.getDataScope());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", role.getId());
        m.put("roleCode", role.getRoleCode());
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRolePermissions(Long roleId, List<String> permCodes) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        assertEditable(role);

        List<String> codes = permCodes == null ? Collections.emptyList() : permCodes.stream()
                .filter(StrUtil::isNotBlank).distinct().collect(Collectors.toList());
        if (!codes.isEmpty()) {
            Integer valid = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM sys_permission WHERE perm_code IN (" + placeholders(codes.size()) + ")",
                    Integer.class, codes.toArray());
            if (valid == null || valid != codes.size()) {
                throw new BusinessException("存在无法识别的权限码");
            }
        }
        assertNotElevate(codes);

        List<String> before = rolePermissions(roleId);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        for (String code : codes) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermCode(code);
            rolePermissionMapper.insert(rp);
        }
        evictAll();
        auditService.success("ROLE_PERM_UPDATE", "ROLE", String.valueOf(roleId),
                "角色 " + role.getRoleName() + " 权限 " + before.size() + " → " + codes.size() + " 项");
    }

    @Override
    public void toggleRoleStatus(Long roleId, int status) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        assertEditable(role);
        role.setStatus(status);
        roleMapper.updateById(role);
        evictAll();
        auditService.success(status == 1 ? "ROLE_ENABLE" : "ROLE_DISABLE", "ROLE",
                String.valueOf(roleId), "角色 " + role.getRoleName());
    }

    /** 内置超级管理员是唯一允许存在的「不可删除」角色（代码里大量按该编码兜底） */
    private static final String PROTECTED_ROLE_CODE = "SUPER_ADMIN";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId, boolean force) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        boolean builtin = role.getBuiltin() != null && role.getBuiltin() == 1;
        if (builtin && PROTECTED_ROLE_CODE.equals(role.getRoleCode())) {
            throw new BusinessException("内置超级管理员角色不可删除");
        }
        Long tenant = TenantContext.getHospitalId();
        if (!builtin && tenant != null && role.getHospitalId() != null && !tenant.equals(role.getHospitalId())) {
            throw new BusinessException(403, "不能删除其它医院的角色");
        }

        Long bound = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        long boundCount = bound == null ? 0L : bound;
        if (boundCount > 0 && !force) {
            throw new BusinessException("该角色仍授权给 " + boundCount + " 个账号，请先解除授权，或确认强制删除");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        roleMapper.deleteById(roleId);
        evictAll();
        auditService.success("ROLE_DELETE", "ROLE", String.valueOf(roleId),
                "删除角色 " + role.getRoleName() + "（" + role.getRoleCode() + "）"
                        + (builtin ? "[平台内置]" : "") + "，解除授权 " + boundCount + " 个账号");
        log.warn("角色已删除：{}（{}），解除授权 {} 个，操作人 {}:{}",
                role.getRoleName(), role.getRoleCode(), boundCount,
                UserContext.getUserType(), UserContext.getUserId());
    }

    private void assertEditable(SysRole role) {
        if (role.getBuiltin() != null && role.getBuiltin() == 1) {
            throw new BusinessException("内置角色不可修改，可复制为新角色后调整");
        }
        Long tenant = TenantContext.getHospitalId();
        if (tenant != null && role.getHospitalId() != null && !tenant.equals(role.getHospitalId())) {
            throw new BusinessException(403, "不能修改其它医院的角色");
        }
    }

    /** 平台级权限（只能由超管分配），其余权限允许医院管理员在本院内配置 */
    private static final Set<String> PLATFORM_ONLY_PERMS = new HashSet<>(Arrays.asList(
            "hospital:manage", "hospital:view"
    ));

    private void assertNotElevate(List<String> codes) {
        if (UserContext.getUserId() == null) {
            return;
        }
        if ("SUPER_ADMIN".equals(UserContext.getRole())) {
            return;
        }
        List<String> extra = codes.stream()
                .filter(PLATFORM_ONLY_PERMS::contains)
                .collect(Collectors.toList());
        if (!extra.isEmpty()) {
            throw new BusinessException("仅平台超级管理员可分配该权限：" + String.join("、", extra));
        }
    }

    private String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    // ==================== 账号 ====================

    @Override
    public Map<String, Object> listAccounts(String keyword, String roleCode, Integer status,
                                            String userType, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }
        StringBuilder base = new StringBuilder(
                "SELECT * FROM ( " +
                        "SELECT 'ADMIN' AS user_type, a.id AS user_id, a.username AS login_name, a.real_name, " +
                        "a.hospital_id, h.hospital_name, NULL AS dept_id, NULL AS ward_id, NULL AS ward_name, " +
                        "NULL AS department, a.phone, a.status, a.role AS primary_role " +
                        "FROM sys_admin_user a LEFT JOIN hospital h ON h.id = a.hospital_id " +
                        "UNION ALL " +
                        "SELECT 'NURSE' AS user_type, n.id AS user_id, n.job_number AS login_name, n.real_name, " +
                        "n.hospital_id, COALESCE(n.hospital_name, h.hospital_name) AS hospital_name, " +
                        "n.dept_id, n.ward_id, n.ward_name, n.department, n.phone, n.status, n.role AS primary_role " +
                        "FROM nurse n LEFT JOIN hospital h ON h.id = n.hospital_id " +
                        ") t WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        Long tenant = TenantContext.getHospitalId();
        if (tenant != null) {
            base.append(" AND t.hospital_id = ?");
            params.add(tenant);
        }
        if (StrUtil.isNotBlank(userType)) {
            base.append(" AND t.user_type = ?");
            params.add(userType);
        }
        if (StrUtil.isNotBlank(keyword)) {
            base.append(" AND (t.real_name LIKE ? OR t.login_name LIKE ?)");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }
        if (status != null) {
            base.append(" AND t.status = ?");
            params.add(status);
        }
        if (StrUtil.isNotBlank(roleCode)) {
            base.append(" AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id " +
                    "WHERE ur.user_type = t.user_type AND ur.user_id = t.user_id AND ur.status = 1 AND r.role_code = ?)");
            params.add(roleCode);
        }

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM (" + base + ") c",
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                base + " ORDER BY t.status DESC, t.user_type, t.user_id LIMIT ? OFFSET ?", pageParams.toArray());

        Map<String, List<Map<String, Object>>> roleMap = roleMapFor(rows);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userType", row.get("user_type"));
            m.put("userId", row.get("user_id"));
            m.put("loginName", row.get("login_name"));
            m.put("realName", row.get("real_name"));
            m.put("hospitalId", row.get("hospital_id"));
            m.put("hospitalName", row.get("hospital_name"));
            m.put("deptId", row.get("dept_id"));
            m.put("wardId", row.get("ward_id"));
            m.put("wardName", row.get("ward_name"));
            m.put("department", row.get("department"));
            m.put("phone", row.get("phone"));
            m.put("status", toInt(row.get("status")));
            m.put("primaryRole", row.get("primary_role"));
            List<Map<String, Object>> roles = roleMap.get(row.get("user_type") + ":" + row.get("user_id"));
            m.put("roles", roles == null ? Collections.emptyList() : roles);
            list.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("size", size);
        result.put("list", list);
        return result;
    }

    /** 批量取一页账号的角色，避免 N+1 */
    private Map<String, List<Map<String, Object>>> roleMapFor(List<Map<String, Object>> rows) {
        List<Long> adminIds = new ArrayList<>();
        List<Long> nurseIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("user_id")).longValue();
            if (TYPE_ADMIN.equals(row.get("user_type"))) {
                adminIds.add(id);
            } else {
                nurseIds.add(id);
            }
        }
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        if (adminIds.isEmpty() && nurseIds.isEmpty()) {
            return grouped;
        }
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (!adminIds.isEmpty()) {
            clauses.add("(ur.user_type = 'ADMIN' AND ur.user_id IN (" + placeholders(adminIds.size()) + "))");
            params.addAll(adminIds);
        }
        if (!nurseIds.isEmpty()) {
            clauses.add("(ur.user_type = 'NURSE' AND ur.user_id IN (" + placeholders(nurseIds.size()) + "))");
            params.addAll(nurseIds);
        }
        String sql = "SELECT ur.user_type, ur.user_id, r.id AS role_id, r.role_code, r.role_name, " +
                "r.data_scope, r.builtin, ur.ward_ids, ur.effective_to, ur.dept_id " +
                "FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id " +
                "WHERE ur.status = 1 AND (" + String.join(" OR ", clauses) + ") ORDER BY r.sort_order";
        List<Map<String, Object>> grants = jdbcTemplate.queryForList(sql, params.toArray());
        for (Map<String, Object> g : grants) {
            String k = g.get("user_type") + ":" + g.get("user_id");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roleId", g.get("role_id"));
            item.put("roleCode", g.get("role_code"));
            item.put("roleName", g.get("role_name"));
            item.put("dataScope", g.get("data_scope"));
            item.put("builtin", toInt(g.get("builtin")));
            item.put("wardIds", g.get("ward_ids"));
            item.put("effectiveTo", formatDate(g.get("effective_to")));
            grouped.computeIfAbsent(k, x -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    @Override
    public List<Map<String, Object>> accountRoles(String userType, Long userId) {
        String sql = "SELECT ur.role_id, r.role_code, r.role_name, r.data_scope, r.builtin, " +
                "ur.ward_ids, ur.effective_to, ur.dept_id, ur.status " +
                "FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id " +
                "WHERE ur.user_type = ? AND ur.user_id = ? ORDER BY r.sort_order";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userType, userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roleId", row.get("role_id"));
            m.put("roleCode", row.get("role_code"));
            m.put("roleName", row.get("role_name"));
            m.put("dataScope", row.get("data_scope"));
            m.put("builtin", toInt(row.get("builtin")));
            m.put("wardIds", row.get("ward_ids"));
            m.put("effectiveTo", formatDate(row.get("effective_to")));
            m.put("deptId", row.get("dept_id"));
            m.put("status", toInt(row.get("status")));
            result.add(m);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(String userType, Long userId, RbacDtos.AssignRoles dto, Long hospitalId) {
        Map<String, Object> target = accountRow(userType, userId);
        if (target == null) {
            throw new BusinessException("账号不存在");
        }
        if (userId.equals(UserContext.getUserId()) && userType.equals(UserContext.getUserType())) {
            throw new BusinessException("不能修改自己的角色，请联系其他管理员");
        }
        Long targetHospital = target.get("hospital_id") == null ? null
                : ((Number) target.get("hospital_id")).longValue();
        Long tenant = TenantContext.getHospitalId();
        if (tenant != null && targetHospital != null && !tenant.equals(targetHospital)) {
            throw new BusinessException(403, "不能给其它医院的账号授权");
        }

        List<Long> roleIds = dto.getRoleIds() == null ? Collections.emptyList()
                : dto.getRoleIds().stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        List<SysRole> roles = roleIds.isEmpty() ? Collections.emptyList() : roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException("存在无效的角色");
        }
        boolean superAdmin = "SUPER_ADMIN".equals(UserContext.getRole());
        for (SysRole r : roles) {
            if (r.getStatus() == null || r.getStatus() != 1) {
                throw new BusinessException("角色「" + r.getRoleName() + "」已停用");
            }
            if (r.getHospitalId() != null && r.getHospitalId() != 0
                    && targetHospital != null && !r.getHospitalId().equals(targetHospital)) {
                throw new BusinessException("角色「" + r.getRoleName() + "」不属于该账号所在医院");
            }
            if ("SUPER_ADMIN".equals(r.getRoleCode()) && !superAdmin) {
                throw new BusinessException("仅平台超级管理员可授予该角色");
            }
            if (TYPE_NURSE.equals(userType)) {
                Set<String> perms = new HashSet<>(rolePermissions(r.getId()));
                if (perms.contains("role:manage") || perms.contains("account:manage")
                        || perms.contains("hospital:manage")) {
                    throw new BusinessException("护士账号不能授予系统管理类角色");
                }
            }
        }
        if (!superAdmin && !roles.isEmpty()) {
            List<String> all = new ArrayList<>();
            roles.forEach(r -> all.addAll(rolePermissions(r.getId())));
            assertNotElevate(all);
        }

        // 最后一个医院管理员保护
        boolean keepsHospitalAdmin = roles.stream().anyMatch(r -> "HOSPITAL_ADMIN".equals(r.getRoleCode()));
        if (!keepsHospitalAdmin && targetHospital != null && "HOSPITAL_ADMIN".equals(target.get("primary_role"))) {
            Integer others = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM sys_admin_user WHERE hospital_id = ? AND role = 'HOSPITAL_ADMIN' " +
                            "AND status = 1 AND id <> ?", Integer.class, targetHospital, userId);
            if (others == null || others == 0) {
                throw new BusinessException("每个医院至少保留一名医院管理员，操作已拦截");
            }
        }

        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserType, userType)
                .eq(SysUserRole::getUserId, userId));

        Date effectiveTo = null;
        if (StrUtil.isNotBlank(dto.getEffectiveTo())) {
            try {
                effectiveTo = new SimpleDateFormat("yyyy-MM-dd").parse(dto.getEffectiveTo());
            } catch (Exception e) {
                throw new BusinessException("有效期格式应为 yyyy-MM-dd");
            }
        }
        String wardIds = dto.getWardIds() == null || dto.getWardIds().isEmpty() ? null
                : dto.getWardIds().stream().map(String::valueOf).collect(Collectors.joining(","));
        for (SysRole r : roles) {
            SysUserRole ur = new SysUserRole();
            ur.setUserType(userType);
            ur.setUserId(userId);
            ur.setRoleId(r.getId());
            ur.setHospitalId(targetHospital == null ? 0L : targetHospital);
            ur.setDeptId(dto.getDeptId());
            ur.setWardIds(wardIds);
            ur.setEffectiveTo(effectiveTo);
            ur.setStatus(1);
            ur.setGrantBy(UserContext.getUserId());
            userRoleMapper.insert(ur);
        }
        evictAll();
        auditService.success("ROLE_GRANT", userType.equals(TYPE_ADMIN) ? "ADMIN_USER" : "NURSE_USER",
                String.valueOf(userId),
                "账号 " + target.get("real_name") + " 授权 " + roles.size() + " 个角色：" +
                        roles.stream().map(SysRole::getRoleName).collect(Collectors.joining("、")) +
                        (wardIds == null ? "" : "；病区 " + wardIds) +
                        (effectiveTo == null ? "" : "；有效期至 " + dto.getEffectiveTo()));
    }

    @Override
    public void ensurePrimaryGrant(String userType, Long userId, String roleCode,
                                   Long hospitalId, Long deptId, Long wardId) {
        if (userId == null) {
            return;
        }
        Long exists = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserType, userType)
                .eq(SysUserRole::getUserId, userId));
        if (exists != null && exists > 0) {
            return;
        }
        String code = StrUtil.blankToDefault(roleCode, "NURSE");
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, code).eq(SysRole::getHospitalId, 0L).last("limit 1"));
        if (role == null) {
            return;
        }
        SysUserRole ur = new SysUserRole();
        ur.setUserType(userType);
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        ur.setHospitalId(hospitalId == null ? 0L : hospitalId);
        ur.setDeptId(deptId);
        ur.setWardIds(wardId == null ? null : String.valueOf(wardId));
        ur.setStatus(1);
        ur.setGrantBy(UserContext.getUserId());
        userRoleMapper.insert(ur);
        evictUser(userType, userId);
        log.info("新账号自动授权：{}:{} -> {}", userType, userId, code);
    }

    @Override
    public Map<String, Object> resetPassword(String userType, Long userId) {
        Map<String, Object> target = accountRow(userType, userId);
        if (target == null) {
            throw new BusinessException("账号不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (TYPE_ADMIN.equals(userType)) {
            String pwd = randomPassword(10);
            SysAdminUser user = adminUserMapper.selectById(userId);
            user.setPassword(SecureUtil.md5(pwd));
            adminUserMapper.updateById(user);
            evictUser(userType, userId);
            auditService.success("ACCOUNT_RESET_PWD", "ADMIN_USER", String.valueOf(userId),
                    "重置管理员 " + target.get("real_name") + " 的密码");
            result.put("mode", "GENERATED");
            result.put("password", pwd);
            result.put("tip", "该密码仅展示一次，请通过安全渠道转达并提醒首次登录后修改");
        } else {
            auditService.success("ACCOUNT_RESET_PWD", "NURSE_USER", String.valueOf(userId),
                    "重置护士 " + target.get("real_name") + " 的密码（工号登录规则）");
            result.put("mode", "DEFAULT");
            result.put("password", "手机号后 6 位 或 123456");
            result.put("tip", "护士账号按工号登录，密码规则为手机号后 6 位，未登记手机号时使用初始密码 123456");
        }
        return result;
    }

    private String randomPassword(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    public void toggleAccountStatus(String userType, Long userId, int status) {
        if (userId.equals(UserContext.getUserId()) && userType.equals(UserContext.getUserType())) {
            throw new BusinessException("不能停用自己的账号");
        }
        Map<String, Object> target = accountRow(userType, userId);
        if (target == null) {
            throw new BusinessException("账号不存在");
        }
        Long tenant = TenantContext.getHospitalId();
        Object targetHospital = target.get("hospital_id");
        if (tenant != null && targetHospital != null && !tenant.equals(((Number) targetHospital).longValue())) {
            throw new BusinessException(403, "不能操作其它医院的账号");
        }
        if (TYPE_ADMIN.equals(userType)) {
            SysAdminUser u = adminUserMapper.selectById(userId);
            if (u == null) {
                throw new BusinessException("账号不存在");
            }
            if (status == 0 && "HOSPITAL_ADMIN".equals(u.getRole()) && u.getHospitalId() != null) {
                Integer others = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM sys_admin_user WHERE hospital_id = ? AND role = 'HOSPITAL_ADMIN' " +
                                "AND status = 1 AND id <> ?", Integer.class, u.getHospitalId(), userId);
                if (others == null || others == 0) {
                    throw new BusinessException("每个医院至少保留一名启用的医院管理员");
                }
            }
            u.setStatus(status);
            adminUserMapper.updateById(u);
        } else {
            Nurse n = nurseMapper.selectById(userId);
            if (n == null) {
                throw new BusinessException("账号不存在");
            }
            n.setStatus(status);
            nurseMapper.updateById(n);
        }
        evictUser(userType, userId);
        auditService.success(status == 1 ? "ACCOUNT_ENABLE" : "ACCOUNT_DISABLE",
                TYPE_ADMIN.equals(userType) ? "ADMIN_USER" : "NURSE_USER",
                String.valueOf(userId),
                (status == 1 ? "启用" : "停用") + "账号 " + target.get("real_name"));
    }

    // ==================== 审计 ====================

    @Override
    public Map<String, Object> listAuditLogs(String keyword, String action, String result,
                                             String from, String to, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        Long tenant = TenantContext.getHospitalId();
        if (tenant != null) {
            where.append(" AND (hospital_id = ? OR hospital_id IS NULL)");
            params.add(tenant);
        }
        if (StrUtil.isNotBlank(keyword)) {
            where.append(" AND (username LIKE ? OR real_name LIKE ? OR target_id LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (StrUtil.isNotBlank(action)) {
            where.append(" AND action = ?");
            params.add(action);
        }
        if (StrUtil.isNotBlank(result)) {
            where.append(" AND result = ?");
            params.add(result);
        }
        if (StrUtil.isNotBlank(from)) {
            where.append(" AND create_time >= ?");
            params.add(from + " 00:00:00");
        }
        if (StrUtil.isNotBlank(to)) {
            where.append(" AND create_time <= ?");
            params.add(to + " 23:59:59");
        }

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_audit_log" + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM sys_audit_log" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", pageParams.toArray());

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.get("id"));
            m.put("userType", row.get("user_type"));
            m.put("userId", row.get("user_id"));
            m.put("username", row.get("username"));
            m.put("realName", row.get("real_name"));
            m.put("roleCode", row.get("role_code"));
            m.put("hospitalId", row.get("hospital_id"));
            m.put("action", row.get("action"));
            m.put("targetType", row.get("target_type"));
            m.put("targetId", row.get("target_id"));
            m.put("detail", row.get("detail"));
            m.put("result", row.get("result"));
            m.put("ip", row.get("ip"));
            m.put("createTime", formatDateTime(row.get("create_time")));
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total == null ? 0 : total);
        out.put("page", page);
        out.put("size", size);
        out.put("list", list);
        return out;
    }

    /** tinyint 经 JDBC 可能映射为 Boolean，统一归一为 0/1 */
    private int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? 1 : 0;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return 0;
    }

    private String formatDate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.util.Date) {
            return new SimpleDateFormat("yyyy-MM-dd").format((java.util.Date) v);
        }
        return String.valueOf(v);
    }

    private String formatDateTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.util.Date) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((java.util.Date) v);
        }
        return String.valueOf(v);
    }
}
