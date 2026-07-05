/*
 * Function: Export service — creates and manages asynchronous data export tasks
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.governance.domain.export.ExportOperation;
import io.opengeobot.platform.governance.dto.CreateExportRequest;
import io.opengeobot.platform.governance.dto.ExportTaskDto;
import io.opengeobot.platform.governance.export.repository.ExportOperationRepository;
import io.opengeobot.platform.governance.web.ActorResolver;
import io.opengeobot.platform.governance.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

/**
 * Service for asynchronous data export. Creates an export task, emits an
 * {@code export.requested.v1} event, and delegates processing to the
 * {@link ExportWorker}. The worker emits {@code export.completed.v1} when
 * the export reaches a terminal state.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final String EXPORT_REQUESTED_EVENT = "export.requested.v1";
    private static final String RESOURCE_TYPE = "export_operation";
    private static final String DOWNLOAD_URL_PREFIX = "/api/v1/exports/";

    private static final Set<String> SUPPORTED_RESOURCE_TYPES = Set.of("audit_log", "dict_type", "config");
    private static final Set<String> SUPPORTED_FORMATS = Set.of("csv", "json");

    private final ExportOperationRepository exportRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final ExportWorker exportWorker;

    public ExportService(ExportOperationRepository exportRepository,
                         OutboxRepository outboxRepository,
                         AuditService auditService,
                         ActorResolver actorResolver,
                         ClockProvider clockProvider,
                         PublicIdGenerator idGenerator,
                         ObjectMapper objectMapper,
                         ExportWorker exportWorker) {
        this.exportRepository = exportRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.exportWorker = exportWorker;
    }

    @Transactional
    public ExportTaskDto createExport(CreateExportRequest request, String userId) {
        if (!SUPPORTED_RESOURCE_TYPES.contains(request.resourceType())) {
            throw new IllegalArgumentException("Unsupported export resource type: " + request.resourceType());
        }
        String format = request.format() != null ? request.format() : "csv";
        if (!SUPPORTED_FORMATS.contains(format)) {
            throw new IllegalArgumentException("Unsupported export format: " + format);
        }

        String exportId = idGenerator.generate("exp");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String traceId = actorResolver.currentTraceId();

        ExportOperation entity = new ExportOperation();
        entity.setExportId(exportId);
        entity.setResourceType(request.resourceType());
        entity.setFormat(format);
        entity.setFilter(request.filter());
        entity.setStatus("PENDING");
        entity.setRequestedBy(userId);
        entity.setTraceId(traceId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        exportRepository.insert(entity);

        writeExportEvent(EXPORT_REQUESTED_EVENT, exportId, request.resourceType(), "PENDING");
        audit("export.create", exportId, "SUCCESS", null, toJson(Map.of(
                "resource_type", request.resourceType(), "format", format)));
        log.info("Created export task {} for resource type {}", exportId, request.resourceType());

        exportWorker.processExport(exportId);

        return toDto(entity);
    }

    public ExportTaskDto getExport(String exportId) {
        ExportOperation entity = findByExportId(exportId);
        return toDto(entity);
    }

    public ExportOperation getExportEntity(String exportId) {
        return findByExportId(exportId);
    }

    /**
     * Returns the download URL for a completed export.
     * @throws IllegalStateException if export is not ready
     */
    public String downloadExport(String exportId) {
        ExportOperation entity = findByExportId(exportId);
        if (!"SUCCEEDED".equals(entity.getStatus())) {
            throw new IllegalStateException("Export " + exportId + " is not ready for download (status=" + entity.getStatus() + ")");
        }
        if (entity.getFileUrl() == null || entity.getFileUrl().isBlank()) {
            throw new IllegalStateException("Export " + exportId + " has no file URL");
        }
        return entity.getFileUrl();
    }

    public String getFilePath(String exportId) {
        ExportOperation entity = findByExportId(exportId);
        if (!"SUCCEEDED".equals(entity.getStatus())) {
            throw new IllegalStateException("Export " + exportId + " is not ready for download (status=" + entity.getStatus() + ")");
        }
        return entity.getFilePath();
    }

    private ExportOperation findByExportId(String exportId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExportOperation> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(ExportOperation::getExportId, exportId);
        ExportOperation entity = exportRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Export task '" + exportId + "' not found");
        }
        return entity;
    }

    private void writeExportEvent(String eventType, String exportId, String resourceType, String status) {
        Map<String, Object> payload = Map.of(
                "export_id", exportId,
                "resource_type", resourceType,
                "status", status
        );
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                eventType,
                "1",
                RESOURCE_TYPE,
                exportId,
                1L,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                actorResolver.currentTraceId(),
                false,
                null,
                0
        );
        outboxRepository.save(event);
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

    static ExportTaskDto toDto(ExportOperation entity) {
        return new ExportTaskDto(
                entity.getExportId(),
                entity.getResourceType(),
                entity.getFormat(),
                entity.getStatus(),
                entity.getFileUrl(),
                entity.getFileSize(),
                entity.getErrorMessage(),
                entity.getRequestedBy(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getTraceId()
        );
    }
}
