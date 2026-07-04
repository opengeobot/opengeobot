/*
 * Function: Health controller — liveness, readiness and version info endpoints
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.bootstrap.health;

import io.opengeobot.platform.common.error.ErrorCode;
import io.opengeobot.platform.common.error.ErrorEnvelope;
import io.opengeobot.platform.common.health.ServiceHealthState;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Implements the deployment health contract: {@code /health/live},
 * {@code /health/ready} and {@code /health/info}. Liveness always returns 200.
 * Readiness verifies the database connection and returns 503 with a
 * ProblemDetails body when the database is unreachable.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private static final String DEPENDENCY_POSTGRESQL = "postgresql";

    private final DataSource dataSource;
    private final String serviceName;
    private final String serviceVersion;

    public HealthController(DataSource dataSource,
                           @Value("${spring.application.name:cloud-control}") String serviceName,
                           @Value("${app.version:0.1.0-SNAPSHOT}") String serviceVersion) {
        this.dataSource = dataSource;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
    }

    @GetMapping("/live")
    public ServiceHealth live() {
        return new ServiceHealth(
                serviceName,
                ServiceHealthState.HEALTHY,
                Instant.now(),
                List.of(),
                serviceVersion
        );
    }

    @GetMapping("/ready")
    public ResponseEntity<Object> ready(HttpServletRequest request) {
        boolean dbReady = checkDatabase();

        if (dbReady) {
            ServiceHealth health = new ServiceHealth(
                    serviceName,
                    ServiceHealthState.HEALTHY,
                    Instant.now(),
                    List.of(new DependencyHealth(DEPENDENCY_POSTGRESQL, ServiceHealthState.HEALTHY, null)),
                    serviceVersion
            );
            return ResponseEntity.ok(health);
        }

        ErrorEnvelope error = ErrorEnvelope.of(
                ErrorCode.HEALTH_NOT_READY,
                resolveTraceId(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(error);
    }

    @GetMapping("/info")
    public VersionInfo info() {
        return new VersionInfo(
                serviceName,
                serviceVersion,
                null,
                null,
                Runtime.version().feature(),
                SpringBootVersion.getVersion()
        );
    }

    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            return "n/a";
        }
        return traceId;
    }
}
