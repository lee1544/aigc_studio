package com.example.aigc.service;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.entity.Organization;
import com.example.aigc.entity.Permission;
import com.example.aigc.entity.Role;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.ConflictException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.OrganizationRepository;
import com.example.aigc.repository.PermissionRepository;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色数据范围与系统保留编码约束。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionService permissionService;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PermissionRepository permissionRepository;

    @InjectMocks private RoleService roleService;

    @Test
    void orgCreatorRoleRejectsManagementPermissionCode() {
        Role role = orgCreatorRole();
        role.setPermissionVersion(0);
        when(roleRepository.findById(20)).thenReturn(Optional.of(role));
        Permission management = new Permission();
        management.setId(1);
        management.setPermissionCode("user:view");
        when(permissionRepository.findAllById(Arrays.asList(1))).thenReturn(Arrays.asList(management));

        assertThrows(BusinessException.class,
                () -> roleService.assignPermissionsToRole(20, new Integer[]{1}, 0, null));

        verify(permissionService, never()).assignPermissionsToRole(any(), any());
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void orgCreatorRoleRejectsStalePermissionVersion() {
        Role role = orgCreatorRole();
        role.setPermissionVersion(3);
        when(roleRepository.findById(20)).thenReturn(Optional.of(role));

        assertThrows(ConflictException.class,
                () -> roleService.assignPermissionsToRole(20, new Integer[]{17}, 2, null));

        verify(permissionService, never()).assignPermissionsToRole(any(), any());
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void orgCreatorRoleAcceptsFeatureAndBumpsVersion() {
        Role role = orgCreatorRole();
        role.setPermissionVersion(0);
        when(roleRepository.findById(20)).thenReturn(Optional.of(role));
        Permission feature = new Permission();
        feature.setId(17);
        feature.setPermissionCode("script");
        when(permissionRepository.findAllById(Arrays.asList(17))).thenReturn(Arrays.asList(feature));

        roleService.assignPermissionsToRole(20, new Integer[]{17}, 0, null);

        verify(permissionService).assignPermissionsToRole(20, new Integer[]{17});
        assertEquals(Integer.valueOf(1), role.getPermissionVersion());
        verify(roleRepository).save(role);
    }

    private Role orgCreatorRole() {
        Role role = new Role();
        role.setId(20);
        role.setRoleCode("ORG_ADMIN_1");
        role.setRoleName("组织创作角色");
        role.setOrgId(1);
        return role;
    }

    @Test
    void createRoleRejectsReservedRoleCode() {
        Role role = new Role();
        role.setRoleCode("ORG_ADMIN_9");
        role.setRoleName("伪造的组织管理员");

        assertThrows(BusinessException.class, () -> roleService.createRole(1, 1, role));

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void createRoleRejectsDuplicateCodeGlobally() {
        Role role = new Role();
        role.setRoleCode("EDITOR");
        role.setRoleName("编辑");
        when(roleRepository.existsByRoleCode("EDITOR")).thenReturn(true);

        assertThrows(BusinessException.class, () -> roleService.createRole(1, 1, role));

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void createRoleBindsCallerOrgForOrgAdmin() {
        Role role = new Role();
        role.setRoleCode("EDITOR");
        role.setRoleName("编辑");
        when(roleRepository.existsByRoleCode("EDITOR")).thenReturn(false);
        when(organizationRepository.existsById(1)).thenReturn(true);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role saved = invocation.getArgument(0);
            saved.setId(7);
            return saved;
        });

        Role created = roleService.createRole(1, 1, role);

        assertEquals(Integer.valueOf(7), created.getId());
        assertEquals(Integer.valueOf(1), created.getOrgId());
        assertEquals(Integer.valueOf(1), created.getStatus());
    }

    @Test
    void orgAdminCannotReadAnotherOrgRole() {
        Role role = new Role();
        role.setId(5);
        role.setRoleCode("EDITOR");
        role.setOrgId(2);
        when(roleRepository.findById(5)).thenReturn(Optional.of(role));

        assertThrows(NotFoundException.class, () -> roleService.getRoleById(5, 1));
    }

    @Test
    void superAdminRoleListExcludesPersonalRolesAndFillsOrgName() {
        Role personal = role(1, FeaturePermissions.PERSONAL_ROLE(12), null);
        Role global = role(2, "SUPER_ADMIN", null);
        Role orgRole = role(3, "EDITOR", 1);
        when(roleRepository.findAll()).thenReturn(Arrays.asList(personal, global, orgRole));
        when(permissionService.getPermissionsByRoleIds(any()))
                .thenReturn(Collections.<Integer, List<Permission>>emptyMap());

        Organization org = new Organization();
        org.setId(1);
        org.setOrgName("组织A");
        when(organizationRepository.findAllById(any())).thenReturn(Collections.singletonList(org));

        List<Role> roles = roleService.getRoles(null);

        assertEquals(2, roles.size());
        assertEquals("SUPER_ADMIN", roles.get(0).getRoleCode());
        assertEquals("全局", roles.get(0).getOrgName());
        assertEquals("EDITOR", roles.get(1).getRoleCode());
        assertEquals("组织A", roles.get(1).getOrgName());
        assertTrue(roles.stream().noneMatch(r -> r.getRoleCode().startsWith("USER_FEATURES_")));
    }

    @Test
    void superAdminCanFilterRolesByOrg() {
        Role orgRole = role(3, "EDITOR", 2);
        when(roleRepository.findByOrgId(2)).thenReturn(Collections.singletonList(orgRole));
        when(permissionService.getPermissionsByRoleIds(any()))
                .thenReturn(Collections.<Integer, List<Permission>>emptyMap());

        Organization org = new Organization();
        org.setId(2);
        org.setOrgName("组织B");
        when(organizationRepository.findAllById(any())).thenReturn(Collections.singletonList(org));

        List<Role> roles = roleService.getRoles(null, 2);

        assertEquals(1, roles.size());
        assertEquals("EDITOR", roles.get(0).getRoleCode());
        assertEquals("组织B", roles.get(0).getOrgName());
        verify(roleRepository).findByOrgId(2);
        verify(roleRepository, never()).findAll();
    }

    private Role role(Integer id, String code, Integer orgId) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setOrgId(orgId);
        return role;
    }
}
