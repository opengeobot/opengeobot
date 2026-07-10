/*
 * Function: Adapter service - compatibility queries and adapter health management
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application service for adapter compatibility and health management
 * (F-ADAPTER-002). Adapters describe how a robot hardware model maps to a
 * protocol (ROS2 / ROS1 / Unitree / custom). Health status updates emit an
 * {@code adapter.health_changed.v1} event via the transactional outbox and are
 * recorded in the audit trail. The {@code adapter_type} and
 * {@code health_status} values are platform code contracts validated here.
 */
@Service
public class AdapterService {

    private static final Logger log = LoggerFactory.getLogger(AdapterService.class);
    private static final String RESOURCE_TYPE = "adapter_compatibility";
    private static final String HEALTH_CHANGED_EVENT = "adapter.health_changed.v1";
    private static final String HEALTH_HEALTHY = "HEALTHY";
    private static final String HEALTH_DEGRADED = "DEGRADED";
    private static final String HEALTH_UNHEALTHY = "UNHEALTHY";
    private static final String HEALTH_UNKNOWN = "UNKNOWN";
    private static final Set<String> ALLOWED_HEALTH_STATUSES =
            Set.of(HEALTH_HEALTHY, HEALTH_DEGRADED, HEALTH_UNHEALTHY, HEALTH_UNKNOWN);

    private final AdapterCompatibilityRepository adapterRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public AdapterService(AdapterCompatibilityRepository adapterRepository,
                          OutboxRepository outboxRepository,
                          AuditService auditService,
                          ActorResolver actorResolver,
                          ClockProvider clockProvider,
                          PublicIdGenerator idGenerator,
                          ObjectMapper objectMapper) {
        this.adapterRepository = adapterRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the adapter compatibility entries for a robot model.
     */
    public List<AdapterCompatibilityDto> listByRobotModel(String robotModelId) {
        return adapterRepository.findAllByRobotModelId(robotModelId).stream()
                .map(AdapterService::toDto)
                .toList();
    }

    /**
     * Returns the current health status of an adapter compatibility entry.
     */
    public AdapterCompatibilityDto getHealth(String adapterId) {
        return toDto(requireAdapter(adapterId));
    }

    /**
     * Updates the runtime health status of an adapter. Emits an
     * {@code adapter.health_changed.v1} outbox event when the status changes.
     */
    @Transactional
    public AdapterCompatibilityDto updateHealth(String adapterId, UpdateAdapterHealthRequest request) {
        String normalized = normalizeHealth(request.healthStatus());
        AdapterCompatibility entity = requireAdapter(adapterId);
        String previousHealth = entity.getHealthStatus();
        String payloadBefore = toJson(entity);
        entity.setHealthStatus(normalized);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entity.setUpdatedAt(now);
        adapterRepository.updateById(entity);

        if (previousHealth == null || !previousHealth.equals(normalized)) {
            writeHealthChangedEvent(entity, previousHealth, normalized, request.reason());
        }
        audit("adapter.update_health", adapterId, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Adapter {} health updated: {} -> {}", adapterId, previousHealth, normalized);
        return toDto(entity);
    }

    // ----- helpers -----

    private AdapterCompatibility requireAdapter(String adapterId) {
        AdapterCompatibility entity = adapterRepository.findByAdapterId(adapterId);
        if (entity == null) {
            throw new ResourceNotFoundException("Adapter '" + adapterId + "' not found");
        }
        return entity;
    }

    private String normalizeHealth(String healthStatus) {
        if (healthStatus == null) {
            throw new IllegalArgumentException("health_status must not be blank");
        }
        String normalized = healthStatus.trim().toUpperCase();
        if (!ALLOWED_HEALTH_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid health_status '" + healthStatus + "'. Allowed values: "
                            + String.join(", ", HEALTH_HEALTHY, HEALTH_DEGRADED, HEALTH_UNHEALTHY, HEALTH_UNKNOWN));
        }
        return normalized;
    }

    private void writeHealthChangedEvent(AdapterCompatibility entity, String previousHealth,
                                         String currentHealth, String reason) {
        String traceId = actorResolver.currentTraceId();
        Map<String, Object> payload = Map.of(
                "adapter_id", entity.getAdapterId(),
                "robot_model_id", entity.getRobotModelId() != null ? entity.getRobotModelId() : "",
                "adapter_type", entity.getAdapterType() != null ? entity.getAdapterType() : "",
                "previous_health_status", previousHealth != null ? previousHealth : "",
                "health_status", currentHealth,
                "reason", reason != null ? reason : "",
                "occurred_at", OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "trace_id", traceId != null ? traceId : ""
        );
        OutboxEvent outbox = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                HEALTH_CHANGED_EVENT,
                "1",
                RESOURCE_TYPE,
                entity.getAdapterId(),
                0L,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                traceId,
                false,
                null,
                0
        );
        outboxRepository.save(outbox);
    }

    private void audit(String action, String resourceId, String result,
                       String payloadBefore, String payloadAfter) {
        AuditEvent event = new AuditEvent(
                "user",
                actorResolver.currentActor(),
                action,
                RESOURCE_TYPE,
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

    private static AdapterCompatibilityDto toDto(AdapterCompatibility entity) {
        return new AdapterCompatibilityDto(
                entity.getAdapterId(),
                entity.getRobotModelId(),
                entity.getAdapterType(),
                entity.getRosVersion(),
                entity.getControlProtocol(),
                entity.getCompatible(),
                entity.getHealthStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
