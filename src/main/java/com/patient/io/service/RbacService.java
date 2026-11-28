package com.patient.io.service;

import com.patient.io.dto.RbacDtos;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色权限服务（RBAC）。
 *
 * 关键约定：
 *  - 权限码是唯一的裁决依据；sys_admin_user.role / nurse.role 只作为「主角色」用于展示与兼容。
 *  - 账号类型 userType：ADMIN -> sys_admin_user，NURSE -> nurse。
 *  - 数据范围：ALL > HOSPITAL > DEPT > WARD > SELF，多角色取最宽。
 */
public interface RbacService {

    /** 权限缓存命中/聚合：某账号当前生效的权限码 */
    Set<String> permissionsOf(String userType, Long userId);

    /** 数据范围（含病区白名单） */
    Map<String, Object> scopeOf(String userType, Long userId);

    /** 当前登录账号是否具备某权限码 */
    boolean hasPermission(String permCode);

    /** 是否开启强制鉴权（false 时只记录影子日志） */
    boolean enforced();

    void evictUser(String userType, Long userId);

    void evictAll();

    /** 登录后返回：账号信息 + 角色 + 权限 + 数据范围 */
    Map<String, Object> me(String userType, Long userId);

    /** 全部权限码（按模块排序） */
    List<Map<String, Object>> listPermissions();

    /** 角色列表：内置 + 本院自建 */
    List<Map<String, Object>> listRoles(Long hospitalId);

    /** 某角色的权限码 */
    List<String> rolePermissions(Long roleId);

    /** 新建 / 编辑自建角色 */
    Map<String, Object> saveRole(Long hospitalId, RbacDtos.RoleSave dto);

    /** 保存角色权限（内置角色拒绝） */
    void saveRolePermissions(Long roleId, List<String> permCodes);

    void toggleRoleStatus(Long roleId, int status);

    /**
     * 删除角色（级联清理角色权限与账号授权）。
     * 唯一不可删除的是内置超级管理员 SUPER_ADMIN，其它内置/自建角色均可删。
     *
     * @param force 角色仍绑定账号时是否强制删除；false 则拒绝并提示先解绑
     */
    void deleteRole(Long roleId, boolean force);

    /** 账号联合列表（管理员 + 护士） */
    Map<String, Object> listAccounts(String keyword, String roleCode, Integer status,
                                     String userType, int page, int size);

    /** 某账号的授权明细 */
    List<Map<String, Object>> accountRoles(String userType, Long userId);

    /** 全量覆盖式授权 */
    void assignRoles(String userType, Long userId, RbacDtos.AssignRoles dto, Long hospitalId);

    /**
     * 新账号兜底授权：账号创建后若还没有任何授权，按主角色补一条，
     * 否则「已迁移」状态下新账号会一条权限都没有。
     */
    void ensurePrimaryGrant(String userType, Long userId, String roleCode,
                            Long hospitalId, Long deptId, Long wardId);

    /** 重置密码：管理员返回一次性明文，护士返回默认口令说明 */
    Map<String, Object> resetPassword(String userType, Long userId);

    void toggleAccountStatus(String userType, Long userId, int status);

    /** 审计日志分页查询 */
    Map<String, Object> listAuditLogs(String keyword, String action, String result,
                                      String from, String to, int page, int size);
}
