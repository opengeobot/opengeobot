/*
 * Function: Robot DTO — API response model for robot entries
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * API response DTO for a registered robot. Maps the {@code robot_registry.robot}
 * entity plus its declared capabilities to the OpenAPI contract {@code Robot}
 * schema. Jackson serialises field names in snake_case globally.
 *
 * @param robotId      public identifier (ULID-based, prefixed with {@code rbt_})
 * @param name         human-friendly display name
 * @param modelId      identifier of the robot model
 * @param serialNumber unique hardware serial number
 * @param status       operational status (ONLINE, OFFLINE, BUSY, ERROR, MAINTENANCE)
 * @param orgId        owning organisation identifier
 * @param capabilities capabilities currently declared by the robot
 * @param lastSeenAt   UTC timestamp of the last heartbeat
 * @param metadata     arbitrary structured attributes
 * @param createdAt    UTC timestamp of registration
 * @param updatedAt    UTC timestamp of last update
 */
public record RobotDto(
        String robotId,
        String name,
        String modelId,
        String serialNumber,
        String status,
        String orgId,
        List<RobotCapabilityDto> capabilities,
        OffsetDateTime lastSeenAt,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
