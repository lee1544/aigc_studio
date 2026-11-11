package com.example.aigc.service;

import com.example.aigc.constant.SystemConstant;
import com.example.aigc.dto.CreateUserRequest;
import com.example.aigc.dto.contract.CreateUserContractRequest;
import com.example.aigc.dto.contract.UpdateUserContractRequest;
import com.example.aigc.entity.Organization;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户服务。
 *
 * <p>数据范围：超级管理员可管理全部组织的账号；组织管理员只能管理本组织的账号。
 * 角色分配同样受组织约束：组织管理员不能在跨组织账号上操作，也不能分配全局角色。
 */
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
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserSessionCache userSessionCache;

    @Autowired
    private FeaturePermissionService featurePermissionService;

    @Transactional
    public User createUser(Integer currentOrgId, CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (currentOrgId == null || !organizationRepository.existsById(currentOrgId)) {
            throw new BusinessException("组织不存在");
        }
        // 组织管理员新增用户：固定归属当前组织，角色为创作者
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setName(request.getRealName());
        user.setRole("user");
        user.setOrgId(currentOrgId);
        user.setStatus(1);
        user.setTokenVersion(0);
        user.setPermissionVersion(0);
        user.setAccountSource("组织管理员创建");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    /**
     * 用户分页列表。
     *
     * @param orgId 数据范围：null 表示全部组织（超级管理员），否则只查该组织
     */
    public Page<User> getUsersByOrgId(Integer orgId, String realName, Integer status,
                                      Pageable pageable, boolean includeRoles) {
        Page<User> userPage = (orgId == null)
                ? userRepository.searchAllByRealname(realName, status, pageable)
                : userRepository.findByOrgIdAndRealname(orgId, realName, status, pageable);
        if (orgId == null) {
            // 超级管理员视角：跨组织列表需要逐人回填组织名称
            fillOrgNames(userPage.getContent());
        } else {
            String orgName = organizationService.getOrgNameById(orgId);
            userPage.forEach(user -> user.setOrgName(orgName));
        }
        if (includeRoles) {
            fillRoleSummaries(userPage.getContent());
        }
        return userPage;
    }

    /** 批量补齐组织名称，避免逐条查询。 */
    private void fillOrgNames(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Set<Integer> orgIds = new LinkedHashSet<>();
        for (User user : users) {
            if (user.getOrgId() != null && user.getOrgId() > 0) {
                orgIds.add(user.getOrgId());
            }
        }
        Map<Integer, String> orgNames = new HashMap<>();
        if (!orgIds.isEmpty()) {
            for (Organization org : organizationRepository.findAllById(orgIds)) {
                orgNames.put(org.getId(), org.getOrgName());
            }
        }
        for (User user : users) {
            if (user.getOrgId() == null || user.getOrgId() <= 0) {
                user.setOrgName("未分配组织");
            } else {
                user.setOrgName(orgNames.getOrDefault(user.getOrgId(), "未知组织"));
            }
        }
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
        user.setOrgName(organizationService.getOrgNameById(user.getOrgId()));
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
    public User updateUserPassword(Integer userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        // 必须先校验原密码，避免会话被盗后直接覆盖密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
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

    /**
     * 整体替换用户角色。
     *
     * @param scopeOrgId 调用方数据范围：null 表示超级管理员（可跨组织），否则只能是该组织
     */
    @Transactional
    public void assignRolesToUser(Integer userId, Integer[] roleIds, Integer scopeOrgId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        requireUserInScope(target, scopeOrgId);

        userRoleRepository.deleteByUserId(userId);
        boolean adminRole = false;
        if (roleIds != null) {
            Set<Integer> distinctRoleIds = new LinkedHashSet<>();
            for (Integer roleId : roleIds) {
                if (roleId != null) {
                    distinctRoleIds.add(roleId);
                }
            }
            for (Integer roleId : distinctRoleIds) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new NotFoundException("角色不存在"));
                if (SystemConstant.isAdminRoleCode(role.getRoleCode())) {
                    adminRole = true;
                }
                boolean globalRole = role.getOrgId() == null;
                if (globalRole) {
                    // 全局角色只有超级管理员角色允许分配，且调用方必须是超级管理员
                    if (!SystemConstant.SUPER_ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
                        throw new BusinessException("该角色不支持分配: " + role.getRoleCode());
                    }
                    if (scopeOrgId != null) {
                        throw new ForbiddenException("超级管理员角色仅限超级管理员分配");
                    }
                    if (!"admin".equals(target.getRole())) {
                        throw new BusinessException("仅管理员账号可以分配超级管理员角色");
                    }
                } else if (!Objects.equals(role.getOrgId(), target.getOrgId())) {
                    throw new BusinessException("角色不属于该用户所在组织");
                }
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleRepository.save(userRole);
            }
        }
        // 只有 SUPER_ADMIN 会切换账号类型；ORG_ADMIN_*（组织创作角色）、
        // NORMAL_ADMIN_*（已废弃）与自建角色只增加权限集合，账号仍保持 user。
        if (SystemConstant.DEFAULT_ADMIN_USERNAME.equals(target.getUsername()) && !adminRole) {
            throw new BusinessException("默认超级管理员必须保留超级管理员角色");
        }
        String expectedRole = adminRole ? "admin" : "user";
        if (!expectedRole.equals(target.getRole())) {
            target.setRole(expectedRole);
            target.setUpdateTime(LocalDateTime.now());
            userRepository.save(target);
        }
        // 角色变更后立即刷新该用户的会话权限
        userSessionCache.evict(userId);
    }

    // ==================== 契约：/api/admin/users 管理 ====================

    /** POST /api/admin/users：创建创作者账号（用户名 + 姓名 + 密码），归属调用方组织，可选 features。 */
    @Transactional
    public User createCreator(CreateUserContractRequest req, Integer orgId) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new ConflictException("用户名已存在");
        }
        if (orgId == null || !organizationRepository.existsById(orgId)) {
            throw new BusinessException("组织不存在");
        }
        User user = new User();
        // 登录凭据即 username；创建接口不再采集 email，邮箱列留空
        user.setUsername(req.getUsername());
        user.setName(req.getName());
        user.setRealName(req.getName());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("user");
        user.setStatus(1);
        user.setTokenVersion(0);
        user.setPermissionVersion(0);
        user.setAccountSource("管理员创建");
        user.setOrgId(orgId);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user = userRepository.save(user);
        // features 省略时全 false；与账号行同事务
        featurePermissionService.initForNewUser(user, req.getFeatures());
        userSessionCache.evict(user.getId());
        return user;
    }

    /** PATCH /api/admin/users/{id}：未传字段沿用；密码/状态修改撤销旧 token（邮箱仅作联系方式，不改登录名）。 */
    @Transactional
    public User patchCreator(Integer userId, UpdateUserContractRequest req, Integer scopeOrgId) {
        User user = requireCreator(userId, scopeOrgId);
        boolean revoke = false;
        if (req.getName() != null) {
            user.setName(req.getName());
            user.setRealName(req.getName());
        }
        if (req.getEmail() != null && !req.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(req.getEmail())) {
                throw new ConflictException("邮箱已存在");
            }
            // 邮箱降级为可选联系方式：改名不再同步 username，避免登录名被静默改掉
            user.setEmail(req.getEmail());
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
    public User patchCreatorStatus(Integer userId, String status, Integer scopeOrgId) {
        User user = requireCreator(userId, scopeOrgId);
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
    public void softDeleteCreator(Integer userId, Integer scopeOrgId) {
        User user = requireCreator(userId, scopeOrgId);
        user.setStatus(-1);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        userSessionCache.evict(user.getId());
    }

    private User requireCreator(Integer userId, Integer scopeOrgId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        if (!"user".equals(user.getRole())) {
            throw new BusinessException("仅可操作创作者账号");
        }
        requireUserInScope(user, scopeOrgId);
        return user;
    }

    /** 组织管理员只能操作本组织账号；超级管理员（scopeOrgId=null）不受限制。 */
    private void requireUserInScope(User user, Integer scopeOrgId) {
        if (scopeOrgId != null && !Objects.equals(scopeOrgId, user.getOrgId())) {
            throw new NotFoundException("用户不存在");
        }
    }

    /** 校验操作者与目标账号同组织（供控制器复用）。 */
    public User requireUserAccessible(Integer userId, Integer scopeOrgId) {
        User user = getUsersById(userId);
        requireUserInScope(user, scopeOrgId);
        return user;
    }
}
