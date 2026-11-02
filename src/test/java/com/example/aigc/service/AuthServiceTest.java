package com.example.aigc.service;

import com.example.aigc.dto.contract.AdminLoginRequest;
import com.example.aigc.dto.contract.ContractUserDto;
import com.example.aigc.dto.contract.LoginResult;
import com.example.aigc.entity.User;
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
        when(userRoleService.getRoleIdsByUserId(admin.getId())).thenReturn(Arrays.asList(2));
        when(userRoleService.getRoleCodeById(2)).thenReturn("SUPER_ADMIN");
        when(permissionService.getPermissionCodesByRoleIds(Arrays.asList(2)))
                .thenReturn(Arrays.asList("user:create", "role:view"));
        when(organizationService.getOrgNameById(1)).thenReturn("总部");
    }

    private void assertAdminFields(ContractUserDto dto, boolean firstLogin) {
        assertEquals("admin", dto.getUsername());
        assertEquals("管理员", dto.getRealName());
        assertEquals(Integer.valueOf(1), dto.getOrgId());
        assertEquals("总部", dto.getOrgName());
        assertEquals(Boolean.valueOf(firstLogin), dto.getFirstLogin());
        assertEquals(Arrays.asList("SUPER_ADMIN"), dto.getRoles());
        assertTrue(dto.getPermissions().contains("user:create"));
        assertNotNull(dto.getFeatures());
    }
}
