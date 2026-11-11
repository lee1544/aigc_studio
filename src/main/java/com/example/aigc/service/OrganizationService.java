package com.example.aigc.service;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.constant.SystemConstant;
import com.example.aigc.dto.CreateOrganizationRequest;
import com.example.aigc.dto.UpdateOrganizationRequest;
import com.example.aigc.entity.Organization;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.entity.UserRole;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.ForbiddenException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.filter.UserSessionCache;
import com.example.aigc.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织与组织内置角色服务。
 *
 * <p>数据范围：超级管理员可查看/管理全部组织；组织管理员只能看到自己的组织。
 * 组织生命周期（创建/修改/删除）属于平台级操作，仅超级管理员可执行。
 */
@Service
public class OrganizationService {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserSessionCache userSessionCache;

    /**
     * 创建组织：仅超级管理员。
     * <p>同一事务内依次完成：组织落库 → 组织创作角色（无默认权限）→ 学校创作账号并绑定角色。
     */
    @Transactional
    public Organization createOrganization(CreateOrganizationRequest request, boolean superAdmin) {
        if (!superAdmin) {
            throw new ForbiddenException("创建组织仅限超级管理员操作");
        }
        // 统一去掉首尾空格，避免出现“看起来一样、实际不同”的组织/账号
        request.setOrgCode(trim(request.getOrgCode()));
        request.setOrgName(trim(request.getOrgName()));
        request.setAdminUsername(trim(request.getAdminUsername()));
        request.setAdminRealName(trim(request.getAdminRealName()));

        // 先做完全部唯一性校验，避免产生“组织已建、账号建失败”的中间态
        if (organizationRepository.existsByOrgCode(request.getOrgCode())) {
            throw new BusinessException("组织编码已存在");
        }
        if (organizationRepository.existsByOrgName(request.getOrgName())) {
            throw new BusinessException("组织名称已存在");
        }
        if (userRepository.existsByUsername(request.getAdminUsername())) {
            throw new BusinessException("管理员用户名已存在");
        }
        validateAdminPassword(request.getAdminPassword());

        Organization org = new Organization();
        org.setOrgCode(request.getOrgCode());
        org.setOrgName(request.getOrgName());
        org.setDescription(request.getDescription());
        org.setStatus(1);
        org.setCreateTime(LocalDateTime.now());
        org.setUpdateTime(LocalDateTime.now());
        organizationRepository.save(org);

        Role orgAdminRole = ensureDefaultOrgRoles(org);
        createOrgAdminUser(org, orgAdminRole, request);
        return org;
    }

    /**
     * 确保组织拥有内置的“组织创作角色”（幂等）。
     * <p>该角色是学校创作账号的共享角色，创建时<b>不分配任何权限</b>，
     * 由超级管理员在管理后台按学校开通 25 项创作 feature。
     *
     * @return 该组织的组织创作角色
     */
    @Transactional
    public Role ensureDefaultOrgRoles(Organization org) {
        return ensureRole(org,
                SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX + org.getId(),
                SystemConstant.ORG_ADMIN_ROLE_NAME,
                SystemConstant.ORG_ADMIN_ROLE_DESCRIPTION,
                SystemConstant.ORG_ADMIN_PERMISSIONS);
    }

    private Role ensureRole(Organization org, String roleCode, String roleName,
                            String description, String[] permissionCodes) {
        Role existing = roleRepository.findByRoleCode(roleCode).orElse(null);
        if (existing != null) {
            return existing;
        }
        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setDescription(description);
        role.setOrgId(org.getId());
        role.setStatus(1);
        role.setPermissionVersion(0);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        roleRepository.save(role);
        if (permissionCodes != null && permissionCodes.length > 0) {
            permissionService.assignPermissionsByCodesToRole(role.getId(), permissionCodes);
        }
        return role;
    }

    private void createOrgAdminUser(Organization org, Role orgAdminRole, CreateOrganizationRequest request) {
        User adminUser = new User();
        adminUser.setUsername(request.getAdminUsername());
        adminUser.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        adminUser.setRealName(request.getAdminRealName());
        adminUser.setName(request.getAdminRealName());
        adminUser.setOrgId(org.getId());
        // 学校创作账号：等同创作者，只能登录创作平台，不能登录管理后台
        adminUser.setRole("user");
        adminUser.setStatus(1);
        adminUser.setTokenVersion(0);
        adminUser.setPermissionVersion(0);
        adminUser.setAccountSource("学校创作账号");
        adminUser.setCreateTime(LocalDateTime.now());
        adminUser.setUpdateTime(LocalDateTime.now());
        userRepository.save(adminUser);

        UserRole userRole = new UserRole();
        userRole.setUserId(adminUser.getId());
        userRole.setRoleId(orgAdminRole.getId());
        userRoleRepository.save(userRole);
    }

    private void validateAdminPassword(String password) {
        if (password == null || password.length() < 6) {
            throw new BusinessException("管理员密码至少 6 个字符");
        }
    }

    public String getOrgNameById(Integer orgId) {
        if (orgId == null || orgId <= 0) {
            return "未分配组织";
        }
        return organizationRepository.findById(orgId)
                .map(Organization::getOrgName)
                .orElse("未知组织");
    }

    /** 组织状态：1 启用 / 0 禁用；组织不存在返回 null。 */
    public Integer getOrgStatusById(Integer orgId) {
        if (orgId == null || orgId <= 0) {
            return null;
        }
        return organizationRepository.findById(orgId)
                .map(Organization::getStatus)
                .orElse(null);
    }

    /**
     * 组织详情。组织管理员只能读取自己的组织，跨组织按 404 处理，避免泄漏组织是否存在。
     */
    public Organization getOrganizationById(Integer orgId, Integer scopeOrgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new NotFoundException("组织不存在"));
        requireOrgInScope(org, scopeOrgId);
        return org;
    }

    /**
     * 组织分页列表。
     *
     * @param scopeOrgId 数据范围：null 表示全部（超级管理员），否则只返回该组织
     */
    public Page<Organization> getOrganizations(String keyword, Integer scopeOrgId, Pageable pageable) {
        String normalized = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return organizationRepository.search(normalized, scopeOrgId, pageable);
    }

    @Transactional
    public Organization updateOrganization(Integer id, UpdateOrganizationRequest request, boolean superAdmin) {
        if (!superAdmin) {
            throw new ForbiddenException("修改组织仅限超级管理员操作");
        }
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("组织不存在"));

        if (request.getOrgCode() != null) {
            if (SystemConstant.DEFAULT_ORG_CODE.equals(org.getOrgCode())
                    && !SystemConstant.DEFAULT_ORG_CODE.equals(request.getOrgCode())) {
                throw new BusinessException("默认组织的组织编码不允许修改");
            }
            if (!request.getOrgCode().equals(org.getOrgCode())
                    && organizationRepository.existsByOrgCode(request.getOrgCode())) {
                throw new BusinessException("组织编码已存在");
            }
            org.setOrgCode(request.getOrgCode());
        }

        if (request.getOrgName() != null) {
            if (!request.getOrgName().equals(org.getOrgName())
                    && organizationRepository.existsByOrgName(request.getOrgName())) {
                throw new BusinessException("组织名称已存在");
            }
            org.setOrgName(request.getOrgName());
        }

        if (request.getDescription() != null) {
            org.setDescription(request.getDescription());
        }

        boolean disabling = false;
        if (request.getStatus() != null) {
            if (SystemConstant.DEFAULT_ORG_CODE.equals(org.getOrgCode()) && request.getStatus() != 1) {
                throw new BusinessException("默认组织不允许禁用");
            }
            disabling = request.getStatus() == 0
                    && (org.getStatus() == null || org.getStatus() != 0);
            org.setStatus(request.getStatus());
        }

        org.setUpdateTime(LocalDateTime.now());
        Organization saved = organizationRepository.save(org);
        if (disabling) {
            // 组织禁用后旧会话立即失效（tokenVersion 是数据库级，跨实例生效）
            revokeOrgSessions(id);
        }
        return saved;
    }

    private void revokeOrgSessions(Integer orgId) {
        LocalDateTime now = LocalDateTime.now();
        for (User user : userRepository.findByOrgId(orgId)) {
            user.setTokenVersion(user.getTokenVersion() + 1);
            user.setUpdateTime(now);
            userRepository.save(user);
            userSessionCache.evict(user.getId());
        }
    }

    /**
     * 删除组织：仅超级管理员，且默认组织不可删除。
     * <p>组织内用户软删除并撤销会话；组织角色与权限关联清理。
     */
    @Transactional
    public void deleteOrganization(Integer id, boolean superAdmin) {
        if (!superAdmin) {
            throw new ForbiddenException("删除组织仅限超级管理员操作");
        }
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("组织不存在"));
        if (SystemConstant.DEFAULT_ORG_CODE.equals(org.getOrgCode())) {
            throw new BusinessException("默认组织不允许删除");
        }

        LocalDateTime now = LocalDateTime.now();

        // 用户改为软删除：保留审计与历史关联，同时立即撤销其会话
        List<User> users = userRepository.findByOrgId(id);
        for (User user : users) {
            userRoleRepository.deleteByUserId(user.getId());
            // 个人 feature 角色 org_id 为 NULL，不属于组织角色，需要按用户清理
            roleRepository.findByRoleCode(FeaturePermissions.PERSONAL_ROLE(user.getId())).ifPresent(role -> {
                rolePermissionRepository.deleteByRoleId(role.getId());
                roleRepository.delete(role);
            });
            user.setStatus(-1);
            user.setTokenVersion(user.getTokenVersion() + 1);
            user.setUpdateTime(now);
            userRepository.save(user);
            userSessionCache.evict(user.getId());
        }

        List<Role> roles = roleRepository.findByOrgId(id);
        for (Role role : roles) {
            rolePermissionRepository.deleteByRoleId(role.getId());
        }
        roleRepository.deleteAll(roles);

        organizationRepository.delete(org);
    }

    private void requireOrgInScope(Organization org, Integer scopeOrgId) {
        if (scopeOrgId != null && !scopeOrgId.equals(org.getId())) {
            throw new NotFoundException("组织不存在");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
