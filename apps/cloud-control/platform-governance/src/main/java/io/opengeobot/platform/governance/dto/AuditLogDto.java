/*
 * Function: Audit log DTO — API response model for operation audit records
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.time.OffsetDateTime;

/**
 * Immutable DTO representing an operation audit record in API responses.
 * The {@code traceId} links the audit entry to the end-to-end trace context.
 */
public record AuditLogDto(
        String auditId,
        OffsetDateTime occurredAt,
        String actorType,
        String actorId,
        String action,
        String resourceType,
        String resourceId,
        String result,
        String reasonCode,
        String reasonDetail,
        String traceId,
        String sourceIp
) {
}
