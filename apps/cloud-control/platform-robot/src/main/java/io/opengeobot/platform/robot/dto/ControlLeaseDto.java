/*
 * Function: Control lease DTO — API response model for F-MONITOR-001
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a robot control lease. Jackson serialises field names
 * in snake_case globally.
 */
public record ControlLeaseDto(
        String leaseId,
        String robotId,
        String holderUserId,
        String status,
        OffsetDateTime acquiredAt,
        OffsetDateTime expiresAt,
        OffsetDateTime releasedAt,
        String fencingToken,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
