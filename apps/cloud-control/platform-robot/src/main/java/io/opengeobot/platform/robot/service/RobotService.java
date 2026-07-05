/*
 * Function: Robot service — CRUD, status transitions, capabilities for F-ROBOT-001
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
import io.opengeobot.platform.common.robot.RobotStatus;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.Robot;
import io.opengeobot.platform.robot.domain.RobotCapability;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application service for robot lifecycle management (F-ROBOT-001). Handles
 * registration, updates, deletion, status transitions and capability
 * management. All mutations emit domain events via the transactional outbox
 * and are recorded in the audit trail. Status transitions are validated
 * against the SM-ROBOT-001 state machine.
 */
@Service
public class RobotService {

    private static final Logger log = LoggerFactory.getLogger(RobotService.class);
    private static final String ROBOT_REGISTERED_EVENT = "robot.registered.v1";
    private static final String ROBOT_STATUS_CHANGED_EVENT = "robot.status_changed.v1";
    private static final String ROBOT_MANIFEST_CHANGED_EVENT = "robot.manifest_changed.v1";
    private static final String RESOURCE_TYPE = "robot";
    private static final Set<RobotStatus> DELETABLE_STATUSES = EnumSet.of(RobotStatus.OFFLINE);

    private final RobotRepository robotRepository;
    private final RobotCapabilityRepository capabilityRepository;
    private final RobotStatusHistoryRepository statusHistoryRepository;
    private final RobotModelRepository modelRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public RobotService(RobotRepository robotRepository,
                        RobotCapabilityRepository capabilityRepository,
                        RobotStatusHistoryRepository statusHistoryRepository,
                        RobotModelRepository modelRepository,
                        OutboxRepository outboxRepository,
                        AuditService auditService,
                        ActorResolver actorResolver,
                        ClockProvider clockProvider,
                        PublicIdGenerator idGenerator,
                        ObjectMapper objectMapper) {
        this.robotRepository = robotRepository;
        this.capabilityRepository = capabilityRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.modelRepository = modelRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<RobotDto> list(PageRequest pageRequest, String status, String modelId, String orgId) {
        LambdaQueryWrapper<Robot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), Robot::getStatus, status)
                .eq(modelId != null && !modelId.isBlank(), Robot::getModelId, modelId)
                .eq(orgId != null && !orgId.isBlank(), Robot::getOrgId, orgId)
                .orderByAsc(Robot::getName);
        Page<Robot> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<Robot> result = robotRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(this::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public RobotDto getByRobotId(String robotId) {
        return toDto(requireRobot(robotId));
    }

    @Transactional
    public RobotDto create(CreateRobotRequest request) {
        if (existsBySerialNumber(request.serialNumber())) {
            throw new ConflictException("Robot with serial number '" + request.serialNumber() + "' already exists");
        }
        if (modelRepository.findByModelId(request.modelId()) == null) {
            throw new ResourceNotFoundException("Robot model '" + request.modelId() + "' not found");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        String traceId = actorResolver.currentTraceId();
        Robot entity = new Robot();
        entity.setRobotId(idGenerator.generate("rbt"));
        entity.setName(request.name());
        entity.setModelId(request.modelId());
        entity.setSerialNumber(request.serialNumber());
        entity.setStatus(RobotStatus.OFFLINE.name());
        entity.setOrgId(request.orgId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        robotRepository.insert(entity);

        if (request.capabilities() != null && !request.capabilities().isEmpty()) {
            persistCapabilities(entity.getRobotId(), request.capabilities(), now);
        }

        writeRobotRegisteredEvent(entity, traceId);
        audit("robot.create", entity.getRobotId(), null, toJson(entity));
        log.info("Registered robot {} ({})", entity.getRobotId(), entity.getName());
        return toDto(entity);
    }

    @Transactional
    public RobotDto update(String robotId, UpdateRobotRequest request) {
        Robot entity = requireRobot(robotId);
        String payloadBefore = toJson(entity);
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.orgId() != null) {
            entity.setOrgId(request.orgId());
        }
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        robotRepository.updateById(entity);
        audit("robot.update", robotId, payloadBefore, toJson(entity));
        log.info("Updated robot {}", robotId);
        return toDto(entity);
    }

    @Transactional
    public void delete(String robotId) {
        Robot entity = requireRobot(robotId);
        RobotStatus currentStatus = RobotStatus.valueOf(entity.getStatus());
        if (!DELETABLE_STATUSES.contains(currentStatus)) {
            throw new ConflictException("Cannot delete robot '" + robotId + "' with non-terminal status " + currentStatus);
        }
        String payloadBefore = toJson(entity);
        capabilityRepository.deleteByRobotId(robotId);
        robotRepository.deleteById(entity.getId());
        audit("robot.delete", robotId, payloadBefore, null);
        log.info("Deleted robot {}", robotId);
    }

    @Transactional
    public void updateStatus(String robotId, UpdateRobotStatusRequest request) {
        Robot entity = requireRobot(robotId);
        RobotStatus oldStatus = RobotStatus.valueOf(entity.getStatus());
        RobotStatus newStatus = RobotStatus.valueOf(request.status());
        if (!canTransition(oldStatus, newStatus)) {
            throw new ConflictException(
                    "Status transition from " + oldStatus + " to " + newStatus + " is not allowed by SM-ROBOT-001");
        }
        String payloadBefore = toJson(entity);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String traceId = actorResolver.currentTraceId();

        entity.setStatus(newStatus.name());
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(now);
        robotRepository.updateById(entity);

        RobotStatusHistory history = new RobotStatusHistory();
        history.setRobotId(robotId);
        history.setOldStatus(oldStatus.name());
        history.setNewStatus(newStatus.name());
        history.setReason(request.reason());
        history.setOccurredAt(now);
        history.setTraceId(traceId);
        statusHistoryRepository.insert(history);

        writeStatusChangedEvent(robotId, oldStatus.name(), newStatus.name(), request.reason(), traceId);
        audit("robot.status_change", robotId, payloadBefore, toJson(entity));
        log.info("Robot {} status transitioned {} -> {}", robotId, oldStatus, newStatus);
    }

    public List<RobotCapabilityDto> getCapabilities(String robotId) {
        requireRobot(robotId);
        List<RobotCapability> capabilities = capabilityRepository.findByRobotId(robotId);
        return capabilities.stream().map(RobotService::toCapabilityDto).toList();
    }

    @Transactional
    public void updateCapabilities(String robotId, List<RobotCapabilityDto> capabilities) {
        Robot entity = requireRobot(robotId);
        String payloadBefore = toJson(entity);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        capabilityRepository.deleteByRobotId(robotId);
        if (capabilities != null && !capabilities.isEmpty()) {
            persistCapabilities(robotId, capabilities, now);
        }
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(now);
        robotRepository.updateById(entity);
        writeManifestChangedEvent(robotId, actorResolver.currentTraceId());
        audit("robot.capabilities_update", robotId, payloadBefore, toJson(entity));
        log.info("Updated capabilities for robot {}", robotId);
    }

    // ----- helpers -----

    private Robot requireRobot(String robotId) {
        Robot entity = robotRepository.findByRobotId(robotId);
        if (entity == null) {
            throw new ResourceNotFoundException("Robot '" + robotId + "' not found");
        }
        return entity;
    }

    private boolean existsBySerialNumber(String serialNumber) {
        LambdaQueryWrapper<Robot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Robot::getSerialNumber, serialNumber);
        return robotRepository.selectCount(wrapper) > 0;
    }

    private void persistCapabilities(String robotId, List<RobotCapabilityDto> capabilities, OffsetDateTime now) {
        for (RobotCapabilityDto dto : capabilities) {
            RobotCapability entity = new RobotCapability();
            entity.setRobotId(robotId);
            entity.setCapabilityType(dto.capabilityType());
            entity.setCapabilityValue(dto.capabilityValue());
            entity.setDetails(dto.details());
            entity.setCreatedAt(now);
            capabilityRepository.insert(entity);
        }
    }

    /**
     * Validates a status transition against the SM-ROBOT-001 state machine.
     * OFFLINE is the resting state; ONLINE/BUSY indicate active operation;
     * MAINTENANCE is an admin-supervised state; ERROR is a fault state.
     */
    private boolean canTransition(RobotStatus from, RobotStatus to) {
        if (from == to) {
            return true;
        }
        return switch (from) {
            case OFFLINE -> to == RobotStatus.ONLINE || to == RobotStatus.MAINTENANCE || to == RobotStatus.ERROR;
            case ONLINE -> to == RobotStatus.OFFLINE || to == RobotStatus.BUSY
                    || to == RobotStatus.MAINTENANCE || to == RobotStatus.ERROR;
            case BUSY -> to == RobotStatus.ONLINE || to == RobotStatus.OFFLINE
                    || to == RobotStatus.MAINTENANCE || to == RobotStatus.ERROR;
            case MAINTENANCE -> to == RobotStatus.ONLINE || to == RobotStatus.OFFLINE || to == RobotStatus.ERROR;
            case ERROR -> to == RobotStatus.MAINTENANCE || to == RobotStatus.OFFLINE;
        };
    }

    private void writeRobotRegisteredEvent(Robot entity, String traceId) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "robot_id", entity.getRobotId(),
                "name", entity.getName(),
                "model_id", entity.getModelId(),
                "org_id", entity.getOrgId(),
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", traceId != null ? traceId : ""
        );
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                ROBOT_REGISTERED_EVENT,
                "1",
                RESOURCE_TYPE,
                entity.getRobotId(),
                null,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                traceId,
                false,
                null,
                0
        );
        outboxRepository.save(event);
    }

    private void writeStatusChangedEvent(String robotId, String oldStatus, String newStatus,
                                        String reason, String traceId) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "robot_id", robotId,
                "old_status", oldStatus,
                "new_status", newStatus,
                "reason", reason != null ? reason : "",
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", traceId != null ? traceId : ""
        );
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                ROBOT_STATUS_CHANGED_EVENT,
                "1",
                RESOURCE_TYPE,
                robotId,
                null,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                traceId,
                false,
                null,
                0
        );
        outboxRepository.save(event);
    }

    private void writeManifestChangedEvent(String robotId, String traceId) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "robot_id", robotId,
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", traceId != null ? traceId : ""
        );
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                ROBOT_MANIFEST_CHANGED_EVENT,
                "1",
                RESOURCE_TYPE,
                robotId,
                null,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                traceId,
                false,
                null,
                0
        );
        outboxRepository.save(event);
    }

    private void audit(String action, String resourceId, String payloadBefore, String payloadAfter) {
        AuditEvent event = new AuditEvent(
                "user",
                actorResolver.currentActor(),
                action,
                RESOURCE_TYPE,
                resourceId,
                "SUCCESS",
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

    private RobotDto toDto(Robot entity) {
        List<RobotCapability> capabilities = capabilityRepository.findByRobotId(entity.getRobotId());
        return new RobotDto(
                entity.getRobotId(),
                entity.getName(),
                entity.getModelId(),
                entity.getSerialNumber(),
                entity.getStatus(),
                entity.getOrgId(),
                capabilities.stream().map(RobotService::toCapabilityDto).toList(),
                entity.getLastSeenAt(),
                entity.getMetadata(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static RobotCapabilityDto toCapabilityDto(RobotCapability entity) {
        return new RobotCapabilityDto(
                entity.getCapabilityType(),
                entity.getCapabilityValue(),
                entity.getDetails()
        );
    }
}
