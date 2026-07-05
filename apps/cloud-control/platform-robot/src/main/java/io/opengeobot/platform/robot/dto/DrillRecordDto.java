/*
 * Function: DrillRecord DTO — API response model for disaster recovery drills
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a disaster recovery drill record. Maps the
 * {@code recovery.drill_record} entity to the OpenAPI contract
 * {@code DrillRecord} schema. Jackson serialises field names in snake_case
 * globally.
 *
 * @param drillId     public identifier (ULID-based, prefixed with {@code drl_})
 * @param type         drill type (BACKUP_VERIFY, RESTORE_SIMULATION, FAILOVER)
 * @param result       drill outcome (PASSED, FAILED, PARTIAL)
 * @param notes        optional notes from the drill
 * @param executedAt  UTC timestamp when the drill was executed
 * @param executedBy   actor that executed the drill
 */
public record DrillRecordDto(
        String drillId,
        String type,
        String result,
        String notes,
        OffsetDateTime executedAt,
        String executedBy
) {
}
