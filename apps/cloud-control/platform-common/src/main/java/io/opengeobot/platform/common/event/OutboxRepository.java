/*
 * Function: Outbox repository interface — save, poll unpublished, mark published
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Repository for the transactional outbox. Implementations persist
 * {@link OutboxEvent} rows alongside domain changes and support polling
 * for unpublished events.
 */
public interface OutboxRepository {

    void save(OutboxEvent event);

    List<OutboxEvent> findUnpublished(int limit);

    /**
     * Polls for unpublished events that are eligible for immediate relay,
     * i.e. events whose {@code next_retry_at} is either null or in the past.
     * This is the query used by the Outbox Relay.
     */
    List<OutboxEvent> findUnpublishedForRelay(int limit);

    void markPublished(Long id);

    /**
     * Increments the retry count and sets the next retry time for an event
     * that failed to publish. The event remains unpublished.
     */
    void markPublishFailed(Long id, OffsetDateTime nextRetryAt);
}
