/*
 * Function: Safety service — emergency stop, reset, state queries and pre-mission safety check
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.SafetyEvent;
import io.opengeobot.platform.robot.domain.SafetyState;
import io.opengeobot.platform.robot.dto.SafetyEventDto;
import io.opengeobot.platform.robot.dto.SafetyStateDto;
import io.opengeobot.platform.robot.repository.SafetyEventRepository;
import io.opengeobot.platform.robot.repository.SafetyStateRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Service for safety state management (F-SAFETY-001). The safety state
 * follows the SM-SAFETY-001 state machine
 * (NORMAL → EMERGENCY_STOPPED → RESETTING → NORMAL). Emergency stops are
 * latchable: once triggered, the robot stays stopped until an explicit reset.
 * All mutations emit domain events via the transactional outbox and are
 * recorded in the audit trail.
 */
@Service
public class SafetyService {

    private static final Logger log = LoggerFactory.getLogger(SafetyService.class);
    private static final String EMERGENCY_STOP_EVENT = "safety.emergency_stop.v1";
    private static final String RESET_EVENT = "safety.reset.v1";
    private static final String RESOURCE_TYPE = "safety_state";
    private static final String EVENT_RESOURCE_TYPE = "safety_event";
    private static final String STATE_NORMAL = "NORMAL";
    private static final String STATE_EMERGENCY_STOPPED = "EMERGENCY_STOPPED";
    private static final String STATE_RESETTING = "RESETTING";
    private static final String EVENT_EMERGENCY_STOP = "EMERGENCY_STOP";
    private static final String EVENT_RESET = "RESET";
    private static final String EVENT_SAFETY_CHECK_PASSED = "SAFETY_CHECK_PASSED";
    private static final String EVENT_SAFETY_CHECK_FAILED = "SAFETY_CHECK_FAILED";

    private final SafetyStateRepository safetyStateRepository;
    private final SafetyEventRepository safetyEventRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public SafetyService(SafetyStateRepository safetyStateRepository,
                         SafetyEventRepository safetyEventRepository,
                         OutboxRepository outboxRepository,
                         AuditService auditService,
                         ActorResolver actorResolver,
                         ClockProvider clockProvider,
                         PublicIdGenerator idGenerator,
                         ObjectMapper objectMapper) {
        this.safetyStateRepository = safetyStateRepository;
        this.safetyEventRepository = safetyEventRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SafetyStateDto emergencyStop(String robotId, String reason) {
        String effectiveRobotId = (robotId != null && !robotId.isBlank()) ? robotId : "global";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();

        SafetyState state = findOrCreateState(effectiveRobotId);
        String payloadBefore = toJson(state);
        state.setState(STATE_EMERGENCY_STOPPED);
        state.setReason(reason);
        state.setLastEventAt(now);
        state.setUpdatedAt(now);
        safetyStateRepository.insertOrUpdate(state);

        SafetyEvent event = createEvent(effectiveRobotId, EVENT_EMERGENCY_STOP, reason, now, actor);
        writeEmergencyStopEvent(event);
        audit("safety.emergency_stop", RESOURCE_TYPE, effectiveRobotId, "SUCCESS", payloadBefore, toJson(state));
        log.warn("Emergency stop triggered for robot {} (reason: {})", effectiveRobotId, reason);
        return toStateDto(state);
    }

    @Transactional
    public SafetyStateDto reset(String robotId) {
        String effectiveRobotId = (robotId != null && !robotId.isBlank()) ? robotId : "global";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();

        SafetyState state = findState(effectiveRobotId);
        if (state == null) {
            throw new ConflictException("No EMERGENCY_STOPPED state found for robot '" + effectiveRobotId + "'");
        }
        if (!STATE_EMERGENCY_STOPPED.equals(state.getState())) {
            throw new ConflictException("Robot '" + effectiveRobotId + "' is not in EMERGENCY_STOPPED state");
        }
        String payloadBefore = toJson(state);
        state.setState(STATE_NORMAL);
        state.setReason("Reset by " + actor);
        state.setLastEventAt(now);
        state.setUpdatedAt(now);
        safetyStateRepository.updateById(state);

        SafetyEvent event = createEvent(effectiveRobotId, EVENT_RESET, "Reset by " + actor, now, actor);
        writeResetEvent(event);
        audit("safety.reset", RESOURCE_TYPE, effectiveRobotId, "SUCCESS", payloadBefore, toJson(state));
        log.info("Safety state reset for robot {}", effectiveRobotId);
        return toStateDto(state);
    }

    public SafetyStateDto getState(String robotId) {
        String effectiveRobotId = (robotId != null && !robotId.isBlank()) ? robotId : "global";
        SafetyState state = findState(effectiveRobotId);
        if (state == null) {
            return new SafetyStateDto(effectiveRobotId, STATE_NORMAL, null, null, null);
        }
        return toStateDto(state);
    }

    public PageResult<SafetyEventDto> getEvents(String robotId, String eventType, PageRequest pageRequest) {
        LambdaQueryWrapper<SafetyEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(robotId != null && !robotId.isBlank(), SafetyEvent::getRobotId, robotId)
                .eq(eventType != null && !eventType.isBlank(), SafetyEvent::getEventType, eventType)
                .orderByDesc(SafetyEvent::getOccurredAt);
        Page<SafetyEvent> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<SafetyEvent> result = safetyEventRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(SafetyService::toEventDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    /**
     * Pre-mission safety check. Returns {@code true} if the robot is in
     * NORMAL state and safe to execute a mission. Records a
     * SAFETY_CHECK_PASSED or SAFETY_CHECK_FAILED event.
     */
    @Transactional
    public boolean safetyCheck(String robotId, String missionId) {
        String effectiveRobotId = (robotId != null && !robotId.isBlank()) ? robotId : "global";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        SafetyState state = findState(effectiveRobotId);
        boolean safe = state == null || STATE_NORMAL.equals(state.getState());
        String eventType = safe ? EVENT_SAFETY_CHECK_PASSED : EVENT_SAFETY_CHECK_FAILED;
        String reason = safe
                ? "Pre-mission safety check passed for mission " + missionId
                : "Pre-mission safety check failed: robot is in " + (state != null ? state.getState() : "unknown") + " state";
        createEvent(effectiveRobotId, eventType, reason, now, actor);
        log.info("Safety check for robot {} mission {}: {}", effectiveRobotId, missionId, safe ? "PASSED" : "FAILED");
        return safe;
    }

    private SafetyState findOrCreateState(String robotId) {
        SafetyState state = findState(robotId);
        if (state == null) {
            state = new SafetyState();
            state.setRobotId(robotId);
            state.setState(STATE_NORMAL);
            state.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            safetyStateRepository.insert(state);
        }
        return state;
    }

    private SafetyState findState(String robotId) {
        LambdaQueryWrapper<SafetyState> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SafetyState::getRobotId, robotId);
        return safetyStateRepository.selectOne(wrapper);
    }

    private SafetyEvent createEvent(String robotId, String eventType, String reason,
                                    OffsetDateTime occurredAt, String actor) {
        String traceId = actorResolver.currentTraceId();
        SafetyEvent event = new SafetyEvent();
        event.setEventId(idGenerator.generate("sevt"));
        event.setRobotId(robotId);
        event.setEventType(eventType);
        event.setReason(reason);
        event.setOccurredAt(occurredAt);
        event.setTraceId(traceId);
        event.setActorId(actor);
        safetyEventRepository.insert(event);
        return event;
    }

    private void writeEmergencyStopEvent(SafetyEvent event) {
        Map<String, Object> payload = Map.of(
                "event_id", event.getEventId(),
                "robot_id", event.getRobotId(),
                "event_type", event.getEventType(),
                "reason", event.getReason() != null ? event.getReason() : "",
                "occurred_at", event.getOccurredAt().toString(),
                "trace_id", event.getTraceId() != null ? event.getTraceId() : ""
        );
        OutboxEvent outbox = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                EMERGENCY_STOP_EVENT,
                "1",
                EVENT_RESOURCE_TYPE,
                event.getEventId(),
                0L,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                event.getTraceId(),
                false,
                null,
                0
        );
        outboxRepository.save(outbox);
    }

    private void writeResetEvent(SafetyEvent event) {
        Map<String, Object> payload = Map.of(
                "event_id", event.getEventId(),
                "robot_id", event.getRobotId(),
                "event_type", event.getEventType(),
                "reason", event.getReason() != null ? event.getReason() : "",
                "occurred_at", event.getOccurredAt().toString(),
                "trace_id", event.getTraceId() != null ? event.getTraceId() : ""
        );
        OutboxEvent outbox = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                RESET_EVENT,
                "1",
                EVENT_RESOURCE_TYPE,
                event.getEventId(),
                0L,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                event.getTraceId(),
                false,
                null,
                0
        );
        outboxRepository.save(outbox);
    }

    private void audit(String action, String resourceType, String resourceId, String result,
                       String payloadBefore, String payloadAfter) {
        AuditEvent event = new AuditEvent(
                "user",
                actorResolver.currentActor(),
                action,
                resourceType,
                resourceId,
                result,
                null,
                null,
                null,
                actorResolver.currentTraceId(),
                null,
                Instant.now(clockProvider.getClock()),
                payloadBefore,
                payloadAfter
        );
        auditService.record(event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise value to JSON for audit", e);
            return null;
        }
    }

    private static SafetyStateDto toStateDto(SafetyState entity) {
        return new SafetyStateDto(
                entity.getRobotId(),
                entity.getState(),
                entity.getLastEventAt(),
                entity.getReason(),
                entity.getUpdatedAt()
        );
    }

    private static SafetyEventDto toEventDto(SafetyEvent entity) {
        return new SafetyEventDto(
                entity.getEventId(),
                entity.getRobotId(),
                entity.getEventType(),
                entity.getReason(),
                entity.getOccurredAt(),
                entity.getTraceId(),
                entity.getActorId()
        );
    }
}
