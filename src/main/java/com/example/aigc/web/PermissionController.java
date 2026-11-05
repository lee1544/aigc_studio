package com.example.aigc.web;

import com.example.aigc.annotation.RequirePermission;
import com.example.aigc.entity.Permission;
import com.example.aigc.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    /**
     * 权限码列表。
     * <ul>
     *   <li>默认：管理端 RBAC 权限码；</li>
     *   <li>{@code scope=feature}：25 项创作 feature，供组织创作角色授权。</li>
     * </ul>
     */
    @RequirePermission("role:view")
    @GetMapping
    public List<Permission> getPermissions(
            @RequestParam(name = "scope", required = false) String scope) {
        if ("feature".equalsIgnoreCase(scope)) {
            return permissionService.getFeaturePermissions();
        }
        return permissionService.getAllBackendPermissions();
    }

    /** 权限码启停是全局操作，仅超级管理员。 */
    @RequirePermission("role:update")
    @PutMapping("/{id}/status")
    public Permission updatePermissionStatus(@PathVariable Integer id,
                                             @RequestParam Integer status) {
        return permissionService.updatePermissionStatus(id, status);
    }
}
