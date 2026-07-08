/*
 * Function: OutboxRelay unit tests — publish success, NATS unavailable, failure retry
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link OutboxRelay} using Mockito mocks. No Spring
 * context is required. The tests cover the main relay scenarios: successful
 * publish, NATS unavailable, publish failure with retry, empty batch, and
 * unexpected exception handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private NatsConnectionManager connectionManager;
    @Mock
    private JetStream jetStream;

    private NatsProperties properties;
    private EventSubjectResolver subjectResolver;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        properties = new NatsProperties();
        properties.getOutbox().setBatchSize(10);
        properties.getOutbox().setRetryBackoffMs(1000);
        properties.getOutbox().setMaxRetryCount(5);

        subjectResolver = new EventSubjectResolver();
        relay = new OutboxRelay(outboxRepository, connectionManager, subjectResolver, properties);
    }

    private static OutboxEvent createEvent(Long id, String eventId, String eventType,
                                          String aggregateType, String traceId, int retryCount) {
        return new OutboxEvent(
                id, eventId, eventType, "1", aggregateType, "agg-001", 1L,
                "{\"event_id\":\"" + eventId + "\"}", Instant.now(), traceId,
                false, null, retryCount
        );
    }

    @Test
    void relayEvents_publishesAndMarksAsPublished() throws Exception {
        OutboxEvent event = createEvent(1L, "evt-001", "mission.created.v1", "mission", "trace-1", 0);
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.getJetStream()).thenReturn(jetStream);
        when(outboxRepository.findUnpublishedForRelay(10)).thenReturn(List.of(event));
        PublishAck ack = mock(PublishAck.class);
        when(ack.getSeqno()).thenReturn(42L);
        when(jetStream.publish(anyString(), any(byte[].class), any())).thenReturn(ack);

        relay.relayEvents();

        verify(outboxRepository).markPublished(1L);
        verify(outboxRepository, never()).markPublishFailed(anyLong(), any());
    }

    @Test
    void relayEvents_natsUnavailable_skipsCycle() {
        when(connectionManager.isConnected()).thenReturn(false);
        when(connectionManager.tryConnect()).thenReturn(false);

        relay.relayEvents();

        verify(outboxRepository, never()).findUnpublishedForRelay(anyInt());
        verify(outboxRepository, never()).markPublished(anyLong());
    }

    @Test
    void relayEvents_natsReconnectsAndPublishes() throws Exception {
        OutboxEvent event = createEvent(2L, "evt-002", "safety.emergency_stop.v1", "safety", "trace-2", 0);
        when(connectionManager.isConnected()).thenReturn(false).thenReturn(true);
        when(connectionManager.tryConnect()).thenReturn(true);
        when(connectionManager.getJetStream()).thenReturn(jetStream);
        when(outboxRepository.findUnpublishedForRelay(10)).thenReturn(List.of(event));
        PublishAck ack = mock(PublishAck.class);
        when(jetStream.publish(anyString(), any(byte[].class), any())).thenReturn(ack);

        relay.relayEvents();

        verify(connectionManager).tryConnect();
        verify(outboxRepository).markPublished(2L);
    }

    @Test
    void relayEvents_publishFailure_incrementsRetryCount() throws Exception {
        OutboxEvent event = createEvent(3L, "evt-003", "mission.failed.v1", "mission", "trace-3", 0);
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.getJetStream()).thenReturn(jetStream);
        when(outboxRepository.findUnpublishedForRelay(10)).thenReturn(List.of(event));
        when(jetStream.publish(anyString(), any(byte[].class), any()))
                .thenThrow(new RuntimeException("NATS publish timeout"));

        relay.relayEvents();

        verify(outboxRepository, never()).markPublished(anyLong());
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(outboxRepository).markPublishFailed(idCaptor.capture(), any());
        assertEquals(3L, idCaptor.getValue());
    }

    @Test
    void relayEvents_jetStreamNull_marksAsFailed() {
        OutboxEvent event = createEvent(4L, "evt-004", "robot.registered.v1", "robot", "trace-4", 0);
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.getJetStream()).thenReturn(null);
        when(outboxRepository.findUnpublishedForRelay(10)).thenReturn(List.of(event));

        relay.relayEvents();

        verify(outboxRepository, never()).markPublished(anyLong());
        verify(outboxRepository).markPublishFailed(eq(4L), any());
    }

    @Test
    void relayEvents_emptyBatch_doesNothing() {
        when(connectionManager.isConnected()).thenReturn(true);
        when(outboxRepository.findUnpublishedForRelay(10)).thenReturn(List.of());

        relay.relayEvents();

        verify(outboxRepository, never()).markPublished(anyLong());
        verify(outboxRepository, never()).markPublishFailed(anyLong(), any());
    }

    @Test
    void relayEvents_unexpectedException_doesNotCrash() {
        when(connectionManager.isConnected()).thenReturn(true);
        when(outboxRepository.findUnpublishedForRelay(10))
                .thenThrow(new RuntimeException("Database connection lost"));

        assertDoesNotThrow(() -> relay.relayEvents());
    }

    @Test
    void relayEvents_multipleEvents_publishesIndependently() throws Exception {
        OutboxEvent event1 = createEvent(1L, "evt-a", "mission.created.v1", "mission", "trace-a", 0);
        OutboxEvent event2 = createEvent(2L, "evt-b", "mission.completed.v1", "mission", "trace-b", 0);
        OutboxEvent event3 = createEvent(3L, "evt-c", "mission.failed.v1", "mission", "trace-c", 0);

        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.getJetStream()).thenReturn(jetStream);
        when(outboxRepository.findUnpublishedForRelay(10)).thenReturn(List.of(event1, event2, event3));
        PublishAck ack = mock(PublishAck.class);
        when(jetStream.publish(anyString(), any(byte[].class), any()))
                .thenReturn(ack)
                .thenThrow(new RuntimeException("Transient failure"))
                .thenReturn(ack);

        relay.relayEvents();

        verify(outboxRepository).markPublished(1L);
        verify(outboxRepository).markPublishFailed(eq(2L), any());
        verify(outboxRepository).markPublished(3L);
    }

    @Test
    void relayEvents_markFailedException_doesNotCrash() throws Exception {
        OutboxEvent event = createEvent(5L, "evt-005", "alarm.triggered.v1", "alarm", "trace-5", 0);
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.getJetStream()).thenReturn(jetStream);
        when(outboxRepository.findUnpublishedForRelay(10)).thenReturn(List.of(event));
        when(jetStream.publish(anyString(), any(byte[].class), any()))
                .thenThrow(new RuntimeException("NATS error"));
        doThrow(new RuntimeException("DB error"))
                .when(outboxRepository).markPublishFailed(anyLong(), any());

        assertDoesNotThrow(() -> relay.relayEvents());

        verify(outboxRepository, never()).markPublished(anyLong());
    }

    @Test
    void subjectResolver_mapsCorrectly() {
        OutboxEvent event = createEvent(1L, "evt-001", "mission.created.v1", "mission", "trace-1", 0);

        String subject = subjectResolver.resolveSubject(event);

        assertEquals("opengeobot.events.mission.mission_created_v1", subject);
    }

    @Test
    void subjectResolver_handlesNullFields() {
        OutboxEvent event = new OutboxEvent(
                1L, "evt-001", null, "1", null, "agg-001", 1L,
                "{}", Instant.now(), null, false, null, 0
        );

        String subject = subjectResolver.resolveSubject(event);

        assertEquals("opengeobot.events.unknown.unknown", subject);
    }
}
