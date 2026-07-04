/*
 * Function: Outbox event record — transactional outbox pattern for reliable event publishing
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import java.time.Instant;

/**
 * Represents a domain event persisted in the transactional outbox table.
 * Events are written in the same database transaction as the aggregate change,
 * then asynchronously published and marked as such.
 */
public record OutboxEvent(
        Long id,
        String eventId,
        String eventType,
        String eventVersion,
        String aggregateType,
        String aggregateId,
        Long aggregateVersion,
        String payload,
        Instant occurredAt,
        String traceId,
        boolean published,
        Instant publishedAt,
        int retryCount
) {
}
