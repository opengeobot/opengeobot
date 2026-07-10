/*
 * Function: Control lease service — acquire, release and active lease lookup with fencing
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.ControlLease;
import io.opengeobot.platform.robot.dto.AcquireControlLeaseRequest;
import io.opengeobot.platform.robot.dto.ControlLeaseDto;
import io.opengeobot.platform.robot.repository.ControlLeaseRepository;
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

/**
 * Application service for robot control leases (F-MONITOR-001 / SM-CONTROL-001).
 * ACTIVE leases carry a unique fencing token. Expired ACTIVE leases are marked
 * EXPIRED before acquire/get/release decisions.
 */
@Service
public class ControlLeaseService {

    private static final Logger log = LoggerFactory.getLogger(ControlLeaseService.class);
    private static final String RESOURCE_TYPE = "control_lease";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_RELEASED = "RELEASED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final int DEFAULT_TTL_SECONDS = 300;

    private final ControlLeaseRepository leaseRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public ControlLeaseService(ControlLeaseRepository leaseRepository,
                               AuditService auditService,
                               ActorResolver actorResolver,
                               ClockProvider clockProvider,
                               PublicIdGenerator idGenerator,
                               ObjectMapper objectMapper) {
        this.leaseRepository = leaseRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ControlLeaseDto acquire(String robotId, AcquireControlLeaseRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        expireIfNeeded(leaseRepository.findActiveByRobotId(robotId), now);

        ControlLease existing = leaseRepository.findActiveByRobotId(robotId);
        if (existing != null) {
            throw new ConflictException(
                    "Robot '" + robotId + "' already has an active control lease '" + existing.getLeaseId() + "'");
        }

        int ttlSeconds = DEFAULT_TTL_SECONDS;
        if (request != null && request.ttlSeconds() != null) {
            ttlSeconds = request.ttlSeconds();
        }

        ControlLease entity = new ControlLease();
        entity.setLeaseId(idGenerator.generate("lease"));
        entity.setRobotId(robotId);
        entity.setHolderUserId(actorResolver.currentActor());
        entity.setStatus(STATUS_ACTIVE);
        entity.setAcquiredAt(now);
        entity.setExpiresAt(now.plusSeconds(ttlSeconds));
        entity.setFencingToken(idGenerator.generate("ftk"));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        leaseRepository.insert(entity);

        audit("control_lease.acquire", entity.getLeaseId(), null, toJson(entity));
        log.info("Acquired control lease {} for robot {} fencing={}",
                entity.getLeaseId(), robotId, entity.getFencingToken());
        return toDto(entity);
    }

    @Transactional
    public ControlLeaseDto getActive(String robotId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ControlLease entity = leaseRepository.findActiveByRobotId(robotId);
        entity = expireIfNeeded(entity, now);
        if (entity == null || !STATUS_ACTIVE.equals(entity.getStatus())) {
            return null;
        }
        return toDto(entity);
    }

    @Transactional
    public ControlLeaseDto release(String robotId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ControlLease entity = leaseRepository.findActiveByRobotId(robotId);
        entity = expireIfNeeded(entity, now);
        if (entity == null || !STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new ResourceNotFoundException(
                    "No active control lease for robot '" + robotId + "'");
        }
        String payloadBefore = toJson(entity);
        entity.setStatus(STATUS_RELEASED);
        entity.setReleasedAt(now);
        entity.setUpdatedAt(now);
        leaseRepository.updateById(entity);
        audit("control_lease.release", entity.getLeaseId(), payloadBefore, toJson(entity));
        log.info("Released control lease {} for robot {}", entity.getLeaseId(), robotId);
        return toDto(entity);
    }

    private ControlLease expireIfNeeded(ControlLease entity, OffsetDateTime now) {
        if (entity == null) {
            return null;
        }
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            return entity;
        }
        if (entity.getExpiresAt() != null && !entity.getExpiresAt().isAfter(now)) {
            String payloadBefore = toJson(entity);
            entity.setStatus(STATUS_EXPIRED);
            entity.setReleasedAt(now);
            entity.setUpdatedAt(now);
            leaseRepository.updateById(entity);
            audit("control_lease.expire", entity.getLeaseId(), payloadBefore, toJson(entity));
            log.info("Expired control lease {} for robot {}", entity.getLeaseId(), entity.getRobotId());
            return entity;
        }
        return entity;
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

    private static ControlLeaseDto toDto(ControlLease entity) {
        return new ControlLeaseDto(
                entity.getLeaseId(),
                entity.getRobotId(),
                entity.getHolderUserId(),
                entity.getStatus(),
                entity.getAcquiredAt(),
                entity.getExpiresAt(),
                entity.getReleasedAt(),
                entity.getFencingToken(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
