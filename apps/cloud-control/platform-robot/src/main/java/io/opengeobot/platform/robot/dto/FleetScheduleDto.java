/*
 * Function: FleetSchedule DTO — API response model for fleet schedule entries
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a fleet schedule entry. Maps the
 * {@code fleet.fleet_schedule} entity to the OpenAPI contract
 * {@code FleetSchedule} schema. Jackson serialises field names in snake_case
 * globally.
 *
 * @param scheduleId   public identifier (ULID-based, prefixed with {@code sch_})
 * @param missionId    public identifier of the scheduled mission
 * @param robotId      public identifier of the assigned robot
 * @param plannedStart UTC timestamp of the planned execution start
 * @param plannedEnd   UTC timestamp of the planned execution end
 * @param priority     scheduling priority (LOW, NORMAL, HIGH, URGENT)
 * @param status       schedule status (PENDING, APPROVED, ACTIVE, COMPLETED, CANCELLED)
 * @param createdAt    UTC timestamp when the schedule was created
 */
public record FleetScheduleDto(
        String scheduleId,
        String missionId,
        String robotId,
        OffsetDateTime plannedStart,
        OffsetDateTime plannedEnd,
        String priority,
        String status,
        OffsetDateTime createdAt
) {
}
