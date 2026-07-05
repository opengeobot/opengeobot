/*
 * Function: Outbox repository implementation — persists events to platform_governance.outbox_event
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import io.opengeobot.platform.common.repository.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Default {@link OutboxRepository} backed by {@link OutboxEventMapper}. Maps
 * the {@link OutboxEvent} record to the wider {@link OutboxEventEntity} so that
 * producer, actor and retry columns are populated. Events are saved in the
 * same transaction as the domain change that produced them.
 */
@Repository
public class OutboxRepositoryImpl implements OutboxRepository {

    private static final Logger log = LoggerFactory.getLogger(OutboxRepositoryImpl.class);

    private final OutboxEventMapper mapper;
    private final String producer;

    public OutboxRepositoryImpl(OutboxEventMapper mapper,
                                @Value("${spring.application.name:cloud-control}") String producer) {
        this.mapper = mapper;
        this.producer = producer;
    }

    @Override
    @Transactional
    public void save(OutboxEvent event) {
        OutboxEventEntity entity = toEntity(event);
        mapper.insert(entity);
        log.debug("Saved outbox event id={} type={} aggregateId={}",
                event.eventId(), event.eventType(), event.aggregateId());
    }

    @Override
    public List<OutboxEvent> findUnpublished(int limit) {
        return mapper.findUnpublished(limit).stream()
                .map(OutboxRepositoryImpl::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(Long id) {
        mapper.markPublished(id);
    }

    private OutboxEventEntity toEntity(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setEventId(event.eventId());
        entity.setEventType(event.eventType());
        entity.setEventVersion(parseVersion(event.eventVersion()));
        entity.setAggregateType(event.aggregateType());
        entity.setAggregateId(event.aggregateId());
        entity.setAggregateVersion(event.aggregateVersion() != null ? event.aggregateVersion().intValue() : 0);
        entity.setPayload(event.payload());
        entity.setOccurredAt(toOffsetDateTime(event.occurredAt()));
        entity.setProducer(producer);
        entity.setTraceId(event.traceId());
        entity.setPublished(event.published());
        entity.setPublishedAt(toOffsetDateTime(event.publishedAt()));
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
                entity.getAggregateVersion() != null ? entity.getAggregateVersion().longValue() : 0L,
                entity.getPayload(),
                entity.getOccurredAt() != null ? entity.getOccurredAt().toInstant() : null,
                entity.getTraceId(),
                entity.getPublished() != null && entity.getPublished(),
                entity.getPublishedAt() != null ? entity.getPublishedAt().toInstant() : null,
                entity.getRetryCount() != null ? entity.getRetryCount() : 0
        );
    }

    private static int parseVersion(String version) {
        if (version == null || version.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atOffset(ZoneOffset.UTC);
    }
}
