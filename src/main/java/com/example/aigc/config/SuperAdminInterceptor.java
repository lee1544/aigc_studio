package com.example.aigc.config;

import com.example.aigc.annotation.SuperAdminOnly;
import com.example.aigc.exception.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@link SuperAdminOnly} 校验拦截器。
 * <p>
 * 当前只用于组织生命周期（创建/修改/删除）等平台级操作；
 * 模型连接/预设、Agent、工作流、Skill、系统设置、权限启停对管理员（含组织管理员）开放。
 * <p>
 * 超级管理员标识由 JWT 过滤器按数据库角色实时解析后写入 {@code isSuperAdmin} 请求属性，
 * 不信任 token 中的任何角色声明；DEV_BYPASS_TOKEN 视为超级管理员。
 */
@Component
public class SuperAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // CORS 预检由 CorsFilter 处理
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        SuperAdminOnly annotation = handlerMethod.getMethodAnnotation(SuperAdminOnly.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(SuperAdminOnly.class);
        }
        if (annotation == null) {
            return true;
        }

        if (Boolean.TRUE.equals(request.getAttribute("isDevBypass"))
                || Boolean.TRUE.equals(request.getAttribute("isSuperAdmin"))) {
            return true;
        }

        String action = annotation.value();
        String message = (action == null || action.isEmpty())
                ? "仅超级管理员可以执行该操作"
                : action + "仅限超级管理员操作";
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(objectMapper.writeValueAsString(
                new ApiResponse<>(HttpStatus.FORBIDDEN.value(), message)));
        return false;
    }
}
