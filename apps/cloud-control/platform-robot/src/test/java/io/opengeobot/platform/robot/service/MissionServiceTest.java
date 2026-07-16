/*
 * Function: Mission service unit tests — lifecycle, state machine, approval
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.mission.MissionState;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.Mission;
import io.opengeobot.platform.robot.domain.MissionApproval;
import io.opengeobot.platform.robot.domain.MissionStep;
import io.opengeobot.platform.robot.dto.ApprovalRequest;
import io.opengeobot.platform.robot.dto.CreateMissionRequest;
import io.opengeobot.platform.robot.dto.MissionApprovalDto;
import io.opengeobot.platform.robot.dto.MissionDto;
import io.opengeobot.platform.robot.dto.MissionStepDto;
import io.opengeobot.platform.robot.dto.PlanProposalDto;
import io.opengeobot.platform.robot.dto.PlanStepDto;
import io.opengeobot.platform.robot.dto.RevisePlanRequest;
import io.opengeobot.platform.robot.dto.UpdateMissionRequest;
import io.opengeobot.platform.robot.monitor.MonitorEventPublisher;
import io.opengeobot.platform.robot.repository.MissionApprovalRepository;
import io.opengeobot.platform.robot.repository.MissionRepository;
import io.opengeobot.platform.robot.repository.MissionStepRepository;
import io.opengeobot.platform.robot.repository.MissionTemplateRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MissionService}. Covers the SM-MISSION-001 state
 * machine (PENDING → READY → EXECUTING → COMPLETED/FAILED), pause/resume,
 * cancel, approval flow, and plan revision.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MissionServiceTest {

    @Mock private MissionRepository missionRepository;
    @Mock private MissionStepRepository missionStepRepository;
    @Mock private MissionTemplateRepository missionTemplateRepository;
    @Mock private MissionApprovalRepository missionApprovalRepository;
    @Mock private AuditService auditService;
    @Mock private OutboxRepository outboxRepository;
    @Mock private PublicIdGenerator publicIdGenerator;
    @Mock private ClockProvider clockProvider;
    @Mock private ActorResolver actorResolver;
    @Mock private MissionOrchestrator missionOrchestrator;
    @Mock private MonitorEventPublisher monitorEventPublisher;
    @Mock private TraceRecorder traceRecorder;

    private MissionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(publicIdGenerator.generate(any(String.class))).thenReturn("msn_001");
        service = new MissionService(missionRepository, missionStepRepository,
                missionTemplateRepository, missionApprovalRepository, auditService,
                outboxRepository, publicIdGenerator, clockProvider, actorResolver,
                objectMapper, missionOrchestrator, monitorEventPublisher, traceRecorder, 3);
    }

    private Mission createMission(String missionId, MissionState state) {
        Mission mission = new Mission();
        mission.setId(1L);
        mission.setMissionId(missionId);
        mission.setName("Test Mission");
        mission.setRobotId("rbt_001");
        mission.setStatus(state.name());
        mission.setPriority("NORMAL");
        mission.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mission.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return mission;
    }

    private List<MissionStepDto> createStepDtos() {
        return List.of(new MissionStepDto(null, null, "skl_nav", 1,
                Map.of("target", "room_a"), null, "PENDING", null, null, null));
    }

    @Test
    void create_validRequestInsertsMissionAndSteps() {
        CreateMissionRequest request = new CreateMissionRequest(
                "Test Mission", "desc", "rbt_001", "NORMAL", null, createStepDtos());

        MissionDto result = service.create(request);

        assertEquals("msn_001", result.missionId());
        assertEquals("PENDING", result.status());
        assertEquals("rbt_001", result.robotId());

        ArgumentCaptor<Mission> missionCaptor = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).insert(missionCaptor.capture());
        assertEquals("PENDING", missionCaptor.getValue().getStatus());
        assertEquals("user_001", missionCaptor.getValue().getCreatedBy());

        verify(missionStepRepository).insert(any(MissionStep.class));
        verify(auditService).record(any());
        verify(outboxRepository).save(any());
    }

    @Test
    void create_nullPriorityDefaultsToNormal() {
        CreateMissionRequest request = new CreateMissionRequest(
                "Test", null, "rbt_001", null, null, createStepDtos());

        service.create(request);

        ArgumentCaptor<Mission> captor = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).insert(captor.capture());
        assertEquals("NORMAL", captor.getValue().getPriority());
    }

    @Test
    void start_fromReadyTransitionsToExecuting() {
        Mission mission = createMission("msn_001", MissionState.READY);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionDto result = service.start("msn_001");

        assertEquals("EXECUTING", result.status());
        verify(missionRepository).updateById((Mission) any());
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
        verify(missionOrchestrator).executeMission("msn_001");
    }

    @Test
    void start_fromPendingThrowsConflict() {
        Mission mission = createMission("msn_001", MissionState.PENDING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        assertThrows(ConflictException.class, () -> service.start("msn_001"));
    }

    @Test
    void pause_fromExecutingTransitionsToPaused() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionDto result = service.pause("msn_001");

        assertEquals("PAUSED", result.status());
        verify(missionRepository).updateById((Mission) any());
    }

    @Test
    void resume_fromPausedTransitionsToExecuting() {
        Mission mission = createMission("msn_001", MissionState.PAUSED);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionDto result = service.resume("msn_001");

        assertEquals("EXECUTING", result.status());
        verify(missionRepository).updateById((Mission) any());
    }

    @Test
    void resume_fromPendingThrowsConflict() {
        Mission mission = createMission("msn_001", MissionState.PENDING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        assertThrows(ConflictException.class, () -> service.resume("msn_001"));
    }

    @Test
    void cancel_fromExecutingTransitionsToCancelled() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionDto result = service.cancel("msn_001");

        assertEquals("CANCELLED", result.status());
        assertNotNull(result.completedAt());
        verify(missionRepository).updateById((Mission) any());
        verify(outboxRepository).save(any());
    }

    @Test
    void cancel_fromTerminalStateThrowsConflict() {
        Mission mission = createMission("msn_001", MissionState.COMPLETED);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        assertThrows(ConflictException.class, () -> service.cancel("msn_001"));
    }

    @Test
    void completeMission_fromExecutingTransitionsToCompleted() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionDto result = service.completeMission("msn_001");

        assertEquals("COMPLETED", result.status());
        assertNotNull(result.completedAt());
        verify(missionRepository).updateById((Mission) any());
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void completeMission_fromPendingThrowsConflict() {
        Mission mission = createMission("msn_001", MissionState.PENDING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        assertThrows(ConflictException.class, () -> service.completeMission("msn_001"));
    }

    @Test
    void failMission_fromExecutingTransitionsToFailed() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        when(missionRepository.selectOne(any())).thenReturn(mission);
        when(missionStepRepository.selectByMissionId("msn_001")).thenReturn(List.of());

        MissionDto result = service.failMission("msn_001", "sensor_timeout");

        assertEquals("FAILED", result.status());
        assertEquals("sensor_timeout", result.failedReason());
        assertNotNull(result.completedAt());
        verify(missionRepository).updateById((Mission) any());
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void failMission_skipsReplanWhenNoFailedStepFound() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        when(missionRepository.selectOne(any())).thenReturn(mission);
        when(missionStepRepository.selectByMissionId("msn_001"))
                .thenReturn(List.of());

        service.failMission("msn_001", "sensor_timeout");

        verify(missionOrchestrator, never()).replanMission(any(), any(), anyInt());
    }

    @Test
    void failMission_attemptsAutoReplanWhenUnderMaxCount() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        mission.setReplanCount(0);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionStep failedStep = new MissionStep();
        failedStep.setSkillId("skl_pickup");
        failedStep.setStatus("FAILED");
        failedStep.setErrorMessage("Gripper jammed");
        failedStep.setStepOrder(2);
        when(missionStepRepository.selectByMissionId("msn_001"))
                .thenReturn(List.of(failedStep));

        PlanProposalDto successProposal = new PlanProposalDto(
                "plan_replan", "msn_001", "trace_001", "rbt_001",
                List.of(new PlanStepDto("step_0", 1, "skl_navigate",
                        Map.of("target", "room_b"), "Navigate", true, null)),
                0.7, "Revised after failure", false, null,
                "2026-07-16T10:00:00Z");
        when(missionOrchestrator.replanMission("msn_001", "Gripper jammed", 0))
                .thenReturn(successProposal);

        MissionDto result = service.failMission("msn_001", "Gripper jammed");

        verify(missionOrchestrator).replanMission("msn_001", "Gripper jammed", 0);
        // The mission status should reflect the replan (orchestrator handled the transition)
        assertNotNull(result);
    }

    @Test
    void failMission_skipsReplanWhenMaxCountReached() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        mission.setReplanCount(3);
        when(missionRepository.selectOne(any())).thenReturn(mission);
        when(missionStepRepository.selectByMissionId("msn_001"))
                .thenReturn(List.of());

        MissionDto result = service.failMission("msn_001", "sensor_timeout");

        assertEquals("FAILED", result.status());
        verify(missionOrchestrator, never()).replanMission(any(), any(), anyInt());
    }

    @Test
    void failMission_keepsFailedWhenReplanReturnsError() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        mission.setReplanCount(0);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionStep failedStep = new MissionStep();
        failedStep.setSkillId("skl_pickup");
        failedStep.setStatus("FAILED");
        failedStep.setErrorMessage("Gripper jammed");
        when(missionStepRepository.selectByMissionId("msn_001"))
                .thenReturn(List.of(failedStep));

        PlanProposalDto errorProposal = new PlanProposalDto(
                "", "msn_001", "trace_001", "rbt_001",
                List.of(), 0.0, "", false, "QwenPaw replan timed out",
                "2026-07-16T10:00:00Z");
        when(missionOrchestrator.replanMission("msn_001", "Gripper jammed", 0))
                .thenReturn(errorProposal);

        MissionDto result = service.failMission("msn_001", "Gripper jammed");

        assertEquals("FAILED", result.status());
        verify(missionOrchestrator).replanMission("msn_001", "Gripper jammed", 0);
    }

    @Test
    void failMission_fromCompletedThrowsConflict() {
        Mission mission = createMission("msn_001", MissionState.COMPLETED);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        assertThrows(ConflictException.class, () -> service.failMission("msn_001", "error"));
    }

    @Test
    void submitApproval_fromReadyCreatesPendingApproval() {
        Mission mission = createMission("msn_001", MissionState.READY);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionApprovalDto result = service.submitApproval("msn_001");

        assertEquals("PENDING", result.status());
        assertEquals("user_001", result.approverId());
        verify(missionApprovalRepository).insert((MissionApproval) any());
        verify(outboxRepository).save(any());
    }

    @Test
    void submitApproval_notReadyThrowsConflict() {
        Mission mission = createMission("msn_001", MissionState.PENDING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        assertThrows(ConflictException.class, () -> service.submitApproval("msn_001"));
    }

    @Test
    void approve_pendingApprovalTransitionsToApproved() {
        Mission mission = createMission("msn_001", MissionState.READY);
        when(missionRepository.selectOne(any())).thenReturn(mission);
        MissionApproval approval = new MissionApproval();
        approval.setMissionId("msn_001");
        approval.setStatus("PENDING");
        when(missionApprovalRepository.selectPendingByMissionId("msn_001")).thenReturn(approval);

        MissionApprovalDto result = service.approve("msn_001", new ApprovalRequest("Looks good"));

        assertEquals("APPROVED", result.status());
        assertEquals("Looks good", result.comment());
        verify(missionApprovalRepository).updateById(approval);
        verify(outboxRepository).save(any());
    }

    @Test
    void approve_noPendingApprovalThrowsConflict() {
        Mission mission = createMission("msn_001", MissionState.READY);
        when(missionRepository.selectOne(any())).thenReturn(mission);
        when(missionApprovalRepository.selectPendingByMissionId("msn_001")).thenReturn(null);

        assertThrows(ConflictException.class,
                () -> service.approve("msn_001", new ApprovalRequest("ok")));
    }

    @Test
    void reject_pendingApprovalTransitionsToRejected() {
        Mission mission = createMission("msn_001", MissionState.READY);
        when(missionRepository.selectOne(any())).thenReturn(mission);
        MissionApproval approval = new MissionApproval();
        approval.setMissionId("msn_001");
        approval.setStatus("PENDING");
        when(missionApprovalRepository.selectPendingByMissionId("msn_001")).thenReturn(approval);

        MissionApprovalDto result = service.reject("msn_001", new ApprovalRequest("Safety concern"));

        assertEquals("REJECTED", result.status());
        assertEquals("Safety concern", result.comment());
    }

    @Test
    void revisePlan_fromPendingTransitionsToReady() {
        Mission mission = createMission("msn_001", MissionState.PENDING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionDto result = service.revisePlan("msn_001", new RevisePlanRequest(createStepDtos()));

        assertEquals("READY", result.status());
        verify(missionStepRepository).deleteByMissionId("msn_001");
        verify(missionStepRepository).insert(any(MissionStep.class));
        verify(missionRepository).updateById((Mission) any());
        verify(outboxRepository).save(any());
    }

    @Test
    void revisePlan_fromExecutingThrowsConflict() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        assertThrows(ConflictException.class,
                () -> service.revisePlan("msn_001", new RevisePlanRequest(createStepDtos())));
    }

    @Test
    void update_fromPendingUpdatesFields() {
        Mission mission = createMission("msn_001", MissionState.PENDING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        MissionDto result = service.update("msn_001",
                new UpdateMissionRequest("New Name", "New desc", "HIGH", null));

        assertEquals("New Name", result.name());
        assertEquals("HIGH", result.priority());
        verify(missionRepository).updateById((Mission) any());
    }

    @Test
    void update_fromExecutingThrowsConflict() {
        Mission mission = createMission("msn_001", MissionState.EXECUTING);
        when(missionRepository.selectOne(any())).thenReturn(mission);

        assertThrows(ConflictException.class,
                () -> service.update("msn_001", new UpdateMissionRequest("New", null, null, null)));
    }

    @Test
    void getDetail_notFoundThrowsResourceNotFound() {
        when(missionRepository.selectOne(any())).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.getDetail("msn_999"));
    }

    @Test
    void getDetail_returnsMissionWithSteps() {
        Mission mission = createMission("msn_001", MissionState.READY);
        when(missionRepository.selectOne(any())).thenReturn(mission);
        MissionStep step = new MissionStep();
        step.setStepId("step_001");
        step.setMissionId("msn_001");
        step.setSkillId("skl_nav");
        step.setStepOrder(1);
        step.setStatus("PENDING");
        when(missionStepRepository.selectByMissionId("msn_001")).thenReturn(List.of(step));

        MissionDto result = service.getDetail("msn_001");

        assertEquals("msn_001", result.missionId());
        assertNotNull(result.steps());
        assertEquals(1, result.steps().size());
        assertEquals("skl_nav", result.steps().get(0).skillId());
    }

    @Test
    void list_returnsPagedMissions() {
        Mission mission = createMission("msn_001", MissionState.PENDING);
        when(missionRepository.selectByFilter(any(), any(), anyLong(), anyInt())).thenReturn(List.of(mission));
        when(missionRepository.countByFilter(any(), any())).thenReturn(1L);

        PageResult<MissionDto> result = service.list(null, null, 1, 10);

        assertEquals(1, result.items().size());
        assertEquals("msn_001", result.items().get(0).missionId());
        assertEquals(1, result.total());
    }
}
