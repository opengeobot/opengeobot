/*
 * Function: Outbox repository interface — save, poll unpublished, mark published
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import java.util.List;

/**
 * Repository for the transactional outbox. Implementations persist
 * {@link OutboxEvent} rows alongside domain changes and support polling
 * for unpublished events.
 */
public interface OutboxRepository {

    void save(OutboxEvent event);

    List<OutboxEvent> findUnpublished(int limit);

    void markPublished(Long id);
}
