package com.example.aigc.event;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 角色权限变更后需要失效的会话范围。
 * <p>
 * 以事件方式发布，避免 {@code PermissionService -> UserSessionCache -> PermissionService} 的循环依赖；
 * 监听方在事务提交后执行失效，避免并发请求读到未提交的旧权限。
 * {@code userIds == null} 表示全部会话失效。
 */
public class SessionPermissionChangedEvent {

    private final Set<Integer> userIds;

    private SessionPermissionChangedEvent(Set<Integer> userIds) {
        this.userIds = userIds;
    }

    public static SessionPermissionChangedEvent all() {
        return new SessionPermissionChangedEvent(null);
    }

    public static SessionPermissionChangedEvent forUsers(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new SessionPermissionChangedEvent(Collections.emptySet());
        }
        return new SessionPermissionChangedEvent(new LinkedHashSet<>(ids));
    }

    public boolean isAll() {
        return userIds == null;
    }

    public Set<Integer> getUserIds() {
        return userIds;
    }
}
