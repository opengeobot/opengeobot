/*
 * Function: BackupRecord DTO — API response model for backup records
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a backup record. Maps the
 * {@code recovery.backup_record} entity to the OpenAPI contract
 * {@code BackupRecord} schema. Jackson serialises field names in snake_case
 * globally.
 *
 * @param backupId    public identifier (ULID-based, prefixed with {@code bkp_})
 * @param type         backup target type (DATABASE or MINIO)
 * @param filePath     path where the backup file is stored
 * @param fileSize     size of the backup file in bytes
 * @param status        backup lifecycle status
 * @param startedAt   UTC timestamp when the backup started
 * @param completedAt UTC timestamp when the backup finished
 * @param errorMessage error details when the backup failed
 * @param createdBy     actor that triggered the backup
 */
public record BackupRecordDto(
        String backupId,
        String type,
        String filePath,
        Long fileSize,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String errorMessage,
        String createdBy
) {
}
