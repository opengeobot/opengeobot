/*
 * Function: Control lease service unit tests — acquire, release, expire and conflict
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.ControlLease;
import io.opengeobot.platform.robot.dto.AcquireControlLeaseRequest;
import io.opengeobot.platform.robot.dto.ControlLeaseDto;
import io.opengeobot.platform.robot.repository.ControlLeaseRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ControlLeaseService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControlLeaseServiceTest {

    @Mock private ControlLeaseRepository leaseRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;

    private ControlLeaseService service;
    private final AtomicInteger idSeq = new AtomicInteger(1);

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        when(actorResolver.currentActor()).thenReturn("usr_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate(any(String.class))).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            return prefix + "_" + String.format("%03d", idSeq.getAndIncrement());
        });
        service = new ControlLeaseService(leaseRepository, auditService, actorResolver,
                clockProvider, idGenerator, objectMapper);
    }

    private ControlLease createActiveLease(String leaseId, String robotId, OffsetDateTime expiresAt) {
        ControlLease entity = new ControlLease();
        entity.setId(1L);
        entity.setLeaseId(leaseId);
        entity.setRobotId(robotId);
        entity.setHolderUserId("usr_other");
        entity.setStatus("ACTIVE");
        entity.setAcquiredAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        entity.setExpiresAt(expiresAt);
        entity.setFencingToken("ftk_old");
        entity.setCreatedAt(entity.getAcquiredAt());
        entity.setUpdatedAt(entity.getAcquiredAt());
        return entity;
    }

    @Test
    void acquire_insertsActiveLeaseWithFencingToken() {
        when(leaseRepository.findActiveByRobotId("rbt_001")).thenReturn(null);

        ControlLeaseDto result = service.acquire("rbt_001", new AcquireControlLeaseRequest(120, "MANUAL"));

        assertTrue(result.leaseId().startsWith("lease_"));
        assertTrue(result.fencingToken().startsWith("ftk_"));
        assertEquals("ACTIVE", result.status());
        assertEquals("rbt_001", result.robotId());
        assertEquals("usr_001", result.holderUserId());

        ArgumentCaptor<ControlLease> captor = ArgumentCaptor.forClass(ControlLease.class);
        verify(leaseRepository).insert(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getFencingToken());
        verify(auditService).record(any());
    }

    @Test
    void acquire_defaultTtlWhenRequestNull() {
        when(leaseRepository.findActiveByRobotId("rbt_001")).thenReturn(null);

        ControlLeaseDto result = service.acquire("rbt_001", null);

        assertEquals("ACTIVE", result.status());
        assertTrue(result.expiresAt().isAfter(result.acquiredAt().plusSeconds(299)));
    }

    @Test
    void acquire_conflictWhenActiveLeaseExists() {
        when(leaseRepository.findActiveByRobotId("rbt_001"))
                .thenReturn(createActiveLease("lease_001", "rbt_001",
                        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        assertThrows(ConflictException.class,
                () -> service.acquire("rbt_001", new AcquireControlLeaseRequest(60, null)));
        verify(leaseRepository, never()).insert(any(ControlLease.class));
    }

    @Test
    void acquire_expiresStaleLeaseThenCreatesNew() {
        ControlLease expired = createActiveLease("lease_old", "rbt_001",
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10));
        when(leaseRepository.findActiveByRobotId("rbt_001"))
                .thenReturn(expired)
                .thenReturn(null);

        ControlLeaseDto result = service.acquire("rbt_001", new AcquireControlLeaseRequest(60, null));

        assertEquals("ACTIVE", result.status());
        verify(leaseRepository).updateById(any(ControlLease.class));
        verify(leaseRepository).insert(any(ControlLease.class));
    }

    @Test
    void getActive_returnsNullWhenNone() {
        when(leaseRepository.findActiveByRobotId("rbt_001")).thenReturn(null);

        assertNull(service.getActive("rbt_001"));
    }

    @Test
    void getActive_returnsActiveLease() {
        when(leaseRepository.findActiveByRobotId("rbt_001"))
                .thenReturn(createActiveLease("lease_001", "rbt_001",
                        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        ControlLeaseDto result = service.getActive("rbt_001");

        assertNotNull(result);
        assertEquals("lease_001", result.leaseId());
        assertEquals("ACTIVE", result.status());
    }

    @Test
    void getActive_expiresStaleAndReturnsNull() {
        when(leaseRepository.findActiveByRobotId("rbt_001"))
                .thenReturn(createActiveLease("lease_001", "rbt_001",
                        OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)));

        ControlLeaseDto result = service.getActive("rbt_001");

        assertNull(result);
        ArgumentCaptor<ControlLease> captor = ArgumentCaptor.forClass(ControlLease.class);
        verify(leaseRepository).updateById(captor.capture());
        assertEquals("EXPIRED", captor.getValue().getStatus());
    }

    @Test
    void release_marksReleased() {
        when(leaseRepository.findActiveByRobotId("rbt_001"))
                .thenReturn(createActiveLease("lease_001", "rbt_001",
                        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        ControlLeaseDto result = service.release("rbt_001");

        assertEquals("RELEASED", result.status());
        assertNotNull(result.releasedAt());
        verify(leaseRepository).updateById(any(ControlLease.class));
        verify(auditService).record(any());
    }

    @Test
    void release_notFoundWhenNoActiveLease() {
        when(leaseRepository.findActiveByRobotId("rbt_001")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.release("rbt_001"));
    }
}
