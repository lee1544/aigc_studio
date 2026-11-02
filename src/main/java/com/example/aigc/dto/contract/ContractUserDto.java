package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 前端契约 User / SessionUser / UserListItem 的统一 DTO。
 * 对应 OpenAPI 的 User、SessionUser、UserListItem schema。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractUserDto {

    private Integer id;

    private String name;

    private String email;

    /** "admin" 或 "user" */
    private String role;

    /** "active" / "disabled" / "deleted" */
    private String status;

    @JsonProperty("created_at")
    private String createdAt;

    /** SessionUser 专属 */
    @JsonProperty("token_version")
    private Integer tokenVersion;

    /** SessionUser 专属：完整 feature 快照 */
    private Map<String, Boolean> features;

    /** PATCH /api/admin/users/{id} 用 */
    private String phone;

    // ---- 旧管理后台 RBAC 面板兼容字段（契约 SessionUser 不禁止额外字段） ----
    @JsonProperty("username")
    private String username;

    @JsonProperty("realName")
    private String realName;

    @JsonProperty("orgId")
    private Integer orgId;

    @JsonProperty("orgName")
    private String orgName;

    @JsonProperty("firstLogin")
    private Boolean firstLogin;

    private List<String> roles;

    private List<String> permissions;
}
