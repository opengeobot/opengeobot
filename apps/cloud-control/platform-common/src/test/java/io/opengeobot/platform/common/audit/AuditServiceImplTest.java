/*
 * Function: Audit service unit tests — record audit events to PostgreSQL
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.audit;

import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.repository.AuditEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditServiceImpl}. Verifies audit event mapping to
 * entity, audit_id generation, and default result handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditServiceImplTest {

    @Mock private AuditEventMapper mapper;
    @Mock private PublicIdGenerator idGenerator;

    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        when(idGenerator.generate("aud")).thenReturn("aud_001");
        service = new AuditServiceImpl(mapper, idGenerator);
    }

    private AuditEvent createAuditEvent(String result) {
        return new AuditEvent(
                "USER",
                "usr_001",
                "robot.register",
                "robot",
                "rbt_001",
                result,
                null,
                "192.168.1.1",
                "test-agent",
                "trace-001",
                "req-001",
                Instant.parse("2026-07-06T10:00:00Z"),
                "{\"status\":\"OFFLINE\"}",
                "{\"status\":\"ONLINE\"}"
        );
    }

    @Test
    void record_mapsEventToEntityAndInserts() {
        AuditEvent event = createAuditEvent("SUCCESS");

        service.record(event);

        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(mapper).insert(captor.capture());
        AuditEventEntity saved = captor.getValue();

        assertEquals("aud_001", saved.getAuditId());
        assertEquals("USER", saved.getActorType());
        assertEquals("usr_001", saved.getActorId());
        assertEquals("robot.register", saved.getAction());
        assertEquals("robot", saved.getResourceType());
        assertEquals("rbt_001", saved.getResourceId());
        assertEquals("SUCCESS", saved.getResult());
        assertEquals("192.168.1.1", saved.getSourceIp());
        assertEquals("test-agent", saved.getUserAgent());
        assertEquals("trace-001", saved.getTraceId());
        assertEquals("req-001", saved.getRequestId());
        assertEquals("{\"status\":\"OFFLINE\"}", saved.getPayloadBefore());
        assertEquals("{\"status\":\"ONLINE\"}", saved.getPayloadAfter());
        assertNotNull(saved.getOccurredAt());
    }

    @Test
    void record_nullResultDefaultsToSuccess() {
        AuditEvent event = createAuditEvent(null);

        service.record(event);

        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getResult());
    }

    @Test
    void record_failureResultIsPreserved() {
        AuditEvent event = createAuditEvent("FAILURE");

        service.record(event);

        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals("FAILURE", captor.getValue().getResult());
    }

    @Test
    void record_nullOccurredAtUsesCurrentTime() {
        AuditEvent event = new AuditEvent(
                "SYSTEM", "system", "policy.evaluate", "policy", "pol_001",
                "SUCCESS", null, null, null,
                "trace-002", "req-002", null, null, null
        );

        service.record(event);

        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(mapper).insert(captor.capture());
        assertNotNull(captor.getValue().getOccurredAt());
    }

    @Test
    void record_generatesAuditIdForEachCall() {
        when(idGenerator.generate("aud")).thenReturn("aud_001", "aud_002");

        service.record(createAuditEvent("SUCCESS"));
        service.record(createAuditEvent("SUCCESS"));

        verify(idGenerator, times(2)).generate("aud");
        verify(mapper, times(2)).insert(any(AuditEventEntity.class));
    }
}
