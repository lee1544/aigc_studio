package com.example.aigc.service;

import com.example.aigc.entity.Permission;
import com.example.aigc.entity.RolePermission;
import com.example.aigc.entity.UserRole;
import com.example.aigc.event.SessionPermissionChangedEvent;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.repository.PermissionRepository;
import com.example.aigc.repository.RolePermissionRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public List<String> getPermissionCodesByRoleIds(List<Integer> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Permission> permissions = permissionRepository.findByRoleIds(roleIds);
        return permissions.stream()
                .map(Permission::getPermissionCode)
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignAllPermissionsToRole(Integer roleId) {
        Integer[] permissionIds = permissionRepository.findByStatus(1).stream()
                .map(Permission::getId)
                .toArray(Integer[]::new);
        assignPermissionsToRole(roleId, permissionIds);
    }

    @Transactional
    public void assignPermissionsByCodesToRole(Integer roleId, String[] permissionCodes) {
        rolePermissionRepository.deleteByRoleId(roleId);
        List<RolePermission> links = new ArrayList<>();
        for (String code : new LinkedHashSet<>(Arrays.asList(permissionCodes))) {
            Permission permission = permissionRepository.findByPermissionCode(code)
                    .orElseThrow(() -> new BusinessException("权限不存在: " + code));
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permission.getId());
            links.add(rolePermission);
        }
        if (!links.isEmpty()) {
            rolePermissionRepository.saveAll(links);
        }
        evictCacheByRoleId(roleId);
    }

    /**
     * 整体替换角色权限。
     * <p>
     * 重复提交同一组权限必须幂等：入参先去重，且先执行批量删除再插入，
     * 否则会撞 t_role_permission 的 (role_id, permission_id) 唯一索引。
     * 传入已不存在的权限 id 时返回 400 而不是 500（前端缓存的旧数据能直接看懂）。
     */
    @Transactional
    public void assignPermissionsToRole(Integer roleId, Integer[] permissionIds) {
        List<Integer> targetIds = distinctIds(permissionIds);
        if (!targetIds.isEmpty()) {
            List<Permission> found = permissionRepository.findAllById(targetIds);
            if (found.size() != targetIds.size()) {
                java.util.Set<Integer> existIds = found.stream().map(Permission::getId).collect(Collectors.toSet());
                List<Integer> missing = targetIds.stream()
                        .filter(id -> !existIds.contains(id))
                        .collect(Collectors.toList());
                throw new BusinessException("权限不存在: " + missing);
            }
        }
        rolePermissionRepository.deleteByRoleId(roleId);
        if (!targetIds.isEmpty()) {
            List<RolePermission> rolePermissions = targetIds.stream()
                    .map(permissionId -> {
                        RolePermission rolePermission = new RolePermission();
                        rolePermission.setRoleId(roleId);
                        rolePermission.setPermissionId(permissionId);
                        return rolePermission;
                    })
                    .collect(Collectors.toList());
            rolePermissionRepository.saveAll(rolePermissions);
        }
        evictCacheByRoleId(roleId);
    }

    /** 去空、去重，保持请求顺序；权限 id 重复会让保存撞唯一索引。 */
    private List<Integer> distinctIds(Integer[] permissionIds) {
        if (permissionIds == null || permissionIds.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(permissionIds)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Permission> getAllActivePermissions() {
        return permissionRepository.findByStatus(1);
    }

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAllByOrderByPermissionCodeAsc();
    }

    @Transactional
    public Permission updatePermissionStatus(Integer permissionId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("权限状态只能是 0 或 1");
        }
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new BusinessException("权限不存在"));
        permission.setStatus(status);
        permission.setUpdateTime(LocalDateTime.now());
        Permission saved = permissionRepository.save(permission);
        // 权限本身启停会影响所有持有该权限码的账号，直接失效全部会话
        eventPublisher.publishEvent(SessionPermissionChangedEvent.all());
        return saved;
    }

    public List<Permission> getPermissionsByRoleId(Integer roleId) {
        return permissionRepository.findAllByRoleId(roleId);
    }

    public Map<Integer, List<Permission>> getPermissionsByRoleIds(List<Integer> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<RolePermission> links = rolePermissionRepository.findByRoleIdIn(roleIds);
        if (links.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Integer> permissionIds = links.stream()
                .map(RolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, Permission> permissionMap = permissionRepository.findAllById(permissionIds).stream()
                .collect(Collectors.toMap(Permission::getId, permission -> permission));

        Map<Integer, List<Permission>> result = new HashMap<>();
        for (RolePermission link : links) {
            Permission permission = permissionMap.get(link.getPermissionId());
            if (permission == null) {
                continue;
            }
            result.computeIfAbsent(link.getRoleId(), key -> new ArrayList<>()).add(permission);
        }
        return result;
    }

    @Transactional
    public void clearRolePermissions(Integer roleId) {
        // 先清缓存（此时 user_role 记录还存在），再删 role_permission
        evictCacheByRoleId(roleId);
        rolePermissionRepository.deleteByRoleId(roleId);
    }

    /**
     * 根据角色ID清除拥有该角色的所有用户的会话权限缓存。
     */
    private void evictCacheByRoleId(Integer roleId) {
        List<Integer> userIds = userRoleRepository.findByRoleId(roleId).stream()
                .map(UserRole::getUserId)
                .collect(Collectors.toList());
        evictSessionsForUsers(userIds);
    }

    /** 供 RoleService 在删除角色（级联删除 user_role 前）显式指定受影响用户。 */
    public void evictSessionsForUsers(Collection<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(SessionPermissionChangedEvent.forUsers(userIds));
    }
}
