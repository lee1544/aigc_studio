package com.example.aigc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 跨域配置。
 * <p>
 * 管理后台与 API 同源部署时无需跨域；仅在前后端分域时通过 {@code cors.allowed-origins}
 * 配置白名单（逗号分隔，默认 {@code *} 仅为兼容本地联调）。生产建议显式收窄。
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        String[] origins = parseOrigins(allowedOrigins);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        // 流水分页游标跨域可见
                        .exposedHeaders("X-Next-Cursor");
            }
        };
    }

    private String[] parseOrigins(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new String[]{"*"};
        }
        String[] parsed = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
        return parsed.length == 0 ? new String[]{"*"} : parsed;
    }
}
