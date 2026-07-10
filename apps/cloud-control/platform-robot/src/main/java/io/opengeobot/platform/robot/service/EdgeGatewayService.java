/*
 * Function: Edge gateway service — identity, activation, heartbeat and certificate rotation
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.EdgeGateway;
import io.opengeobot.platform.robot.domain.EdgeGatewayCertificate;
import io.opengeobot.platform.robot.dto.ActivateEdgeGatewayRequest;
import io.opengeobot.platform.robot.dto.CreateEdgeGatewayRequest;
import io.opengeobot.platform.robot.dto.EdgeGatewayCertificateDto;
import io.opengeobot.platform.robot.dto.EdgeGatewayDto;
import io.opengeobot.platform.robot.dto.EdgeGatewayHeartbeatRequest;
import io.opengeobot.platform.robot.dto.RevokeEdgeGatewayRequest;
import io.opengeobot.platform.robot.dto.RotateCertificateRequest;
import io.opengeobot.platform.robot.repository.EdgeGatewayCertificateRepository;
import io.opengeobot.platform.robot.repository.EdgeGatewayRepository;
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
import java.util.Set;

/**
 * Application service for edge gateway identity, activation, heartbeat and
 * certificate rotation (F-EDGE-001). All mutations are recorded in the audit
 * trail.
 */
@Service
public class EdgeGatewayService {

    private static final Logger log = LoggerFactory.getLogger(EdgeGatewayService.class);
    private static final String RESOURCE_TYPE = "edge_gateway";
    private static final String STATUS_REGISTERED = "REGISTERED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final String CERT_ACTIVE = "ACTIVE";
    private static final String CERT_ROTATED = "ROTATED";
    private static final Set<String> ACTIVATABLE = Set.of(STATUS_REGISTERED, STATUS_DEGRADED);

    private final EdgeGatewayRepository gatewayRepository;
    private final EdgeGatewayCertificateRepository certificateRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public EdgeGatewayService(EdgeGatewayRepository gatewayRepository,
                              EdgeGatewayCertificateRepository certificateRepository,
                              AuditService auditService,
                              ActorResolver actorResolver,
                              ClockProvider clockProvider,
                              PublicIdGenerator idGenerator,
                              ObjectMapper objectMapper) {
        this.gatewayRepository = gatewayRepository;
        this.certificateRepository = certificateRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<EdgeGatewayDto> list(PageRequest pageRequest, String status, String orgId) {
        LambdaQueryWrapper<EdgeGateway> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), EdgeGateway::getStatus, status)
                .eq(orgId != null && !orgId.isBlank(), EdgeGateway::getOrgId, orgId)
                .orderByDesc(EdgeGateway::getCreatedAt);
        Page<EdgeGateway> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<EdgeGateway> result = gatewayRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(EdgeGatewayService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public EdgeGatewayDto getByGatewayId(String gatewayId) {
        return toDto(requireGateway(gatewayId));
    }

    @Transactional
    public EdgeGatewayDto register(CreateEdgeGatewayRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String gatewayId = idGenerator.generate("gw");
        EdgeGateway entity = new EdgeGateway();
        entity.setGatewayId(gatewayId);
        entity.setName(request.name());
        entity.setOrgId(request.orgId());
        entity.setStatus(STATUS_REGISTERED);
        entity.setBoundRobotId(blankToNull(request.boundRobotId()));
        entity.setRuntimeVersion(blankToNull(request.runtimeVersion()));
        entity.setCertificateFingerprint(blankToNull(request.certificateFingerprint()));
        entity.setCertificateExpiresAt(request.certificateExpiresAt());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        gatewayRepository.insert(entity);

        if (entity.getCertificateFingerprint() != null) {
            insertCertificate(gatewayId, entity.getCertificateFingerprint(),
                    now, request.certificateExpiresAt() != null ? request.certificateExpiresAt() : now.plusYears(1),
                    now);
        }

        audit("edge_gateway.register", gatewayId, null, toJson(entity));
        log.info("Registered edge gateway {} ({})", gatewayId, entity.getName());
        return toDto(entity);
    }

    @Transactional
    public EdgeGatewayDto activate(String gatewayId, ActivateEdgeGatewayRequest request) {
        EdgeGateway entity = requireGateway(gatewayId);
        if (STATUS_REVOKED.equals(entity.getStatus())) {
            throw new ConflictException("Edge gateway '" + gatewayId + "' is revoked and cannot be activated");
        }
        if (!ACTIVATABLE.contains(entity.getStatus()) && !STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new ConflictException(
                    "Edge gateway '" + gatewayId + "' cannot transition from " + entity.getStatus() + " to ACTIVE");
        }
        if (STATUS_ACTIVE.equals(entity.getStatus())) {
            return toDto(entity);
        }
        String payloadBefore = toJson(entity);
        entity.setStatus(STATUS_ACTIVE);
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        gatewayRepository.updateById(entity);
        audit("edge_gateway.activate", gatewayId, payloadBefore, toJson(entity));
        log.info("Activated edge gateway {} reason={}", gatewayId,
                request != null ? request.reason() : null);
        return toDto(entity);
    }

    @Transactional
    public EdgeGatewayDto revoke(String gatewayId, RevokeEdgeGatewayRequest request) {
        EdgeGateway entity = requireGateway(gatewayId);
        if (STATUS_REVOKED.equals(entity.getStatus())) {
            throw new ConflictException("Edge gateway '" + gatewayId + "' is already revoked");
        }
        String payloadBefore = toJson(entity);
        entity.setStatus(STATUS_REVOKED);
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        gatewayRepository.updateById(entity);

        EdgeGatewayCertificate activeCert = certificateRepository.findActiveByGatewayId(gatewayId);
        if (activeCert != null) {
            activeCert.setStatus("REVOKED");
            certificateRepository.updateById(activeCert);
        }

        audit("edge_gateway.revoke", gatewayId, payloadBefore, toJson(entity));
        log.info("Revoked edge gateway {} reason={}", gatewayId,
                request != null ? request.reason() : null);
        return toDto(entity);
    }

    @Transactional
    public EdgeGatewayDto heartbeat(String gatewayId, EdgeGatewayHeartbeatRequest request) {
        EdgeGateway entity = requireGateway(gatewayId);
        if (STATUS_REVOKED.equals(entity.getStatus())) {
            throw new ConflictException("Edge gateway '" + gatewayId + "' is revoked and cannot accept heartbeats");
        }
        String payloadBefore = toJson(entity);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entity.setLastHeartbeatAt(now);
        if (request != null && request.runtimeVersion() != null && !request.runtimeVersion().isBlank()) {
            entity.setRuntimeVersion(request.runtimeVersion());
        }
        entity.setUpdatedAt(now);
        gatewayRepository.updateById(entity);
        audit("edge_gateway.heartbeat", gatewayId, payloadBefore, toJson(entity));
        log.debug("Heartbeat accepted for edge gateway {}", gatewayId);
        return toDto(entity);
    }

    @Transactional
    public EdgeGatewayCertificateDto rotateCertificate(String gatewayId, RotateCertificateRequest request) {
        EdgeGateway entity = requireGateway(gatewayId);
        if (STATUS_REVOKED.equals(entity.getStatus())) {
            throw new ConflictException("Edge gateway '" + gatewayId + "' is revoked; certificate rotation denied");
        }
        String payloadBefore = toJson(entity);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime issuedAt = request.issuedAt() != null ? request.issuedAt() : now;

        EdgeGatewayCertificate previous = certificateRepository.findActiveByGatewayId(gatewayId);
        if (previous != null) {
            previous.setStatus(CERT_ROTATED);
            certificateRepository.updateById(previous);
        }

        EdgeGatewayCertificate created = insertCertificate(
                gatewayId, request.fingerprint(), issuedAt, request.expiresAt(), now);

        entity.setCertificateFingerprint(request.fingerprint());
        entity.setCertificateExpiresAt(request.expiresAt());
        entity.setUpdatedAt(now);
        gatewayRepository.updateById(entity);

        audit("edge_gateway.certificate_rotate", gatewayId, payloadBefore, toJson(entity));
        log.info("Rotated certificate for edge gateway {} -> {}", gatewayId, created.getCertId());
        return toCertificateDto(created);
    }

    private EdgeGatewayCertificate insertCertificate(String gatewayId, String fingerprint,
                                                     OffsetDateTime issuedAt, OffsetDateTime expiresAt,
                                                     OffsetDateTime createdAt) {
        EdgeGatewayCertificate cert = new EdgeGatewayCertificate();
        cert.setCertId(idGenerator.generate("cert"));
        cert.setGatewayId(gatewayId);
        cert.setFingerprint(fingerprint);
        cert.setIssuedAt(issuedAt);
        cert.setExpiresAt(expiresAt);
        cert.setStatus(CERT_ACTIVE);
        cert.setCreatedAt(createdAt);
        certificateRepository.insert(cert);
        return cert;
    }

    private EdgeGateway requireGateway(String gatewayId) {
        EdgeGateway entity = gatewayRepository.findByGatewayId(gatewayId);
        if (entity == null) {
            throw new ResourceNotFoundException("Edge gateway '" + gatewayId + "' not found");
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static EdgeGatewayDto toDto(EdgeGateway entity) {
        return new EdgeGatewayDto(
                entity.getGatewayId(),
                entity.getName(),
                entity.getOrgId(),
                entity.getStatus(),
                entity.getCertificateFingerprint(),
                entity.getCertificateExpiresAt(),
                entity.getRuntimeVersion(),
                entity.getBoundRobotId(),
                entity.getLastHeartbeatAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static EdgeGatewayCertificateDto toCertificateDto(EdgeGatewayCertificate entity) {
        return new EdgeGatewayCertificateDto(
                entity.getCertId(),
                entity.getGatewayId(),
                entity.getFingerprint(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
