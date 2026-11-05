package com.example.aigc.config;

import com.example.aigc.service.RbacBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时执行 RBAC 初始化。
 * <p>
 * Flyway 迁移（t_organization/t_role/t_permission 等种子数据）先于 ApplicationRunner 执行，
 * 因此这里只做“补齐缺失项”的幂等收敛：默认组织、超级管理员角色全量启用权限、
 * 默认超级管理员账号与角色绑定、各组织内置角色。
 * <p>
 * 初始化失败会让应用启动失败：缺少超级管理员时继续运行会让平台处于不可管理状态。
 */
@Component
@Order(0)
public class RbacInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RbacInitializer.class);

    @Autowired
    private RbacBootstrapService rbacBootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            rbacBootstrapService.initialize();
        } catch (RuntimeException e) {
            log.error("RBAC 初始化失败：请检查 Flyway 是否已执行、t_permission 种子数据是否完整", e);
            throw e;
        }
    }
}
