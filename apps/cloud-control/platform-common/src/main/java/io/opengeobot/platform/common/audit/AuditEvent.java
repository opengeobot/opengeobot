/*
 * Function: Audit event record — security and business audit trail
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.audit;

import java.time.Instant;

/**
 * Represents a single audit record capturing who performed what action on which
 * resource, with the result, trace context and before/after payloads.
 * Critical business and security audits are persisted to PostgreSQL.
 */
public record AuditEvent(
        String actorType,
        String actorId,
        String action,
        String resourceType,
        String resourceId,
        String result,
        String reasonCode,
        String sourceIp,
        String userAgent,
        String traceId,
        String requestId,
        Instant occurredAt,
        String payloadBefore,
        String payloadAfter
) {
}
