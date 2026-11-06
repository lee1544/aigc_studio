package com.example.aigc.service;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.constant.SystemConstant;
import com.example.aigc.dto.contract.RolePermissionSnapshotDto;
import com.example.aigc.entity.Organization;
import com.example.aigc.entity.Permission;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.UserRole;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.ConflictException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.OrganizationRepository;
import com.example.aigc.repository.PermissionRepository;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色与角色-权限服务。
 *
 * <p>数据范围：超级管理员可查看/维护全部组织的角色；组织管理员只能维护本组织的角色。
 * 全局角色（{@code SUPER_ADMIN}，org_id 为 NULL）与创作者个人角色对组织管理员不可见。
 */
@Service
public class RoleService {
    
    private static final String ROLE_CODE_PATTERN = "^[A-Za-z0-9_-]{2,50}$";

    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    /**
     * 角色列表。
     *
     * @param scopeOrgId 数据范围：null 表示全部（超级管理员），否则只返回该组织的角色
     */
    public List<Role> getRoles(Integer scopeOrgId) {
        return getRoles(scopeOrgId, null);
    }

    /**
     * 角色列表（可按组织筛选）。
     *
     * @param scopeOrgId 调用方数据范围：null 表示超级管理员，否则只能是该组织
     * @param orgIdFilter 组织筛选：null 表示不筛选（超级管理员全部 / 组织管理员本组织）
     */
    public List<Role> getRoles(Integer scopeOrgId, Integer orgIdFilter) {
        Integer effectiveOrgId = scopeOrgId != null ? scopeOrgId : orgIdFilter;
        List<Role> roles = (effectiveOrgId == null)
                ? roleRepository.findAll()
                : roleRepository.findByOrgId(effectiveOrgId);
        roles = roles.stream()
                .filter(role -> !isPersonalRole(role))
                .sorted(Comparator.comparing(Role::getId))
                .collect(Collectors.toList());
        if (roles.isEmpty()) {
            return roles;
        }

        List<Integer> roleIds = roles.stream()
                .map(Role::getId)
                .collect(Collectors.toList());
        Map<Integer, List<Permission>> permissionMap = permissionService.getPermissionsByRoleIds(roleIds);
        roles.forEach(role -> role.setPermissions(
                manageablePermissions(role, permissionMap.getOrDefault(role.getId(), Collections.emptyList()))));
        fillOrgNames(roles);
        return roles;
    }
    
    public Role getRoleById(Integer id, Integer scopeOrgId) {
        Role role = requireRoleInScope(id, scopeOrgId);
        fillOrgNames(Collections.singletonList(role));
        return role;
    }
    
    @Transactional
    public Role createRole(Integer currentOrgId, Integer scopeOrgId, Role role) {
        if (role == null) {
            throw new BusinessException("角色信息不能为空");
        }
        String roleCode = trimToNull(role.getRoleCode());
        if (roleCode == null || !roleCode.matches(ROLE_CODE_PATTERN)) {
            throw new BusinessException("角色编码需为 2-50 位字母、数字、下划线或短横线");
        }
        if (SystemConstant.isReservedRoleCode(roleCode)) {
            throw new BusinessException("角色编码为系统保留编码，不允许手工创建: " + roleCode);
        }
        // t_role.role_code 全局唯一，不能只按组织判断
        if (roleRepository.existsByRoleCode(roleCode)) {
            throw new BusinessException("角色编码已存在");
        }
        String roleName = trimToNull(role.getRoleName());
        if (roleName == null) {
            throw new BusinessException("角色名称不能为空");
        }
        if (roleName.length() > 50) {
            throw new BusinessException("角色名称长度不能超过50个字符");
        }
        if (role.getDescription() != null && role.getDescription().length() > 255) {
            throw new BusinessException("角色描述长度不能超过255个字符");
        }

        Integer targetOrgId = scopeOrgId != null
                ? scopeOrgId
                : (role.getOrgId() != null ? role.getOrgId() : currentOrgId);
        if (targetOrgId == null || !organizationRepository.existsById(targetOrgId)) {
            throw new BusinessException("组织不存在");
        }

        role.setId(null);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setOrgId(targetOrgId);
        role.setStatus(1);
        role.setPermissionVersion(0);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        return roleRepository.save(role);
    }
    
    @Transactional
    public Role updateRole(Integer id, Role roleData, Integer scopeOrgId) {
        Role role = requireRoleInScope(id, scopeOrgId);
        if (roleData == null) {
            throw new BusinessException("角色信息不能为空");
        }
        String roleName = trimToNull(roleData.getRoleName());
        if (roleName == null) {
            throw new BusinessException("角色名称不能为空");
        }
        if (roleName.length() > 50) {
            throw new BusinessException("角色名称长度不能超过50个字符");
        }
        if (roleData.getDescription() != null && roleData.getDescription().length() > 255) {
            throw new BusinessException("角色描述长度不能超过255个字符");
        }
        role.setRoleName(roleName);
        role.setDescription(roleData.getDescription());
        role.setUpdateTime(LocalDateTime.now());
        return roleRepository.save(role);
    }
    
    @Transactional
    public void deleteRole(Integer id, Integer scopeOrgId) {
        Role role = requireRoleInScope(id, scopeOrgId);
        
        if (SystemConstant.SUPER_ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
            throw new BusinessException("超级管理员角色不能删除");
        }
        if (SystemConstant.isOrgAdminRoleCode(role.getRoleCode())) {
            throw new BusinessException("组织创作角色不能删除");
        }
        if (SystemConstant.isNormalAdminRoleCode(role.getRoleCode())) {
            throw new BusinessException("普通管理员角色不能删除");
        }
        // 在删除角色前先获取拥有该角色的用户：删除后 user_role 可能已被级联清理
        List<UserRole> affectedUserRoles = userRoleRepository.findByRoleId(id);
        permissionService.evictSessionsForUsers(affectedUserRoles.stream()
                .map(UserRole::getUserId)
                .collect(Collectors.toList()));
        roleRepository.deleteById(id);
        permissionService.clearRolePermissions(id);
    }
    
    @Transactional
    public void assignPermissionsToRole(Integer roleId, Integer[] permissionIds, Integer scopeOrgId) {
        assignPermissionsToRole(roleId, permissionIds, null, scopeOrgId);
    }

    /**
     * 整体替换角色权限，带版本校验。
     * <p>组织创作角色（ORG_ADMIN_*）只允许 25 项创作 feature，拒绝管理端权限码。
     */
    @Transactional
    public void assignPermissionsToRole(Integer roleId, Integer[] permissionIds,
                                        Integer expectedVersion, Integer scopeOrgId) {
        Role role = requireRoleInScope(roleId, scopeOrgId);
        int currentVersion = role.getPermissionVersion() == null ? 0 : role.getPermissionVersion();
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new ConflictException("角色权限已被修改，请刷新后重试");
        }
        if (SystemConstant.isOrgAdminRoleCode(role.getRoleCode())) {
            validateFeatureOnly(permissionIds);
        } else {
            // 管理端角色只维护后台 RBAC 权限；客户端 feature 统一在客户端后台配置
            validateBackendOnly(permissionIds);
        }
        // PermissionService 内部已处理缓存清除与权限 ID 校验
        permissionService.assignPermissionsToRole(roleId, permissionIds);
        role.setPermissionVersion(currentVersion + 1);
        role.setUpdateTime(LocalDateTime.now());
        roleRepository.save(role);
    }

    /** 组织创作角色只能分配创作 feature；任何管理端权限码都返回 400。 */
    private void validateFeatureOnly(Integer[] permissionIds) {
        List<Integer> ids = distinctNonNull(permissionIds);
        if (ids.isEmpty()) {
            return;
        }
        List<Permission> permissions = permissionRepository.findAllById(ids);
        for (Permission permission : permissions) {
            if (!FeaturePermissions.isKnown(permission.getPermissionCode())) {
                throw new BusinessException("组织创作角色只能分配创作功能: "
                        + permission.getPermissionCode());
            }
        }
    }

    /** 管理端角色只能分配后端 RBAC 权限码；客户端 feature 不属于管理后台配置范围。 */
    private void validateBackendOnly(Integer[] permissionIds) {
        List<Integer> ids = distinctNonNull(permissionIds);
        if (ids.isEmpty()) {
            return;
        }
        List<Permission> permissions = permissionRepository.findAllById(ids);
        for (Permission permission : permissions) {
            if (!SystemConstant.isBackendPermissionCode(permission.getPermissionCode())) {
                throw new BusinessException("管理端角色只能分配后台管理权限: "
                        + permission.getPermissionCode());
            }
        }
    }

    private List<Integer> distinctNonNull(Integer[] permissionIds) {
        if (permissionIds == null || permissionIds.length == 0) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(permissionIds)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 管理端接口只暴露后端 RBAC 权限码；组织创作角色的 feature 由客户端后台维护。
     */
    private List<Permission> manageablePermissions(Role role, List<Permission> permissions) {
        if (permissions == null) {
            return Collections.emptyList();
        }
        if (SystemConstant.isOrgAdminRoleCode(role.getRoleCode())) {
            return permissions;
        }
        return permissions.stream()
                .filter(permission -> SystemConstant.isBackendPermissionCode(permission.getPermissionCode()))
                .collect(Collectors.toList());
    }

    /** 角色权限快照：当前权限 + 版本，供前端 expectedVersion 保存。 */
    public RolePermissionSnapshotDto getPermissionSnapshot(Integer roleId, Integer scopeOrgId) {
        Role role = requireRoleInScope(roleId, scopeOrgId);
        RolePermissionSnapshotDto dto = new RolePermissionSnapshotDto();
        dto.setVersion(role.getPermissionVersion() == null ? 0 : role.getPermissionVersion());
        dto.setPermissions(manageablePermissions(role, permissionService.getPermissionsByRoleId(roleId)));
        return dto;
    }
    
    public List<Permission> getRolePermissions(Integer roleId, Integer scopeOrgId) {
        Role role = requireRoleInScope(roleId, scopeOrgId);
        return manageablePermissions(role, permissionService.getPermissionsByRoleId(roleId));
    }

    public Role getRoleByCode(String roleCode) {
        return roleRepository.findByRoleCode(roleCode).orElse(null);
    }

    public List<Integer> getUserIdsByRoleId(Integer roleId) {
        return userRoleRepository.findByRoleId(roleId).stream()
                .map(UserRole::getUserId)
                .collect(Collectors.toList());
    }

    /**
     * 读取并校验角色数据范围：跨组织、全局角色（非超级管理员）、个人角色一律按 404 处理。
     */
    private Role requireRoleInScope(Integer roleId, Integer scopeOrgId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("角色不存在"));
        if (isPersonalRole(role)) {
            throw new NotFoundException("角色不存在");
        }
        if (scopeOrgId != null && !scopeOrgId.equals(role.getOrgId())) {
            throw new NotFoundException("角色不存在");
        }
        return role;
    }

    private boolean isPersonalRole(Role role) {
        return role.getRoleCode() != null
                && role.getRoleCode().startsWith(FeaturePermissions.PERSONAL_ROLE_PREFIX);
    }

    /** 补齐角色所属组织名称；全局角色显示“全局”。 */
    private void fillOrgNames(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        Set<Integer> orgIds = new LinkedHashSet<>();
        for (Role role : roles) {
            if (role.getOrgId() != null) {
                orgIds.add(role.getOrgId());
            }
        }
        Map<Integer, String> orgNames = new HashMap<>();
        if (!orgIds.isEmpty()) {
            for (Organization org : organizationRepository.findAllById(orgIds)) {
                orgNames.put(org.getId(), org.getOrgName());
            }
        }
        for (Role role : roles) {
            if (role.getOrgId() == null) {
                role.setOrgName("全局");
            } else {
                role.setOrgName(orgNames.getOrDefault(role.getOrgId(), "未知组织"));
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
