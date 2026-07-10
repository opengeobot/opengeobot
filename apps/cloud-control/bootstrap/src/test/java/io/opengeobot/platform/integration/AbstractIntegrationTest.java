/*
 * Function: Abstract base class for Testcontainers-based integration tests
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Abstract base class for real integration tests backed by a PostgreSQL
 * container (pgvector image matching production). Subclasses inherit a
 * fully wired Spring context with Flyway migrations applied against real
 * PostgreSQL instead of H2.
 *
 * <p>The {@code test} profile is activated so that non-datasource test
 * configuration (JWT secret, NATS disabled, OAuth2 exclusions) applies.
 * Datasource properties are injected dynamically from the container so
 * Flyway runs against the real database.
 *
 * <p>Uses the <b>singleton container pattern</b>: the PostgreSQL container
 * is started once in a static initializer and shared across all test classes
 * that extend this base. This avoids Spring context cache invalidation
 * issues that occur when each test class manages its own container lifecycle
 * (the cached context would retain the old container's JDBC URL after the
 * container is stopped).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // stringtype=unspecified lets the PostgreSQL JDBC driver send String
        // values for jsonb columns (e.g. outbox_event.payload) without an
        // explicit cast, which is required by MyBatis-Plus.
        String jdbcUrl = postgres.getJdbcUrl();
        String urlWithParam = jdbcUrl.contains("?")
                ? jdbcUrl + "&stringtype=unspecified"
                : jdbcUrl + "?stringtype=unspecified";
        registry.add("spring.datasource.url", () -> urlWithParam);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }
}
