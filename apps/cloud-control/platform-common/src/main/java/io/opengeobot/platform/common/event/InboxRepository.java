/*
 * Function: Inbox repository interface — check and mark processed for idempotent consumption
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

/**
 * Repository for the inbox deduplication table. Consumers check
 * {@link #isProcessed} before handling an event and call
 * {@link #markProcessed} once processing succeeds.
 */
public interface InboxRepository {

    boolean isProcessed(String eventId, String consumerName);

    void markProcessed(String eventId, String consumerName);
}
