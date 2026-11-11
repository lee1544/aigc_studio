package com.example.aigc.service;

import com.example.aigc.constant.SystemConstant;
import com.example.aigc.dto.CreateOrganizationRequest;
import com.example.aigc.entity.Organization;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.entity.UserRole;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.ForbiddenException;
import com.example.aigc.filter.UserSessionCache;
import com.example.aigc.repository.OrganizationRepository;
import com.example.aigc.repository.RolePermissionRepository;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组织创建：仅超级管理员；同事务创建组织管理员/普通管理员角色 + 组织管理员账号并绑定角色。
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PermissionService permissionService;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserSessionCache userSessionCache;

    @InjectMocks private OrganizationService organizationService;

    private CreateOrganizationRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateOrganizationRequest();
        request.setOrgCode("DEMO_ORG");
        request.setOrgName("演示组织");
        request.setDescription("演示");
        request.setAdminUsername("demo_admin");
        request.setAdminPassword("secret123");
        request.setAdminRealName("演示管理员");
    }

    @Test
    void createOrganizationRejectedForNonSuperAdmin() {
        assertThrows(ForbiddenException.class,
                () -> organizationService.createOrganization(request, false));

        verify(organizationRepository, never()).save(any(Organization.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createOrganizationCreatesCreatorRoleAndBindsAccount() {
        when(organizationRepository.existsByOrgCode("DEMO_ORG")).thenReturn(false);
        when(organizationRepository.existsByOrgName("演示组织")).thenReturn(false);
        when(userRepository.existsByUsername("demo_admin")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization org = invocation.getArgument(0);
            org.setId(2);
            return org;
        });
        // 新组织还没有内置角色：只创建 ORG_ADMIN 一个组织创作角色
        when(roleRepository.findByRoleCode(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(20);
            return role;
        });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(30);
            return user;
        });

        Organization created = organizationService.createOrganization(request, true);

        assertEquals(Integer.valueOf(2), created.getId());
        assertEquals("DEMO_ORG", created.getOrgCode());
        assertEquals(Integer.valueOf(1), created.getStatus());

        // 组织创作角色默认无权限，创建时不应写任何 role_permission
        verify(permissionService, never()).assignPermissionsByCodesToRole(anyInt(), any(String[].class));
        verify(roleRepository).save(any(Role.class));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User admin = userCaptor.getValue();
        assertEquals("demo_admin", admin.getUsername());
        assertNull(admin.getEmail());
        assertEquals("encoded", admin.getPassword());
        assertEquals("演示管理员", admin.getRealName());
        assertEquals(Integer.valueOf(2), admin.getOrgId());
        // 学校创作账号：等同创作者，不是管理后台管理员
        assertEquals("user", admin.getRole());
        assertEquals(Integer.valueOf(1), admin.getStatus());

        ArgumentCaptor<UserRole> userRoleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(userRoleCaptor.capture());
        assertEquals(Integer.valueOf(30), userRoleCaptor.getValue().getUserId());
        assertEquals(Integer.valueOf(20), userRoleCaptor.getValue().getRoleId());
    }

    @Test
    void createOrganizationRejectsDuplicateAdminUsernameBeforeWriting() {
        when(organizationRepository.existsByOrgCode("DEMO_ORG")).thenReturn(false);
        when(organizationRepository.existsByOrgName("演示组织")).thenReturn(false);
        when(userRepository.existsByUsername("demo_admin")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> organizationService.createOrganization(request, true));
        assertEquals("管理员用户名已存在", ex.getMessage());

        verify(organizationRepository, never()).save(any(Organization.class));
        verify(roleRepository, never()).save(any(Role.class));
        verify(permissionService, never()).assignPermissionsByCodesToRole(anyInt(), any(String[].class));
    }

    @Test
    void createOrganizationRejectsReservedShortAdminPassword() {
        when(organizationRepository.existsByOrgCode("DEMO_ORG")).thenReturn(false);
        when(organizationRepository.existsByOrgName("演示组织")).thenReturn(false);
        when(userRepository.existsByUsername("demo_admin")).thenReturn(false);
        request.setAdminPassword("123");

        assertThrows(BusinessException.class,
                () -> organizationService.createOrganization(request, true));

        verify(organizationRepository, never()).save(any(Organization.class));
    }

    @Test
    void defaultOrganizationCannotBeDeleted() {
        Organization org = new Organization();
        org.setId(1);
        org.setOrgCode(SystemConstant.DEFAULT_ORG_CODE);
        when(organizationRepository.findById(1)).thenReturn(Optional.of(org));

        assertThrows(BusinessException.class, () -> organizationService.deleteOrganization(1, true));

        verify(organizationRepository, never()).delete(any(Organization.class));
    }

    @Test
    void updateOrganizationRejectedForNonSuperAdmin() {
        assertThrows(ForbiddenException.class,
                () -> organizationService.updateOrganization(1, null, false));
        // 非超级管理员在读取组织前就被拒绝
        verify(organizationRepository, never()).findById(anyInt());
    }
}
