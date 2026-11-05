package com.example.aigc.service;

import com.example.aigc.constant.SystemConstant;
import com.example.aigc.entity.Organization;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.entity.UserRole;
import com.example.aigc.repository.OrganizationRepository;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RBAC 启动初始化（幂等）。
 *
 * <p>系统初始化时保证以下不变量：
 * <ol>
 *   <li>存在默认组织 {@code DEFAULT_ORG}，作为平台级账号的归属组织；</li>
 *   <li>存在全局超级管理员角色 {@code SUPER_ADMIN}（org_id 为 NULL），并拥有全部“启用中”的权限码；</li>
 *   <li>存在默认超级管理员账号 {@code admin}，并绑定超级管理员角色；</li>
 *   <li>每个组织都有内置的“组织管理员”“普通管理员”角色（缺失时补齐）。</li>
 * </ol>
 * 所有步骤都只补不覆盖：已存在的账号不会被重置密码，已有角色授权不会被清空。
 */
@Service
public class RbacBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(RbacBootstrapService.class);

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private PermissionService permissionService;

    /** 初始化结果，便于启动日志与单元测试断言。 */
    public static class Summary {
        public final Integer defaultOrgId;
        public final Integer superAdminRoleId;
        public final int grantedPermissions;
        public final boolean superAdminUserCreated;
        public final int organizationsEnsured;

        Summary(Integer defaultOrgId, Integer superAdminRoleId, int grantedPermissions,
                boolean superAdminUserCreated, int organizationsEnsured) {
            this.defaultOrgId = defaultOrgId;
            this.superAdminRoleId = superAdminRoleId;
            this.grantedPermissions = grantedPermissions;
            this.superAdminUserCreated = superAdminUserCreated;
            this.organizationsEnsured = organizationsEnsured;
        }
    }

    @Transactional
    public Summary initialize() {
        Organization defaultOrg = ensureDefaultOrganization();
        Role superAdminRole = ensureSuperAdminRole();
        int granted = permissionService.grantMissingActivePermissions(superAdminRole.getId());

        User admin = userRepository.findByUsername(SystemConstant.DEFAULT_ADMIN_USERNAME).orElse(null);
        boolean adminCreated = false;
        if (admin == null) {
            admin = createDefaultSuperAdmin(defaultOrg);
            adminCreated = true;
        } else if (admin.getOrgId() == null || admin.getOrgId() <= 0) {
            // 老库可能存在 org_id=0 的默认管理员，归位到默认组织
            admin.setOrgId(defaultOrg.getId());
            admin.setUpdateTime(LocalDateTime.now());
            userRepository.save(admin);
        }
        if (!"admin".equals(admin.getRole())) {
            // 避免把同名创作者账号提升为超级管理员：保留数据、阻断启动并提示人工处理
            throw new IllegalStateException("默认超级管理员用户名 “" + SystemConstant.DEFAULT_ADMIN_USERNAME
                    + "” 已被非管理员账号（role=" + admin.getRole() + "）占用，请先重命名或删除该账号后重启");
        }
        bindSuperAdminRole(admin, superAdminRole);

        // 每个组织都补齐内置角色（新建组织在创建流程里已生成，这里兜底历史数据）
        List<Organization> organizations = organizationRepository.findAll();
        for (Organization organization : organizations) {
            organizationService.ensureDefaultOrgRoles(organization);
        }

        Summary summary = new Summary(defaultOrg.getId(), superAdminRole.getId(), granted,
                adminCreated, organizations.size());
        log.info("RBAC 初始化完成：默认组织 id={}，超级管理员角色 id={}，补充权限 {} 项，"
                        + "默认超级管理员{}，组织内置角色校验 {} 个",
                summary.defaultOrgId, summary.superAdminRoleId, summary.grantedPermissions,
                summary.superAdminUserCreated ? "已创建" : "已存在", summary.organizationsEnsured);
        return summary;
    }

    private Organization ensureDefaultOrganization() {
        Organization existing = organizationRepository.findByOrgCode(SystemConstant.DEFAULT_ORG_CODE)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        Organization org = new Organization();
        org.setOrgCode(SystemConstant.DEFAULT_ORG_CODE);
        org.setOrgName(SystemConstant.DEFAULT_ORG_NAME);
        org.setDescription(SystemConstant.DEFAULT_ORG_DESCRIPTION);
        org.setStatus(1);
        org.setCreateTime(LocalDateTime.now());
        org.setUpdateTime(LocalDateTime.now());
        organizationRepository.save(org);
        log.info("RBAC 初始化：创建默认组织 {}（id={}）", org.getOrgCode(), org.getId());
        return org;
    }

    private Role ensureSuperAdminRole() {
        Role existing = roleRepository.findByRoleCode(SystemConstant.SUPER_ADMIN_ROLE_CODE).orElse(null);
        if (existing != null) {
            if (existing.getOrgId() != null) {
                // 超级管理员角色必须是全局角色，否则会同时被组织数据范围命中
                log.warn("超级管理员角色 {} 的 org_id 应为 NULL，当前为 {}，已自动纠正为全局角色",
                        existing.getRoleCode(), existing.getOrgId());
                existing.setOrgId(null);
                existing.setUpdateTime(LocalDateTime.now());
                roleRepository.save(existing);
            }
            return existing;
        }
        Role role = new Role();
        role.setRoleCode(SystemConstant.SUPER_ADMIN_ROLE_CODE);
        role.setRoleName("超级管理员");
        role.setDescription("系统全局超级管理员，可见并管理所有组织的数据");
        role.setOrgId(null);
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        roleRepository.save(role);
        log.info("RBAC 初始化：创建超级管理员角色（id={}）", role.getId());
        return role;
    }

    private User createDefaultSuperAdmin(Organization defaultOrg) {
        User user = new User();
        user.setUsername(SystemConstant.DEFAULT_ADMIN_USERNAME);
        user.setPassword(passwordEncoder.encode(SystemConstant.DEFAULT_ADMIN_PASSWORD));
        user.setRealName(SystemConstant.DEFAULT_ADMIN_REAL_NAME);
        user.setName(SystemConstant.DEFAULT_ADMIN_REAL_NAME);
        user.setRole("admin");
        user.setOrgId(defaultOrg.getId());
        user.setStatus(1);
        user.setTokenVersion(0);
        user.setPermissionVersion(0);
        user.setAccountSource("系统初始化");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        log.info("RBAC 初始化：创建默认超级管理员账号 {}（id={}）",
                user.getUsername(), user.getId());
        return user;
    }

    private void bindSuperAdminRole(User admin, Role superAdminRole) {
        List<Integer> roleIds = userRoleRepository.findRoleIdsByUserId(admin.getId());
        if (roleIds != null && roleIds.contains(superAdminRole.getId())) {
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(superAdminRole.getId());
        userRoleRepository.save(userRole);
        log.info("RBAC 初始化：为账号 {} 绑定超级管理员角色", admin.getUsername());
    }
}
