package com.example.aigc.service;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.dto.contract.LoginResult;
import com.example.aigc.dto.contract.RegisterRequest;
import com.example.aigc.entity.User;
import com.example.aigc.exception.ForbiddenException;
import com.example.aigc.filter.UserSessionCache;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.utils.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自助注册必须受 allowRegistration 约束：开关取自 system_settings（与 GET /api/public/settings 同一配置源），
 * 关闭时 403 且在写入之前拦截，不允许落库。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterGateTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenUtil jwtTokenUtil;
    @Mock private UserSessionCache userSessionCache;
    @Mock private FeaturePermissionService featurePermissionService;
    @Mock private SystemSettingsService systemSettingsService;

    @InjectMocks private AuthService authService;

    private RegisterRequest request() {
        RegisterRequest r = new RegisterRequest();
        r.setName("新创作者");
        r.setEmail("new-creator@example.test");
        r.setPassword("pwd123456");
        return r;
    }

    @Test
    void closedSwitchRejectsBeforeAnyWrite() {
        when(systemSettingsService.isRegistrationAllowed()).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> authService.register(request()));

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).existsByEmail(anyString());
        verify(featurePermissionService, never()).initForNewUser(any(User.class), any());
    }

    @Test
    void openSwitchCreatesCreatorWithAllFalseFeatures() {
        when(systemSettingsService.isRegistrationAllowed()).thenReturn(true);
        when(userRepository.existsByEmail("new-creator@example.test")).thenReturn(false);
        when(passwordEncoder.encode("pwd123456")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(88);
            return u;
        });
        when(featurePermissionService.getFeatures(88)).thenReturn(FeaturePermissions.allFalse());
        when(jwtTokenUtil.generateToken(anyString(), eq(88), eq(0), eq(0), eq("user"))).thenReturn("token");

        LoginResult result = authService.register(request());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        verify(featurePermissionService).initForNewUser(any(User.class), isNull());
        assertEquals("user", saved.getValue().getRole());
        assertEquals(Integer.valueOf(1), saved.getValue().getStatus());
        assertEquals("token", result.getToken());
    }
}
