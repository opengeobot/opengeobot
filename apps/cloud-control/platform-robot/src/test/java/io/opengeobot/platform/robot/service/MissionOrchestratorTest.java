/*
 * Function: Mission orchestrator unit tests - plan and execute flows
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.dto.AcquireControlLeaseRequest;
import io.opengeobot.platform.robot.dto.ControlLeaseDto;
import io.opengeobot.platform.robot.dto.EdgeCommandDto;
import io.opengeobot.platform.robot.dto.MissionDto;
import io.opengeobot.platform.robot.dto.MissionStepDto;
import io.opengeobot.platform.robot.dto.PlanProposalDto;
import io.opengeobot.platform.robot.dto.PlanStepDto;
import io.opengeobot.platform.robot.dto.ReplanRequestDto;
import io.opengeobot.platform.robot.dto.RevisePlanRequest;
import io.opengeobot.platform.robot.nats.AgentRuntimeNatsClient;
import io.opengeobot.platform.robot.web.ActorResolver;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MissionOrchestrator}. Verifies that the orchestrator
 * correctly sequences QwenPaw planning, plan persistence, policy evaluation,
 * control lease acquisition, and edge command dispatch.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MissionOrchestratorTest {

    @Mock private MissionService missionService;
    @Mock private AgentRuntimeNatsClient agentRuntimeClient;
    @Mock private PolicyService policyService;
    @Mock private ControlLeaseService controlLeaseService;
    @Mock private EdgeCommandDispatcher edgeDispatcher;
    @Mock private ActorResolver actorResolver;
    @Mock private PublicIdGenerator publicIdGenerator;
    @Mock private ClockProvider clockProvider;

    private MissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(publicIdGenerator.generate(any(String.class))).thenReturn("cmd_001");

        orchestrator = new MissionOrchestrator(
                missionService, agentRuntimeClient, policyService,
                controlLeaseService, edgeDispatcher,
                actorResolver, publicIdGenerator, clockProvider);
    }

    private MissionDto createMissionDto(String missionId, String robotId) {
        return new MissionDto(
                missionId, "Test Mission", "Navigate to room A",
                robotId, "READY", "NORMAL", null, null, null, null,
                "user_001", OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC), "trace_001", List.of(
                        new MissionStepDto("stp_exec_1", missionId, "stand_up", 1,
                                Map.of("speed", 0.5), null, "PENDING", null, null, null),
                        new MissionStepDto("stp_exec_2", missionId, "move_forward", 2,
                                Map.of("speed", 0.3, "duration", 6.7), null, "PENDING", null, null, null)
                ));
    }

    private PlanProposalDto createPlanProposal(String missionId) {
        List<PlanStepDto> steps = List.of(
                new PlanStepDto("step_0", 1, "skl_navigate",
                        Map.of("target", "room_a"), "Navigate to room A",
                        true, null),
                new PlanStepDto("step_1", 2, "skl_pickup",
                        Map.of("object", "cup"), "Pick up cup",
                        true, null));
        return new PlanProposalDto("plan_001", missionId, "trace_001",
                "rbt_001", steps, 0.85, "Direct path", false, null,
                "2026-07-16T10:00:00Z");
    }

    @Test
    void planWithAgent_callsAgentRuntimeAndPersistsSteps() {
        String missionId = "msn_001";
        when(missionService.getDetail(missionId)).thenReturn(createMissionDto(missionId, "rbt_001"));
        when(agentRuntimeClient.planMission(any(), any())).thenReturn(createPlanProposal(missionId));
        when(policyService.evaluate(any(), eq("rbt_001"))).thenReturn(List.of());

        PlanProposalDto result = orchestrator.planWithAgent(missionId);

        assertNotNull(result);
        assertEquals("plan_001", result.planId());
        assertEquals(2, result.steps().size());
        assertFalse(result.isTrusted());

        verify(agentRuntimeClient).planMission(any(), any());
        verify(missionService).revisePlan(eq(missionId), any(RevisePlanRequest.class));
        verify(policyService).evaluate(any(), eq("rbt_001"));
    }

    @Test
    void planWithAgent_returnsEarlyOnError() {
        String missionId = "msn_002";
        when(missionService.getDetail(missionId)).thenReturn(createMissionDto(missionId, "rbt_002"));
        PlanProposalDto errorProposal = new PlanProposalDto(
                "", missionId, "trace_001", "rbt_002",
                List.of(), 0.0, "", false, "QwenPaw timed out",
                "2026-07-16T10:01:00Z");
        when(agentRuntimeClient.planMission(any(), any())).thenReturn(errorProposal);

        PlanProposalDto result = orchestrator.planWithAgent(missionId);

        assertEquals("QwenPaw timed out", result.error());
        verify(missionService, never()).revisePlan(any(), any());
        verify(policyService, never()).evaluate(any(), any());
    }

    @Test
    void planWithAgent_returnsEarlyOnEmptySteps() {
        String missionId = "msn_003";
        when(missionService.getDetail(missionId)).thenReturn(createMissionDto(missionId, "rbt_003"));
        PlanProposalDto emptyProposal = new PlanProposalDto(
                "plan_empty", missionId, "trace_001", "rbt_003",
                List.of(), 0.5, "", false, null,
                "2026-07-16T10:02:00Z");
        when(agentRuntimeClient.planMission(any(), any())).thenReturn(emptyProposal);

        PlanProposalDto result = orchestrator.planWithAgent(missionId);

        assertEquals("plan_empty", result.planId());
        verify(missionService, never()).revisePlan(any(), any());
        verify(policyService, never()).evaluate(any(), any());
    }

    @Test
    void planWithAgent_logsPolicyViolationsWithoutFailing() {
        String missionId = "msn_004";
        when(missionService.getDetail(missionId)).thenReturn(createMissionDto(missionId, "rbt_004"));
        when(agentRuntimeClient.planMission(any(), any())).thenReturn(createPlanProposal(missionId));
        when(policyService.evaluate(any(), eq("rbt_004")))
                .thenReturn(List.of("Step targets restricted zone"));

        PlanProposalDto result = orchestrator.planWithAgent(missionId);

        assertNotNull(result);
        assertEquals("plan_001", result.planId());
        verify(policyService).evaluate(any(), eq("rbt_004"));
    }

    @Test
    void executeMission_acquiresLeaseAndDispatchesCommand() {
        String missionId = "msn_005";
        when(missionService.getDetail(missionId)).thenReturn(createMissionDto(missionId, "rbt_005"));
        ControlLeaseDto lease = new ControlLeaseDto(
                "lease_001", "rbt_005", "user_001", "ACTIVE",
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(300),
                null, "ftk_001",
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC));
        when(controlLeaseService.acquire(eq("rbt_005"), any(AcquireControlLeaseRequest.class)))
                .thenReturn(lease);

        orchestrator.executeMission(missionId);

        verify(controlLeaseService).acquire(eq("rbt_005"), any(AcquireControlLeaseRequest.class));

        ArgumentCaptor<EdgeCommandDto> commandCaptor = ArgumentCaptor.forClass(EdgeCommandDto.class);
        verify(edgeDispatcher).dispatch(eq("rbt_005"), commandCaptor.capture());

        EdgeCommandDto command = commandCaptor.getValue();
        assertEquals("start_mission", command.commandType());
        assertEquals(missionId, command.missionId());
        assertEquals("trace_001", command.traceId());
        assertEquals("lease_001", command.params().get("lease_id"));
        assertEquals("ftk_001", command.params().get("fencing_token"));
        assertInstanceOf(List.class, command.params().get("steps"));
        List<?> steps = (List<?>) command.params().get("steps");
        assertEquals(2, steps.size());
        assertInstanceOf(Map.class, steps.get(0));
        Map<?, ?> firstStep = (Map<?, ?>) steps.get(0);
        assertEquals("stand_up", firstStep.get("skill_id"));
        assertEquals(1, firstStep.get("step_order"));
        assertNotNull(command.commandId());
        assertNotNull(command.issuedAt());
    }

    @Test
    void executeMission_propagatesLeaseAcquisitionFailure() {
        String missionId = "msn_006";
        when(missionService.getDetail(missionId)).thenReturn(createMissionDto(missionId, "rbt_006"));
        when(controlLeaseService.acquire(eq("rbt_006"), any()))
                .thenThrow(new io.opengeobot.platform.robot.web.ConflictException("Lease already held"));

        assertThrows(io.opengeobot.platform.robot.web.ConflictException.class,
                () -> orchestrator.executeMission(missionId));

        verify(edgeDispatcher, never()).dispatch(any(), any());
    }

    // ------------------------------------------------------------------
    // replanMission tests
    // ------------------------------------------------------------------

    private MissionDto createMissionDtoWithSteps(String missionId, String robotId) {
        List<MissionStepDto> steps = List.of(
                new MissionStepDto("stp_1", missionId, "skl_navigate", 1,
                        Map.of("target", "room_a"), null, "COMPLETED", null, null, null),
                new MissionStepDto("stp_2", missionId, "skl_pickup", 2,
                        Map.of("object", "cup"), null, "FAILED", null, null, "Gripper jammed"),
                new MissionStepDto("stp_3", missionId, "skl_navigate", 3,
                        Map.of("target", "room_b"), null, "PENDING", null, null, null));
        return new MissionDto(
                missionId, "Test Mission", "Navigate to room A and pick up cup",
                robotId, "FAILED", "NORMAL", null, null, null, "Gripper jammed",
                "user_001", OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC), "trace_001", steps);
    }

    @Test
    void replanMission_callsAgentRuntimeAndPersistsSteps() {
        String missionId = "msn_replan_001";
        when(missionService.getDetail(missionId))
                .thenReturn(createMissionDtoWithSteps(missionId, "rbt_001"));
        when(agentRuntimeClient.replanMission(any(ReplanRequestDto.class), any()))
                .thenReturn(createPlanProposal(missionId));
        when(policyService.evaluate(any(), eq("rbt_001"))).thenReturn(List.of());

        PlanProposalDto result = orchestrator.replanMission(missionId, "Gripper jammed", 1);

        assertNotNull(result);
        assertEquals("plan_001", result.planId());
        assertEquals(2, result.steps().size());
        assertFalse(result.isTrusted());

        // Verify the agent-runtime was called with correct replan context
        ArgumentCaptor<ReplanRequestDto> captor = ArgumentCaptor.forClass(ReplanRequestDto.class);
        verify(agentRuntimeClient).replanMission(captor.capture(), any());
        ReplanRequestDto sent = captor.getValue();
        assertEquals(missionId, sent.missionId());
        assertEquals("rbt_001", sent.robotId());
        assertEquals("Gripper jammed", sent.failureReason());
        assertEquals(1, sent.completedSteps().size());
        assertEquals("skl_navigate", sent.completedSteps().get(0).get("skill_id"));
        assertEquals("skl_pickup", sent.failedStep().get("skill_id"));
        assertEquals("Gripper jammed", sent.failedStep().get("error"));
        assertEquals(1, sent.remainingSteps().size());
        assertEquals("skl_navigate", sent.remainingSteps().get(0).get("skill_id"));

        // Verify the mission was reset and new steps persisted
        verify(missionService).resetForReplan(missionId);
        verify(missionService).revisePlan(eq(missionId), any(RevisePlanRequest.class));
    }

    @Test
    void replanMission_returnsEarlyOnError() {
        String missionId = "msn_replan_002";
        when(missionService.getDetail(missionId))
                .thenReturn(createMissionDtoWithSteps(missionId, "rbt_002"));
        PlanProposalDto errorProposal = new PlanProposalDto(
                "", missionId, "trace_001", "rbt_002",
                List.of(), 0.0, "", false, "QwenPaw replan timed out",
                "2026-07-16T10:01:00Z");
        when(agentRuntimeClient.replanMission(any(ReplanRequestDto.class), any()))
                .thenReturn(errorProposal);

        PlanProposalDto result = orchestrator.replanMission(missionId, "Gripper jammed", 1);

        assertEquals("QwenPaw replan timed out", result.error());
        verify(missionService, never()).resetForReplan(any());
        verify(missionService, never()).revisePlan(any(), any());
    }

    @Test
    void replanMission_returnsEarlyOnEmptySteps() {
        String missionId = "msn_replan_003";
        when(missionService.getDetail(missionId))
                .thenReturn(createMissionDtoWithSteps(missionId, "rbt_003"));
        PlanProposalDto emptyProposal = new PlanProposalDto(
                "plan_empty", missionId, "trace_001", "rbt_003",
                List.of(), 0.5, "", false, null,
                "2026-07-16T10:02:00Z");
        when(agentRuntimeClient.replanMission(any(ReplanRequestDto.class), any()))
                .thenReturn(emptyProposal);

        PlanProposalDto result = orchestrator.replanMission(missionId, "Gripper jammed", 1);

        assertEquals("plan_empty", result.planId());
        verify(missionService, never()).resetForReplan(any());
        verify(missionService, never()).revisePlan(any(), any());
    }

    @Test
    void replanMission_returnsErrorForInvalidStepIndex() {
        String missionId = "msn_replan_004";
        when(missionService.getDetail(missionId))
                .thenReturn(createMissionDtoWithSteps(missionId, "rbt_004"));

        PlanProposalDto result = orchestrator.replanMission(missionId, "reason", 99);

        assertNotNull(result.error());
        verify(agentRuntimeClient, never()).replanMission(any(), any());
    }
}
