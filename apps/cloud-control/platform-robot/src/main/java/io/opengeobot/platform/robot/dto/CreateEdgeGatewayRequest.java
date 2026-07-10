/*
 * Function: Create edge gateway request DTO
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

/**
 * Request body for registering a new edge gateway. Jackson serialises field
 * names in snake_case globally.
 */
public record CreateEdgeGatewayRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @NotBlank(message = "org_id must not be blank")
        String orgId,

        String boundRobotId,

        String runtimeVersion,

        String certificateFingerprint,

        OffsetDateTime certificateExpiresAt
) {
}
