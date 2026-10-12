package com.sakana.just_because_meme_understands_you.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 中文注释：验证标签分类迁移中的字段注释、历史关联回填和主标签约束。 */
class MemeTagTaxonomySchemaContractTest {

    private static final String MIGRATION_RESOURCE =
            "/db/migration/V2026101201__tag_taxonomy_and_primary.sql";

    @Test
    void migrationDocumentsColumnsSeedsEightyCoreTagsAndBackfillsOnePrimary() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(MIGRATION_RESOURCE)) {
            assertNotNull(input, "标签分类迁移必须打包到 classpath");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            for (String comment : List.of(
                    "COMMENT ''标签所属分类代码''",
                    "COMMENT ''分类内展示顺序，数值越小越靠前''",
                    "COMMENT ''是否为该梗的主分类标签，1是0否''",
                    "COMMENT ''主标签唯一约束辅助列，由应用在主标签关联中写入梗ID''")) {
                assertTrue(sql.contains(comment), "迁移中的列必须有中文 COMMENT：" + comment);
            }
            assertTrue(sql.contains("UPDATE meme_tag_relation"), "应回填已有标签关联");
            assertTrue(sql.contains("MIN(meme_tag_id)"), "存量主标签应按最小标签 ID 确定性选取");
            assertTrue(sql.contains("ADD COLUMN primary_meme_id BIGINT NULL"),
                    "唯一约束辅助列必须是普通可空列，避免依赖带级联外键的 meme_id 生成列");
            assertTrue(sql.contains("SET primary_meme_id = CASE WHEN is_primary = 1 THEN meme_id ELSE NULL END"),
                    "迁移必须将主标签梗ID写入辅助列并将普通标签清空");
            assertFalse(sql.contains("primary_meme_id BIGINT GENERATED ALWAYS AS"),
                    "不能为带级联外键的 meme_id 创建依赖生成列");
            assertTrue(sql.contains("uk_meme_tag_primary_per_meme"), "应限制每条梗最多一个主标签");
            assertTrue(sql.contains("information_schema.columns"), "迁移应识别部分执行后已存在的列");
            assertTrue(sql.contains("PREPARE"), "迁移 DDL 应支持在修复失败记录后安全重试");
            assertTrue(Pattern.compile("(?is)INSERT\\s+INTO\\s+tmp_standard_meme_tag.*?VALUES")
                    .matcher(sql).find(), "应创建标准标签清单");
            long standardTagCount = Pattern.compile("\\('[^']+', '(?:SOURCE|FORMAT|EMOTION|SCENARIO|CONTENT|ERA|PROPAGATION|TONE)', \\d+\\)")
                    .matcher(sql)
                    .results()
                    .count();
            assertEquals(80, standardTagCount, "首批标准标签应控制在 80 个");
            assertTrue(sql.contains("UNCATEGORIZED"), "旧标签应保留并进入待分类分组");
        }
    }

    @Test
    void bootstrapSchemaUsesTheSameOrdinaryPrimaryTagHelperColumn() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/db/mysql.sql")) {
            assertNotNull(input, "全量建库脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("primary_meme_id bigint null comment '主标签唯一约束辅助列，由应用在主标签关联中写入梗ID'"),
                    "全量建库脚本必须使用普通可空辅助列，与线上迁移保持一致");
            assertFalse(sql.contains("primary_meme_id bigint generated always as"),
                    "全量建库脚本不得再次创建依赖 meme_id 外键的生成列");
        }
    }
}
