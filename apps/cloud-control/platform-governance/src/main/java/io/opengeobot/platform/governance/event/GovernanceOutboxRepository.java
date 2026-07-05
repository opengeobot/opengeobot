/*
 * Function: OutboxRepository implementation — persists events to outbox_event table
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.event;

import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxEventEntity;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.repository.OutboxEventMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Primary implementation of {@link OutboxRepository} backed by the
 * {@code platform_governance.outbox_event} table via the common MyBatis-Plus mapper.
 */
@Repository
@Primary
public class GovernanceOutboxRepository implements OutboxRepository {

    private final OutboxEventMapper outboxEventMapper;

    public GovernanceOutboxRepository(OutboxEventMapper outboxEventMapper) {
        this.outboxEventMapper = outboxEventMapper;
    }

    @Override
    @Transactional
    public void save(OutboxEvent event) {
        OutboxEventEntity entity = toEntity(event);
        outboxEventMapper.insert(entity);
    }

    @Override
    public List<OutboxEvent> findUnpublished(int limit) {
        return outboxEventMapper.findUnpublished(limit).stream()
                .map(GovernanceOutboxRepository::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(Long id) {
        outboxEventMapper.markPublished(id);
    }

    private static OutboxEventEntity toEntity(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setEventId(event.eventId());
        entity.setEventType(event.eventType());
        entity.setEventVersion(event.eventVersion() != null ? Integer.parseInt(event.eventVersion()) : 1);
        entity.setAggregateType(event.aggregateType());
        entity.setAggregateId(event.aggregateId());
        entity.setAggregateVersion(event.aggregateVersion() != null ? event.aggregateVersion().intValue() : null);
        entity.setPayload(event.payload());
        entity.setOccurredAt(java.time.OffsetDateTime.ofInstant(event.occurredAt(), java.time.ZoneOffset.UTC));
        entity.setProducer("platform-governance");
        entity.setTraceId(event.traceId());
        entity.setPublished(event.published());
        entity.setRetryCount(event.retryCount());
        return entity;
    }

    private static OutboxEvent toRecord(OutboxEventEntity entity) {
        return new OutboxEvent(
                entity.getId(),
                entity.getEventId(),
                entity.getEventType(),
                String.valueOf(entity.getEventVersion()),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getAggregateVersion() != null ? entity.getAggregateVersion().longValue() : null,
                entity.getPayload(),
                entity.getOccurredAt() != null ? entity.getOccurredAt().toInstant() : null,
                entity.getTraceId(),
                Boolean.TRUE.equals(entity.getPublished()),
                entity.getPublishedAt() != null ? entity.getPublishedAt().toInstant() : null,
                entity.getRetryCount() != null ? entity.getRetryCount() : 0
        );
    }
}
