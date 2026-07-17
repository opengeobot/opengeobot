/*
 * Function: Mission service — orchestrates mission lifecycle, state machine, audit and outbox events
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.mission.MissionState;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.Mission;
import io.opengeobot.platform.robot.domain.MissionApproval;
import io.opengeobot.platform.robot.domain.MissionStep;
import io.opengeobot.platform.robot.domain.MissionTemplate;
import io.opengeobot.platform.robot.monitor.MonitorEventPublisher;
import io.opengeobot.platform.robot.dto.ApprovalRequest;
import io.opengeobot.platform.robot.dto.CreateMissionRequest;
import io.opengeobot.platform.robot.dto.CreateMissionTemplateRequest;
import io.opengeobot.platform.robot.dto.MissionApprovalDto;
import io.opengeobot.platform.robot.dto.MissionDto;
import io.opengeobot.platform.robot.dto.MissionStepDto;
import io.opengeobot.platform.robot.dto.MissionTemplateDto;
import io.opengeobot.platform.robot.dto.PlanProposalDto;
import io.opengeobot.platform.robot.dto.RevisePlanRequest;
import io.opengeobot.platform.robot.dto.UpdateMissionRequest;
import io.opengeobot.platform.robot.repository.MissionApprovalRepository;
import io.opengeobot.platform.robot.repository.MissionRepository;
import io.opengeobot.platform.robot.repository.MissionStepRepository;
import io.opengeobot.platform.robot.repository.MissionTemplateRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application service that orchestrates the mission lifecycle for
 * F-MISSION-001/002/003. Each state transition validates the SM-MISSION-001
 * state machine via {@link MissionState}, persists the change, records an
 * audit event and writes a transactional outbox event. Physical execution is
 * dispatched to the edge Safety Gateway elsewhere; this service only manages
 * cloud-side mission state.
 */
@Service
public class MissionService {

    private static final Logger log = LoggerFactory.getLogger(MissionService.class);
    private static final String DEFAULT_PRIORITY = "NORMAL";
    private static final String DEFAULT_STEP_STATUS = "PENDING";
    private static final String PRODUCER = "cloud-control";
    private static final TypeReference<List<MissionStepDto>> STEP_LIST_TYPE = new TypeReference<>() {
    };

    private final MissionRepository missionRepository;
    private final MissionStepRepository missionStepRepository;
    private final MissionTemplateRepository missionTemplateRepository;
    private final MissionApprovalRepository missionApprovalRepository;
    private final AuditService auditService;
    private final OutboxRepository outboxRepository;
    private final PublicIdGenerator publicIdGenerator;
    private final ClockProvider clockProvider;
    private final ActorResolver actorResolver;
    private final ObjectMapper objectMapper;
    private final MissionOrchestrator missionOrchestrator;
    private final MonitorEventPublisher monitorEventPublisher;
    private final TraceRecorder traceRecorder;
    private final ControlLeaseService controlLeaseService;
    private final int maxReplanCount;

    public MissionService(MissionRepository missionRepository,
                          MissionStepRepository missionStepRepository,
                          MissionTemplateRepository missionTemplateRepository,
                          MissionApprovalRepository missionApprovalRepository,
                          AuditService auditService,
                          OutboxRepository outboxRepository,
                          PublicIdGenerator publicIdGenerator,
                          ClockProvider clockProvider,
                          ActorResolver actorResolver,
                          ObjectMapper objectMapper,
                          @Lazy MissionOrchestrator missionOrchestrator,
                          MonitorEventPublisher monitorEventPublisher,
                          TraceRecorder traceRecorder,
                          ControlLeaseService controlLeaseService,
                          @org.springframework.beans.factory.annotation.Value("${opengeobot.mission.max-replan-count:3}") int maxReplanCount) {
        this.missionRepository = missionRepository;
        this.missionStepRepository = missionStepRepository;
        this.missionTemplateRepository = missionTemplateRepository;
        this.missionApprovalRepository = missionApprovalRepository;
        this.auditService = auditService;
        this.outboxRepository = outboxRepository;
        this.publicIdGenerator = publicIdGenerator;
        this.clockProvider = clockProvider;
        this.actorResolver = actorResolver;
        this.objectMapper = objectMapper;
        this.missionOrchestrator = missionOrchestrator;
        this.monitorEventPublisher = monitorEventPublisher;
        this.traceRecorder = traceRecorder;
        this.controlLeaseService = controlLeaseService;
        this.maxReplanCount = maxReplanCount;
    }

    // ----- F-MISSION-001: create, list, get, update, plan -----

    @Transactional
    public MissionDto create(CreateMissionRequest request) {
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();
        String missionId = publicIdGenerator.generate("mission");

        Mission mission = new Mission();
        mission.setMissionId(missionId);
        mission.setName(request.name());
        mission.setDescription(request.description());
        mission.setRobotId(request.robotId());
        mission.setStatus(MissionState.PENDING.name());
        mission.setPriority(request.priority() != null ? request.priority() : DEFAULT_PRIORITY);
        mission.setScheduledAt(request.scheduledAt());
        mission.setCreatedBy(actor);
        mission.setCreatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedBy(actor);
        mission.setTraceId(traceId);
        missionRepository.insert(mission);

        persistSteps(missionId, request.steps(), now);

        audit(actor, "mission.create", missionId, now, traceId, null);
        publishEvent("mission.created.v1", missionId, buildCreatedPayload(mission, now, traceId), now, traceId);

        monitorEventPublisher.publishMissionUpdate(missionId, mission.getRobotId(), mission.getStatus(), traceId);
        Map<String, Object> createAttrs = new LinkedHashMap<>();
        createAttrs.put("name", mission.getName());
        createAttrs.put("priority", mission.getPriority());
        traceRecorder.recordFact(traceId, "mission.created", missionId, "mission",
                mission.getRobotId(), missionId, actor, createAttrs);

        log.info("Created mission {} for robot {}", missionId, request.robotId());
        return toDto(mission, null);
    }

    public PageResult<MissionDto> list(String status, String robotId, int page, int pageSize) {
        long offset = (long) (page - 1) * pageSize;
        List<Mission> missions = missionRepository.selectByFilter(status, robotId, offset, pageSize);
        long total = missionRepository.countByFilter(status, robotId);
        List<MissionDto> items = missions.stream().map(m -> toDto(m, null)).toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    public MissionDto getDetail(String missionId) {
        Mission mission = requireMission(missionId);
        List<MissionStep> steps = missionStepRepository.selectByMissionId(missionId);
        return toDto(mission, toStepDtos(steps));
    }

    @Transactional
    public MissionDto update(String missionId, UpdateMissionRequest request) {
        Mission mission = requireMission(missionId);
        assertEditable(mission);
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        if (request.name() != null) {
            mission.setName(request.name());
        }
        if (request.description() != null) {
            mission.setDescription(request.description());
        }
        if (request.priority() != null) {
            mission.setPriority(request.priority());
        }
        if (request.scheduledAt() != null) {
            mission.setScheduledAt(request.scheduledAt());
        }
        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedBy(actor);
        mission.setTraceId(traceId);
        missionRepository.updateById(mission);

        audit(actor, "mission.update", missionId, now, traceId, null);
        log.info("Updated mission {}", missionId);
        return toDto(mission, null);
    }

    @Transactional
    public MissionDto revisePlan(String missionId, RevisePlanRequest request) {
        Mission mission = requireMission(missionId);
        MissionState current = MissionState.valueOf(mission.getStatus());
        if (!current.canTransitionTo(MissionState.PLANNING)) {
            throw new ConflictException("Mission " + missionId + " is in state " + current + " and cannot be revised");
        }
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        missionStepRepository.deleteByMissionId(missionId);
        persistSteps(missionId, request.steps(), now);

        mission.setStatus(MissionState.READY.name());
        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedBy(actor);
        mission.setTraceId(traceId);
        missionRepository.updateById(mission);

        audit(actor, "mission.plan_revise", missionId, now, traceId, null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", publicIdGenerator.generate("evt"));
        payload.put("mission_id", missionId);
        payload.put("status", MissionState.READY.name());
        payload.put("step_count", request.steps().size());
        payload.put("revised_by", actor);
        payload.put("occurred_at", now.toString());
        payload.put("trace_id", traceId);
        publishEvent("mission.plan_revised.v1", missionId, payload, now, traceId);

        log.info("Revised plan for mission {} with {} steps", missionId, request.steps().size());
        return toDto(mission, null);
    }

    // ----- F-MISSION-003: start, pause, resume, cancel -----

    @Transactional
    public MissionDto start(String missionId) {
        Mission mission = requireMission(missionId);
        transition(mission, MissionState.EXECUTING);
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        mission.setStartedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedBy(actor);
        mission.setTraceId(traceId);
        missionRepository.updateById(mission);

        audit(actor, "mission.start", missionId, now, traceId, null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", publicIdGenerator.generate("evt"));
        payload.put("mission_id", missionId);
        payload.put("robot_id", mission.getRobotId());
        payload.put("started_by", actor);
        payload.put("started_at", now.toString());
        payload.put("occurred_at", now.toString());
        payload.put("trace_id", traceId);
        publishEvent("mission.started.v1", missionId, payload, now, traceId);

        monitorEventPublisher.publishMissionUpdate(missionId, mission.getRobotId(),
                MissionState.EXECUTING.name(), traceId);
        traceRecorder.recordFact(traceId, "mission.started", missionId, "mission",
                mission.getRobotId(), missionId, actor, Map.of("started_by", actor));

        // Dispatch to edge Safety Gateway via orchestrator: acquire control lease
        // and publish start_mission command. If this fails the transaction rolls
        // back, leaving the mission in its pre-start state.
        missionOrchestrator.executeMission(missionId);

        log.info("Started mission {}", missionId);
        return toDto(mission, null);
    }

    @Transactional
    public MissionDto pause(String missionId) {
        Mission mission = requireMission(missionId);
        transition(mission, MissionState.PAUSED);
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedBy(actor);
        mission.setTraceId(traceId);
        missionRepository.updateById(mission);

        audit(actor, "mission.pause", missionId, now, traceId, null);
        monitorEventPublisher.publishMissionUpdate(missionId, mission.getRobotId(),
                MissionState.PAUSED.name(), traceId);
        log.info("Paused mission {}", missionId);
        return toDto(mission, null);
    }

    @Transactional
    public MissionDto resume(String missionId) {
        Mission mission = requireMission(missionId);
        transition(mission, MissionState.EXECUTING);
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedBy(actor);
        mission.setTraceId(traceId);
        missionRepository.updateById(mission);

        audit(actor, "mission.resume", missionId, now, traceId, null);
        monitorEventPublisher.publishMissionUpdate(missionId, mission.getRobotId(),
                MissionState.EXECUTING.name(), traceId);
        log.info("Resumed mission {}", missionId);
        return toDto(mission, null);
    }

    @Transactional
    public MissionDto cancel(String missionId) {
        Mission mission = requireMission(missionId);
        MissionState current = MissionState.valueOf(mission.getStatus());
        if (current.isTerminal()) {
            throw new ConflictException("Mission " + missionId + " is already in terminal state " + current);
        }
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        mission.setStatus(MissionState.CANCELLED.name());
        mission.setCompletedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedBy(actor);
        mission.setTraceId(traceId);
        missionRepository.updateById(mission);

        audit(actor, "mission.cancel", missionId, now, traceId, null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", publicIdGenerator.generate("evt"));
        payload.put("mission_id", missionId);
        payload.put("robot_id", mission.getRobotId());
        payload.put("previous_status", current.name());
        payload.put("cancelled_by", actor);
        payload.put("cancelled_at", now.toString());
        payload.put("occurred_at", now.toString());
        payload.put("trace_id", traceId);
        publishEvent("mission.cancelled.v1", missionId, payload, now, traceId);

        monitorEventPublisher.publishMissionUpdate(missionId, mission.getRobotId(),
                MissionState.CANCELLED.name(), traceId);
        Map<String, Object> cancelAttrs = new LinkedHashMap<>();
        cancelAttrs.put("cancelled_by", actor);
        cancelAttrs.put("previous_status", current.name());
        traceRecorder.recordFact(traceId, "mission.cancelled", missionId, "mission",
                mission.getRobotId(), missionId, actor, cancelAttrs);
        releaseLeaseIfPresent(mission.getRobotId());

        log.info("Cancelled mission {} from state {}", missionId, current);
        return toDto(mission, null);
    }

    /**
     * Transitions a mission to COMPLETED. Called by the edge state listener
     * when the edge Safety Gateway reports mission completion.
     */
    @Transactional
    public MissionDto completeMission(String missionId) {
        Mission mission = requireMission(missionId);
        transition(mission, MissionState.COMPLETED);
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        mission.setCompletedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedBy(actor);
        mission.setTraceId(traceId);
        missionRepository.updateById(mission);

        audit(actor, "mission.complete", missionId, now, traceId, null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", publicIdGenerator.generate("evt"));
        payload.put("mission_id", missionId);
        payload.put("robot_id", mission.getRobotId());
        payload.put("completed_by", actor);
        payload.put("completed_at", now.toString());
        payload.put("occurred_at", now.toString());
        payload.put("trace_id", traceId);
        publishEvent("mission.completed.v1", missionId, payload, now, traceId);

        monitorEventPublisher.publishMissionUpdate(missionId, mission.getRobotId(),
                MissionState.COMPLETED.name(), traceId);
        traceRecorder.recordFact(traceId, "mission.completed", missionId, "mission",
                mission.getRobotId(), missionId, actor, Map.of("completed_by", actor));
        releaseLeaseIfPresent(mission.getRobotId());

        log.info("Completed mission {}", missionId);
        return toDto(mission, null);
    }

    /**
     * Transitions a mission to FAILED. Called by the edge state listener
     * when the edge Safety Gateway reports a mission failure.
     */
    @Transactional
    public MissionDto failMission(String missionId, String reason) {
        Mission mission = requireMission(missionId);
        transition(mission, MissionState.FAILED);
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        mission.setFailedReason(reason);
        mission.setCompletedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        mission.setUpdatedBy(actor);
        mission.setTraceId(traceId);
        missionRepository.updateById(mission);

        audit(actor, "mission.fail", missionId, now, traceId, reason);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", publicIdGenerator.generate("evt"));
        payload.put("mission_id", missionId);
        payload.put("robot_id", mission.getRobotId());
        payload.put("failed_reason", reason);
        payload.put("failed_by", actor);
        payload.put("failed_at", now.toString());
        payload.put("occurred_at", now.toString());
        payload.put("trace_id", traceId);
        publishEvent("mission.failed.v1", missionId, payload, now, traceId);

        monitorEventPublisher.publishMissionUpdate(missionId, mission.getRobotId(),
                MissionState.FAILED.name(), traceId);
        Map<String, Object> failAttrs = new LinkedHashMap<>();
        failAttrs.put("failed_reason", reason);
        failAttrs.put("failed_by", actor);
        traceRecorder.recordFact(traceId, "mission.failed", missionId, "mission",
                mission.getRobotId(), missionId, actor, failAttrs);
        releaseLeaseIfPresent(mission.getRobotId());

        log.info("Failed mission {}: {}", missionId, reason);

        // Attempt auto-replan if under the maximum replan count
        int currentReplanCount = mission.getReplanCount() != null ? mission.getReplanCount() : 0;
        if (currentReplanCount < maxReplanCount) {
            MissionDto result = attemptAutoReplan(missionId, reason, currentReplanCount);
            if (result != null) {
                return result;
            }
        } else {
            log.info("Mission {} has reached max replan count ({}); keeping FAILED", missionId, maxReplanCount);
        }

        return toDto(mission, null);
    }

    private void releaseLeaseIfPresent(String robotId) {
        if (robotId == null || robotId.isBlank()) {
            return;
        }
        try {
            controlLeaseService.release(robotId);
        } catch (Exception e) {
            log.debug("Control lease release skipped for robot {}: {}", robotId, e.getMessage());
        }
    }

    /**
     * Attempts to replan a failed mission by calling the agent-runtime.
     * Returns the updated MissionDto if replan succeeds, or null to keep FAILED.
     */
    private MissionDto attemptAutoReplan(String missionId, String reason, int currentReplanCount) {
        List<MissionStep> steps = missionStepRepository.selectByMissionId(missionId);
        if (steps == null || steps.isEmpty()) {
            log.debug("No steps found for mission {}; skipping auto-replan", missionId);
            return null;
        }
        int failedStepIndex = -1;
        for (int i = 0; i < steps.size(); i++) {
            if ("FAILED".equals(steps.get(i).getStatus())) {
                failedStepIndex = i;
                break;
            }
        }
        if (failedStepIndex < 0) {
            log.debug("No failed step found for mission {}; skipping auto-replan", missionId);
            return null;
        }

        try {
            log.info("Attempting auto-replan for mission {} (attempt {}/{})",
                    missionId, currentReplanCount + 1, maxReplanCount);
            PlanProposalDto proposal = missionOrchestrator.replanMission(missionId, reason, failedStepIndex);
            if (proposal.error() != null && !proposal.error().isBlank()) {
                log.warn("Auto-replan returned error for mission {}: {}", missionId, proposal.error());
                return null;
            }
            if (proposal.steps() == null || proposal.steps().isEmpty()) {
                log.warn("Auto-replan returned no steps for mission {}", missionId);
                return null;
            }
            // Orchestrator already persisted new steps and set status to READY.
            // Reload the mission to reflect the updated state.
            Mission updated = requireMission(missionId);
            log.info("Auto-replan succeeded for mission {} (replan count: {})", missionId, currentReplanCount + 1);
            return toDto(updated, toStepDtos(missionStepRepository.selectByMissionId(missionId)));
        } catch (Exception e) {
            log.warn("Auto-replan failed for mission {}: {}", missionId, e.getMessage());
            return null;
        }
    }

    /**
     * Resets a FAILED mission to PLANNING state for replanning.
     * This is a controlled exception to the state machine, allowing the
     * orchestrator to persist a revised plan via {@link #revisePlan}.
     *
     * @param missionId the mission to reset
     */
    @Transactional
    public void resetForReplan(String missionId) {
        Mission mission = requireMission(missionId);
        int currentCount = mission.getReplanCount() != null ? mission.getReplanCount() : 0;
        mission.setStatus(MissionState.PLANNING.name());
        mission.setReplanCount(currentCount + 1);
        mission.setFailedReason(null);
        mission.setCompletedAt(null);
        Instant now = Instant.now(clockProvider.getClock());
        mission.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        missionRepository.updateById(mission);
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();
        audit(actor, "mission.replan_reset", missionId, now, traceId,
                "reset to PLANNING for auto-replan (count=" + (currentCount + 1) + ")");
        log.info("Reset mission {} to PLANNING for replan (count={})", missionId, currentCount + 1);
    }

    /**
     * Updates the status of a mission step by step order (1-based).
     * Called by the edge state listener when step-level updates are received
     * from the edge Safety Gateway.
     *
     * @param missionId the mission ID
     * @param stepOrder the 1-based step order
     * @param status    the new step status (RUNNING, COMPLETED, FAILED)
     * @param error     optional error message when status is FAILED
     */
    @Transactional
    public void updateStepStatus(String missionId, int stepOrder, String status, String error) {
        List<MissionStep> steps = missionStepRepository.selectByMissionId(missionId);
        Instant now = Instant.now(clockProvider.getClock());
        for (MissionStep step : steps) {
            if (step.getStepOrder() != null && step.getStepOrder() == stepOrder) {
                step.setStatus(status);
                if ("RUNNING".equals(status)) {
                    step.setStartedAt(now.atOffset(ZoneOffset.UTC));
                } else if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    step.setCompletedAt(now.atOffset(ZoneOffset.UTC));
                    if (error != null) {
                        step.setErrorMessage(error);
                    }
                }
                missionStepRepository.updateById(step);
                log.info("Updated step {} of mission {} to status {}", step.getStepId(), missionId, status);
                return;
            }
        }
        log.warn("Step with order {} not found for mission {}", stepOrder, missionId);
    }

    // ----- F-MISSION-002: approval -----

    @Transactional
    public MissionApprovalDto submitApproval(String missionId) {
        Mission mission = requireMission(missionId);
        if (!MissionState.READY.name().equals(mission.getStatus())) {
            throw new ConflictException("Mission " + missionId + " is not READY and cannot be submitted for approval");
        }
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        MissionApproval approval = new MissionApproval();
        approval.setMissionId(missionId);
        approval.setApproverId(actor);
        approval.setStatus("PENDING");
        approval.setCreatedAt(now.atOffset(ZoneOffset.UTC));
        missionApprovalRepository.insert(approval);

        audit(actor, "mission.submit_approval", missionId, now, traceId, null);
        publishEvent("mission.approval_requested.v1", missionId, buildApprovalPayload(missionId, "PENDING", actor, now, traceId), now, traceId);

        log.info("Submitted mission {} for approval", missionId);
        return toApprovalDto(approval);
    }

    @Transactional
    public MissionApprovalDto approve(String missionId, ApprovalRequest request) {
        Mission mission = requireMission(missionId);
        MissionApproval approval = missionApprovalRepository.selectPendingByMissionId(missionId);
        if (approval == null) {
            throw new ConflictException("Mission " + missionId + " has no pending approval");
        }
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        approval.setStatus("APPROVED");
        approval.setComment(request.comment());
        approval.setApproverId(actor);
        approval.setApprovedAt(now.atOffset(ZoneOffset.UTC));
        missionApprovalRepository.updateById(approval);

        audit(actor, "mission.approve", missionId, now, traceId, request.comment());
        publishEvent("mission.approved.v1", missionId, buildApprovalPayload(missionId, "APPROVED", actor, now, traceId), now, traceId);

        log.info("Approved mission {}", missionId);
        return toApprovalDto(approval);
    }

    @Transactional
    public MissionApprovalDto reject(String missionId, ApprovalRequest request) {
        requireMission(missionId);
        MissionApproval approval = missionApprovalRepository.selectPendingByMissionId(missionId);
        if (approval == null) {
            throw new ConflictException("Mission " + missionId + " has no pending approval");
        }
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();

        approval.setStatus("REJECTED");
        approval.setComment(request.comment());
        approval.setApproverId(actor);
        approval.setApprovedAt(now.atOffset(ZoneOffset.UTC));
        missionApprovalRepository.updateById(approval);

        audit(actor, "mission.reject", missionId, now, traceId, request.comment());
        publishEvent("mission.rejected.v1", missionId, buildApprovalPayload(missionId, "REJECTED", actor, now, traceId), now, traceId);

        log.info("Rejected mission {}", missionId);
        return toApprovalDto(approval);
    }

    // ----- F-MISSION-002: templates -----

    public PageResult<MissionTemplateDto> listTemplates(int page, int pageSize) {
        long offset = (long) (page - 1) * pageSize;
        List<MissionTemplate> templates = missionTemplateRepository.selectPage(offset, pageSize);
        long total = missionTemplateRepository.countAll();
        List<MissionTemplateDto> items = templates.stream().map(this::toTemplateDto).toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    @Transactional
    public MissionTemplateDto createTemplate(CreateMissionTemplateRequest request) {
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = effectiveTraceId();
        String templateId = publicIdGenerator.generate("mission_template");

        MissionTemplate template = new MissionTemplate();
        template.setTemplateId(templateId);
        template.setName(request.name());
        template.setDescription(request.description());
        template.setSteps(serializeSteps(request.steps()));
        template.setCreatedAt(now.atOffset(ZoneOffset.UTC));
        template.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        template.setCreatedBy(actor);
        missionTemplateRepository.insert(template);

        audit(actor, "mission_template.create", templateId, now, traceId, null);
        log.info("Created mission template {}", templateId);
        return toTemplateDto(template);
    }

    // ----- helpers -----

    private Mission requireMission(String missionId) {
        Mission mission = missionRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Mission>()
                        .eq("mission_id", missionId));
        if (mission == null) {
            throw new ResourceNotFoundException("Mission not found: " + missionId);
        }
        return mission;
    }

    private String effectiveTraceId() {
        String traceId = actorResolver.currentTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String generatedTraceId = publicIdGenerator.generate("trace");
        MDC.put("traceId", generatedTraceId);
        MDC.put("trace_id", generatedTraceId);
        return generatedTraceId;
    }

    private void assertEditable(Mission mission) {
        MissionState current = MissionState.valueOf(mission.getStatus());
        if (current != MissionState.PENDING && current != MissionState.PLANNING) {
            throw new ConflictException("Mission " + mission.getMissionId() + " is in state " + current + " and cannot be edited");
        }
    }

    private void transition(Mission mission, MissionState target) {
        MissionState current = MissionState.valueOf(mission.getStatus());
        if (!current.canTransitionTo(target)) {
            throw new ConflictException(
                    "Invalid transition for mission " + mission.getMissionId() + ": " + current + " -> " + target);
        }
        mission.setStatus(target.name());
    }

    private void persistSteps(String missionId, List<MissionStepDto> steps, Instant now) {
        for (MissionStepDto step : steps) {
            MissionStep entity = new MissionStep();
            entity.setStepId(publicIdGenerator.generate("mission_step"));
            entity.setMissionId(missionId);
            entity.setSkillId(step.skillId());
            entity.setStepOrder(step.stepOrder());
            entity.setInputParams(step.inputParams());
            entity.setStatus(DEFAULT_STEP_STATUS);
            missionStepRepository.insert(entity);
        }
    }

    private void audit(String actor, String action, String resourceId, Instant now, String traceId, String details) {
        auditService.record(new AuditEvent(
                "user", actor, action, "mission", resourceId,
                "SUCCESS", null, null, null, traceId, null, now, null, details
        ));
    }

    private void publishEvent(String eventType, String missionId, Map<String, Object> payload, Instant now, String traceId) {
        try {
            OutboxEvent event = new OutboxEvent(
                    null,
                    publicIdGenerator.generate("evt"),
                    eventType,
                    "1",
                    "mission",
                    missionId,
                    null,
                    objectMapper.writeValueAsString(payload),
                    now,
                    traceId,
                    false,
                    null,
                    0
            );
            outboxRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise outbox payload for event {} on mission {}", eventType, missionId, e);
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }

    private Map<String, Object> buildCreatedPayload(Mission mission, Instant now, String traceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", publicIdGenerator.generate("evt"));
        payload.put("mission_id", mission.getMissionId());
        payload.put("name", mission.getName());
        payload.put("robot_id", mission.getRobotId());
        payload.put("status", mission.getStatus());
        payload.put("priority", mission.getPriority());
        payload.put("created_by", mission.getCreatedBy());
        payload.put("occurred_at", now.toString());
        payload.put("trace_id", traceId);
        return payload;
    }

    private Map<String, Object> buildApprovalPayload(String missionId, String status, String actor, Instant now, String traceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", publicIdGenerator.generate("evt"));
        payload.put("mission_id", missionId);
        payload.put("approver_id", actor);
        payload.put("status", status);
        payload.put("occurred_at", now.toString());
        payload.put("trace_id", traceId);
        return payload;
    }

    private String serializeSteps(List<MissionStepDto> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise template steps", e);
        }
    }

    private List<MissionStepDto> deserializeSteps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STEP_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialise template steps", e);
            return List.of();
        }
    }

    private MissionDto toDto(Mission mission, List<MissionStepDto> steps) {
        return new MissionDto(
                mission.getMissionId(),
                mission.getName(),
                mission.getDescription(),
                mission.getRobotId(),
                mission.getStatus(),
                mission.getPriority(),
                mission.getScheduledAt(),
                mission.getStartedAt(),
                mission.getCompletedAt(),
                mission.getFailedReason(),
                mission.getCreatedBy(),
                mission.getCreatedAt(),
                mission.getUpdatedAt(),
                mission.getTraceId(),
                steps
        );
    }

    private List<MissionStepDto> toStepDtos(List<MissionStep> steps) {
        List<MissionStepDto> result = new ArrayList<>(steps.size());
        for (MissionStep step : steps) {
            result.add(new MissionStepDto(
                    step.getStepId(),
                    step.getMissionId(),
                    step.getSkillId(),
                    step.getStepOrder(),
                    step.getInputParams(),
                    step.getOutputResult(),
                    step.getStatus(),
                    step.getStartedAt(),
                    step.getCompletedAt(),
                    step.getErrorMessage()
            ));
        }
        return result;
    }

    private MissionTemplateDto toTemplateDto(MissionTemplate template) {
        return new MissionTemplateDto(
                template.getTemplateId(),
                template.getName(),
                template.getDescription(),
                deserializeSteps(template.getSteps()),
                template.getCreatedBy(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    private MissionApprovalDto toApprovalDto(MissionApproval approval) {
        return new MissionApprovalDto(
                approval.getMissionId(),
                approval.getApproverId(),
                approval.getStatus(),
                approval.getComment(),
                approval.getApprovedAt(),
                approval.getCreatedAt()
        );
    }
}
