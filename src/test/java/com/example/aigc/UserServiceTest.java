package com.example.aigc;

import com.example.aigc.dto.CreateUserRequest;
import com.example.aigc.dto.contract.CreateUserContractRequest;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.entity.UserRole;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.ConflictException;
import com.example.aigc.exception.ForbiddenException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.filter.UserSessionCache;
import com.example.aigc.repository.OrganizationRepository;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.repository.UserRoleRepository;
import com.example.aigc.service.FeaturePermissionService;
import com.example.aigc.service.OrganizationService;
import com.example.aigc.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserSessionCache userSessionCache;

    @Mock
    private FeaturePermissionService featurePermissionService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private CreateUserRequest createRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setUsername("newuser");
        testUser.setPassword("encodedPassword");
        testUser.setRealName("新用户");
        testUser.setOrgId(1);
        testUser.setStatus(1);

        createRequest = new CreateUserRequest();
        createRequest.setUsername("newuser");
        createRequest.setPassword("password123");
        createRequest.setRealName("新用户");
    }

    // ==================== createUser ====================

    @Test
    void testCreateUser_Success() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(organizationRepository.existsById(1)).thenReturn(true);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        // 模拟 JPA save 的回填 ID 行为
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User userArg = invocation.getArgument(0);
            userArg.setId(1);
            userArg.setCreateTime(LocalDateTime.now());
            userArg.setUpdateTime(LocalDateTime.now());
            return userArg;
        });

        User result = userService.createUser(1, createRequest);

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals(1, result.getOrgId().intValue());
        assertEquals(1, result.getStatus().intValue());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void testCreateUser_UsernameExists() {
        when(userRepository.existsByUsername("newuser")).thenReturn(true);

        assertThrows(BusinessException.class, () ->
                userService.createUser(1, createRequest));

        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void testCreateUser_UnknownOrgIsRejected() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(organizationRepository.existsById(99)).thenReturn(false);

        assertThrows(BusinessException.class, () ->
                userService.createUser(99, createRequest));

        verify(userRepository, never()).save(any());
    }

    // ==================== getUsersByOrgId ====================

    @Test
    void testGetUsersByOrgId_Success() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        User user = new User();
        user.setId(1);
        user.setOrgId(1);
        List<User> users = Collections.singletonList(user);
        Page<User> userPage = new PageImpl<>(users, pageRequest, 1);

        when(userRepository.findByOrgIdAndRealname(eq(1), anyString(), isNull(), eq(pageRequest))).thenReturn(userPage);
        when(organizationService.getOrgNameById(1)).thenReturn("测试组织");

        Page<User> result = userService.getUsersByOrgId(1, "", null, pageRequest, true);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("测试组织", result.getContent().get(0).getOrgName());
    }

    @Test
    void testGetUsersByOrgId_EmptyResult() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<User> emptyPage = Page.empty(pageRequest);

        when(userRepository.findByOrgIdAndRealname(eq(999), anyString(), isNull(), eq(pageRequest))).thenReturn(emptyPage);

        Page<User> result = userService.getUsersByOrgId(999, "", null, pageRequest, true);

        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
    }

    // ==================== getUsersById ====================

    @Test
    void testGetUsersById_Success() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(organizationService.getOrgNameById(1)).thenReturn("测试组织");

        User result = userService.getUsersById(1);

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals("测试组织", result.getOrgName());
    }

    @Test
    void testGetUsersById_NotFound() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                userService.getUsersById(999));
    }

    // ==================== getRealNamesByIds ====================

    @Test
    void testGetRealNamesByIds_Success() {
        User user1 = new User();
        user1.setId(1);
        user1.setRealName("用户A");
        User user2 = new User();
        user2.setId(2);
        user2.setRealName("用户B");

        when(userRepository.findAllById(Arrays.asList(1, 2))).thenReturn(Arrays.asList(user1, user2));

        Map<Integer, String> result = userService.getRealNamesByIds(Arrays.asList(1, 2));

        assertEquals(2, result.size());
        assertEquals("用户A", result.get(1));
        assertEquals("用户B", result.get(2));
    }

    @Test
    void testGetRealNamesByIds_NullInput() {
        Map<Integer, String> result = userService.getRealNamesByIds(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void testGetRealNamesByIds_EmptyInput() {
        Map<Integer, String> result = userService.getRealNamesByIds(Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository, never()).findAllById(any());
    }

    // ==================== updateUserStatus ====================

    @Test
    void testUpdateUserStatus_Success() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.updateUserStatus(1, 0);

        assertNotNull(result);
        assertEquals(0, result.getStatus());
        assertEquals(1, result.getTokenVersion());
        verify(userRepository).save(testUser);
        verify(userSessionCache).evict(1);
    }

    @Test
    void testUpdateUserStatus_NotFound() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                userService.updateUserStatus(999, 0));

        verify(userRepository, never()).save(any());
    }

    // ==================== updateUserPassword ====================

    @Test
    void testUpdateUserPassword_Success() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.updateUserPassword(1, "oldPassword", "newPassword");

        assertNotNull(result);
        assertEquals(1, result.getTokenVersion());
        verify(userRepository).save(testUser);
        verify(userSessionCache).evict(1);
    }

    @Test
    void testUpdateUserPassword_WrongOldPassword() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.updateUserPassword(1, "wrongPassword", "newPassword"));

        assertEquals("原密码不正确", ex.getMessage());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userSessionCache, never()).evict(anyInt());
    }

    @Test
    void testUpdateUserPassword_NotFound() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                userService.updateUserPassword(999, "oldPassword", "newPassword"));

        verify(userRepository, never()).save(any());
    }

    // ==================== deleteUser ====================

    @Test
    void testDeleteUser_Success() {
        when(userRepository.existsById(1)).thenReturn(true);

        userService.deleteUser(1);

        verify(userRoleRepository).deleteByUserId(1);
        verify(userRepository).deleteById(1);
        verify(userSessionCache).evict(1);
    }

    @Test
    void testDeleteUser_NotFound() {
        when(userRepository.existsById(999)).thenReturn(false);

        assertThrows(NotFoundException.class, () ->
                userService.deleteUser(999));

        verify(userRoleRepository, never()).deleteByUserId(anyInt());
        verify(userRepository, never()).deleteById(anyInt());
        verify(userSessionCache, never()).evict(anyInt());
    }

    // ==================== assignRolesToUser ====================

    @Test
    void testAssignRolesToUser_Success() {
        Role role1 = new Role();
        role1.setId(1);
        role1.setOrgId(1);
        Role role2 = new Role();
        role2.setId(3);
        role2.setOrgId(1);

        Integer[] roleIds = {1, 3};

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(1)).thenReturn(Optional.of(role1));
        when(roleRepository.findById(3)).thenReturn(Optional.of(role2));

        userService.assignRolesToUser(1, roleIds, 1);

        verify(userRoleRepository).deleteByUserId(1);
        verify(roleRepository, times(2)).findById(anyInt());
        verify(userRoleRepository, times(2)).save(any(UserRole.class));
        verify(userSessionCache).evict(1);
    }

    @Test
    void testAssignRolesToUser_RoleNotFound() {
        Integer[] roleIds = {999};

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                userService.assignRolesToUser(1, roleIds, 1));

        verify(userRoleRepository).deleteByUserId(1);
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void testAssignRolesToUser_RoleNotInOrg() {
        Role role = new Role();
        role.setId(1);
        role.setOrgId(2); // different org

        Integer[] roleIds = {1};

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));

        assertThrows(BusinessException.class, () ->
                userService.assignRolesToUser(1, roleIds, 1));

        verify(userRoleRepository).deleteByUserId(1);
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void testAssignRolesToUser_CrossOrgTargetRejected() {
        User otherOrgUser = new User();
        otherOrgUser.setId(7);
        otherOrgUser.setRole("user");
        otherOrgUser.setOrgId(2);
        // 组织管理员（scopeOrgId=1）不能操作其他组织的账号
        when(userRepository.findById(7)).thenReturn(Optional.of(otherOrgUser));

        assertThrows(NotFoundException.class, () ->
                userService.assignRolesToUser(7, new Integer[]{1}, 1));

        verify(userRoleRepository, never()).deleteByUserId(anyInt());
    }

    @Test
    void testAssignRolesToUser_SuperAdminRoleAllowedForSuperAdmin() {
        Role superRole = new Role();
        superRole.setId(9);
        superRole.setRoleCode("SUPER_ADMIN");
        superRole.setOrgId(null); // 全局角色

        User adminTarget = new User();
        adminTarget.setId(2);
        adminTarget.setRole("admin");
        adminTarget.setOrgId(1);

        when(userRepository.findById(2)).thenReturn(Optional.of(adminTarget));
        when(roleRepository.findById(9)).thenReturn(Optional.of(superRole));

        userService.assignRolesToUser(2, new Integer[]{9}, null);

        verify(userRoleRepository).save(any(UserRole.class));
        verify(userSessionCache).evict(2);
    }

    @Test
    void testAssignRolesToUser_SuperAdminRoleRejectedForOrgAdmin() {
        Role superRole = new Role();
        superRole.setId(9);
        superRole.setRoleCode("SUPER_ADMIN");
        superRole.setOrgId(null);

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(9)).thenReturn(Optional.of(superRole));

        assertThrows(ForbiddenException.class, () ->
                userService.assignRolesToUser(1, new Integer[]{9}, 1));

        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void testAssignRolesToUser_GlobalNonSuperRoleRejected() {
        Role personalRole = new Role();
        personalRole.setId(10);
        personalRole.setRoleCode("USER_FEATURES_1");
        personalRole.setOrgId(null);

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(10)).thenReturn(Optional.of(personalRole));

        assertThrows(BusinessException.class, () ->
                userService.assignRolesToUser(1, new Integer[]{10}, 1));

        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void testAssignRolesToUser_OrgAdminRoleKeepsUser() {
        User target = new User();
        target.setId(5);
        target.setRole("user");
        target.setOrgId(1);

        Role role = new Role();
        role.setId(2);
        role.setRoleCode("ORG_ADMIN_1");
        role.setOrgId(1);

        when(userRepository.findById(5)).thenReturn(Optional.of(target));
        when(roleRepository.findById(2)).thenReturn(Optional.of(role));

        userService.assignRolesToUser(5, new Integer[]{2}, 1);

        assertEquals("user", target.getRole());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testAssignRolesToUser_NormalAdminRoleKeepsUser() {
        User target = new User();
        target.setId(6);
        target.setRole("user");
        target.setOrgId(1);

        Role role = new Role();
        role.setId(3);
        role.setRoleCode("NORMAL_ADMIN_1");
        role.setOrgId(1);

        when(userRepository.findById(6)).thenReturn(Optional.of(target));
        when(roleRepository.findById(3)).thenReturn(Optional.of(role));

        userService.assignRolesToUser(6, new Integer[]{3}, 1);

        assertEquals("user", target.getRole());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testAssignRolesToUser_CustomRoleKeepsUser() {
        User target = new User();
        target.setId(7);
        target.setRole("user");
        target.setOrgId(1);

        Role role = new Role();
        role.setId(8);
        role.setRoleCode("EDITOR");
        role.setOrgId(1);

        when(userRepository.findById(7)).thenReturn(Optional.of(target));
        when(roleRepository.findById(8)).thenReturn(Optional.of(role));

        userService.assignRolesToUser(7, new Integer[]{8}, 1);

        assertEquals("user", target.getRole());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testAssignRolesToUser_AdminIsDemotedWhenOnlyCustomRoleRemains() {
        User target = new User();
        target.setId(2);
        target.setRole("admin");
        target.setOrgId(1);

        Role customRole = new Role();
        customRole.setId(7);
        customRole.setRoleCode("EDITOR");
        customRole.setOrgId(1);

        when(userRepository.findById(2)).thenReturn(Optional.of(target));
        when(roleRepository.findById(7)).thenReturn(Optional.of(customRole));

        userService.assignRolesToUser(2, new Integer[]{7}, 1);

        assertEquals("user", target.getRole());
        verify(userRepository).save(target);
    }

    @Test
    void testAssignRolesToUser_DefaultSuperAdminCannotLoseAdminRole() {
        User target = new User();
        target.setId(1);
        target.setUsername("admin");
        target.setRole("admin");
        target.setOrgId(1);

        Role customRole = new Role();
        customRole.setId(7);
        customRole.setRoleCode("EDITOR");
        customRole.setOrgId(1);

        when(userRepository.findById(1)).thenReturn(Optional.of(target));
        when(roleRepository.findById(7)).thenReturn(Optional.of(customRole));

        assertThrows(BusinessException.class, () ->
                userService.assignRolesToUser(1, new Integer[]{7}, 1));

        assertEquals("admin", target.getRole());
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== createCreator（/api/admin/users 创建口径） ====================

    private CreateUserContractRequest creatorRequest() {
        CreateUserContractRequest req = new CreateUserContractRequest();
        req.setUsername("contract_creator");
        req.setName("契约创作者");
        req.setPassword("password123");
        return req;
    }

    @Test
    void testCreateCreator_UsesUsernameAndLeavesEmailEmpty() {
        when(userRepository.existsByUsername("contract_creator")).thenReturn(false);
        when(organizationRepository.existsById(7)).thenReturn(true);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userArg = invocation.getArgument(0);
            userArg.setId(42);
            return userArg;
        });

        User created = userService.createCreator(creatorRequest(), 7);

        assertEquals("contract_creator", created.getUsername());
        assertNull(created.getEmail());
        assertEquals("契约创作者", created.getName());
        assertEquals("契约创作者", created.getRealName());
        assertEquals("user", created.getRole());
        assertEquals(Integer.valueOf(7), created.getOrgId());
        assertEquals("encoded", created.getPassword());
        verify(featurePermissionService).initForNewUser(created, null);
    }

    @Test
    void testCreateCreator_RejectsDuplicateUsernameBeforeWriting() {
        when(userRepository.existsByUsername("contract_creator")).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.createCreator(creatorRequest(), 7));

        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(featurePermissionService, never()).initForNewUser(any(User.class), any());
    }
}
