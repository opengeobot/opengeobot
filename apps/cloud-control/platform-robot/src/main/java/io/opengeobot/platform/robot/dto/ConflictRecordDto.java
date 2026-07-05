/*
 * Function: ConflictRecord DTO — API response model for fleet conflicts
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API response DTO for a detected fleet schedule conflict. Maps the
 * {@code fleet.conflict_record} entity to the OpenAPI contract
 * {@code ConflictRecord} schema. Jackson serialises field names in snake_case
 * globally.
 *
 * @param conflictId    public identifier (ULID-based, prefixed with {@code cfl_})
 * @param scheduleIds   public identifiers of the conflicting schedules
 * @param conflictType  canonical conflict type (TIME_OVERLAP, ROBOT_BUSY, etc.)
 * @param description   human-readable description of the conflict
 * @param detectedAt    UTC timestamp when the conflict was detected
 * @param resolvedAt    UTC timestamp when the conflict was resolved, if applicable
 * @param resolution    resolution strategy applied (REORDER, REASSIGN, CANCEL)
 * @param status        whether the conflict is OPEN or RESOLVED
 */
public record ConflictRecordDto(
        String conflictId,
        List<String> scheduleIds,
        String conflictType,
        String description,
        OffsetDateTime detectedAt,
        OffsetDateTime resolvedAt,
        String resolution,
        String status
) {
}
