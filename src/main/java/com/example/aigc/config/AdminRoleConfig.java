package com.example.aigc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册管理端拦截器：
 * <ul>
 *   <li>{@link AdminRoleInterceptor}：/api/admin/** 仅管理员（role=admin）可访问，登录入口除外；</li>
 *   <li>{@link SuperAdminInterceptor}：标注 {@code @SuperAdminOnly} 的接口仅全局超级管理员可访问，
 *       目前用于组织创建/修改/删除。</li>
 * </ul>
 */
@Configuration
public class AdminRoleConfig implements WebMvcConfigurer {

    @Autowired
    private AdminRoleInterceptor adminRoleInterceptor;

    @Autowired
    private SuperAdminInterceptor superAdminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login");
        registry.addInterceptor(superAdminInterceptor)
                .addPathPatterns("/api/**");
    }
}
