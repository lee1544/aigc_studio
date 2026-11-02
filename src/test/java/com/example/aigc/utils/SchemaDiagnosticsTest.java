package com.example.aigc.utils;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 缺表/缺列/连接失败必须翻译成可执行提示，其它异常交回通用 500 兜底。 */
class SchemaDiagnosticsTest {

    @Test
    void explainsMissingTableFromNestedCause() {
        Exception error = new IllegalStateException("could not prepare statement",
                new SQLSyntaxErrorException("Table 'aigc_studio.projects' doesn't exist"));

        assertEquals("数据库结构未初始化：缺少表 projects，请执行 sql/config-tables-migration.sql（幂等）后重试",
                SchemaDiagnostics.hint(error));
    }

    @Test
    void explainsMissingColumnWithoutEntityAlias() {
        Exception error = new IllegalStateException("could not extract ResultSet",
                new SQLSyntaxErrorException("Unknown column 'user0_.permission_version' in 'field list'"));

        assertEquals("数据库结构未初始化：缺少列 permission_version，请执行 sql/admin-handoff-migration.sql（幂等）后重试",
                SchemaDiagnostics.hint(error));
    }

    @Test
    void explainsUnknownDatabase() {
        Exception error = new IllegalStateException("pool init failed",
                new SQLSyntaxErrorException("Unknown database 'aigc_studio'"));

        assertEquals("数据库 aigc_studio 不存在，请检查 DB_URL 配置", SchemaDiagnostics.hint(error));
    }

    @Test
    void explainsUnreachableDatabase() {
        Exception error = new IllegalStateException("pool init failed",
                new SQLException("Communications link failure"));

        assertEquals("数据库连接不可用，请检查 DB_URL、DB_USERNAME、DB_PASSWORD 与数据库服务状态",
                SchemaDiagnostics.hint(error));
    }

    @Test
    void leavesOtherFailuresToGenericHandler() {
        assertNull(SchemaDiagnostics.hint(new IllegalArgumentException("标题不能为空")));
        assertNull(SchemaDiagnostics.hint(new SQLException("Duplicate entry '1' for key 'PRIMARY'")));
    }

    @Test
    void rootMessagePrefersDeepestCause() {
        assertEquals("boom", SchemaDiagnostics.rootMessage(
                new IllegalStateException("outer", new RuntimeException("boom"))));
    }
}
