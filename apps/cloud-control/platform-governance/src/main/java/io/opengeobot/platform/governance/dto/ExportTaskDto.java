/*
 * Function: Export task DTO — API response model for export operations
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.time.OffsetDateTime;

/**
 * Immutable DTO representing an export task in API responses. The
 * {@code status} field follows the SM-EXPORT-TASK state machine (PENDING,
 * RUNNING, SUCCEEDED, FAILED, CANCELLED).
 */
public record ExportTaskDto(
        String exportId,
        String resourceType,
        String format,
        String status,
        String fileUrl,
        Long fileSize,
        String errorMessage,
        String requestedBy,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        String traceId
) {
}
