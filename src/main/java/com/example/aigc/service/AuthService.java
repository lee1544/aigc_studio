package com.example.aigc.service;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.constant.SystemConstant;
import com.example.aigc.dto.contract.AdminLoginRequest;
import com.example.aigc.dto.contract.ContractUserDto;
import com.example.aigc.dto.contract.CreatorLoginRequest;
import com.example.aigc.dto.contract.LoginResult;
import com.example.aigc.dto.contract.RegisterRequest;
import com.example.aigc.dto.contract.UserMapper;
import com.example.aigc.entity.User;
import com.example.aigc.exception.ConflictException;
import com.example.aigc.exception.ForbiddenException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.exception.UnauthorizedException;
import com.example.aigc.filter.JwtTokenBlacklist;
import com.example.aigc.filter.UserSessionCache;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员与创作者登录/注册/会话服务（契约 /api/admin/login、/api/auth/*）。
 */
@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private JwtTokenBlacklist jwtTokenBlacklist;
    @Autowired private UserSessionCache userSessionCache;
    @Autowired private FeaturePermissionService featurePermissionService;
    @Autowired private UserRoleService userRoleService;
    @Autowired private PermissionService permissionService;
    @Autowired private OrganizationService organizationService;
    @Autowired private SystemSettingsService systemSettingsService;

    /** POST /api/admin/login：仅全局超级管理员（用户名 + 密码）。 */
    public LoginResult adminLogin(AdminLoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));
        ensureActive(user);
        if (!"admin".equals(user.getRole())) {
            throw new UnauthorizedException("非管理员账号");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (!userRoleService.isSuperAdmin(user.getId())) {
            throw new ForbiddenException("仅超级管理员可登录管理后台");
        }
        // 必须在更新 lastLoginTime 之前判断，否则首次登录标记永远为 false
        boolean firstLogin = user.getLastLoginTime() == null;
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.save(user);
        return buildLoginResult(user, "admin", firstLogin);
    }

    /** POST /api/auth/login：学校创作账号 email + 密码。 */
    public LoginResult creatorLogin(CreatorLoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new UnauthorizedException("邮箱或密码错误"));
        ensureActive(user);
        if (!"user".equals(user.getRole())) {
            throw new UnauthorizedException("非创作者账号");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("邮箱或密码错误");
        }
        boolean firstLogin = user.getLastLoginTime() == null;
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.save(user);
        return buildLoginResult(user, "user", firstLogin);
    }

    /**
     * POST /api/auth/register：创作者自助注册，默认全 false 权限。
     * 注册开关与 GET /api/public/settings 同源，关闭时在任何写库动作之前返回 403。
     */
    @Transactional
    public LoginResult register(RegisterRequest req) {
        if (!systemSettingsService.isRegistrationAllowed()) {
            throw new ForbiddenException("平台已关闭注册");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ConflictException("邮箱已注册");
        }
        User user = new User();
        // username 沿用 email，保证唯一且 admin 登录用户名口径不变
        user.setUsername(req.getEmail());
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("user");
        user.setStatus(1);
        user.setTokenVersion(0);
        user.setAccountSource("自助注册");
        user.setOrgId(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user = userRepository.save(user);
        // 注册默认全 false；注册请求传入 features 不生效
        featurePermissionService.initForNewUser(user, null);
        userSessionCache.evict(user.getId());
        return buildLoginResult(user, "user", true);
    }

    /** GET /api/admin/me、GET /api/auth/me：当前会话用户（含 token_version、features）。 */
    public ContractUserDto currentUser(Integer userId) {
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        return buildSessionUser(user, user.getLastLoginTime() == null);
    }

    /**
     * POST /api/auth/logout：撤销当前账号的会话。
     * <p>
     * 除内存黑名单外，同时递增 tokenVersion：重启或多实例部署时黑名单可能不共享，
     * 而 tokenVersion 存在数据库里，旧 token 在所有节点上都会立即失效。
     * 代价是同一账号的其他设备也会被登出（当前为单会话模型）。
     */
    public void logout(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        jwtTokenBlacklist.invalidate(token);

        Integer userId;
        try {
            userId = jwtTokenUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            // 过期/伪造 token 无需再写库
            return;
        }
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setTokenVersion(user.getTokenVersion() + 1);
            user.setUpdateTime(LocalDateTime.now());
            userRepository.save(user);
            userSessionCache.evict(userId);
        });
    }

    private LoginResult buildLoginResult(User user, String role, boolean firstLogin) {
        String token = jwtTokenUtil.generateToken(
                user.getUsername(), user.getId(), user.getOrgId(),
                user.getTokenVersion(), role);
        return new LoginResult(token, buildSessionUser(user, firstLogin));
    }

    /**
     * 组装 SessionUser：所有账号都返回 orgId/orgName/orgStatus、roles、permissions；
     * 学校创作账号的 features 取组织创作角色；超管 features 全 false。
     */
    private ContractUserDto buildSessionUser(User user, boolean firstLogin) {
        boolean admin = "admin".equals(user.getRole());
        Map<String, Boolean> features = admin
                ? FeaturePermissions.allFalse()
                : featurePermissionService.getEffectiveFeatures(user);
        ContractUserDto dto = UserMapper.toSessionUser(user, features);

        boolean superAdmin = admin && userRoleService.isSuperAdmin(user.getId());
        dto.setScope(admin ? "admin" : "creator");
        dto.setSuperAdmin(superAdmin);
        dto.setUsername(user.getUsername());
        dto.setRealName(user.getRealName());
        dto.setFirstLogin(firstLogin);
        dto.setOrgId(user.getOrgId());
        dto.setOrgName(organizationService.getOrgNameById(user.getOrgId()));
        dto.setOrgStatus(organizationService.getOrgStatusById(user.getOrgId()));

        List<Integer> roleIds = userRoleService.getRoleIdsByUserId(user.getId());
        List<String> roleCodes = roleIds.stream()
                .map(userRoleService::getRoleCodeById)
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toList());
        dto.setRoles(roleCodes);
        List<String> permissionCodes = permissionService.getPermissionCodesByRoleIds(roleIds);
        if (admin) {
            // 管理端只接收后台 RBAC 权限码；创作者 feature 不在管理后台配置
            permissionCodes = permissionCodes.stream()
                    .filter(SystemConstant::isBackendPermissionCode)
                    .collect(Collectors.toList());
        }
        dto.setPermissions(permissionCodes);
        return dto;
    }

    private void ensureActive(User user) {
        if (user.getStatus() == null || user.getStatus() < 1) {
            throw new UnauthorizedException("账号已被禁用或删除");
        }
        if (user.getOrgId() != null && user.getOrgId() > 0) {
            Integer orgStatus = organizationService.getOrgStatusById(user.getOrgId());
            if (orgStatus == null) {
                throw new ForbiddenException("组织不存在或已删除");
            }
            if (orgStatus != 1) {
                throw new ForbiddenException("组织已禁用");
            }
        }
    }
}
