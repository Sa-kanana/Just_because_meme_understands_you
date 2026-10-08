package com.sakana.just_because_meme_understands_you.db;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FlywayMigrationCatalogTest {

    /** 中文注释：当前必须随 Flyway 打包的六个增量迁移资源。 */
    private static final List<String> MIGRATIONS = List.of(
            "V2026100401__tag_follow.sql",
            "V2026100402__meme_reports.sql",
            "V2026100403__meme_knowledge.sql",
            "V2026100404__meme_trends.sql",
            "V2026100405__weekly_topics.sql",
            "V2026100501__community_safety.sql"
    );

    @Test
    void keepsAllIncrementalFeaturesInVersionedMigrationResources() {
        for (String migration : MIGRATIONS) {
            assertNotNull(getClass().getClassLoader().getResource("db/migration/" + migration), migration);
        }
    }
}
