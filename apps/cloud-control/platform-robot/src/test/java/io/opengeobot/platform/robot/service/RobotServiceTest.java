/*
 * Function: Robot service unit tests — CRUD, status transitions, capabilities
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.robot.RobotStatus;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.Robot;
import io.opengeobot.platform.robot.domain.RobotCapability;
import io.opengeobot.platform.robot.domain.RobotModel;
import io.opengeobot.platform.robot.domain.RobotStatusHistory;
import io.opengeobot.platform.robot.dto.CreateRobotRequest;
import io.opengeobot.platform.robot.dto.RobotCapabilityDto;
import io.opengeobot.platform.robot.dto.RobotDto;
import io.opengeobot.platform.robot.dto.UpdateRobotRequest;
import io.opengeobot.platform.robot.dto.UpdateRobotStatusRequest;
import io.opengeobot.platform.robot.repository.RobotCapabilityRepository;
import io.opengeobot.platform.robot.repository.RobotModelRepository;
import io.opengeobot.platform.robot.repository.RobotRepository;
import io.opengeobot.platform.robot.repository.RobotStatusHistoryRepository;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RobotService}. Covers robot registration, update,
 * delete with state-machine validation, status transitions (SM-ROBOT-001),
 * and capability management.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RobotServiceTest {

    @Mock private RobotRepository robotRepository;
    @Mock private RobotCapabilityRepository capabilityRepository;
    @Mock private RobotStatusHistoryRepository statusHistoryRepository;
    @Mock private RobotModelRepository modelRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;

    private RobotService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate(any(String.class))).thenReturn("rbt_001");
        service = new RobotService(robotRepository, capabilityRepository,
                statusHistoryRepository, modelRepository, outboxRepository,
                auditService, actorResolver, clockProvider, idGenerator, objectMapper);
    }

    private Robot createRobot(String robotId, String status) {
        Robot robot = new Robot();
        robot.setId(1L);
        robot.setRobotId(robotId);
        robot.setName("TestBot");
        robot.setModelId("unitree-go2");
        robot.setSerialNumber("SN-001");
        robot.setStatus(status);
        robot.setOrgId("org_001");
        robot.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        robot.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return robot;
    }

    private RobotModel createModel() {
        RobotModel model = new RobotModel();
        model.setModelId("unitree-go2");
        model.setModelName("Unitree Go2");
        return model;
    }

    @Test
    void create_validRequestInsertsRobot() {
        when(robotRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(modelRepository.findByModelId("unitree-go2")).thenReturn(createModel());
        when(capabilityRepository.findByRobotId(any())).thenReturn(List.of());

        CreateRobotRequest request = new CreateRobotRequest(
                "TestBot", "unitree-go2", "SN-001", "org_001", List.of());

        RobotDto result = service.create(request);

        assertEquals("rbt_001", result.robotId());
        assertEquals("TestBot", result.name());
        assertEquals("OFFLINE", result.status());
        assertEquals("org_001", result.orgId());

        ArgumentCaptor<Robot> captor = ArgumentCaptor.forClass(Robot.class);
        verify(robotRepository).insert((Robot) captor.capture());
        assertEquals("rbt_001", captor.getValue().getRobotId());
        assertEquals("OFFLINE", captor.getValue().getStatus());
        assertEquals("user_001", captor.getValue().getCreatedBy());
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void create_duplicateSerialNumberThrowsConflict() {
        when(robotRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        CreateRobotRequest request = new CreateRobotRequest(
                "TestBot", "unitree-go2", "SN-001", "org_001", List.of());

        ConflictException ex = assertThrows(ConflictException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("SN-001"));
        verify(robotRepository, never()).insert(any(Robot.class));
    }

    @Test
    void create_unknownModelThrowsResourceNotFound() {
        when(robotRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(modelRepository.findByModelId("unknown-model")).thenReturn(null);

        CreateRobotRequest request = new CreateRobotRequest(
                "TestBot", "unknown-model", "SN-002", "org_001", List.of());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.create(request));
        assertTrue(ex.getMessage().contains("unknown-model"));
    }

    @Test
    void create_withCapabilitiesPersistsEach() {
        when(robotRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(modelRepository.findByModelId("unitree-go2")).thenReturn(createModel());
        when(capabilityRepository.findByRobotId(any())).thenReturn(List.of());

        List<RobotCapabilityDto> caps = List.of(
                new RobotCapabilityDto("navigation", "2d", Map.of()),
                new RobotCapabilityDto("perception", "depth", Map.of("sensor", "lidar"))
        );
        CreateRobotRequest request = new CreateRobotRequest(
                "TestBot", "unitree-go2", "SN-001", "org_001", caps);

        service.create(request);

        verify(capabilityRepository, times(2)).insert(any(RobotCapability.class));
    }

    @Test
    void update_existingRobotUpdatesFields() {
        Robot existing = createRobot("rbt_001", "OFFLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);
        when(capabilityRepository.findByRobotId(any())).thenReturn(List.of());

        RobotDto result = service.update("rbt_001", new UpdateRobotRequest("NewName", "org_002"));

        assertEquals("NewName", result.name());
        assertEquals("org_002", result.orgId());
        verify(robotRepository).updateById(existing);
        verify(auditService).record(any());
    }

    @Test
    void update_robotNotFoundThrowsResourceNotFound() {
        when(robotRepository.findByRobotId("rbt_999")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.update("rbt_999", new UpdateRobotRequest("New", null)));
    }

    @Test
    void delete_offlineRobotSucceeds() {
        Robot existing = createRobot("rbt_001", "OFFLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);

        service.delete("rbt_001");

        verify(capabilityRepository).deleteByRobotId("rbt_001");
        verify(robotRepository).deleteById(1L);
        verify(auditService).record(any());
    }

    @Test
    void delete_onlineRobotThrowsConflict() {
        Robot existing = createRobot("rbt_001", "ONLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.delete("rbt_001"));
        assertTrue(ex.getMessage().contains("non-terminal"));
        verify(robotRepository, never()).deleteById(anyLong());
    }

    @Test
    void delete_busyRobotThrowsConflict() {
        Robot existing = createRobot("rbt_001", "BUSY");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);

        assertThrows(ConflictException.class, () -> service.delete("rbt_001"));
    }

    @Test
    void updateStatus_offlineToOnlineSucceeds() {
        Robot existing = createRobot("rbt_001", "OFFLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);

        service.updateStatus("rbt_001", new UpdateRobotStatusRequest("ONLINE", "Heartbeat received"));

        ArgumentCaptor<Robot> captor = ArgumentCaptor.forClass(Robot.class);
        verify(robotRepository).updateById(captor.capture());
        assertEquals("ONLINE", captor.getValue().getStatus());
        verify(statusHistoryRepository).insert((RobotStatusHistory) any());
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void updateStatus_onlineToBusySucceeds() {
        Robot existing = createRobot("rbt_001", "ONLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);

        service.updateStatus("rbt_001", new UpdateRobotStatusRequest("BUSY", "Mission assigned"));

        verify(robotRepository).updateById((Robot) any());
        verify(statusHistoryRepository).insert((RobotStatusHistory) any());
    }

    @Test
    void updateStatus_invalidTransitionThrowsConflict() {
        Robot existing = createRobot("rbt_001", "ERROR");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.updateStatus("rbt_001", new UpdateRobotStatusRequest("ONLINE", "Recovery")));
        assertTrue(ex.getMessage().contains("SM-ROBOT-001"));
    }

    @Test
    void updateStatus_sameStatusSucceeds() {
        Robot existing = createRobot("rbt_001", "ONLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);

        service.updateStatus("rbt_001", new UpdateRobotStatusRequest("ONLINE", "Refresh"));
        verify(robotRepository).updateById((Robot) any());
    }

    @Test
    void getCapabilities_returnsMappedDtos() {
        Robot existing = createRobot("rbt_001", "ONLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);
        RobotCapability cap = new RobotCapability();
        cap.setCapabilityType("navigation");
        cap.setCapabilityValue("2d");
        cap.setDetails(Map.of("mode", "amcl"));
        when(capabilityRepository.findByRobotId("rbt_001")).thenReturn(List.of(cap));

        List<RobotCapabilityDto> result = service.getCapabilities("rbt_001");

        assertEquals(1, result.size());
        assertEquals("navigation", result.get(0).capabilityType());
        assertEquals("2d", result.get(0).capabilityValue());
    }

    @Test
    void updateCapabilities_replacesAllCapabilities() {
        Robot existing = createRobot("rbt_001", "ONLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);

        List<RobotCapabilityDto> newCaps = List.of(
                new RobotCapabilityDto("navigation", "3d", Map.of()));
        service.updateCapabilities("rbt_001", newCaps);

        verify(capabilityRepository).deleteByRobotId("rbt_001");
        verify(capabilityRepository).insert(any(RobotCapability.class));
        verify(robotRepository).updateById((Robot) any());
        verify(outboxRepository).save(any());
    }

    @Test
    void getByRobotId_existingReturnsDto() {
        Robot existing = createRobot("rbt_001", "ONLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(existing);
        when(capabilityRepository.findByRobotId("rbt_001")).thenReturn(List.of());

        RobotDto result = service.getByRobotId("rbt_001");

        assertEquals("rbt_001", result.robotId());
        assertEquals("TestBot", result.name());
    }

    @Test
    void getByRobotId_notFoundThrowsResourceNotFound() {
        when(robotRepository.findByRobotId("rbt_999")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.getByRobotId("rbt_999"));
    }

    @Test
    void list_returnsPagedRobots() {
        Page<Robot> page = new Page<>(1, 10);
        page.setRecords(List.of(createRobot("rbt_001", "ONLINE")));
        page.setTotal(1);
        when(robotRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(capabilityRepository.findByRobotId(any())).thenReturn(List.of());

        PageResult<RobotDto> result = service.list(PageRequest.of(1, 10), "ONLINE", null, null);

        assertEquals(1, result.items().size());
        assertEquals("rbt_001", result.items().get(0).robotId());
        assertEquals(1, result.total());
    }
}
