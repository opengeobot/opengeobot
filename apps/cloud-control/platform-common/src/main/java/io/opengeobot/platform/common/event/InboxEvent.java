/*
 * Function: Inbox event record — idempotent consumption deduplication
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import java.time.Instant;

/**
 * Represents a processed event in the inbox table, used for idempotent
 * consumption. The composite key is ({@code eventId}, {@code consumerName}).
 */
public record InboxEvent(
        String eventId,
        String consumerName,
        Instant processedAt,
        String status
) {
}
