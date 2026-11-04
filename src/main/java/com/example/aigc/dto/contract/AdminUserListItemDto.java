package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * GET /api/admin/users 分页列表项。
 * 契约 UserListItem（id/name/email/role/status/created_at）的超集：
 * 补齐 username、realName、phone、orgId/orgName、roleIds/roleNames、features。
 */
@Data
public class AdminUserListItemDto {

    private Integer id;

    private String name;

    private String email;

    private String username;

    private String realName;

    private String phone;

    /** "admin" 或 "user" */
    private String role;

    /** "active" / "disabled" / "deleted" */
    private String status;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("last_login_at")
    private String lastLoginAt;

    @JsonProperty("orgId")
    private Integer orgId;

    /** orgId 为 0/null（未分配组织）时为 null。 */
    @JsonProperty("orgName")
    private String orgName;

    /** 旧 RBAC 角色 id（不含承载 feature 的个人角色 USER_FEATURES_*）。 */
    @JsonProperty("roleIds")
    private List<Integer> roleIds;

    @JsonProperty("roleNames")
    private List<String> roleNames;

    /** 25 项 feature 快照。 */
    private Map<String, Boolean> features;
}
