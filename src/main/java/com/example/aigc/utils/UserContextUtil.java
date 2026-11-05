package com.example.aigc.utils;

import com.example.aigc.exception.UnauthorizedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public class UserContextUtil {
    
    public static Integer getCurrentUserId() {
        HttpServletRequest request = getCurrentRequest();
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }
        return userId;
    }
    
    public static Integer getCurrentOrgId() {
        HttpServletRequest request = getCurrentRequest();
        Integer orgId = (Integer) request.getAttribute("orgId");
        if (orgId == null) {
            throw new UnauthorizedException("用户未登录");
        }
        return orgId;
    }
    
    public static String getCurrentUsername() {
        HttpServletRequest request = getCurrentRequest();
        String username = (String) request.getAttribute("username");
        if (username == null) {
            throw new UnauthorizedException("用户未登录");
        }
        return username;
    }

    public static String getCurrentRole() {
        HttpServletRequest request = getCurrentRequest();
        Object role = request.getAttribute("role");
        return role == null ? "user" : role.toString();
    }

    public static boolean isAdmin() {
        return "admin".equals(getCurrentRole());
    }

    /**
     * 当前会话是否全局超级管理员。
     * <p>
     * 由 JWT 过滤器按数据库中的角色绑定实时解析并写入请求属性，
     * 不依赖 token 里的角色声明；DEV_BYPASS_TOKEN 视为超级管理员。
     * 非 Web 上下文（例如单元测试直接调用）返回 false。
     */
    public static boolean isSuperAdmin() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        if (Boolean.TRUE.equals(request.getAttribute("isDevBypass"))) {
            return true;
        }
        return Boolean.TRUE.equals(request.getAttribute("isSuperAdmin"));
    }

    /**
     * 数据可见范围：超级管理员返回 {@code null}（不限制组织），
     * 其他管理员返回当前账号所属组织。
     */
    public static Integer getVisibleOrgId() {
        return isSuperAdmin() ? null : getCurrentOrgId();
    }
    
    public static boolean isDevBypass() {
        HttpServletRequest request = getCurrentRequest();
        Boolean isDevBypass = (Boolean) request.getAttribute("isDevBypass");
        return isDevBypass != null && isDevBypass;
    }

    @SuppressWarnings("unchecked")
    public static boolean hasCurrentPermission(String permission) {
        if (isDevBypass()) {
            return true;
        }
        HttpServletRequest request = getCurrentRequest();
        List<String> permissions = (List<String>) request.getAttribute("userPermissions");
        return permissions != null && permissions.contains(permission);
    }
    
    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new UnauthorizedException("无法获取请求上下文");
        }
        return attributes.getRequest();
    }
}
