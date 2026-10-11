package com.sakana.just_because_meme_understands_you.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 在显式配置的 MySQL 测试库中验证迁移执行一次并写入历史；
 * 未配置 MIGRATION_TEST_MYSQL_URL 时跳过，避免连接开发者本机数据库。
 */
class FlywayMigrationIntegrationTest {

    @Test
    void recordsAllAvailableMigrationsAndDoesNotRerunThem() throws Exception {
        String jdbcUrl = System.getenv("MIGRATION_TEST_MYSQL_URL");
        Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank(),
                "此集成测试只在专用 MySQL 测试库中运行");

        int successfulSqlMigrationsBefore = countSuccessfulSqlMigrations(jdbcUrl);

        Flyway flyway = Flyway.configure()
                .dataSource(
                        jdbcUrl,
                        System.getenv().getOrDefault("MIGRATION_TEST_MYSQL_USERNAME", "root"),
                        System.getenv().getOrDefault("MIGRATION_TEST_MYSQL_PASSWORD", ""))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("20261003")
                .load();

        MigrateResult firstRun = flyway.migrate();
        MigrateResult secondRun = flyway.migrate();

        assertEquals(0, secondRun.migrationsExecuted);
        int successfulSqlMigrationsAfter = countSuccessfulSqlMigrations(jdbcUrl);
        assertEquals(firstRun.migrationsExecuted,
                successfulSqlMigrationsAfter - successfulSqlMigrationsBefore);
    }

    private int countSuccessfulSqlMigrations(String jdbcUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl,
                System.getenv().getOrDefault("MIGRATION_TEST_MYSQL_USERNAME", "root"),
                System.getenv().getOrDefault("MIGRATION_TEST_MYSQL_PASSWORD", ""));
             Statement statement = connection.createStatement()) {
            try (ResultSet historyTable = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'")) {
                historyTable.next();
                if (historyTable.getInt(1) == 0) {
                    return 0;
                }
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND type = 'SQL'")) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
