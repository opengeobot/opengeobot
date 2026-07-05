/*
 * Function: I18n service — CRUD and batch import for internationalization resources
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
import io.opengeobot.platform.governance.domain.i18n.I18nResource;
import io.opengeobot.platform.governance.dto.BatchI18nRequest;
import io.opengeobot.platform.governance.dto.BatchI18nResultDto;
import io.opengeobot.platform.governance.dto.CreateI18nResourceRequest;
import io.opengeobot.platform.governance.dto.I18nResourceDto;
import io.opengeobot.platform.governance.dto.UpdateI18nResourceRequest;
import io.opengeobot.platform.governance.i18n.repository.I18nResourceRepository;
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
 * Service for internationalization resource management. Resources are keyed by
 * the combination of {@code resource_key} and {@code locale}. Changes emit a
 * {@code platform.i18n.changed.v1} event via the transactional outbox. All
 * operations are audited.
 */
@Service
public class I18nService {

    private static final Logger log = LoggerFactory.getLogger(I18nService.class);
    private static final String I18N_CHANGED_EVENT = "platform.i18n.changed.v1";
    private static final String RESOURCE_TYPE = "sys_i18n_resource";

    private final I18nResourceRepository repository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public I18nService(I18nResourceRepository repository,
                      OutboxRepository outboxRepository,
                      AuditService auditService,
                      ActorResolver actorResolver,
                      ClockProvider clockProvider,
                      PublicIdGenerator idGenerator,
                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<I18nResourceDto> listResources(String locale, String module,
                                                     String resourceKey, PageRequest pageRequest) {
        LambdaQueryWrapper<I18nResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(locale != null && !locale.isBlank(), I18nResource::getLocale, locale)
                .eq(module != null && !module.isBlank(), I18nResource::getModule, module)
                .like(resourceKey != null && !resourceKey.isBlank(), I18nResource::getResourceKey, resourceKey)
                .orderByAsc(I18nResource::getResourceKey);
        Page<I18nResource> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<I18nResource> result = repository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(I18nService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    @Transactional
    public I18nResourceDto createResource(CreateI18nResourceRequest request) {
        if (existsByKey(request.resourceKey(), request.locale())) {
            throw new ConflictException("i18n resource '" + request.resourceKey()
                    + "' for locale '" + request.locale() + "' already exists");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        I18nResource entity = new I18nResource();
        entity.setResourceKey(request.resourceKey());
        entity.setLocale(request.locale());
        entity.setResourceValue(request.resourceValue());
        entity.setModule(request.module());
        entity.setDescription(request.description());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.insert(entity);
        writeI18nChangedEvent("CREATED", entity);
        audit("i18n.resource.create", request.resourceKey() + "/" + request.locale(), "SUCCESS", null, toJson(entity));
        log.info("Created i18n resource {}/{}", request.resourceKey(), request.locale());
        return toDto(entity);
    }

    @Transactional
    public I18nResourceDto updateResource(String resourceKey, String locale, UpdateI18nResourceRequest request) {
        I18nResource entity = findResource(resourceKey, locale);
        String payloadBefore = toJson(entity);
        entity.setResourceValue(request.resourceValue());
        if (request.module() != null) {
            entity.setModule(request.module());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.updateById(entity);
        writeI18nChangedEvent("UPDATED", entity);
        audit("i18n.resource.update", resourceKey + "/" + locale, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Updated i18n resource {}/{}", resourceKey, locale);
        return toDto(entity);
    }

    @Transactional
    public void deleteResource(String resourceKey, String locale) {
        I18nResource entity = findResource(resourceKey, locale);
        String payloadBefore = toJson(entity);
        repository.deleteById(entity.getId());
        writeI18nChangedEvent("DELETED", entity);
        audit("i18n.resource.delete", resourceKey + "/" + locale, "SUCCESS", payloadBefore, null);
        log.info("Deleted i18n resource {}/{}", resourceKey, locale);
    }

    @Transactional
    public BatchI18nResultDto batchImport(BatchI18nRequest request) {
        int imported = 0;
        int skipped = 0;
        for (BatchI18nRequest.I18nResourceEntry entry : request.resources()) {
            try {
                upsertResource(entry);
                imported++;
            } catch (Exception e) {
                log.warn("Skipped i18n entry {}/{}: {}", entry.resourceKey(), entry.locale(), e.getMessage());
                skipped++;
            }
        }
        writeI18nBatchEvent("BATCH_IMPORTED", imported, skipped);
        audit("i18n.resource.batch_import", "batch", "SUCCESS", null,
                toJson(Map.of("imported", imported, "skipped", skipped)));
        log.info("Batch imported {} i18n resources, skipped {}", imported, skipped);
        return new BatchI18nResultDto(imported, skipped);
    }

    private void upsertResource(BatchI18nRequest.I18nResourceEntry entry) {
        I18nResource existing = findByKey(entry.resourceKey(), entry.locale());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (existing != null) {
            existing.setResourceValue(entry.resourceValue());
            existing.setModule(entry.module());
            if (entry.description() != null) {
                existing.setDescription(entry.description());
            }
            existing.setUpdatedAt(now);
            repository.updateById(existing);
        } else {
            I18nResource entity = new I18nResource();
            entity.setResourceKey(entry.resourceKey());
            entity.setLocale(entry.locale());
            entity.setResourceValue(entry.resourceValue());
            entity.setModule(entry.module());
            entity.setDescription(entry.description());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            repository.insert(entity);
        }
    }

    private I18nResource findResource(String resourceKey, String locale) {
        I18nResource entity = findByKey(resourceKey, locale);
        if (entity == null) {
            throw new ResourceNotFoundException("i18n resource '" + resourceKey
                    + "' for locale '" + locale + "' not found");
        }
        return entity;
    }

    private I18nResource findByKey(String resourceKey, String locale) {
        LambdaQueryWrapper<I18nResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(I18nResource::getResourceKey, resourceKey)
                .eq(I18nResource::getLocale, locale);
        return repository.selectOne(wrapper);
    }

    private boolean existsByKey(String resourceKey, String locale) {
        return findByKey(resourceKey, locale) != null;
    }

    private void writeI18nChangedEvent(String action, I18nResource entity) {
        Map<String, Object> payload = Map.of(
                "action", action,
                "resource_key", entity.getResourceKey(),
                "locale", entity.getLocale(),
                "module", entity.getModule()
        );
        saveOutboxEvent(action, payload, entity.getResourceKey(), 1L);
    }

    private void writeI18nBatchEvent(String action, int imported, int skipped) {
        Map<String, Object> payload = Map.of(
                "action", action,
                "imported", imported,
                "skipped", skipped
        );
        saveOutboxEvent(action, payload, "batch", 1L);
    }

    private void saveOutboxEvent(String action, Map<String, Object> payload, String aggregateId, Long aggregateVersion) {
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                I18N_CHANGED_EVENT,
                "1",
                RESOURCE_TYPE,
                aggregateId,
                aggregateVersion,
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

    private static I18nResourceDto toDto(I18nResource entity) {
        return new I18nResourceDto(
                entity.getResourceKey(),
                entity.getLocale(),
                entity.getResourceValue(),
                entity.getModule(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
