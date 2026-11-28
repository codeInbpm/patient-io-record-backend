package com.patient.io.dto;

import lombok.Data;

import java.util.List;

/**
 * RBAC 相关请求体。集中放这里，避免为每个轻量结构单独建文件。
 */
public class RbacDtos {

    @Data
    public static class RoleSave {
        private Long id;
        private String roleCode;
        private String roleName;
        private String roleDesc;
        private String dataScope;
        private Integer sortOrder;
        /** 复制模板：可为空 */
        private List<String> permCodes;
    }

    @Data
    public static class AssignRoles {
        private List<Long> roleIds;
        private Long deptId;
        /** 病区 ID 列表，空=按角色默认范围 */
        private List<Long> wardIds;
        /** yyyy-MM-dd，空=长期有效 */
        private String effectiveTo;
    }

    @Data
    public static class StatusBody {
        private Integer status;
    }
}
