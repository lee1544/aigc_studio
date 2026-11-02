package com.example.aigc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;

/**
 * 启动期安全自检。
 * <p>
 * DEV_BYPASS_TOKEN 会让携带该值的请求直接以 admin(userId=1) 身份通过，
 * 生产 profile 下必须禁止；本地启用时打印显著告警，避免误带到线上。
 */
@Component
public class SecurityStartupGuard {

    private static final Logger logger = LoggerFactory.getLogger(SecurityStartupGuard.class);

    @Value("${dev.bypass-token:}")
    private String bypassToken;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @PostConstruct
    void checkBypassToken() {
        if (bypassToken == null || bypassToken.trim().isEmpty()) {
            return;
        }
        boolean prod = activeProfiles != null && Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
        if (prod) {
            throw new IllegalStateException(
                    "DEV_BYPASS_TOKEN 在 " + activeProfiles + " 环境被启用，该值等价管理员身份，必须留空");
        }
        logger.warn("DEV_BYPASS_TOKEN 已启用：任何携带该值的请求等价 admin(userId=1)，仅限本地联调，禁止用于公网部署");
    }
}
