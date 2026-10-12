package com.sakana.just_because_meme_understands_you.db;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationCatalogTest {

    /** 中文注释：当前必须随 Flyway 打包的全部增量迁移资源。 */
    private static final List<String> MIGRATIONS = List.of(
            "V2026100401__tag_follow.sql",
            "V2026100402__meme_reports.sql",
            "V2026100403__meme_knowledge.sql",
            "V2026100404__meme_trends.sql",
            "V2026100405__weekly_topics.sql",
            "V2026100501__community_safety.sql",
            "V2026100801__remove_meme_knowledge_correction.sql",
            "V2026100802__integration_outbox_event.sql",
            "V2026100901__database_schema_clarity.sql",
            "V2026100902__comment_meme_report_archive_columns.sql",
            "V2026100903__database_integrity_and_index_cleanup.sql",
            "V2026100904__denormalized_counter_cleanup.sql",
            "V2026100905__retire_unused_meme_knowledge_card.sql",
            "V2026100906__ai_chat_message_status.sql",
            "V2026100907__comment_notification_outbox.sql",
            "V2026100908__shared_user_roles.sql",
            "V2026101001__reliable_admin_knowledge_indexing.sql",
            "V2026101002__user_soft_delete.sql",
            "V2026101003__meme_edit_revisions.sql",
            "V2026101101__file_asset_lifecycle.sql",
            "V2026101102__file_asset_temp_expiry_index.sql",
            "V2026101201__tag_taxonomy_and_primary.sql",
            "V2026101202__sensitive_word_moderation_architecture.sql",
            "V2026101203__meme_vector_reconciliation_jobs.sql",
            "V2026101204__user_public_uuid.sql",
            "V2026101205__remove_untracked_user_public_uuid.sql"
    );

    /** 中文注释：将动态 SQL 字符串中的双单引号还原为实际执行 SQL，避免断言依赖转义写法。 */
    private static String normalizePreparedSql(String sql) {
        return sql.replace("''", "'");
    }

    @Test
    void assignsEveryMigrationAUniqueVersion() {
        var versions = MIGRATIONS.stream()
                .map(migration -> migration.substring(1, migration.indexOf("__")))
                .toList();

        assertEquals(versions.size(), new HashSet<>(versions).size(), "所有 Flyway 迁移版本号必须唯一");
    }

    @Test
    void keepsAllIncrementalFeaturesInVersionedMigrationResources() {
        for (String migration : MIGRATIONS) {
            assertNotNull(getClass().getClassLoader().getResource("db/migration/" + migration), migration);
        }
    }

    @Test
    void preservesAppliedPublicUuidMigrationAndAuditsItsCleanup() throws Exception {
        var appliedMigration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026101204__user_public_uuid.sql");
        assertNotNull(appliedMigration, "数据库中已执行的 UUID 迁移必须保留在源码迁移目录");
        String appliedSql;
        try (appliedMigration) {
            appliedSql = new String(appliedMigration.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(appliedSql.contains("LOWER(UUID())"), "历史迁移必须与已执行的 UUID 回填行为一致");
        assertTrue(appliedSql.contains("uk_user_public_uuid"), "历史迁移必须记录 UUID 唯一索引的来源");

        var cleanupMigration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026101205__remove_untracked_user_public_uuid.sql");
        assertNotNull(cleanupMigration, "误执行的 UUID 列必须通过后续迁移清理");
        String cleanupSql;
        try (cleanupMigration) {
            cleanupSql = new String(cleanupMigration.readAllBytes(), StandardCharsets.UTF_8);
        }
        int dropIndex = cleanupSql.indexOf("DROP INDEX uk_user_public_uuid ON user");
        int dropColumn = cleanupSql.indexOf("DROP COLUMN public_uuid");
        assertTrue(dropIndex >= 0 && dropColumn > dropIndex, "必须先删除唯一索引，再删除 UUID 列");
        assertTrue(cleanupSql.contains("information_schema"), "清理迁移必须兼容列或索引已不存在的状态");
    }

    @Test
    void memeVectorReconciliationMigrationCommentsEveryColumnAndRestrictsActiveJobs() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026101203__meme_vector_reconciliation_jobs.sql");
        assertNotNull(migration, "梗向量校准任务迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }
        String normalizedSql = sql.replace("`", "");
        assertTrue(normalizedSql.contains("CREATE TABLE IF NOT EXISTS meme_vector_reconciliation_job"), "迁移必须创建可恢复任务表");
        assertTrue(normalizedSql.contains("UNIQUE KEY uq_mvrj_active_slot (active_slot)"), "数据库必须限制同时只有一个活动任务");
        assertTrue(normalizedSql.contains("KEY idx_mvrj_status_lease (status, lease_until, id)"), "任务领取查询需有状态和租约索引");
        assertTrue(sql.contains("ENGINE=InnoDB"), "任务游标与 outbox 事件需要同事务提交");

        List<String> columns = List.of(
                "id", "active_slot", "status", "phase", "published_cursor", "vector_cursor",
                "published_scanned", "upsert_queued", "vector_scanned", "removal_queued",
                "created_by", "lease_owner", "lease_until", "failure_stage",
                "created_at", "updated_at", "started_at", "finished_at");
        for (String column : columns) {
            assertTrue(sql.matches("(?is).*`" + column + "`\\s+[^,\\n]+COMMENT\\s+'[^']+'.*"),
                    "每个任务字段都必须包含中文 COMMENT: " + column);
        }
    }

    @Test
    void userSoftDeleteMigrationAddsACommentedRecoverableDeletionMarker() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026101002__user_soft_delete.sql");
        assertNotNull(migration, "用户软删除迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }
        sql = normalizePreparedSql(sql);

        assertTrue(sql.contains("ALTER TABLE `user`"), "迁移必须只扩展用户表");
        assertTrue(sql.contains("ADD COLUMN deleted_at DATETIME NULL"), "软删除标记需要可空时间字段");
        assertTrue(sql.contains("COMMENT '逻辑删除时间，NULL表示未删除'"), "新增字段必须包含中文注释");
        assertTrue(sql.contains("idx_user_deleted_create_time"), "普通和注销账号分页都需要索引支持");
        assertTrue(sql.contains("information_schema.COLUMNS"), "已存在软删除字段时必须跳过重复添加");
        assertTrue(sql.contains("information_schema.STATISTICS"), "已存在软删除索引时必须跳过重复添加");
    }

    @Test
    void stoppedAssistantMessageStatusHasACommentedFlywayColumn() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026100906__ai_chat_message_status.sql");
        assertNotNull(migration, "AI 消息状态迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }
        sql = normalizePreparedSql(sql);
        assertTrue(sql.contains("ADD COLUMN status VARCHAR(16)"));
        assertTrue(sql.contains("COMMENT '消息状态：COMPLETED已完成、STOPPED用户停止、FAILED生成失败'"));
        assertTrue(sql.contains("information_schema.COLUMNS"), "已存在消息状态字段时必须跳过重复添加");
    }

    @Test
    void commentNotificationOutboxMigrationAddsCommentedIdempotencyKey() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026100907__comment_notification_outbox.sql");
        assertNotNull(migration, "评论通知 Outbox 幂等迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }
        sql = normalizePreparedSql(sql);
        assertTrue(sql.contains("ADD COLUMN source_event_id BIGINT UNSIGNED NULL"));
        assertTrue(sql.contains("COMMENT '来源 Outbox 事件 ID；为空表示非 Outbox 通知'"));
        assertTrue(sql.contains("UNIQUE KEY uk_user_notification_source_event (source_event_id)"));
        assertTrue(sql.contains("information_schema.COLUMNS"), "已存在来源字段时必须跳过重复添加");
        assertTrue(sql.contains("information_schema.STATISTICS"), "已存在唯一索引时必须跳过重复添加");
    }

    @Test
    void sharedUserRolesMigrationBackfillsLegacyRolesBeforeDroppingTheUserRoleColumn() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026100908__shared_user_roles.sql");
        assertNotNull(migration, "共享用户角色迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }
        sql = normalizePreparedSql(sql);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `role`"), "必须创建角色表");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS user_role"), "必须创建用户角色关联表");
        for (String role : List.of("USER", "ADMIN", "SUPER_ADMIN")) {
            assertTrue(sql.contains("'" + role + "'"), "必须写入角色代码 " + role);
        }
        assertTrue(sql.contains("WHEN 'ROLE_ADMIN' THEN 'ADMIN'"), "旧管理员角色必须迁移为 ADMIN");
        assertTrue(sql.contains("ELSE 'USER'"), "空值和未知旧角色必须以普通用户权限迁移");
        assertTrue(sql.contains("来源字段 user.role") && sql.contains("目标字段 role.code"),
                "角色复制必须说明来源字段和目标字段");
        assertTrue(sql.contains("来源字段 user.id") && sql.contains("目标字段 user_role.user_id"),
                "用户关联复制必须说明来源字段和目标字段");
        assertTrue(sql.contains("来源字段 role.id") && sql.contains("目标字段 user_role.role_id"),
                "角色关联复制必须说明来源字段和目标字段");
        assertTrue(sql.contains("DROP COLUMN role"), "完成回填后必须移除 user.role 单字段");
        assertTrue(sql.contains("COMMENT '"), "所有新增列都必须有中文注释");
        assertTrue(sql.contains("information_schema.COLUMNS"), "当前 schema 缺少旧 role 字段时必须跳过历史回填和删列");
    }

    @Test
    void reliableKnowledgeMigrationDocumentsSha256BackfillAndDurableIndexEvents() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026101001__reliable_admin_knowledge_indexing.sql");
        assertNotNull(migration, "管理员知识可靠索引迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String column : List.of("content_sha256", "index_status", "index_revision", "index_task_id", "index_error")) {
            assertTrue(sql.contains("ADD COLUMN " + column), "迁移必须添加 " + column);
        }
        assertTrue(sql.contains("SHA2(") && sql.contains(", 256)"), "回填必须使用 MySQL 9.6 支持的 SHA-256");
        String executableSql = sql.replaceAll("(?m)^\\s*--.*$", "");
        assertFalse(executableSql.matches("(?is).*\\bMD5\\s*\\(.*"), "迁移不得调用 MySQL 9.6 已移除的 MD5 函数");
        assertTrue(sql.contains("UNIQUE KEY uk_admin_knowledge_content_sha256"), "正文指纹必须由数据库约束唯一");
        assertTrue(sql.contains("information_schema.COLUMNS"), "迁移重跑时必须识别已部分创建的字段");
        assertTrue(sql.contains("REGEXP_REPLACE"), "历史指纹回填需规范化空白");
        assertTrue(sql.contains("KNOWLEDGE_INDEX_UPSERT") && sql.contains("KNOWLEDGE_INDEX_DELETE"),
                "历史记录必须通过 outbox 重建或清理向量");
        assertTrue(sql.contains("来源字段") && sql.contains("目标字段"), "迁移复制字段需说明来源与目标");
        assertTrue(sql.contains("COMMENT ''标准化正文 SHA-256 去重指纹''"), "新增字段必须带中文列注释");
    }

    @Test
    void bootstrapSchemaAndMemeSeedUseRoleAssociationsInsteadOfUserRoleColumn() throws Exception {
        var schemaResource = getClass().getClassLoader().getResourceAsStream("db/mysql.sql");
        assertNotNull(schemaResource, "全量建库脚本必须存在");
        String schema;
        try (schemaResource) {
            schema = new String(schemaResource.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(schema.contains("create table `role`"), "全量建库脚本必须创建角色表");
        assertTrue(schema.contains("create table user_role"), "全量建库脚本必须创建用户角色关联表");
        int userTableStart = schema.indexOf("create table user\n");
        int userTableEnd = schema.indexOf("comment '用户基础信息表'", userTableStart);
        assertTrue(userTableStart >= 0 && userTableEnd > userTableStart, "用户表定义必须存在");
        assertFalse(schema.substring(userTableStart, userTableEnd).matches("(?is).*\\brole\\s+varchar\\b.*"),
                "user 表不得再保存单值 role 字段");

        var seedResource = getClass().getClassLoader().getResourceAsStream("db/seed_memes_200_20261004.sql");
        assertNotNull(seedResource, "梗数据初始化脚本必须存在");
        String seed;
        try (seedResource) {
            seed = new String(seedResource.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(seed.contains("`meme`.`user_role`") && seed.contains("`meme`.`role`"),
                "梗数据脚本应通过关联表选择管理员账号");
        assertFalse(seed.contains("user.role ="), "梗数据脚本不得再访问 user.role");
    }

    @Test
    void databaseSchemaClarityMigrationPreservesHistoryAndAddsIntegrityRules() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026100901__database_schema_clarity.sql");
        assertNotNull(migration, "数据库结构清晰化迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS meme_report_archive LIKE meme_report"), "旧举报表必须归档保留");
        assertTrue(sql.contains("DROP TABLE meme_report;"), "完成归档后不得继续保留旧业务表");
        assertTrue(sql.contains("fk_meme_tag_relation_meme"), "梗标签关联必须约束梗主键");
        assertTrue(sql.contains("fk_meme_tag_relation_tag"), "梗标签关联必须约束标签主键");
        assertTrue(sql.contains("UNIQUE KEY uk_user_meme (user_id, meme_id)"), "点赞与收藏必须按用户和梗唯一");
        assertTrue(sql.contains("来源字段") && sql.contains("目标字段"), "跨表字段迁移需逐项注明来源与目标");
        assertTrue(!sql.contains("DROP TABLE meme_report_archive"), "归档举报不得被删除");
    }

    @Test
    void databasePreflightScriptIsReadOnlyAndExplainsEverySelectedField() throws Exception {
        var script = getClass().getClassLoader().getResourceAsStream("db/database_preflight.sql");
        assertNotNull(script, "数据库迁移预检脚本必须存在");

        String sql;
        try (script) {
            sql = new String(script.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("meme_tag_relation"), "预检必须覆盖梗标签孤儿关系");
        assertTrue(sql.contains("user_like"), "预检必须覆盖点赞软删除重复行");
        assertTrue(sql.contains("user_favorite"), "预检必须覆盖收藏软删除重复行");
        assertTrue(sql.contains("字段说明"), "每个查询输出字段都需要中文说明");
        assertTrue(sql.lines().noneMatch(line -> line.stripLeading().matches("(?i)^(INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE)\\b.*")),
                "预检脚本只能读取数据，不能修改数据库");
    }

    @Test
    void databaseReconciliationScriptIsReadOnlyAndCoversDenormalizedCounts() throws Exception {
        var script = getClass().getClassLoader().getResourceAsStream("db/database_reconciliation.sql");
        assertNotNull(script, "冗余计数对账脚本必须存在");

        String sql;
        try (script) {
            sql = new String(script.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String counter : List.of("meme.likes", "meme.comments", "user_stats", "user_favorite_folder")) {
            assertTrue(sql.contains(counter), "对账脚本必须覆盖冗余计数来源: " + counter);
        }
        assertTrue(sql.contains("字段说明"), "每个查询输出字段都需要中文说明");
        assertTrue(sql.lines().noneMatch(line -> line.stripLeading().matches("(?i)^(INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE)\\b.*")),
                "对账脚本只能读取数据，不能修改数据库");
    }

    @Test
    void archiveCommentMigrationDocumentsEveryColumn() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026100902__comment_meme_report_archive_columns.sql");
        assertNotNull(migration, "归档举报字段注释迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("ALTER TABLE meme_report_archive"), "必须更新现有归档表");
        assertTrue(sql.contains("COMMENT '举报记录主键'"), "主键需要中文注释");
        assertTrue(sql.contains("COMMENT '举报人用户ID'"), "举报人字段需要中文注释");
        assertTrue(sql.contains("COMMENT = '历史梗举报记录归档，仅用于审计查询'"), "归档表需要表注释");
    }

    @Test
    void databaseHardeningMigrationGuardsReferencesAndRemovesRedundantIndexes() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026100903__database_integrity_and_index_cleanup.sql");
        assertNotNull(migration, "数据库结构加固迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String index : List.of(
                "idx_status_views", "idx_status_release_time", "idx_meme_user_id",
                "idx_from_user", "idx_to_user", "idx_user_folder", "idx_report_meme")) {
            assertTrue(sql.contains(index), "必须清理重复或可覆盖索引: " + index);
        }
        for (String foreignKey : List.of(
                "fk_meme_resource_meme", "fk_meme_daily_snapshot_meme",
                "fk_meme_comment_images_comment", "fk_ai_chat_message_session",
                "fk_user_auth_user", "fk_user_stats_user")) {
            assertTrue(sql.contains(foreignKey), "必须包含同模块外键: " + foreignKey);
        }
        assertTrue(sql.contains("SIGNAL SQLSTATE"), "其他外键孤儿数据必须使迁移明确失败");
        assertTrue(sql.contains("ON DELETE RESTRICT"), "外键不得隐式级联删除业务记录");
        assertTrue(sql.contains("idx_meme_tag_name"), "标签名称应有普通查询索引");
        assertTrue(sql.contains("meme.status = 1"), "发布梗数必须按已发布状态对账");
        assertTrue(!sql.contains("uk_meme_tag_name"), "不得擅自限制标签名称唯一");
        assertTrue(!sql.contains("uk_admin_knowledge_content_hash"), "不得擅自限制知识哈希唯一");
        assertTrue(sql.lines().filter(line -> line.stripLeading().startsWith("DELETE "))
                        .allMatch(line -> line.stripLeading().startsWith("DELETE orphan_stats")),
                "迁移仅允许删除已完整归档的孤儿用户统计记录");
    }

    @Test
    void databaseHardeningMigrationArchivesOrphanUserStatsBeforeRemovingTheLiveOrphan() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026100903__database_integrity_and_index_cleanup.sql");
        assertNotNull(migration, "数据库加固迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        int archiveTableIndex = sql.indexOf("CREATE TABLE IF NOT EXISTS user_stats_orphan_archive");
        int archiveRowIndex = sql.indexOf("INSERT IGNORE INTO user_stats_orphan_archive");
        int removeOrphanIndex = sql.indexOf("DELETE orphan_stats");
        int addForeignKeyIndex = sql.indexOf("'user_stats', 'fk_user_stats_user'");

        assertTrue(archiveTableIndex >= 0, "孤儿统计行必须有单独归档表");
        assertTrue(archiveRowIndex > archiveTableIndex, "必须先创建归档表再复制孤儿统计行");
        assertTrue(removeOrphanIndex > archiveRowIndex, "必须先归档再从在线表移除孤儿统计行");
        assertTrue(addForeignKeyIndex > removeOrphanIndex, "清除孤儿引用后才能添加用户外键");
        assertTrue(sql.contains("来源字段 user_stats.user_id") && sql.contains("目标字段 user_stats_orphan_archive.source_user_id"),
                "孤儿记录归档需注明来源字段和目标字段");
    }

    @Test
    void counterCleanupMigrationReconcilesCommentsBeforeDroppingPublishedMemeCount() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026100904__denormalized_counter_cleanup.sql");
        assertNotNull(migration, "冗余计数清理迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("UPDATE meme AS target_meme"), "评论计数需写回梗主表");
        assertTrue(sql.contains("FROM meme_comment AS source_comment"), "评论明细是评论计数来源");
        assertTrue(sql.contains("target_meme.comments"), "迁移需校准 meme.comments");
        assertTrue(sql.contains("DROP COLUMN meme_count"), "用户发布数应从冗余统计字段移除");
        assertTrue(sql.contains("来源字段 meme_comment.meme_id")
                        && sql.contains("目标字段 meme.comments"),
                "评论计数迁移需注明来源与目标字段");
    }

    @Test
    void unusedMemeKnowledgeCardMigrationRemovesTheOptionalTable() throws Exception {
        var migration = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V2026100905__retire_unused_meme_knowledge_card.sql");
        assertNotNull(migration, "停用梗资料卡表迁移必须存在");

        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("DROP TABLE IF EXISTS meme_knowledge_card"),
                "该空壳功能已停用，迁移应清理其专用表");
        assertFalse(sql.contains("CREATE TABLE meme_knowledge_card"),
                "停用迁移不得重新创建资料卡表");
    }
}
