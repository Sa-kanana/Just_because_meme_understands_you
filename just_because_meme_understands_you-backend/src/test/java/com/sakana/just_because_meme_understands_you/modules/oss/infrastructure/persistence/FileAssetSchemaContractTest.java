package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 中文注释：验证文件资产生命周期迁移中的表、约束、索引和列注释契约。 */
class FileAssetSchemaContractTest {

    @Test
    void migrationDefinesAllLifecycleTablesAndChineseColumnComments() throws Exception {
        InputStream migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026101101__file_asset_lifecycle.sql");
        assertNotNull(migration, "文件资产生命周期迁移必须存在");
        if (migration == null) {
            return;
        }

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String table : List.of(
                "file_asset",
                "file_asset_ref",
                "file_delete_task",
                "file_orphan_candidate",
                "file_asset_backfill_state")) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "迁移必须创建表 " + table);
        }

        assertTrue(sql.contains("UNIQUE KEY uk_file_asset_object_key (object_key)"),
                "对象 key 必须全局唯一");
        assertTrue(sql.contains("UNIQUE KEY uk_file_asset_ref_business_field (biz_type, biz_id, field_key)"),
                "同一个业务字段只能有一个当前资产引用");
        assertTrue(sql.contains("CONSTRAINT fk_file_asset_ref_asset"), "资产引用必须通过外键关联资产表");
        assertTrue(sql.contains("idx_file_asset_status"), "资产状态和过期时间必须支持扫描");
        assertTrue(sql.contains("idx_file_delete_due"), "删除队列必须支持按状态和执行时间领取");
        assertTrue(sql.contains("idx_file_orphan_review"), "孤儿候选必须支持复查扫描");
        assertTrue(sql.contains("idx_file_backfill_status"), "回填任务必须支持状态和租约查询");

        for (String line : sql.lines().toList()) {
            String column = line.strip();
            if (column.matches("[a-z][a-zA-Z0-9_]*\\s+.*")) {
                assertTrue(column.contains("COMMENT '"), "数据库列必须有中文 COMMENT：" + column);
            }
        }
    }
}
