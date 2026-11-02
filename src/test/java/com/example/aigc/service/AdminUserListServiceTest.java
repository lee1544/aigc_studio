package com.example.aigc.service;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.dto.contract.AdminUserListItemDto;
import com.example.aigc.entity.Organization;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.entity.UserRole;
import com.example.aigc.exception.BusinessException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserListServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private FeaturePermissionService featurePermissionService;

    @InjectMocks private AdminUserListService adminUserListService;

    @Test
    void invalidStatusIsRejected() {
        assertThrows(BusinessException.class, () -> adminUserListService.page(0, 20, null, "frozen"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageClampsPageSizeAndEnrichesOrgRolesAndFeatures() {
        User creator = creator(12, 3);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(creator), PageRequest.of(0, 20), 1));
        when(organizationRepository.findAllById(any()))
                .thenReturn(Collections.singletonList(org(3, "创作一组")));
        when(userRoleRepository.findByUserIdIn(any()))
                .thenReturn(Collections.singletonList(userRole(12, 5)));
        when(roleRepository.findAllById(any()))
                .thenReturn(Collections.singletonList(role(5, "CREATOR_GROUP", "创作组")));
        Map<String, Boolean> snapshot = FeaturePermissions.allFalse();
        snapshot.put("script", true);
        Map<Integer, Map<String, Boolean>> features = new HashMap<>();
        features.put(12, snapshot);
        when(featurePermissionService.getFeaturesBatch(any())).thenReturn(features);

        Page<AdminUserListItemDto> page = adminUserListService.page(0, 500, "创作", null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());

        assertEquals(1, page.getTotalElements());
        AdminUserListItemDto dto = page.getContent().get(0);
        assertEquals(Integer.valueOf(12), dto.getId());
        assertEquals("创作者A", dto.getName());
        assertEquals("active", dto.getStatus());
        assertEquals("创作一组", dto.getOrgName());
        assertEquals(Collections.singletonList(5), dto.getRoleIds());
        assertEquals(Collections.singletonList("创作组"), dto.getRoleNames());
        assertTrue(dto.getFeatures().get("script"));
        assertNotNull(dto.getCreatedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void personalFeatureRoleIsNotListedAsBusinessRole() {
        User creator = creator(12, 0);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(creator), PageRequest.of(0, 20), 1));
        when(userRoleRepository.findByUserIdIn(any()))
                .thenReturn(Collections.singletonList(userRole(12, 9)));
        when(roleRepository.findAllById(any())).thenReturn(Collections.singletonList(
                role(9, FeaturePermissions.PERSONAL_ROLE(12), "个人功能权限 #12")));
        when(featurePermissionService.getFeaturesBatch(any())).thenReturn(new HashMap<>());

        AdminUserListItemDto dto = adminUserListService.page(0, 20, null, "all").getContent().get(0);

        assertTrue(dto.getRoleIds().isEmpty());
        assertTrue(dto.getRoleNames().isEmpty());
        // orgId=0 视为未分配组织
        assertNull(dto.getOrgName());
        assertEquals(26, dto.getFeatures().size());
    }

    private User creator(Integer id, Integer orgId) {
        User user = new User();
        user.setId(id);
        user.setName("创作者A");
        user.setUsername("creator-a@example.com");
        user.setEmail("creator-a@example.com");
        user.setRole("user");
        user.setStatus(1);
        user.setOrgId(orgId);
        user.setCreateTime(LocalDateTime.of(2026, 10, 1, 8, 0));
        return user;
    }

    private UserRole userRole(Integer userId, Integer roleId) {
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        return ur;
    }

    private Role role(Integer id, String code, String name) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(name);
        return role;
    }

    private Organization org(Integer id, String name) {
        Organization org = new Organization();
        org.setId(id);
        org.setOrgName(name);
        return org;
    }
}
