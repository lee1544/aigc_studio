package com.example.aigc.dto.contract;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.entity.User;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * User 实体与契约 DTO 之间的映射 + status 枚举转换。
 * 内部 status: 1=active, 0=disabled, -1=deleted。
 */
public final class UserMapper {

    private UserMapper() {}

    public static final String ACTIVE = "active";
    public static final String DISABLED = "disabled";
    public static final String DELETED = "deleted";

    public static String statusToString(Integer status) {
        if (status == null) return ACTIVE;
        switch (status) {
            case 0: return DISABLED;
            case -1: return DELETED;
            default: return ACTIVE;
        }
    }

    /** active/disabled -> 1/0；deleted 不通过此接口（软删除专用）。 */
    public static Integer statusToInt(String status) {
        if (status == null) return null;
        switch (status) {
            case DISABLED: return 0;
            case DELETED: return -1;
            case ACTIVE:
            default: return 1;
        }
    }

    /**
     * 数据库里的 DATETIME 是 JVM 本地时区的墙上时间（由 {@code LocalDateTime.now()} 写入），
     * 而契约要求 ISO-8601 UTC。这里先按本地时区解释该时间，再转换到 UTC 输出，
     * 避免把 14:45（+08:00）直接标成 {@code 14:45:25Z} 导致前端再加 8 小时。
     */
    public static String iso(LocalDateTime time) {
        if (time == null) return null;
        return DateTimeFormatter.ISO_DATE_TIME.format(
                time.atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime());
    }

    /** 基础 User（无 created_at/features）。 */
    public static ContractUserDto toUser(User u) {
        ContractUserDto dto = new ContractUserDto();
        dto.setId(u.getId());
        dto.setName(displayName(u));
        dto.setEmail(u.getEmail());
        dto.setRole(u.getRole());
        dto.setStatus(statusToString(u.getStatus()));
        return dto;
    }

    /** 列表项（含 created_at）。 */
    public static ContractUserDto toListItem(User u) {
        ContractUserDto dto = toUser(u);
        dto.setCreatedAt(iso(u.getCreateTime()));
        return dto;
    }

    /**
     * GET /api/admin/users 分页列表项：契约字段 + 管理页扩展字段。
     * orgName/roleIds/roleNames/features 需调用方批量补齐。
     */
    public static AdminUserListItemDto toAdminListItem(User u) {
        AdminUserListItemDto dto = new AdminUserListItemDto();
        dto.setId(u.getId());
        dto.setName(displayName(u));
        dto.setEmail(u.getEmail());
        dto.setUsername(u.getUsername());
        dto.setRealName(u.getRealName());
        dto.setPhone(u.getPhone());
        dto.setRole(u.getRole());
        dto.setStatus(statusToString(u.getStatus()));
        dto.setCreatedAt(iso(u.getCreateTime()));
        dto.setLastLoginAt(iso(u.getLastLoginTime()));
        dto.setOrgId(u.getOrgId());
        return dto;
    }

    /** SessionUser（含 token_version、features、created_at）。 */
    public static ContractUserDto toSessionUser(User u, Map<String, Boolean> features) {
        ContractUserDto dto = toUser(u);
        dto.setTokenVersion(u.getTokenVersion());
        dto.setFeatures(features == null ? FeaturePermissions.allFalse() : features);
        dto.setCreatedAt(iso(u.getCreateTime()));
        return dto;
    }

    /** 契约 name 优先；其次 realName；最后 username/email。 */
    public static String displayName(User u) {
        if (u.getName() != null && !u.getName().isEmpty()) return u.getName();
        if (u.getRealName() != null && !u.getRealName().isEmpty()) return u.getRealName();
        if (u.getEmail() != null && !u.getEmail().isEmpty()) return u.getEmail();
        return u.getUsername();
    }
}
