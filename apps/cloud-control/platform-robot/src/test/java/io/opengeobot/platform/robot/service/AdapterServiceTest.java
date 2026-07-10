/*
 * Function: Adapter service unit tests - compatibility queries and health updates
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.adapter.AdapterCompatibility;
import io.opengeobot.platform.robot.dto.AdapterCompatibilityDto;
import io.opengeobot.platform.robot.dto.UpdateAdapterHealthRequest;
import io.opengeobot.platform.robot.repository.AdapterCompatibilityRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AdapterService}. Covers compatibility queries by robot
 * model, health status updates, {@code adapter.health_changed.v1} outbox event
 * publishing, and input validation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdapterServiceTest {

    @Mock private AdapterCompatibilityRepository adapterRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;

    private AdapterService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate(any(String.class))).thenReturn("evt_001");
        service = new AdapterService(adapterRepository, outboxRepository, auditService,
                actorResolver, clockProvider, idGenerator, objectMapper);
    }

    private AdapterCompatibility createAdapter(String adapterId, String robotModelId,
                                               String adapterType, String healthStatus) {
        AdapterCompatibility entity = new AdapterCompatibility();
        entity.setId(1L);
        entity.setAdapterId(adapterId);
        entity.setRobotModelId(robotModelId);
        entity.setAdapterType(adapterType);
        entity.setRosVersion("humble");
        entity.setControlProtocol("zenoh");
        entity.setCompatible(true);
        entity.setHealthStatus(healthStatus);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    @Test
    void listByRobotModel_returnsCompatibilityEntries() {
        AdapterCompatibility ros2 = createAdapter("adp_001", "mdl_001", "ros2", "HEALTHY");
        AdapterCompatibility unitree = createAdapter("adp_002", "mdl_001", "unitree", "UNKNOWN");
        when(adapterRepository.findAllByRobotModelId("mdl_001"))
                .thenReturn(List.of(ros2, unitree));

        List<AdapterCompatibilityDto> result = service.listByRobotModel("mdl_001");

        assertEquals(2, result.size());
        assertEquals("adp_001", result.get(0).adapterId());
        assertEquals("ros2", result.get(0).adapterType());
        assertEquals("HEALTHY", result.get(0).healthStatus());
        assertEquals("unitree", result.get(1).adapterType());
    }

    @Test
    void listByRobotModel_returnsEmptyWhenNoneFound() {
        when(adapterRepository.findAllByRobotModelId("mdl_empty"))
                .thenReturn(List.of());

        List<AdapterCompatibilityDto> result = service.listByRobotModel("mdl_empty");

        assertTrue(result.isEmpty());
    }

    @Test
    void getHealth_returnsCurrentHealth() {
        when(adapterRepository.findByAdapterId("adp_001"))
                .thenReturn(createAdapter("adp_001", "mdl_001", "ros2", "HEALTHY"));

        AdapterCompatibilityDto result = service.getHealth("adp_001");

        assertEquals("adp_001", result.adapterId());
        assertEquals("HEALTHY", result.healthStatus());
    }

    @Test
    void getHealth_notFoundThrows() {
        when(adapterRepository.findByAdapterId("adp_missing")).thenReturn(null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.getHealth("adp_missing"));

        assertTrue(ex.getMessage().contains("adp_missing"));
    }

    @Test
    void updateHealth_publishesHealthChangedEventWhenStatusChanges() {
        AdapterCompatibility entity = createAdapter("adp_001", "mdl_001", "ros2", "HEALTHY");
        when(adapterRepository.findByAdapterId("adp_001")).thenReturn(entity);

        AdapterCompatibilityDto result = service.updateHealth(
                "adp_001", new UpdateAdapterHealthRequest("DEGRADED", "Adapter lag detected"));

        assertEquals("DEGRADED", result.healthStatus());
        verify(adapterRepository).updateById(entity);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEvent published = outboxCaptor.getValue();
        assertEquals("adapter.health_changed.v1", published.eventType());
        assertEquals("1", published.eventVersion());
        assertEquals("adapter_compatibility", published.aggregateType());
        assertEquals("adp_001", published.aggregateId());
        assertNotNull(published.payload());
        assertTrue(published.payload().contains("\"health_status\":\"DEGRADED\""));
        assertTrue(published.payload().contains("\"previous_health_status\":\"HEALTHY\""));
        assertEquals("trace_001", published.traceId());

        verify(auditService).record(any());
    }

    @Test
    void updateHealth_doesNotPublishWhenStatusUnchanged() {
        AdapterCompatibility entity = createAdapter("adp_001", "mdl_001", "ros2", "HEALTHY");
        when(adapterRepository.findByAdapterId("adp_001")).thenReturn(entity);

        AdapterCompatibilityDto result = service.updateHealth(
                "adp_001", new UpdateAdapterHealthRequest("healthy", "Heartbeat ok"));

        assertEquals("HEALTHY", result.healthStatus());
        verify(adapterRepository).updateById(entity);
        verify(outboxRepository, never()).save(any());
        verify(auditService).record(any());
    }

    @Test
    void updateHealth_publishesWhenPreviousStatusNull() {
        AdapterCompatibility entity = createAdapter("adp_001", "mdl_001", "ros2", null);
        when(adapterRepository.findByAdapterId("adp_001")).thenReturn(entity);

        AdapterCompatibilityDto result = service.updateHealth(
                "adp_001", new UpdateAdapterHealthRequest("HEALTHY", "Initial health"));

        assertEquals("HEALTHY", result.healthStatus());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("adapter.health_changed.v1", outboxCaptor.getValue().eventType());
    }

    @Test
    void updateHealth_invalidStatusThrows() {
        when(adapterRepository.findByAdapterId("adp_001"))
                .thenReturn(createAdapter("adp_001", "mdl_001", "ros2", "HEALTHY"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateHealth("adp_001", new UpdateAdapterHealthRequest("BROKEN", "bad")));

        assertTrue(ex.getMessage().contains("Invalid health_status"));
        verify(adapterRepository, never()).updateById(any(AdapterCompatibility.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void updateHealth_notFoundThrows() {
        when(adapterRepository.findByAdapterId("adp_missing")).thenReturn(null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.updateHealth("adp_missing",
                        new UpdateAdapterHealthRequest("HEALTHY", "ok")));

        assertTrue(ex.getMessage().contains("adp_missing"));
    }
}
