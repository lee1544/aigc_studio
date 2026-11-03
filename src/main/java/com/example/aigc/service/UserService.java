package com.example.aigc.service;

import com.example.aigc.constant.SystemConstant;
import com.example.aigc.dto.CreateUserRequest;
import com.example.aigc.dto.contract.CreateUserContractRequest;
import com.example.aigc.dto.contract.UpdateUserContractRequest;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.entity.UserRole;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.ConflictException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.exception.UnauthorizedException;
import com.example.aigc.filter.UserSessionCache;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private UserSessionCache userSessionCache;

    @Autowired
    private FeaturePermissionService featurePermissionService;

    @Transactional
    public User createUser(Integer currentOrgId, CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        //组织管理员新增用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setOrgId(currentOrgId);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    public Page<User> getUsersByOrgId(Integer orgId, String realName, Integer status,
                                      Pageable pageable, boolean includeRoles) {
        Page<User> userPage = userRepository.findByOrgIdAndRealname(orgId, realName, status, pageable);
        String orgName = organizationService.getOrgNameById(orgId);
        userPage.forEach(user -> user.setOrgName(orgName));
        if (includeRoles) {
            fillRoleSummaries(userPage.getContent());
        }
        return userPage;
    }

    private void fillRoleSummaries(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }

        List<Integer> userIds = new ArrayList<>();
        for (User user : users) {
            userIds.add(user.getId());
        }
        List<UserRole> links = userRoleRepository.findByUserIdIn(userIds);
        if (links.isEmpty()) {
            users.forEach(user -> {
                user.setRoleIds(Collections.emptyList());
                user.setRoleNames(Collections.emptyList());
            });
            return;
        }

        List<Integer> roleIds = new ArrayList<>();
        for (UserRole link : links) {
            roleIds.add(link.getRoleId());
        }
        Map<Integer, String> roleNameMap = new HashMap<>();
        for (Role role : roleRepository.findAllById(roleIds)) {
            roleNameMap.put(role.getId(), role.getRoleName());
        }

        Map<Integer, List<Integer>> roleIdsByUser = new HashMap<>();
        Map<Integer, List<String>> roleNamesByUser = new HashMap<>();
        for (UserRole link : links) {
            roleIdsByUser.computeIfAbsent(link.getUserId(), key -> new ArrayList<>())
                    .add(link.getRoleId());
            String roleName = roleNameMap.get(link.getRoleId());
            if (roleName != null) {
                roleNamesByUser.computeIfAbsent(link.getUserId(), key -> new ArrayList<>())
                        .add(roleName);
            }
        }

        for (User user : users) {
            user.setRoleIds(roleIdsByUser.getOrDefault(user.getId(), Collections.emptyList()));
            user.setRoleNames(roleNamesByUser.getOrDefault(user.getId(), Collections.emptyList()));
        }
    }

    public User getUsersById(Integer userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在"));
        String orgName = organizationService.getOrgNameById(user.getOrgId());
        user.setOrgName(orgName);
        return user;
    }

    public Map<Integer, String> getRealNamesByIds(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<User> users = userRepository.findAllById(userIds);
        Map<Integer, String> realNameMap = new HashMap<>();
        for (User user : users) {
            realNameMap.put(user.getId(), user.getRealName());
        }
        return realNameMap;
    }

    @Transactional
    public User updateUserRealName(Integer userId, String realName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        user.setRealName(realName);
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserStatus(Integer userId, Integer status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        user.setStatus(status);
        // 禁用/启用都撤销旧 token，避免已禁用账号继续访问
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdateTime(LocalDateTime.now());
        User saved = userRepository.save(user);
        userSessionCache.evict(userId);
        return saved;
    }

    @Transactional
    public User updateUserPassword(Integer userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        user.setPassword(passwordEncoder.encode(password));
        // 改密后旧 token 必须立即失效
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdateTime(LocalDateTime.now());
        User saved = userRepository.save(user);
        userSessionCache.evict(userId);
        return saved;
    }

    @Transactional
    public User resetUserPassword(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        // 重置密码为系统初始密码12345678
        user.setPassword(passwordEncoder.encode(SystemConstant.DEFAULT_SYSTEM_PASSWORD));
        // 清空最后登录时间，使下次登录时触发 firstLogin=true，提示修改密码
        user.setLastLoginTime(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);

        userSessionCache.evict(userId);

        return user;
    }

    @Transactional
    public void deleteUser(Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("用户不存在");
        }
        userRoleRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
        userSessionCache.evict(userId);
    }

    @Transactional
    public void assignRolesToUser(Integer userId, Integer[] roleIds, Integer currentOrgId) {
        userRoleRepository.deleteByUserId(userId);
        if (roleIds != null) {
            for (Integer roleId : roleIds) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new NotFoundException("角色不存在"));
                // org_id 为 NULL 表示全局角色（如超级管理员），任何组织都可以分配
                if (role.getOrgId() != null && !Objects.equals(role.getOrgId(), currentOrgId)) {
                    throw new UnauthorizedException("角色不属于当前组织");
                }
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleRepository.save(userRole);
            }
        }
        // 角色变更后立即刷新该用户的会话权限
        userSessionCache.evict(userId);
    }

    // ==================== 契约：/api/admin/users 管理 ====================

    /** POST /api/admin/users：创建创作者账号，可选 features。 */
    @Transactional
    public User createCreator(CreateUserContractRequest req) {
        if (req.getEmail() == null || req.getEmail().isEmpty()) {
            throw new BusinessException("email 不能为空");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ConflictException("邮箱已存在");
        }
        User user = new User();
        user.setUsername(req.getEmail());
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("user");
        user.setStatus(1);
        user.setTokenVersion(0);
        user.setAccountSource("管理员创建");
        user.setOrgId(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user = userRepository.save(user);
        // features 省略时全 false；与账号行同事务
        featurePermissionService.initForNewUser(user, req.getFeatures());
        userSessionCache.evict(user.getId());
        return user;
    }

    /** PATCH /api/admin/users/{id}：未传字段沿用；密码/邮箱/状态修改撤销旧 token。 */
    @Transactional
    public User patchCreator(Integer userId, UpdateUserContractRequest req) {
        User user = requireCreator(userId);
        boolean revoke = false;
        if (req.getName() != null) {
            user.setName(req.getName());
        }
        if (req.getEmail() != null && !req.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(req.getEmail())) {
                throw new ConflictException("邮箱已存在");
            }
            user.setEmail(req.getEmail());
            user.setUsername(req.getEmail());
            revoke = true;
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getStatus() != null) {
            Integer s = com.example.aigc.dto.contract.UserMapper.statusToInt(req.getStatus());
            if (s == null || s < 0) {
                throw new BusinessException("状态只能为 active/disabled");
            }
            user.setStatus(s);
            revoke = true;
        }
        if (req.getPassword() != null && !req.getPassword().isEmpty()) {
            if (req.getPassword().length() < 6) {
                throw new BusinessException("密码至少 6 位");
            }
            user.setPassword(passwordEncoder.encode(req.getPassword()));
            revoke = true;
        }
        if (revoke) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        user.setUpdateTime(LocalDateTime.now());
        user = userRepository.save(user);
        userSessionCache.evict(user.getId());
        return user;
    }

    /** PATCH /api/admin/users/{id}/status：启用或禁用。 */
    @Transactional
    public User patchCreatorStatus(Integer userId, String status) {
        User user = requireCreator(userId);
        Integer s = com.example.aigc.dto.contract.UserMapper.statusToInt(status);
        if (s == null || s < 0) {
            throw new BusinessException("状态只能为 active/disabled");
        }
        user.setStatus(s);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdateTime(LocalDateTime.now());
        user = userRepository.save(user);
        userSessionCache.evict(user.getId());
        return user;
    }

    /** DELETE /api/admin/users/{id}：软删除，保留历史。 */
    @Transactional
    public void softDeleteCreator(Integer userId) {
        User user = requireCreator(userId);
        user.setStatus(-1);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        userSessionCache.evict(user.getId());
    }

    private User requireCreator(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        if (!"user".equals(user.getRole())) {
            throw new BusinessException("仅可操作创作者账号");
        }
        return user;
    }
}
