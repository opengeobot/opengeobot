/*
 * Function: Config service — CRUD and version history for platform configs
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.service;

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
import io.opengeobot.platform.governance.config.repository.ConfigHistoryRepository;
import io.opengeobot.platform.governance.config.repository.ConfigRepository;
import io.opengeobot.platform.governance.domain.config.ConfigHistory;
import io.opengeobot.platform.governance.domain.config.SysConfig;
import io.opengeobot.platform.governance.dto.ConfigDto;
import io.opengeobot.platform.governance.dto.ConfigHistoryDto;
import io.opengeobot.platform.governance.dto.CreateConfigRequest;
import io.opengeobot.platform.governance.dto.UpdateConfigRequest;
import io.opengeobot.platform.governance.web.ActorResolver;
import io.opengeobot.platform.governance.web.ConflictException;
import io.opengeobot.platform.governance.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Service for platform configuration management. Configs are versioned; each
 * update increments {@code version} and appends a row to
 * {@code sys_config_history}. Config changes emit a
 * {@code platform.config.changed.v1} event via the transactional outbox.
 * Encrypted configs have their value masked in API responses. All operations
 * are audited.
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    private static final String CONFIG_CHANGED_EVENT = "platform.config.changed.v1";
    private static final String RESOURCE_TYPE = "sys_config";
    private static final String MASKED_VALUE = "***";

    private final ConfigRepository configRepository;
    private final ConfigHistoryRepository configHistoryRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public ConfigService(ConfigRepository configRepository,
                         ConfigHistoryRepository configHistoryRepository,
                         OutboxRepository outboxRepository,
                         AuditService auditService,
                         ActorResolver actorResolver,
                         ClockProvider clockProvider,
                         PublicIdGenerator idGenerator,
                         ObjectMapper objectMapper) {
        this.configRepository = configRepository;
        this.configHistoryRepository = configHistoryRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<ConfigDto> listConfigs(String module, String key, PageRequest pageRequest) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(module != null && !module.isBlank(), SysConfig::getModule, module)
                .likeRight(key != null && !key.isBlank(), SysConfig::getConfigKey, key)
                .orderByAsc(SysConfig::getConfigKey);
        Page<SysConfig> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<SysConfig> result = configRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(ConfigService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public ConfigDto getConfig(String configKey) {
        SysConfig entity = findByKey(configKey);
        return toDto(entity);
    }

    @Transactional
    public ConfigDto createConfig(CreateConfigRequest request) {
        if (existsByKey(request.configKey())) {
            throw new ConflictException("Config key '" + request.configKey() + "' already exists");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        SysConfig entity = new SysConfig();
        entity.setConfigKey(request.configKey());
        entity.setConfigValue(request.configValue());
        entity.setValueType(request.valueType());
        entity.setModule(request.module());
        entity.setDescription(request.description());
        entity.setEncrypted(request.encrypted() != null ? request.encrypted() : false);
        entity.setVersion(1);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        configRepository.insert(entity);
        saveHistory(entity, "CREATED", actor);
        writeConfigChangedEvent("CREATED", entity);
        audit("config.create", request.configKey(), "SUCCESS", null, toJson(entity));
        log.info("Created config {}", request.configKey());
        return toDto(entity);
    }

    @Transactional
    public ConfigDto updateConfig(String configKey, UpdateConfigRequest request) {
        SysConfig entity = findByKey(configKey);
        String payloadBefore = toJson(entity);
        entity.setConfigValue(request.configValue());
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        configRepository.updateById(entity);
        saveHistory(entity, "UPDATED", entity.getUpdatedBy());
        writeConfigChangedEvent("UPDATED", entity);
        audit("config.update", configKey, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Updated config {} to version {}", configKey, entity.getVersion());
        return toDto(entity);
    }

    public PageResult<ConfigHistoryDto> getConfigHistory(String configKey, PageRequest pageRequest) {
        if (!existsByKey(configKey)) {
            throw new ResourceNotFoundException("Config '" + configKey + "' not found");
        }
        LambdaQueryWrapper<ConfigHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigHistory::getConfigKey, configKey)
                .orderByDesc(ConfigHistory::getChangedAt);
        Page<ConfigHistory> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<ConfigHistory> result = configHistoryRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(ConfigService::toHistoryDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    private SysConfig findByKey(String configKey) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, configKey);
        SysConfig entity = configRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Config '" + configKey + "' not found");
        }
        return entity;
    }

    private boolean existsByKey(String configKey) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, configKey);
        return configRepository.selectCount(wrapper) > 0;
    }

    private void saveHistory(SysConfig config, String changeType, String changedBy) {
        ConfigHistory history = new ConfigHistory();
        history.setConfigKey(config.getConfigKey());
        history.setConfigValue(config.getConfigValue());
        history.setValueType(config.getValueType());
        history.setModule(config.getModule());
        history.setVersion(config.getVersion());
        history.setChangedBy(changedBy);
        history.setChangedAt(OffsetDateTime.now(ZoneOffset.UTC));
        history.setTraceId(actorResolver.currentTraceId());
        history.setChangeType(changeType);
        configHistoryRepository.insert(history);
    }

    private void writeConfigChangedEvent(String action, SysConfig entity) {
        Map<String, Object> payload = Map.of(
                "action", action,
                "config_key", entity.getConfigKey(),
                "version", entity.getVersion(),
                "module", entity.getModule()
        );
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                CONFIG_CHANGED_EVENT,
                "1",
                RESOURCE_TYPE,
                entity.getConfigKey(),
                entity.getVersion() != null ? entity.getVersion().longValue() : 1L,
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

    private static ConfigDto toDto(SysConfig entity) {
        boolean encrypted = Boolean.TRUE.equals(entity.getEncrypted());
        return new ConfigDto(
                entity.getConfigKey(),
                encrypted ? MASKED_VALUE : entity.getConfigValue(),
                entity.getValueType(),
                entity.getModule(),
                entity.getDescription(),
                entity.getEncrypted(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static ConfigHistoryDto toHistoryDto(ConfigHistory entity) {
        return new ConfigHistoryDto(
                entity.getConfigKey(),
                entity.getConfigValue(),
                entity.getValueType(),
                entity.getModule(),
                entity.getVersion(),
                entity.getChangeType(),
                entity.getChangedBy(),
                entity.getChangedAt(),
                entity.getTraceId()
        );
    }
}
