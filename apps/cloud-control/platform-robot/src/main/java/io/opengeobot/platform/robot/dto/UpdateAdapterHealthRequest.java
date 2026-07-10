/*
 * Function: Update adapter health request DTO
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for updating the runtime health status of an adapter
 * compatibility entry. The {@code health_status} must be one of the platform
 * code-contract values (HEALTHY / DEGRADED / UNHEALTHY / UNKNOWN). The optional
 * {@code reason} is recorded in the audit log. Jackson serialises field names
 * in snake_case globally.
 */
public record UpdateAdapterHealthRequest(
        @NotBlank(message = "health_status must not be blank")
        String healthStatus,

        String reason
) {
}
