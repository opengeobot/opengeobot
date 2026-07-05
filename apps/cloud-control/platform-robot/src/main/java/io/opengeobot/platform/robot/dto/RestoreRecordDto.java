/*
 * Function: RestoreRecord DTO — API response model for restore records
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a restore record. Maps the
 * {@code recovery.restore_record} entity to the OpenAPI contract
 * {@code RestoreRecord} schema. Jackson serialises field names in snake_case
 * globally.
 *
 * @param restoreId   public identifier (ULID-based, prefixed with {@code rst_})
 * @param backupId     identifier of the backup being restored
 * @param status        restore lifecycle status
 * @param startedAt   UTC timestamp when the restore started
 * @param completedAt UTC timestamp when the restore finished
 * @param errorMessage error details when the restore failed
 * @param restoredBy   actor that initiated the restore
 */
public record RestoreRecordDto(
        String restoreId,
        String backupId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String errorMessage,
        String restoredBy
) {
}
