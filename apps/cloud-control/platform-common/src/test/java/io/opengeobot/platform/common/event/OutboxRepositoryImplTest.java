/*
 * Function: Outbox repository unit tests — save, poll unpublished, mark published, retry
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import io.opengeobot.platform.common.repository.OutboxEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxRepositoryImpl}. Verifies that the repository
 * correctly maps {@link OutboxEvent} records to entities, delegates to the
 * mapper, and converts entities back to records.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxRepositoryImplTest {

    @Mock private OutboxEventMapper mapper;

    private OutboxRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new OutboxRepositoryImpl(mapper, "cloud-control-test");
    }

    private OutboxEvent createSampleEvent() {
        return new OutboxEvent(
                null,
                "evt_001",
                "robot.registered",
                "1",
                "robot",
                "rbt_001",
                1L,
                "{\"robotId\":\"rbt_001\"}",
                Instant.parse("2026-07-06T10:00:00Z"),
                "trace-001",
                false,
                null,
                0
        );
    }

    private OutboxEventEntity createSampleEntity(long id, boolean published) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(id);
        entity.setEventId("evt_00" + id);
        entity.setEventType("robot.registered");
        entity.setEventVersion(1);
        entity.setAggregateType("robot");
        entity.setAggregateId("rbt_00" + id);
        entity.setAggregateVersion(1);
        entity.setPayload("{\"robotId\":\"rbt_00" + id + "\"}");
        entity.setOccurredAt(OffsetDateTime.of(2026, 7, 6, 10, 0, 0, 0, ZoneOffset.UTC));
        entity.setProducer("cloud-control-test");
        entity.setTraceId("trace-00" + id);
        entity.setPublished(published);
        entity.setRetryCount(0);
        return entity;
    }

    @Test
    void save_mapsEventToEntityAndInserts() {
        OutboxEvent event = createSampleEvent();

        repository.save(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(mapper).insert(captor.capture());
        OutboxEventEntity saved = captor.getValue();

        assertEquals("evt_001", saved.getEventId());
        assertEquals("robot.registered", saved.getEventType());
        assertEquals(1, saved.getEventVersion());
        assertEquals("robot", saved.getAggregateType());
        assertEquals("rbt_001", saved.getAggregateId());
        assertEquals(1, saved.getAggregateVersion());
        assertEquals("{\"robotId\":\"rbt_001\"}", saved.getPayload());
        assertEquals("cloud-control-test", saved.getProducer());
        assertEquals("trace-001", saved.getTraceId());
        assertFalse(saved.getPublished());
        assertNull(saved.getPublishedAt());
        assertEquals(0, saved.getRetryCount());
        assertNotNull(saved.getOccurredAt());
    }

    @Test
    void save_blankVersionDefaultsToOne() {
        OutboxEvent event = new OutboxEvent(
                null, "evt_002", "mission.created", "   ",
                "mission", "msn_001", 1L, "{}",
                Instant.now(), "trace-002", false, null, 0
        );

        repository.save(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getEventVersion());
    }

    @Test
    void save_nonNumericVersionDefaultsToOne() {
        OutboxEvent event = new OutboxEvent(
                null, "evt_003", "mission.updated", "abc",
                "mission", "msn_001", 2L, "{}",
                Instant.now(), "trace-003", false, null, 0
        );

        repository.save(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getEventVersion());
    }

    @Test
    void findUnpublished_mapsEntitiesToRecords() {
        when(mapper.findUnpublished(10))
                .thenReturn(List.of(createSampleEntity(1, false), createSampleEntity(2, false)));

        List<OutboxEvent> result = repository.findUnpublished(10);

        assertEquals(2, result.size());
        assertEquals("evt_001", result.get(0).eventId());
        assertFalse(result.get(0).published());
        assertEquals("evt_002", result.get(1).eventId());
    }

    @Test
    void findUnpublished_emptyResultReturnsEmptyList() {
        when(mapper.findUnpublished(10)).thenReturn(List.of());

        List<OutboxEvent> result = repository.findUnpublished(10);

        assertTrue(result.isEmpty());
    }

    @Test
    void findUnpublishedForRelay_mapsEntitiesToRecords() {
        when(mapper.findUnpublishedForRelay(5))
                .thenReturn(List.of(createSampleEntity(1, false)));

        List<OutboxEvent> result = repository.findUnpublishedForRelay(5);

        assertEquals(1, result.size());
        assertEquals("evt_001", result.get(0).eventId());
        assertFalse(result.get(0).published());
    }

    @Test
    void markPublished_delegatesToMapper() {
        repository.markPublished(42L);

        verify(mapper).markPublished(42L);
    }

    @Test
    void markPublishFailed_incrementsRetryCount() {
        OffsetDateTime nextRetry = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        repository.markPublishFailed(42L, nextRetry);

        verify(mapper).incrementRetryCount(eq(42L), eq(nextRetry));
    }
}
