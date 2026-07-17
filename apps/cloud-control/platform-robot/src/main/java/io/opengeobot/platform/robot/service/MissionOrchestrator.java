/*
 * Function: Mission orchestrator - ties QwenPaw planning, policy validation, control lease and edge dispatch
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.dto.AcquireControlLeaseRequest;
import io.opengeobot.platform.robot.dto.ControlLeaseDto;
import io.opengeobot.platform.robot.dto.EdgeCommandDto;
import io.opengeobot.platform.robot.dto.MissionContextDto;
import io.opengeobot.platform.robot.dto.MissionDto;
import io.opengeobot.platform.robot.dto.MissionStepDto;
import io.opengeobot.platform.robot.dto.PlanProposalDto;
import io.opengeobot.platform.robot.dto.PlanStepDto;
import io.opengeobot.platform.robot.dto.ReplanRequestDto;
import io.opengeobot.platform.robot.dto.RevisePlanRequest;
import io.opengeobot.platform.robot.nats.AgentRuntimeNatsClient;
import io.opengeobot.platform.robot.web.ActorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the full mission business loop: QwenPaw planning -> policy
 * validation -> control lease acquisition -> edge command dispatch -> state
 * monitoring.
 * <p>
 * This is the single integration point that ties together the agent-runtime
 * NATS client, policy service, control lease service, and edge command
 * dispatcher. It does NOT access mappers or databases directly - all
 * persistence goes through {@link MissionService}.
 * <p>
 * Safety: agent-generated plans are UNTRUSTED. They must pass policy
 * validation before being persisted. Physical execution requires a valid
 * control lease and goes through the edge Safety Gateway.
 */
@Component
public class MissionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MissionOrchestrator.class);
    private static final Duration DEFAULT_AGENT_TIMEOUT = Duration.ofSeconds(60);
    private static final String COMMAND_START_MISSION = "start_mission";

    private final MissionService missionService;
    private final AgentRuntimeNatsClient agentRuntimeClient;
    private final PolicyService policyService;
    private final ControlLeaseService controlLeaseService;
    private final EdgeCommandDispatcher edgeDispatcher;
    private final ActorResolver actorResolver;
    private final PublicIdGenerator publicIdGenerator;
    private final ClockProvider clockProvider;

    public MissionOrchestrator(MissionService missionService,
                               AgentRuntimeNatsClient agentRuntimeClient,
                               PolicyService policyService,
                               ControlLeaseService controlLeaseService,
                               EdgeCommandDispatcher edgeDispatcher,
                               ActorResolver actorResolver,
                               PublicIdGenerator publicIdGenerator,
                               ClockProvider clockProvider) {
        this.missionService = missionService;
        this.agentRuntimeClient = agentRuntimeClient;
        this.policyService = policyService;
        this.controlLeaseService = controlLeaseService;
        this.edgeDispatcher = edgeDispatcher;
        this.actorResolver = actorResolver;
        this.publicIdGenerator = publicIdGenerator;
        this.clockProvider = clockProvider;
    }

    /**
     * Requests a plan from the QwenPaw agent-runtime, persists the plan steps,
     * and validates them against active policies.
     * <p>
     * The returned proposal is UNTRUSTED and must not be used for execution
     * without further safety validation.
     *
     * @param missionId the mission to plan
     * @return the UNTRUSTED plan proposal from the agent-runtime
     */
    public PlanProposalDto planWithAgent(String missionId) {
        String traceId = actorResolver.currentTraceId();
        if (traceId != null) {
            MDC.put("trace_id", traceId);
        }
        try {
            // 1. Load mission from DB
            MissionDto mission = missionService.getDetail(missionId);
            log.info("Planning mission {} with agent-runtime for robot {}", missionId, mission.robotId());

            // 2. Build MissionContextDto
            String objective = mission.description() != null && !mission.description().isBlank()
                    ? mission.description() : mission.name();
            Map<String, Object> constraints = new LinkedHashMap<>();
            if (mission.priority() != null) {
                constraints.put("priority", mission.priority());
            }
            if (mission.scheduledAt() != null) {
                constraints.put("scheduled_at", mission.scheduledAt().toString());
            }
            String requestedAt = Instant.now(clockProvider.getClock()).toString();
            MissionContextDto context = new MissionContextDto(
                    missionId, traceId, mission.robotId(), objective, constraints, requestedAt);

            // 3. Call agent-runtime via NATS request-reply
            PlanProposalDto proposal = agentRuntimeClient.planMission(context, DEFAULT_AGENT_TIMEOUT);

            // If the agent returned an error, return early without modifying the mission
            if (proposal.error() != null && !proposal.error().isBlank()) {
                log.warn("Agent-runtime returned error for mission {}: {}", missionId, proposal.error());
                return proposal;
            }

            // 4. Write plan steps to mission_step table via MissionService.revisePlan
            List<PlanStepDto> planSteps = proposal.steps();
            if (planSteps == null || planSteps.isEmpty()) {
                log.warn("Agent-runtime returned empty plan for mission {}", missionId);
                return proposal;
            }

            List<MissionStepDto> stepDtos = new ArrayList<>(planSteps.size());
            for (int i = 0; i < planSteps.size(); i++) {
                PlanStepDto step = planSteps.get(i);
                stepDtos.add(new MissionStepDto(
                        null,
                        null,
                        step.skillId(),
                        step.stepOrder() > 0 ? step.stepOrder() : i + 1,
                        step.params(),
                        null,
                        "PENDING",
                        null,
                        null,
                        null
                ));
            }
            missionService.revisePlan(missionId, new RevisePlanRequest(stepDtos));
            log.info("Persisted {} plan steps for mission {}", stepDtos.size(), missionId);

            // 5. Call policyService.evaluate() to validate the plan
            List<String> violations = policyService.evaluate(stepDtos, mission.robotId());
            if (!violations.isEmpty()) {
                log.warn("Policy evaluation found {} violation(s) for mission {}: {}",
                        violations.size(), missionId, violations);
            }

            // 6. Return the UNTRUSTED plan proposal
            return proposal;
        } finally {
            MDC.remove("trace_id");
        }
    }

    /**
     * Requests a revised plan from the QwenPaw agent-runtime after a mission
     * step failure, persists the new plan steps, and returns the proposal.
     * <p>
     * The orchestrator builds a {@link ReplanRequestDto} from the current
     * mission state (completed steps, failed step, remaining steps) and sends
     * it to the agent-runtime. If the agent returns a valid revised plan, the
     * old steps are replaced with the new ones via
     * {@link MissionService#revisePlan(String, RevisePlanRequest)}.
     * <p>
     * Safety: the revised plan is UNTRUSTED. It must pass policy validation
     * before being executed.
     *
     * @param missionId       the mission to replan
     * @param failureReason    human-readable reason for the step failure
     * @param failedStepIndex  0-based index of the failed step
     * @return the UNTRUSTED revised plan proposal, or an error proposal
     */
    public PlanProposalDto replanMission(String missionId, String failureReason, int failedStepIndex) {
        String traceId = actorResolver.currentTraceId();
        if (traceId != null) {
            MDC.put("trace_id", traceId);
        }
        try {
            // 1. Load mission and steps from DB
            MissionDto mission = missionService.getDetail(missionId);
            List<MissionStepDto> allSteps = mission.steps();
            if (allSteps == null || allSteps.isEmpty() || failedStepIndex < 0 || failedStepIndex >= allSteps.size()) {
                log.warn("Cannot replan mission {}: invalid step index {} (steps={})",
                        missionId, failedStepIndex, allSteps != null ? allSteps.size() : 0);
                return new PlanProposalDto("", missionId, traceId, mission.robotId(),
                        List.of(), 0.0, "", false, "Invalid failed step index", null);
            }

            // 2. Build ReplanRequestDto with completed/failed/remaining steps
            List<Map<String, Object>> completedSteps = new ArrayList<>();
            for (int i = 0; i < failedStepIndex; i++) {
                MissionStepDto step = allSteps.get(i);
                Map<String, Object> stepMap = new LinkedHashMap<>();
                stepMap.put("skill_id", step.skillId());
                stepMap.put("params", step.inputParams() != null ? step.inputParams() : Map.of());
                stepMap.put("result", step.status());
                completedSteps.add(stepMap);
            }

            MissionStepDto failedStepDto = allSteps.get(failedStepIndex);
            Map<String, Object> failedStep = new LinkedHashMap<>();
            failedStep.put("skill_id", failedStepDto.skillId());
            failedStep.put("params", failedStepDto.inputParams() != null ? failedStepDto.inputParams() : Map.of());
            failedStep.put("error", failedStepDto.errorMessage() != null ? failedStepDto.errorMessage() : failureReason);

            List<Map<String, Object>> remainingSteps = new ArrayList<>();
            for (int i = failedStepIndex + 1; i < allSteps.size(); i++) {
                MissionStepDto step = allSteps.get(i);
                Map<String, Object> stepMap = new LinkedHashMap<>();
                stepMap.put("skill_id", step.skillId());
                stepMap.put("params", step.inputParams() != null ? step.inputParams() : Map.of());
                remainingSteps.add(stepMap);
            }

            String objective = mission.description() != null && !mission.description().isBlank()
                    ? mission.description() : mission.name();

            ReplanRequestDto replanRequest = new ReplanRequestDto(
                    missionId, traceId, mission.robotId(), objective,
                    completedSteps, failedStep, failureReason, remainingSteps);

            // 3. Call agent-runtime via NATS request-reply
            PlanProposalDto proposal = agentRuntimeClient.replanMission(replanRequest, DEFAULT_AGENT_TIMEOUT);

            // 4. If the agent returned an error, return early
            if (proposal.error() != null && !proposal.error().isBlank()) {
                log.warn("Agent-runtime replan returned error for mission {}: {}", missionId, proposal.error());
                return proposal;
            }

            List<PlanStepDto> planSteps = proposal.steps();
            if (planSteps == null || planSteps.isEmpty()) {
                log.warn("Agent-runtime returned empty replan for mission {}", missionId);
                return proposal;
            }

            // 5. Reset mission state to PLANNING so revisePlan can accept it
            missionService.resetForReplan(missionId);

            // 6. Write new plan steps to mission_step table via MissionService.revisePlan
            List<MissionStepDto> stepDtos = new ArrayList<>(planSteps.size());
            for (int i = 0; i < planSteps.size(); i++) {
                PlanStepDto step = planSteps.get(i);
                stepDtos.add(new MissionStepDto(
                        null, null, step.skillId(),
                        step.stepOrder() > 0 ? step.stepOrder() : i + 1,
                        step.params(), null, "PENDING", null, null, null
                ));
            }
            missionService.revisePlan(missionId, new RevisePlanRequest(stepDtos));
            log.info("Persisted {} replanned steps for mission {}", stepDtos.size(), missionId);

            // 7. Evaluate policies on the revised plan
            List<String> violations = policyService.evaluate(stepDtos, mission.robotId());
            if (!violations.isEmpty()) {
                log.warn("Policy evaluation found {} violation(s) for replanned mission {}: {}",
                        violations.size(), missionId, violations);
            }

            // 8. Return the UNTRUSTED plan proposal
            return proposal;
        } finally {
            MDC.remove("trace_id");
        }
    }

    /**
     * Executes a mission by acquiring a control lease and dispatching a
     * start_mission command to the edge Safety Gateway.
     * <p>
     * Called by {@link MissionService#start(String)} after the mission state
     * has transitioned to EXECUTING. If lease acquisition or edge dispatch
     * fails, the exception propagates and the calling transaction rolls back.
     *
     * @param missionId the mission to execute
     */
    public void executeMission(String missionId) {
        String traceId = actorResolver.currentTraceId();
        if (traceId != null) {
            MDC.put("trace_id", traceId);
        }
        try {
            // 1. Load mission from DB
            MissionDto mission = missionService.getDetail(missionId);
            log.info("Executing mission {} on robot {}", missionId, mission.robotId());

            // 2. Acquire control lease for the robot
            ControlLeaseDto lease = controlLeaseService.acquire(
                    mission.robotId(),
                    new AcquireControlLeaseRequest(null, "mission:" + missionId));
            log.info("Acquired control lease {} for robot {} (fencing={})",
                    lease.leaseId(), mission.robotId(), lease.fencingToken());

            // 3. Build EdgeCommandDto (start_mission)
            String commandId = publicIdGenerator.generate("cmd");
            String issuedAt = Instant.now(clockProvider.getClock())
                    .atOffset(ZoneOffset.UTC)
                    .toString();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("lease_id", lease.leaseId());
            params.put("fencing_token", lease.fencingToken());
            List<MissionStepDto> missionSteps = mission.steps();
            if (missionSteps != null && !missionSteps.isEmpty()) {
                List<Map<String, Object>> serializedSteps = new ArrayList<>(missionSteps.size());
                for (MissionStepDto step : missionSteps) {
                    Map<String, Object> stepPayload = new HashMap<>();
                    stepPayload.put("step_id", step.stepId());
                    stepPayload.put("step_order", step.stepOrder());
                    stepPayload.put("skill_id", step.skillId());
                    stepPayload.put("params", step.inputParams() != null ? step.inputParams() : Map.of());
                    stepPayload.put("description", null);
                    serializedSteps.add(stepPayload);
                }
                params.put("steps", serializedSteps);
            }

            EdgeCommandDto command = new EdgeCommandDto(
                    commandId,
                    traceId,
                    COMMAND_START_MISSION,
                    missionId,
                    null,
                    params,
                    issuedAt
            );

            // 4. Dispatch to edge Safety Gateway
            edgeDispatcher.dispatch(mission.robotId(), command);
            log.info("Dispatched {} command to edge for robot {}", COMMAND_START_MISSION, mission.robotId());
        } finally {
            MDC.remove("trace_id");
        }
    }
}
