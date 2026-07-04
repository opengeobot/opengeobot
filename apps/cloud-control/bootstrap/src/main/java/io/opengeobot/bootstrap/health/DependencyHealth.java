/*
 * Function: DependencyHealth response DTO — health of a single critical dependency
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.bootstrap.health;

import io.opengeobot.platform.common.health.ServiceHealthState;

/**
 * Health of a single critical dependency (e.g. postgresql, nats).
 * Field names are serialised in snake_case to match the OpenAPI contract.
 */
public record DependencyHealth(
        String name,
        ServiceHealthState state,
        String detail
) {
}
