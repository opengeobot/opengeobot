/*
 * Function: Flyway migration file validation tests — verifies naming conventions and schema/table contents
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.bootstrap.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Flyway migration files exist, follow Flyway naming conventions,
 * and declare the expected domain schemas and common tables.
 *
 * <p>This is a static, file-content validation that does not require a running
 * database, so it can run in the standard H2 test profile without Testcontainers.
 */
class FlywayMigrationTest {

    @Test
    void migrationFilesExistAndFollowNamingConvention() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath:db/migration/V*.sql");

        assertTrue(resources.length >= 2, "At least 2 migration files should exist");

        for (var resource : resources) {
            var filename = resource.getFilename();
            assertNotNull(filename);
            assertTrue(filename.matches("V\\d+__\\w+\\.sql"),
                "Migration file " + filename + " should follow Flyway naming convention V{version}__{description}.sql");
        }
    }

    @Test
    void v1MigrationCreatesSchemas() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath:db/migration/V1__*.sql");
        assertEquals(1, resources.length, "Exactly one V1 migration should exist");

        var content = resources[0].getContentAsString(StandardCharsets.UTF_8);

        // Verify all required schemas are created
        assertTrue(content.contains("CREATE SCHEMA IF NOT EXISTS platform_iam"), "platform_iam schema missing");
        assertTrue(content.contains("CREATE SCHEMA IF NOT EXISTS platform_governance"), "platform_governance schema missing");
        assertTrue(content.contains("CREATE SCHEMA IF NOT EXISTS robot_registry"), "robot_registry schema missing");
        assertTrue(content.contains("CREATE SCHEMA IF NOT EXISTS mission"), "mission schema missing");
        assertTrue(content.contains("CREATE SCHEMA IF NOT EXISTS fleet"), "fleet schema missing");
    }

    @Test
    void v2MigrationCreatesCommonTables() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath:db/migration/V2__*.sql");
        assertEquals(1, resources.length, "Exactly one V2 migration should exist");

        var content = resources[0].getContentAsString(StandardCharsets.UTF_8);

        // Verify common tables are created
        assertTrue(content.contains("outbox_event"), "outbox_event table missing");
        assertTrue(content.contains("inbox_event"), "inbox_event table missing");
        assertTrue(content.contains("sys_operation_audit"), "sys_operation_audit table missing");
        assertTrue(content.contains("sys_idempotency_record"), "sys_idempotency_record table missing");

        // Verify unique constraints
        assertTrue(content.contains("UNIQUE"), "Unique constraints missing");
    }
}
