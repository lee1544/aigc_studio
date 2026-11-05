package com.example.aigc.web;

import com.example.aigc.annotation.RequirePermission;
import com.example.aigc.dto.contract.RolePermissionSnapshotDto;
import com.example.aigc.entity.Permission;
import com.example.aigc.entity.Role;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.service.RoleService;
import com.example.aigc.utils.UserContextUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理：/api/roles。
 * <p>超级管理员可查看全部组织的角色；学校创作账号没有管理权限，不能访问本控制器。
 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {
    
    @Autowired
    private RoleService roleService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @RequirePermission("role:view")
    @GetMapping
    public List<Role> getRoles(@RequestParam(name = "orgId", required = false) Integer orgId) {
        Integer scopeOrgId = UserContextUtil.getVisibleOrgId();
        // 超级管理员可按组织筛选；非超管强制本组织
        return roleService.getRoles(scopeOrgId, scopeOrgId != null ? scopeOrgId : orgId);
    }
    
    @RequirePermission("role:view")
    @GetMapping("/{id}")
    public Role getRole(@PathVariable Integer id) {
        return roleService.getRoleById(id, UserContextUtil.getVisibleOrgId());
    }
    
    @RequirePermission("role:create")
    @PostMapping
    public Role createRole(@RequestBody Role role) {
        return roleService.createRole(
                UserContextUtil.getCurrentOrgId(),
                UserContextUtil.getVisibleOrgId(),
                role);
    }
    
    @RequirePermission("role:update")
    @PutMapping("/{id}")
    public Role updateRole(@PathVariable Integer id, @RequestBody Role role) {
        return roleService.updateRole(id, role, UserContextUtil.getVisibleOrgId());
    }
    
    @RequirePermission("role:delete")
    @DeleteMapping("/{id}")
    public void deleteRole(@PathVariable Integer id) {
        roleService.deleteRole(id, UserContextUtil.getVisibleOrgId());
    }

    /**
     * 覆盖角色权限。
     * <p>兼容两种 body：
     * <ul>
     *   <li>旧格式：{@code [1,2,3]}（无版本校验，内部工具兼容）；</li>
     *   <li>新格式：{@code {"permissionIds":[1,2,3],"expectedVersion":2}}，
     *       版本不一致返回 409；组织创作角色只允许 25 项 feature。</li>
     * </ul>
     */
    @RequirePermission("role:assign-permission")
    @PostMapping("/{id}/permissions")
    public void assignPermissionsToRole(@PathVariable Integer id, @RequestBody JsonNode body) {
        Integer scopeOrgId = UserContextUtil.getVisibleOrgId();
        if (body == null || body.isNull()) {
            throw new BusinessException("权限列表不能为空");
        }
        if (body.isArray()) {
            Integer[] permissionIds = objectMapper.convertValue(body, Integer[].class);
            roleService.assignPermissionsToRole(id, permissionIds, scopeOrgId);
            return;
        }
        if (!body.isObject()) {
            throw new BusinessException("权限列表格式不正确");
        }
        JsonNode idsNode = body.get("permissionIds");
        Integer[] permissionIds = idsNode == null || idsNode.isNull()
                ? new Integer[0]
                : objectMapper.convertValue(idsNode, Integer[].class);
        Integer expectedVersion = body.hasNonNull("expectedVersion")
                ? body.get("expectedVersion").asInt()
                : null;
        roleService.assignPermissionsToRole(id, permissionIds, expectedVersion, scopeOrgId);
    }
    
    @RequirePermission("role:view")
    @GetMapping("/{id}/permissions")
    public List<Permission> getRolePermissions(@PathVariable Integer id) {
        return roleService.getRolePermissions(id, UserContextUtil.getVisibleOrgId());
    }

    /** 角色权限快照（权限 + 版本），供前端带 expectedVersion 保存。 */
    @RequirePermission("role:view")
    @GetMapping("/{id}/permission-snapshot")
    public RolePermissionSnapshotDto getPermissionSnapshot(@PathVariable Integer id) {
        return roleService.getPermissionSnapshot(id, UserContextUtil.getVisibleOrgId());
    }
}
