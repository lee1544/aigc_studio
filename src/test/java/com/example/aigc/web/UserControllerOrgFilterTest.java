package com.example.aigc.web;

import com.example.aigc.dto.CreateUserRequest;
import com.example.aigc.entity.User;
import com.example.aigc.service.RoleService;
import com.example.aigc.service.UserRoleService;
import com.example.aigc.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 用户列表组织筛选：超级管理员可选组织，组织管理员强制本组织。 */
@ExtendWith(MockitoExtension.class)
class UserControllerOrgFilterTest {

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
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void superAdminCanFilterUsersByOrg() throws Exception {
        when(userService.getUsersByOrgId(eq(2), isNull(), isNull(), any(Pageable.class), eq(false)))
                .thenReturn(new PageImpl<User>(Collections.emptyList(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/users")
                        .param("orgId", "2")
                        .requestAttr("userId", 1)
                        .requestAttr("orgId", 1)
                        .requestAttr("role", "admin")
                        .requestAttr("isSuperAdmin", true)
                        .requestAttr("userPermissions", Collections.singletonList("user:view")))
                .andExpect(status().isOk());

        verify(userService).getUsersByOrgId(eq(2), isNull(), isNull(), any(Pageable.class), eq(false));
    }

    @Test
    void orgAdminIsForcedToOwnOrgEvenWhenPassingAnotherOrgId() throws Exception {
        when(userService.getUsersByOrgId(eq(1), isNull(), isNull(), any(Pageable.class), eq(false)))
                .thenReturn(new PageImpl<User>(Collections.emptyList(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/users")
                        .param("orgId", "2")
                        .requestAttr("userId", 2)
                        .requestAttr("orgId", 1)
                        .requestAttr("role", "admin")
                        .requestAttr("isSuperAdmin", false)
                        .requestAttr("userPermissions", Collections.singletonList("user:view")))
                .andExpect(status().isOk());

        verify(userService).getUsersByOrgId(eq(1), isNull(), isNull(), any(Pageable.class), eq(false));
    }

    @Test
    void superAdminCreatesUserInSelectedOrg() throws Exception {
        when(userService.createUser(eq(2), any(CreateUserRequest.class))).thenReturn(new User());

        mockMvc.perform(post("/api/users")
                        .requestAttr("userId", 1)
                        .requestAttr("orgId", 1)
                        .requestAttr("role", "admin")
                        .requestAttr("isSuperAdmin", true)
                        .requestAttr("userPermissions", Collections.singletonList("user:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo_user\",\"password\":\"secret123\","
                                + "\"realName\":\"演示用户\",\"orgId\":2}"))
                .andExpect(status().isOk());

        verify(userService).createUser(eq(2), any(CreateUserRequest.class));
    }
}
