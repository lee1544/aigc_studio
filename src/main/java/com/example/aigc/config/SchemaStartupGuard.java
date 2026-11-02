package com.example.aigc.config;

import com.example.aigc.utils.SchemaDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动期数据库结构自检。
 * <p>
 * ddl-auto=none，表结构不会自动创建；漏执行 sql/ 下的迁移时，问题只会在有人访问对应接口时
 * 以 500 暴露，往往要等联调方反馈才发现。这里在启动完成后把缺失表一次性打进日志。
 * 只告警不阻断：缺一两张表时其余能力仍可用，且自检自身失败不能影响启动。
 */
@Component
public class SchemaStartupGuard {

    private static final Logger logger = LoggerFactory.getLogger(SchemaStartupGuard.class);

    /** Java 侧真实读写的表；清单与 sql/admin-handoff-migration.sql 保持一致。 */
    static final String[] REQUIRED_TABLES = {
            "t_user", "t_role", "t_permission", "t_role_permission", "t_user_role", "t_organization",
            "permission_audits",
            "model_connections", "model_connection_checks", "model_configs", "deleted_model_configs",
            "agent_step_models", "agent_system_prompts", "studio_skills", "comfy_workflows",
            "system_settings", "config_versions",
            "projects", "project_contents"
    };

    @Autowired
    private DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    void checkSchema() {
        List<String> missing = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String table : REQUIRED_TABLES) {
                if (!exists(meta, connection.getCatalog(), table)) {
                    missing.add(table);
                }
            }
        } catch (SQLException e) {
            logger.warn("数据库结构自检跳过：无法连接数据库（{}）", SchemaDiagnostics.rootMessage(e));
            return;
        }
        if (missing.isEmpty()) {
            logger.info("数据库结构自检通过：{} 张必需表齐全", REQUIRED_TABLES.length);
            return;
        }
        logger.error("数据库结构不完整，缺少 {} 张表：{}；请执行 {} 与 {}（均幂等，可重复执行），"
                        + "否则相关接口会返回“缺少表”错误",
                missing.size(), missing, SchemaDiagnostics.CONFIG_SCRIPT, SchemaDiagnostics.FULL_SCRIPT);
    }

    private boolean exists(DatabaseMetaData meta, String catalog, String table) throws SQLException {
        try (ResultSet rs = meta.getTables(catalog, null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
