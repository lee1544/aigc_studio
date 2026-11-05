package com.example.aigc.service;

import com.example.aigc.entity.Permission;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.repository.PermissionRepository;
import com.example.aigc.repository.RolePermissionRepository;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 有效创作权限：组织创作角色优先，旧个人角色兜底，超管全 false。
 */
@ExtendWith(MockitoExtension.class)
class FeaturePermissionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private PermissionRepository permissionRepository;

    @InjectMocks private FeaturePermissionService featurePermissionService;

    @Test
    void orgCreatorUsesSharedRoleFeatures() {
        User user = creator(5, 1);
        Role shared = role(20, "ORG_ADMIN_1");
        when(roleRepository.findByRoleCode("ORG_ADMIN_1")).thenReturn(Optional.of(shared));
        when(userRoleRepository.findRoleIdsByUserId(5)).thenReturn(Arrays.asList(20));
        when(permissionRepository.findAllByRoleId(20))
                .thenReturn(Arrays.asList(permission(17, "script")));

        Map<String, Boolean> features = featurePermissionService.getEffectiveFeatures(user);

        assertTrue(features.get("script"));
        assertFalse(features.get("image"));
    }

    @Test
    void creatorWithoutSharedRoleFallsBackToPersonalRole() {
        User user = creator(6, 1);
        when(roleRepository.findByRoleCode("ORG_ADMIN_1")).thenReturn(Optional.empty());
        Role personal = role(30, "USER_FEATURES_6");
        when(roleRepository.findByRoleCode("USER_FEATURES_6")).thenReturn(Optional.of(personal));
        when(permissionRepository.findAllByRoleId(30))
                .thenReturn(Arrays.asList(permission(18, "image")));

        Map<String, Boolean> features = featurePermissionService.getEffectiveFeatures(user);

        assertTrue(features.get("image"));
        assertFalse(features.get("script"));
    }

    @Test
    void superAdminHasNoFeatureSnapshot() {
        User admin = new User();
        admin.setId(1);
        admin.setRole("admin");
        admin.setOrgId(1);

        Map<String, Boolean> features = featurePermissionService.getEffectiveFeatures(admin);

        assertTrue(features.values().stream().noneMatch(Boolean::booleanValue));
    }

    @Test
    void batchFeaturesApplySharedRoleToAllBoundUsers() {
        User a = creator(5, 1);
        User b = creator(6, 1);
        Role shared = role(20, "ORG_ADMIN_1");
        when(userRepository.findAllById(Arrays.asList(5, 6))).thenReturn(Arrays.asList(a, b));
        when(roleRepository.findByRoleCodeIn(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(shared));
        com.example.aigc.entity.UserRole linkA = new com.example.aigc.entity.UserRole();
        linkA.setUserId(5);
        linkA.setRoleId(20);
        com.example.aigc.entity.UserRole linkB = new com.example.aigc.entity.UserRole();
        linkB.setUserId(6);
        linkB.setRoleId(20);
        when(userRoleRepository.findByUserIdIn(Arrays.asList(5, 6)))
                .thenReturn(Arrays.asList(linkA, linkB));
        when(permissionRepository.findCodesByRoleIds(Arrays.asList(20)))
                .thenReturn(Collections.singletonList(new Object[]{20, "script"}));

        Map<Integer, Map<String, Boolean>> batch =
                featurePermissionService.getFeaturesBatch(Arrays.asList(5, 6));

        assertTrue(batch.get(5).get("script"));
        assertTrue(batch.get(6).get("script"));
    }

    private User creator(int id, int orgId) {
        User user = new User();
        user.setId(id);
        user.setRole("user");
        user.setOrgId(orgId);
        return user;
    }

    private Role role(int id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setOrgId(1);
        return role;
    }

    private Permission permission(int id, String code) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setPermissionCode(code);
        return permission;
    }
}
