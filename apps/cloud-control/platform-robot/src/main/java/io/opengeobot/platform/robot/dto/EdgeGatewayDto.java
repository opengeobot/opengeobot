/*
 * Function: Edge gateway DTO — API response model for F-EDGE-001
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for an edge gateway. Jackson serialises field names in
 * snake_case globally.
 */
public record EdgeGatewayDto(
        String gatewayId,
        String name,
        String orgId,
        String status,
        String certificateFingerprint,
        OffsetDateTime certificateExpiresAt,
        String runtimeVersion,
        String boundRobotId,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
