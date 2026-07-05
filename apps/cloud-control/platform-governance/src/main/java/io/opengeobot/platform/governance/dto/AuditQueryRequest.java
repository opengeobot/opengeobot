/*
 * Function: Audit query request DTO — filter parameters for audit log queries
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.time.OffsetDateTime;

/**
 * Filter parameters for querying operation audit logs. All fields are
 * optional; when null, no filter is applied for that dimension.
 */
public record AuditQueryRequest(
        String actorId,
        String action,
        String resourceType,
        String resourceId,
        String traceId,
        OffsetDateTime occurredFrom,
        OffsetDateTime occurredTo
) {
}
