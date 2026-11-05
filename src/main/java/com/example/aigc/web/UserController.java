package com.example.aigc.web;

import com.example.aigc.annotation.RequirePermission;
import com.example.aigc.dto.CreateUserRequest;
import com.example.aigc.dto.PageResponse;
import com.example.aigc.dto.UpdatePasswordRequest;
import com.example.aigc.dto.UpdateUserRequest;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.exception.ForbiddenException;
import com.example.aigc.service.RoleService;
import com.example.aigc.service.UserRoleService;
import com.example.aigc.service.UserService;
import com.example.aigc.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 旧版用户管理：/api/users（按组织 + RBAC 权限码）。
 * <p>
 * 数据范围：超级管理员可查看/管理全部组织的账号；组织管理员只能管理本组织。
 * 新接入请使用 {@code /api/admin/users}（{@link AdminUserController}），
 * 两者共享同一套会话撤销逻辑；本控制器保留仅为兼容旧管理后台，后续版本将下线。
 */
@Deprecated
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleService userRoleService;

    @RequirePermission("user:create")
    @PostMapping
    public User createUser(@Valid @RequestBody CreateUserRequest createUserRequest) {
        Integer orgId = UserContextUtil.getCurrentOrgId();
        // 超级管理员按组织筛选后新建用户时，落到所选组织；组织管理员始终落到本组织
        if (UserContextUtil.isSuperAdmin() && createUserRequest.getOrgId() != null) {
            orgId = createUserRequest.getOrgId();
        }
        return userService.createUser(orgId, createUserRequest);
    }

    @RequirePermission("user:view")
    @GetMapping
    public PageResponse<User> getUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "realname", required = false) String realname,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "orgId", required = false) Integer orgId) {
        Integer scopeOrgId = UserContextUtil.getVisibleOrgId();
        // 组织管理员忽略 orgId 参数，只能看本组织；超级管理员可按组织筛选，null 表示全部
        Integer queryOrgId = scopeOrgId != null ? scopeOrgId : orgId;
        Sort sort = Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"));
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        boolean includeRoles = UserContextUtil.hasCurrentPermission("role:view");
        Page<User> userPage = userService.getUsersByOrgId(
                queryOrgId, trimToNull(realname), status, pageRequest, includeRoles);

        return PageResponse.of(
                userPage.getContent(),
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements()
        );
    }

    @RequirePermission("user:view")
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Integer id) {
        return requireAccessibleUser(id);
    }

    @RequirePermission("user:view")
    @GetMapping("/{id}/roles")
    public List<Integer> getUserRoles(@PathVariable Integer id) {
        requireAccessibleUser(id);
        return userRoleService.getRoleIdsByUserId(id);
    }

    @RequirePermission("user:update")
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Integer id, @Valid @RequestBody UpdateUserRequest request) {
        requireAccessibleUser(id);
        return userService.updateUserRealName(id, request.getRealName());
    }

    @RequirePermission("user:update")
    @PutMapping("/{id}/status")
    public User updateUserStatus(@PathVariable Integer id, @RequestParam Integer status) {
        requireAccessibleUser(id);
        return userService.updateUserStatus(id, status);
    }

    /**
     * 本人改密走自助通道：只要登录且能提供原密码即可，不要求 user:update；
     * 修改他人密码仍需 user:update，且目标账号必须在调用方数据范围内。
     */
    @PutMapping("/{id}/password")
    public User updateUser(@PathVariable Integer id, @Valid @RequestBody UpdatePasswordRequest request) {
        Integer currentUserId = UserContextUtil.getCurrentUserId();
        if (!currentUserId.equals(id)) {
            if (!UserContextUtil.hasCurrentPermission("user:update")) {
                throw new ForbiddenException("没有权限访问");
            }
            requireAccessibleUser(id);
        }
        return userService.updateUserPassword(id, request.getOldPassword(), request.getPassword());
    }

    @RequirePermission("user:reset-password")
    @PutMapping("/{id}/reset-password")
    public User resetUserPassword(@PathVariable Integer id) {
        requireAccessibleUser(id);
        return userService.resetUserPassword(id);
    }

    @RequirePermission("user:delete")
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Integer id) {
        requireAccessibleUser(id);
        userService.deleteUser(id);
    }

    @RequirePermission("user:assign-role")
    @PostMapping("/{id}/roles")
    public void assignRolesToUser(@PathVariable Integer id, @RequestBody Integer[] roleIds) {
        requireAccessibleUser(id);
        userService.assignRolesToUser(id, roleIds, UserContextUtil.getVisibleOrgId());
    }

    /**
     * 按角色编码查询用户列表
     * 例如：ORG_ADMIN_1, NORMAL_ADMIN_1, SUPER_ADMIN
     */
    @RequirePermission("user:view")
    @GetMapping("/by_role_code")
    public List<User> getUsersByRoleCode(@RequestParam String roleCode) {
        Role role = roleService.getRoleByCode(roleCode);
        if (role == null) {
            return Collections.emptyList();
        }
        List<Integer> userIds = roleService.getUserIdsByRoleId(role.getId());
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        Integer scopeOrgId = UserContextUtil.getVisibleOrgId();
        Integer currentUserId = UserContextUtil.getCurrentUserId();
        return userIds.stream()
                .filter(userId -> !userId.equals(currentUserId))
                .map(userId -> {
                    try {
                        return userService.getUsersById(userId);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(user -> user != null
                        && (scopeOrgId == null || scopeOrgId.equals(user.getOrgId())))
                .collect(Collectors.toList());
    }

    /** 目标账号必须存在且在调用方数据范围内（跨组织按 404 处理）。 */
    private User requireAccessibleUser(Integer userId) {
        return userService.requireUserAccessible(userId, UserContextUtil.getVisibleOrgId());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
