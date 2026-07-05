/*
 * Function: HealthCheck DTO — API response model for component health checks
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * API response DTO for a component health check. Maps the
 * {@code ops.health_check} entity to the OpenAPI contract
 * {@code HealthCheck} schema.
 *
 * @param component    name of the component being checked
 * @param status       health status (HEALTHY, DEGRADED, UNHEALTHY)
 * @param latencyMs    latency of the health check in milliseconds
 * @param errorMessage error message if the check failed
 * @param lastCheckAt  UTC timestamp of the last check
 */
public record HealthCheckDto(
        String component,
        String status,
        @JsonProperty("latency_ms") Long latencyMs,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("last_check_at") OffsetDateTime lastCheckAt
) {
}
