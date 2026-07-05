/*
 * Function: Export worker — processes export tasks asynchronously
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.governance.audit.repository.AuditRepository;
import io.opengeobot.platform.governance.config.repository.ConfigRepository;
import io.opengeobot.platform.governance.dict.repository.DictItemRepository;
import io.opengeobot.platform.governance.dict.repository.DictTypeRepository;
import io.opengeobot.platform.governance.domain.audit.OperationAudit;
import io.opengeobot.platform.governance.domain.config.SysConfig;
import io.opengeobot.platform.governance.domain.dict.DictItem;
import io.opengeobot.platform.governance.domain.dict.DictType;
import io.opengeobot.platform.governance.domain.export.ExportOperation;
import io.opengeobot.platform.governance.dto.ExportTaskDto;
import io.opengeobot.platform.governance.export.repository.ExportOperationRepository;
import io.opengeobot.platform.governance.web.ActorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Asynchronous export processor. Runs on the dedicated {@code exportExecutor}
 * thread pool so export work does not block request threads. The worker
 * generates the export file, updates the export operation to a terminal state,
 * and emits an {@code export.completed.v1} event via the transactional outbox.
 */
@Component
public class ExportWorker {

    private static final Logger log = LoggerFactory.getLogger(ExportWorker.class);
    private static final String EXPORT_COMPLETED_EVENT = "export.completed.v1";
    private static final String RESOURCE_TYPE = "export_operation";
    private static final String EXPORT_DIR = "opengeobot-exports";

    private final ExportOperationRepository exportRepository;
    private final AuditRepository auditRepository;
    private final DictTypeRepository dictTypeRepository;
    private final DictItemRepository dictItemRepository;
    private final ConfigRepository configRepository;
    private final OutboxRepository outboxRepository;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public ExportWorker(ExportOperationRepository exportRepository,
                        AuditRepository auditRepository,
                        DictTypeRepository dictTypeRepository,
                        DictItemRepository dictItemRepository,
                        ConfigRepository configRepository,
                        OutboxRepository outboxRepository,
                        ActorResolver actorResolver,
                        ClockProvider clockProvider,
                        PublicIdGenerator idGenerator,
                        ObjectMapper objectMapper) {
        this.exportRepository = exportRepository;
        this.auditRepository = auditRepository;
        this.dictTypeRepository = dictTypeRepository;
        this.dictItemRepository = dictItemRepository;
        this.configRepository = configRepository;
        this.outboxRepository = outboxRepository;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Async("exportExecutor")
    public void processExport(String exportId) {
        try {
            ExportOperation entity = findByExportId(exportId);
            log.info("Processing export {} for resource type {}", exportId, entity.getResourceType());

            entity.setStatus("RUNNING");
            entity.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
            entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            exportRepository.updateById(entity);

            Path filePath = generateFile(entity);
            long fileSize = Files.size(filePath);

            entity.setStatus("SUCCEEDED");
            entity.setFilePath(filePath.toString());
            entity.setFileUrl("/api/v1/exports/" + exportId + "/download");
            entity.setFileSize(fileSize);
            entity.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            exportRepository.updateById(entity);

            writeCompletedEvent(exportId, "SUCCEEDED", null);
            log.info("Export {} completed successfully ({} bytes)", exportId, fileSize);
        } catch (Exception e) {
            log.error("Export {} failed", exportId, e);
            markFailed(exportId, e.getMessage());
        }
    }

    private Path generateFile(ExportOperation entity) throws IOException {
        Path exportDir = Paths.get(System.getProperty("java.io.tmpdir"), EXPORT_DIR);
        Files.createDirectories(exportDir);
        Path filePath = exportDir.resolve(entity.getExportId() + "." + entity.getFormat());

        String resourceType = entity.getResourceType();
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            switch (resourceType) {
                case "audit_log" -> writeAuditLogs(writer, entity);
                case "dict_type" -> writeDictTypes(writer, entity);
                case "config" -> writeConfigs(writer, entity);
                default -> throw new IllegalArgumentException("Unsupported resource type: " + resourceType);
            }
        }
        return filePath;
    }

    private void writeAuditLogs(BufferedWriter writer, ExportOperation entity) throws IOException {
        writer.write("audit_id,occurred_at,actor_type,actor_id,action,resource_type,resource_id,result,trace_id,source_ip\n");
        int pageNum = 1;
        while (true) {
            Page<OperationAudit> page = new Page<>(pageNum, 1000);
            LambdaQueryWrapper<OperationAudit> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(OperationAudit::getOccurredAt);
            Page<OperationAudit> result = auditRepository.selectPage(page, wrapper);
            for (OperationAudit audit : result.getRecords()) {
                writer.write(String.join(",",
                        csv(audit.getAuditId()),
                        csv(String.valueOf(audit.getOccurredAt())),
                        csv(audit.getActorType()),
                        csv(audit.getActorId()),
                        csv(audit.getAction()),
                        csv(audit.getResourceType()),
                        csv(audit.getResourceId()),
                        csv(audit.getResult()),
                        csv(audit.getTraceId()),
                        csv(audit.getSourceIp())));
                writer.newLine();
            }
            if (result.getRecords().size() < 1000) {
                break;
            }
            pageNum++;
        }
    }

    private void writeDictTypes(BufferedWriter writer, ExportOperation entity) throws IOException {
        writer.write("type_code,type_name,description,status,version,published_version\n");
        List<DictType> types = dictTypeRepository.selectList(null);
        for (DictType type : types) {
            writer.write(String.join(",",
                    csv(type.getTypeCode()),
                    csv(type.getTypeName()),
                    csv(type.getDescription()),
                    csv(type.getStatus()),
                    String.valueOf(type.getVersion()),
                    String.valueOf(type.getPublishedVersion())));
            writer.newLine();
        }
    }

    private void writeConfigs(BufferedWriter writer, ExportOperation entity) throws IOException {
        writer.write("config_key,config_value,value_type,module,description,encrypted,version,status\n");
        List<SysConfig> configs = configRepository.selectList(null);
        for (SysConfig config : configs) {
            boolean encrypted = Boolean.TRUE.equals(config.getEncrypted());
            writer.write(String.join(",",
                    csv(config.getConfigKey()),
                    encrypted ? "***" : csv(config.getConfigValue()),
                    csv(config.getValueType()),
                    csv(config.getModule()),
                    csv(config.getDescription()),
                    String.valueOf(config.getEncrypted()),
                    String.valueOf(config.getVersion()),
                    csv(config.getStatus())));
            writer.newLine();
        }
    }

    private void markFailed(String exportId, String errorMessage) {
        try {
            ExportOperation entity = findByExportId(exportId);
            entity.setStatus("FAILED");
            entity.setErrorMessage(errorMessage);
            entity.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            exportRepository.updateById(entity);
            writeCompletedEvent(exportId, "FAILED", errorMessage);
        } catch (Exception e) {
            log.error("Failed to mark export {} as FAILED", exportId, e);
        }
    }

    private void writeCompletedEvent(String exportId, String status, String errorMessage) {
        Map<String, Object> payload = errorMessage != null
                ? Map.of("export_id", exportId, "status", status, "error_message", errorMessage)
                : Map.of("export_id", exportId, "status", status);
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                EXPORT_COMPLETED_EVENT,
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

    private ExportOperation findByExportId(String exportId) {
        LambdaQueryWrapper<ExportOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExportOperation::getExportId, exportId);
        ExportOperation entity = exportRepository.selectOne(wrapper);
        if (entity == null) {
            throw new io.opengeobot.platform.governance.web.ResourceNotFoundException("Export task '" + exportId + "' not found");
        }
        return entity;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
