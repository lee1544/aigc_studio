package com.example.aigc.service;

import com.example.aigc.constant.SystemConstant;
import com.example.aigc.entity.Organization;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.entity.UserRole;
import com.example.aigc.repository.OrganizationRepository;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统初始化不变量：默认组织、超级管理员角色（全量启用权限）、默认超级管理员账号、各组织内置角色。
 */
@ExtendWith(MockitoExtension.class)
class RbacBootstrapServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OrganizationService organizationService;
    @Mock private PermissionService permissionService;

    @InjectMocks private RbacBootstrapService rbacBootstrapService;

    @Test
    void createsDefaultOrgSuperAdminRoleAndAdminUser() {
        when(organizationRepository.findByOrgCode(SystemConstant.DEFAULT_ORG_CODE)).thenReturn(Optional.empty());
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization org = invocation.getArgument(0);
            org.setId(1);
            return org;
        });
        when(roleRepository.findByRoleCode(SystemConstant.SUPER_ADMIN_ROLE_CODE)).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(10);
            return role;
        });
        when(permissionService.grantMissingActivePermissions(10)).thenReturn(7);
        when(userRepository.findByUsername(SystemConstant.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(SystemConstant.DEFAULT_ADMIN_PASSWORD)).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(5);
            return user;
        });
        when(userRoleRepository.findRoleIdsByUserId(5)).thenReturn(Collections.emptyList());

        Organization defaultOrg = new Organization();
        defaultOrg.setId(1);
        defaultOrg.setOrgCode(SystemConstant.DEFAULT_ORG_CODE);
        when(organizationRepository.findAll()).thenReturn(Collections.singletonList(defaultOrg));

        RbacBootstrapService.Summary summary = rbacBootstrapService.initialize();

        assertEquals(Integer.valueOf(1), summary.defaultOrgId);
        assertEquals(Integer.valueOf(10), summary.superAdminRoleId);
        assertEquals(7, summary.grantedPermissions);
        assertTrue(summary.superAdminUserCreated);
        assertEquals(1, summary.organizationsEnsured);

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        assertEquals(SystemConstant.SUPER_ADMIN_ROLE_CODE, roleCaptor.getValue().getRoleCode());
        assertEquals(null, roleCaptor.getValue().getOrgId());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertEquals(SystemConstant.DEFAULT_ADMIN_USERNAME, created.getUsername());
        assertEquals("admin", created.getRole());
        assertEquals(Integer.valueOf(1), created.getOrgId());
        assertEquals("encoded", created.getPassword());

        ArgumentCaptor<UserRole> userRoleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(userRoleCaptor.capture());
        assertEquals(Integer.valueOf(5), userRoleCaptor.getValue().getUserId());
        assertEquals(Integer.valueOf(10), userRoleCaptor.getValue().getRoleId());

        verify(organizationService).ensureDefaultOrgRoles(defaultOrg);
    }

    @Test
    void isIdempotentWhenEverythingAlreadyExists() {
        Organization defaultOrg = new Organization();
        defaultOrg.setId(1);
        defaultOrg.setOrgCode(SystemConstant.DEFAULT_ORG_CODE);
        when(organizationRepository.findByOrgCode(SystemConstant.DEFAULT_ORG_CODE))
                .thenReturn(Optional.of(defaultOrg));

        Role superRole = new Role();
        superRole.setId(10);
        superRole.setRoleCode(SystemConstant.SUPER_ADMIN_ROLE_CODE);
        when(roleRepository.findByRoleCode(SystemConstant.SUPER_ADMIN_ROLE_CODE))
                .thenReturn(Optional.of(superRole));
        when(permissionService.grantMissingActivePermissions(10)).thenReturn(0);

        User admin = new User();
        admin.setId(5);
        admin.setUsername(SystemConstant.DEFAULT_ADMIN_USERNAME);
        admin.setRole("admin");
        admin.setOrgId(1);
        when(userRepository.findByUsername(SystemConstant.DEFAULT_ADMIN_USERNAME))
                .thenReturn(Optional.of(admin));
        when(userRoleRepository.findRoleIdsByUserId(5)).thenReturn(Collections.singletonList(10));
        when(organizationRepository.findAll()).thenReturn(Collections.singletonList(defaultOrg));

        RbacBootstrapService.Summary summary = rbacBootstrapService.initialize();

        assertEquals(0, summary.grantedPermissions);
        assertFalse(summary.superAdminUserCreated);
        verify(organizationRepository, never()).save(any(Organization.class));
        verify(roleRepository, never()).save(any(Role.class));
        verify(userRepository, never()).save(any(User.class));
        verify(userRoleRepository, never()).save(any(UserRole.class));
        verify(organizationService).ensureDefaultOrgRoles(defaultOrg);
    }
}
