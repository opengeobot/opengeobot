/*
 * Function: ServiceHealth response DTO — matches the SM-SERVICE-HEALTH contract schema
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.bootstrap.health;

import io.opengeobot.platform.common.health.ServiceHealthState;

import java.time.Instant;
import java.util.List;

/**
 * Health snapshot returned by the health endpoints.
 * Field names are serialised in snake_case to match the OpenAPI contract.
 */
public record ServiceHealth(
        String serviceName,
        ServiceHealthState state,
        Instant lastCheckAt,
        List<DependencyHealth> dependencies,
        String version
) {
}
