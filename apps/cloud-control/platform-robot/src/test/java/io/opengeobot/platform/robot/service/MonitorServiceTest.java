/*
 * Function: Monitor service unit tests — snapshots, overview, takeover
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.mission.MissionState;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.Mission;
import io.opengeobot.platform.robot.domain.MissionStep;
import io.opengeobot.platform.robot.domain.Robot;
import io.opengeobot.platform.robot.domain.SafetyState;
import io.opengeobot.platform.robot.dto.MissionMonitorInfo;
import io.opengeobot.platform.robot.dto.MonitorOverview;
import io.opengeobot.platform.robot.dto.RobotMonitorInfo;
import io.opengeobot.platform.robot.dto.UpdateRobotStatusRequest;
import io.opengeobot.platform.robot.repository.MissionRepository;
import io.opengeobot.platform.robot.repository.MissionStepRepository;
import io.opengeobot.platform.robot.repository.RobotRepository;
import io.opengeobot.platform.robot.repository.SafetyStateRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MonitorService}. Covers robot/mission monitoring
 * snapshots, fleet overview aggregation, and manual takeover.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonitorServiceTest {

    @Mock private RobotRepository robotRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private MissionStepRepository missionStepRepository;
    @Mock private SafetyStateRepository safetyStateRepository;
    @Mock private RobotService robotService;
    @Mock private MissionService missionService;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;

    private MonitorService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        service = new MonitorService(robotRepository, missionRepository,
                missionStepRepository, safetyStateRepository, robotService,
                missionService, auditService, actorResolver, clockProvider,
                objectMapper);
    }

    private Robot createRobot(String robotId, String status) {
        Robot robot = new Robot();
        robot.setRobotId(robotId);
        robot.setName("TestBot");
        robot.setStatus(status);
        robot.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));
        robot.setMetadata(Map.of(
                "position", Map.of("x", 1.0, "y", 2.0),
                "battery", Map.of("level", 85)
        ));
        return robot;
    }

    @Test
    void getRobotMonitorInfo_returnsInfoWithTelemetry() {
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(createRobot("rbt_001", "ONLINE"));
        when(missionRepository.selectActiveByRobotId("rbt_001")).thenReturn(List.of());

        RobotMonitorInfo info = service.getRobotMonitorInfo("rbt_001");

        assertEquals("rbt_001", info.robotId());
        assertEquals("ONLINE", info.status());
        assertNull(info.currentMissionId());
        assertNotNull(info.position());
        assertEquals(1.0, info.position().get("x"));
        assertNotNull(info.battery());
        assertEquals(85, info.battery().get("level"));
    }

    @Test
    void getRobotMonitorInfo_includesCurrentMissionId() {
        Robot robot = createRobot("rbt_001", "BUSY");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(robot);
        Mission mission = new Mission();
        mission.setMissionId("msn_001");
        mission.setStatus(MissionState.EXECUTING.name());
        when(missionRepository.selectActiveByRobotId("rbt_001")).thenReturn(List.of(mission));

        RobotMonitorInfo info = service.getRobotMonitorInfo("rbt_001");

        assertEquals("msn_001", info.currentMissionId());
    }

    @Test
    void getRobotMonitorInfo_robotNotFoundThrows() {
        when(robotRepository.findByRobotId("rbt_999")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.getRobotMonitorInfo("rbt_999"));
    }

    @Test
    void getMissionMonitorInfo_returnsProgressInfo() {
        Mission mission = new Mission();
        mission.setMissionId("msn_001");
        mission.setName("Test Mission");
        mission.setRobotId("rbt_001");
        mission.setStatus(MissionState.EXECUTING.name());
        mission.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionStep step1 = new MissionStep();
        step1.setStepOrder(1);
        step1.setStatus("COMPLETED");
        step1.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        step1.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(3));
        MissionStep step2 = new MissionStep();
        step2.setStepOrder(2);
        step2.setStatus("EXECUTING");
        when(missionStepRepository.selectByMissionId("msn_001")).thenReturn(List.of(step1, step2));

        MissionMonitorInfo info = service.getMissionMonitorInfo("msn_001");

        assertEquals("msn_001", info.missionId());
        assertEquals(2, info.totalSteps());
        assertEquals(50, info.progressPercent());
        assertEquals("EXECUTING", info.status());
    }

    @Test
    void getMissionMonitorInfo_completedMissionReturns100Percent() {
        Mission mission = new Mission();
        mission.setMissionId("msn_001");
        mission.setName("Done Mission");
        mission.setRobotId("rbt_001");
        mission.setStatus(MissionState.COMPLETED.name());
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionStep step = new MissionStep();
        step.setStepOrder(1);
        step.setStatus("COMPLETED");
        when(missionStepRepository.selectByMissionId("msn_001")).thenReturn(List.of(step));

        MissionMonitorInfo info = service.getMissionMonitorInfo("msn_001");

        assertEquals(100, info.progressPercent());
        assertEquals(1, info.currentStep());
    }

    @Test
    void getOverview_aggregatesCounts() {
        when(robotRepository.selectCount(any())).thenReturn(10L);
        when(missionRepository.selectCount(any())).thenReturn(3L);
        when(safetyStateRepository.selectCount(any())).thenReturn(1L);

        MonitorOverview overview = service.getOverview();

        assertEquals(10, overview.totalRobots());
        assertEquals(3, overview.activeMissions());
        assertEquals(1, overview.safetyAlerts());
    }

    @Test
    void takeover_pausesActiveMissionAndTransitionsToMaintenance() {
        Robot robot = createRobot("rbt_001", "ONLINE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(robot);
        Mission mission = new Mission();
        mission.setMissionId("msn_001");
        mission.setStatus(MissionState.EXECUTING.name());
        when(missionRepository.selectActiveByRobotId("rbt_001")).thenReturn(List.of(mission));
        when(missionRepository.selectOne(any())).thenReturn(mission);
        when(missionStepRepository.selectByMissionId(any())).thenReturn(List.of());

        service.takeover("rbt_001", "Manual intervention");

        verify(missionService).pause("msn_001");
        verify(robotService).updateStatus(eq("rbt_001"), any(UpdateRobotStatusRequest.class));
        verify(auditService).record(any());
    }

    @Test
    void takeover_alreadyInMaintenanceSkipsStatusUpdate() {
        Robot robot = createRobot("rbt_001", "MAINTENANCE");
        when(robotRepository.findByRobotId("rbt_001")).thenReturn(robot);
        when(missionRepository.selectActiveByRobotId("rbt_001")).thenReturn(List.of());

        service.takeover("rbt_001", "Already in maintenance");

        verify(robotService, never()).updateStatus(any(), any());
        verify(auditService).record(any());
    }

    @Test
    void takeover_robotNotFoundThrows() {
        when(robotRepository.findByRobotId("rbt_999")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.takeover("rbt_999", "test"));
    }
}
