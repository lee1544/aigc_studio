package com.example.aigc.web;

import com.example.aigc.entity.User;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.GlobalExceptionHandler;
import com.example.aigc.service.RoleService;
import com.example.aigc.service.UserRoleService;
import com.example.aigc.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 本人改密不依赖 user:update；修改他人仍必须持有该权限。 */
@ExtendWith(MockitoExtension.class)
class UserControllerPasswordTest {

    @Mock private UserService userService;
    @Mock private RoleService roleService;
    @Mock private UserRoleService userRoleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "roleService", roleService);
        ReflectionTestUtils.setField(controller, "userRoleService", userRoleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void selfPasswordChangeWorksWithoutUserUpdatePermission() throws Exception {
        User updated = new User();
        updated.setId(37);
        when(userService.updateUserPassword(37, "old-pass", "new-pass-1")).thenReturn(updated);

        mockMvc.perform(put("/api/users/37/password")
                        .requestAttr("userId", 37)
                        .requestAttr("orgId", 0)
                        .requestAttr("userPermissions", Collections.emptyList())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old-pass\",\"password\":\"new-pass-1\"}"))
                .andExpect(status().isOk());

        verify(userService).updateUserPassword(37, "old-pass", "new-pass-1");
    }

    @Test
    void selfPasswordChangeReportsWrongOldPasswordAs400() throws Exception {
        when(userService.updateUserPassword(37, "bad-pass", "new-pass-1"))
                .thenThrow(new BusinessException("原密码不正确"));

        mockMvc.perform(put("/api/users/37/password")
                        .requestAttr("userId", 37)
                        .requestAttr("orgId", 0)
                        .requestAttr("userPermissions", Collections.emptyList())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"bad-pass\",\"password\":\"new-pass-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("原密码不正确"));
    }

    @Test
    void changingAnotherUserStillRequiresUserUpdatePermission() throws Exception {
        mockMvc.perform(put("/api/users/2/password")
                        .requestAttr("userId", 37)
                        .requestAttr("orgId", 0)
                        .requestAttr("userPermissions", Collections.emptyList())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old-pass\",\"password\":\"new-pass-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("没有权限访问"));

        verify(userService, never()).updateUserPassword(anyInt(), anyString(), anyString());
    }

    @Test
    void changingAnotherUserInSameOrgWorksWithPermission() throws Exception {
        User target = new User();
        target.setId(2);
        target.setOrgId(0);
        when(userService.getUsersById(2)).thenReturn(target);
        when(userService.updateUserPassword(2, "old-pass", "new-pass-1")).thenReturn(target);

        mockMvc.perform(put("/api/users/2/password")
                        .requestAttr("userId", 37)
                        .requestAttr("orgId", 0)
                        .requestAttr("userPermissions", Collections.singletonList("user:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old-pass\",\"password\":\"new-pass-1\"}"))
                .andExpect(status().isOk());

        verify(userService).getUsersById(2);
        verify(userService).updateUserPassword(2, "old-pass", "new-pass-1");
    }
}
