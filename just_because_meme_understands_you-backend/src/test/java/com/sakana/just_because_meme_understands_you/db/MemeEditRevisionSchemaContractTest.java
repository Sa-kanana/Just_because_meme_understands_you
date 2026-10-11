package com.sakana.just_because_meme_understands_you.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 中文注释：验证梗修改稿迁移表的字段注释和审核查询索引。 */
class MemeEditRevisionSchemaContractTest {

    private static final String MIGRATION_RESOURCE =
            "/db/migration/V2026101003__meme_edit_revisions.sql";

    @Test
    void migrationDocumentsEveryColumnAndAddsReviewIndexes() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(MIGRATION_RESOURCE)) {
            assertNotNull(input, "修改稿迁移脚本必须打包到 classpath");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS meme_edit_revision"));
            for (String column : List.of(
                    "id", "meme_id", "editor_user_id", "base_content_hash", "content_json",
                    "status", "review_note", "reviewed_by", "created_at", "reviewed_at")) {
                Pattern columnComment = Pattern.compile(
                        "(?im)^\\s*" + Pattern.quote(column) + "\\s+[^,\\r\\n]+\\s+COMMENT\\s+'[^']+'");
                assertTrue(columnComment.matcher(sql).find(),
                        "字段 " + column + " 必须有中文 COMMENT");
            }

            assertTrue(sql.contains("DEFAULT 'PENDING'"), "新修改稿默认进入待审核状态");
            assertTrue(Pattern.compile("(?is)KEY\\s+idx_meme_edit_revision_meme_status\\s*\\(\\s*meme_id\\s*,\\s*status\\s*")
                    .matcher(sql).find(), "应支持按梗和审核状态检查待审修改稿");
            assertTrue(Pattern.compile("(?is)KEY\\s+idx_meme_edit_revision_status_created\\s*\\(\\s*status\\s*,\\s*created_at\\s*,\\s*id\\s*")
                    .matcher(sql).find(), "应支持按待审状态和提交时间分页");
        }
    }
}
