package com.example.aigc.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库结构类异常的诊断。
 * <p>
 * 本项目 ddl-auto=none，表结构只由 sql/ 下的幂等脚本维护；部署库一旦漏执行迁移，
 * Hibernate/MySQL 只会抛出 “Table 'xx' doesn't exist” 之类的底层信息，最终被 500 兜底吞掉，
 * 调用方无法区分“代码缺陷”和“环境没初始化”。这里统一把这类异常翻译成可直接执行的提示。
 */
public final class SchemaDiagnostics {

    /** 配置类表补丁（config_versions、agent_*、comfy_workflows、projects 等），幂等可重复执行。 */
    public static final String CONFIG_SCRIPT = "sql/config-tables-migration.sql";
    /** 全量结构脚本（t_user 扩展列、权限审计、模型与积分等全部表），幂等可重复执行。 */
    public static final String FULL_SCRIPT = "sql/admin-handoff-migration.sql";

    private static final Pattern MISSING_TABLE =
            Pattern.compile("Table '(?:[^']*\\.)?([^']+)' doesn't exist");
    private static final Pattern MISSING_COLUMN =
            Pattern.compile("Unknown column '([^']+)'");
    private static final Pattern UNKNOWN_DATABASE =
            Pattern.compile("Unknown database '([^']+)'");

    private static final String[] CONNECTION_MARKERS = {
            "Communications link failure",
            "Could not create connection",
            "Access denied for user",
            "Connection refused",
            "No suitable driver"
    };

    private SchemaDiagnostics() {
    }

    /**
     * 把异常链里的数据库结构/连接问题翻译成面向调用方的提示。
     *
     * @return 提示文本；不属于数据库结构或连接问题时返回 null，由调用方走通用 500 兜底
     */
    public static String hint(Throwable error) {
        String table = find(MISSING_TABLE, error);
        if (table != null) {
            return "数据库结构未初始化：缺少表 " + table + "，请执行 " + CONFIG_SCRIPT + "（幂等）后重试";
        }
        String column = find(MISSING_COLUMN, error);
        if (column != null) {
            return "数据库结构未初始化：缺少列 " + unqualify(column) + "，请执行 " + FULL_SCRIPT + "（幂等）后重试";
        }
        String database = find(UNKNOWN_DATABASE, error);
        if (database != null) {
            return "数据库 " + database + " 不存在，请检查 DB_URL 配置";
        }
        if (containsConnectionMarker(error)) {
            return "数据库连接不可用，请检查 DB_URL、DB_USERNAME、DB_PASSWORD 与数据库服务状态";
        }
        return null;
    }

    /** 异常链最内层的错误信息，只用于日志定位，不回显给调用方。 */
    public static String rootMessage(Throwable error) {
        String message = error.getMessage();
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (current.getMessage() != null) {
                message = current.getMessage();
            }
        }
        return message;
    }

    private static String find(Pattern pattern, Throwable error) {
        for (Throwable current = error; current != null; ) {
            String message = current.getMessage();
            if (message != null) {
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }

    private static boolean containsConnectionMarker(Throwable error) {
        for (Throwable current = error; current != null; ) {
            String message = current.getMessage();
            if (message != null) {
                for (String marker : CONNECTION_MARKERS) {
                    if (message.contains(marker)) {
                        return true;
                    }
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /** Hibernate 报的列名带实体别名（如 user0_.permission_version），提示里只留列名。 */
    private static String unqualify(String column) {
        int dot = column.lastIndexOf('.');
        return dot < 0 ? column : column.substring(dot + 1);
    }
}
