/*
 * Function: FailoverEvent DTO — API response model for fleet failover events
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a fleet failover event. Maps the
 * {@code fleet.failover_event} entity to the OpenAPI contract
 * {@code FailoverEvent} schema. Jackson serialises field names in snake_case
 * globally.
 *
 * @param failoverId     public identifier (ULID-based, prefixed with {@code fov_})
 * @param robotId        public identifier of the source robot
 * @param missionId      public identifier of the transferred mission
 * @param reason         machine-readable reason for the failover
 * @param targetRobotId  public identifier of the robot taking over
 * @param status         failover status (INITIATED, COMPLETED, FAILED)
 * @param occurredAt     UTC timestamp when the failover was initiated
 */
public record FailoverEventDto(
        String failoverId,
        String robotId,
        String missionId,
        String reason,
        String targetRobotId,
        String status,
        OffsetDateTime occurredAt
) {
}
