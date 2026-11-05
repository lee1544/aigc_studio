package com.example.aigc.dto.contract;

import com.example.aigc.entity.Permission;
import lombok.Data;

import java.util.List;

/**
 * GET /api/roles/{id}/permission-snapshot：
 * 角色当前权限 + 权限版本，供前端带 expectedVersion 保存。
 */
@Data
public class RolePermissionSnapshotDto {

    private Integer version;

    private List<Permission> permissions;
}
