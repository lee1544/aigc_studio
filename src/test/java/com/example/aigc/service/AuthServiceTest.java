package com.example.aigc.service;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.dto.contract.AdminLoginRequest;
import com.example.aigc.dto.contract.ContractUserDto;
import com.example.aigc.dto.contract.CreatorLoginRequest;
import com.example.aigc.dto.contract.LoginResult;
import com.example.aigc.entity.User;
import com.example.aigc.exception.ForbiddenException;
import com.example.aigc.exception.UnauthorizedException;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.utils.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenUtil jwtTokenUtil;
    @Mock private FeaturePermissionService featurePermissionService;
    @Mock private UserRoleService userRoleService;
    @Mock private PermissionService permissionService;
    @Mock private OrganizationService organizationService;

    @InjectMocks private AuthService authService;

    @Test
    void currentUserWithoutAuthenticatedUserIsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> authService.currentUser(null));
    }

    @Test
    void adminCurrentUserIncludesLegacyRbacFields() {
        User admin = adminUser();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        stubAdminRbac(admin);

        ContractUserDto dto = authService.currentUser(admin.getId());

        assertAdminFields(dto, false);
    }

    @Test
    void adminLoginIncludesLegacyRbacFields() {
        User admin = adminUser();
        admin.setLastLoginTime(null);
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("admin");
        request.setPassword("secret");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtTokenUtil.generateToken("admin", 1, 1, 0, "admin")).thenReturn("token");
        stubAdminRbac(admin);

        LoginResult result = authService.adminLogin(request);

        assertEquals("token", result.getToken());
        assertAdminFields(result.getUser(), true);
    }

    @Test
    void orgCreatorLoginUsesCreatorScopeAndSharedFeatures() {
        User creator = new User();
        creator.setId(5);
        creator.setUsername("school_admin");
        creator.setEmail("admin@school.com");
        creator.setName("学校创作账号");
        creator.setRealName("学校创作账号");
        creator.setPassword("hash");
        creator.setRole("user");
        creator.setStatus(1);
        creator.setTokenVersion(0);
        creator.setOrgId(3);

        CreatorLoginRequest request = new CreatorLoginRequest();
        request.setEmail("admin@school.com");
        request.setPassword("secret");

        when(userRepository.findByEmail("admin@school.com")).thenReturn(Optional.of(creator));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(organizationService.getOrgStatusById(3)).thenReturn(1);
        when(organizationService.getOrgNameById(3)).thenReturn("XX学校");
        when(userRoleService.getRoleIdsByUserId(5)).thenReturn(Arrays.asList(20));
        when(userRoleService.getRoleCodeById(20)).thenReturn("ORG_ADMIN_3");
        when(permissionService.getPermissionCodesByRoleIds(Arrays.asList(20)))
                .thenReturn(Arrays.asList("script"));
        java.util.Map<String, Boolean> features = FeaturePermissions.allFalse();
        features.put("script", true);
        when(featurePermissionService.getEffectiveFeatures(creator)).thenReturn(features);
        when(jwtTokenUtil.generateToken("school_admin", 5, 3, 0, "user")).thenReturn("creator-token");

        LoginResult result = authService.creatorLogin(request);

        assertEquals("creator-token", result.getToken());
        assertEquals("creator", result.getUser().getScope());
        assertEquals(Boolean.FALSE, result.getUser().getSuperAdmin());
        assertEquals("user", result.getUser().getRole());
        assertEquals(Integer.valueOf(3), result.getUser().getOrgId());
        assertEquals("XX学校", result.getUser().getOrgName());
        assertEquals(Integer.valueOf(1), result.getUser().getOrgStatus());
        assertEquals(Arrays.asList("ORG_ADMIN_3"), result.getUser().getRoles());
        assertEquals(Boolean.TRUE, result.getUser().getFeatures().get("script"));
    }

    @Test
    void adminLoginRejectsRoleAdminWithoutSuperAdminRole() {
        User admin = adminUser();
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("admin");
        request.setPassword("secret");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(organizationService.getOrgStatusById(1)).thenReturn(1);
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(userRoleService.isSuperAdmin(1)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> authService.adminLogin(request));
    }

    private User adminUser() {
        User admin = new User();
        admin.setId(1);
        admin.setUsername("admin");
        admin.setRealName("管理员");
        admin.setName("管理员");
        admin.setEmail("admin@example.test");
        admin.setPassword("hash");
        admin.setRole("admin");
        admin.setStatus(1);
        admin.setTokenVersion(0);
        admin.setOrgId(1);
        admin.setLastLoginTime(LocalDateTime.now());
        return admin;
    }

    private void stubAdminRbac(User admin) {
        when(userRoleService.isSuperAdmin(admin.getId())).thenReturn(true);
        when(userRoleService.getRoleIdsByUserId(admin.getId())).thenReturn(Arrays.asList(2));
        when(userRoleService.getRoleCodeById(2)).thenReturn("SUPER_ADMIN");
        when(permissionService.getPermissionCodesByRoleIds(Arrays.asList(2)))
                .thenReturn(Arrays.asList("user:create", "role:view"));
        when(organizationService.getOrgNameById(1)).thenReturn("总部");
        when(organizationService.getOrgStatusById(1)).thenReturn(1);
    }

    private void assertAdminFields(ContractUserDto dto, boolean firstLogin) {
        assertEquals("admin", dto.getUsername());
        assertEquals("管理员", dto.getRealName());
        assertEquals(Integer.valueOf(1), dto.getOrgId());
        assertEquals("总部", dto.getOrgName());
        assertEquals(Integer.valueOf(1), dto.getOrgStatus());
        assertEquals("admin", dto.getScope());
        assertEquals(Boolean.TRUE, dto.getSuperAdmin());
        assertEquals(Boolean.valueOf(firstLogin), dto.getFirstLogin());
        assertEquals(Arrays.asList("SUPER_ADMIN"), dto.getRoles());
        assertTrue(dto.getPermissions().contains("user:create"));
        assertNotNull(dto.getFeatures());
    }
}
