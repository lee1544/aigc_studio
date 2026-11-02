package com.example.aigc.filter;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.entity.User;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.service.PermissionService;
import com.example.aigc.service.UserRoleService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用户会话信息缓存：tokenVersion、role、有效权限码（admin 权限码 + 26 项 feature）。
 * <p>
 * filter 每次请求取用，tokenVersion 用于使旧 token 立即失效；权限变更/改密/禁用/登出时 evict。
 */
@Component
public class UserSessionCache {

    public static class SessionInfo {
        public final Integer userId;
        public final Integer tokenVersion;
        public final String role;
        public final List<String> permissions;

        SessionInfo(Integer userId, Integer tokenVersion, String role, List<String> permissions) {
            this.userId = userId;
            this.tokenVersion = tokenVersion;
            this.role = role;
            this.permissions = permissions == null ? Collections.emptyList() : permissions;
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private PermissionService permissionService;

    private final Cache<Integer, SessionInfo> cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public SessionInfo get(Integer userId) {
        SessionInfo info = cache.getIfPresent(userId);
        if (info != null) {
            return info;
        }
        info = load(userId);
        if (info != null) {
            cache.put(userId, info);
        }
        return info;
    }

    /** 不走缓存强制加载（tokenVersion 校验失败时复核用）。 */
    public SessionInfo loadFresh(Integer userId) {
        SessionInfo info = load(userId);
        if (info != null) {
            cache.put(userId, info);
        }
        return info;
    }

    private SessionInfo load(Integer userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        // -1=deleted / 0=disabled 视为无有效会话（即使 tokenVersion 未变也立即 401）
        if (user.getStatus() != null && user.getStatus() < 1) {
            return null;
        }

        List<Integer> roleIds = userRoleService.getRoleIdsByUserId(userId);
        List<String> codes = permissionService.getPermissionCodesByRoleIds(roleIds);

        // 合并 feature 与 admin 权限码，保持去重
        Set<String> merged = new LinkedHashSet<>(codes);
        // 个人角色中的 feature 权限码已被 permissionService 读出，无需额外处理
        return new SessionInfo(userId, user.getTokenVersion(), user.getRole(), new ArrayList<>(merged));
    }

    public void evict(Integer userId) {
        cache.invalidate(userId);
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    /** 当前用户是否拥有某 feature/权限码。 */
    public static boolean has(SessionInfo info, String code) {
        return info != null && info.permissions.contains(code);
    }

    /** 当前用户是否为管理员角色。 */
    public static boolean isAdmin(SessionInfo info) {
        return info != null && "admin".equals(info.role);
    }

    /** 列出当前会话的 26 项 feature 快照（布尔）。 */
    public static java.util.Map<String, Boolean> featureSnapshot(SessionInfo info) {
        java.util.Map<String, Boolean> snap = FeaturePermissions.allFalse();
        if (info != null) {
            for (String code : FeaturePermissions.CODES) {
                if (info.permissions.contains(code)) {
                    snap.put(code, true);
                }
            }
        }
        return snap;
    }
}
