package com.example.aigc.config;

import com.example.aigc.exception.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * /api/admin/** 管理员角色校验。
 * <p>
 * JWT 过滤器只负责认证，不区分 admin/user；这里补上管理端授权边界。
 * /api/admin/login 在 WebMvcConfig 中排除；DEV_BYPASS_TOKEN 由过滤器写入 role=admin，仍然放行。
 */
@Component
public class AdminRoleInterceptor implements HandlerInterceptor {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // CORS 预检由 CorsFilter 处理，这里放行避免误伤
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Object role = request.getAttribute("role");
        if ("admin".equals(role)) {
            return true;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(objectMapper.writeValueAsString(
                new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "没有权限访问")));
        return false;
    }
}
