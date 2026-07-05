/*
 * Function: Dictionary service — CRUD and publish operations for dict types and items
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
import io.opengeobot.platform.governance.dict.repository.DictItemRepository;
import io.opengeobot.platform.governance.dict.repository.DictTypeRepository;
import io.opengeobot.platform.governance.domain.dict.DictItem;
import io.opengeobot.platform.governance.domain.dict.DictType;
import io.opengeobot.platform.governance.dto.CreateDictItemRequest;
import io.opengeobot.platform.governance.dto.CreateDictTypeRequest;
import io.opengeobot.platform.governance.dto.DictItemDto;
import io.opengeobot.platform.governance.dto.DictTypeDto;
import io.opengeobot.platform.governance.dto.UpdateDictItemRequest;
import io.opengeobot.platform.governance.dto.UpdateDictTypeRequest;
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
 * Service for dictionary type and item management. Types follow the
 * SM-VERSIONED-CONFIG state machine (DRAFT → PUBLISHED → ARCHIVED). Publishing
 * a type emits a {@code platform.dictionary.changed.v1} event via the
 * transactional outbox. All operations are audited.
 */
@Service
public class DictService {

    private static final Logger log = LoggerFactory.getLogger(DictService.class);
    private static final String DICT_CHANGED_EVENT = "platform.dictionary.changed.v1";
    private static final String RESOURCE_TYPE = "sys_dict_type";
    private static final String ITEM_RESOURCE_TYPE = "sys_dict_item";

    private final DictTypeRepository dictTypeRepository;
    private final DictItemRepository dictItemRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public DictService(DictTypeRepository dictTypeRepository,
                       DictItemRepository dictItemRepository,
                       OutboxRepository outboxRepository,
                       AuditService auditService,
                       ActorResolver actorResolver,
                       ClockProvider clockProvider,
                       PublicIdGenerator idGenerator,
                       ObjectMapper objectMapper) {
        this.dictTypeRepository = dictTypeRepository;
        this.dictItemRepository = dictItemRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<DictTypeDto> listTypes(PageRequest pageRequest, String status, String typeCode) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), DictType::getStatus, status)
                .like(typeCode != null && !typeCode.isBlank(), DictType::getTypeCode, typeCode)
                .orderByDesc(DictType::getCreatedAt);
        Page<DictType> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<DictType> result = dictTypeRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(DictService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public DictTypeDto getType(String typeCode) {
        DictType entity = findTypeByCode(typeCode);
        return toDto(entity);
    }

    @Transactional
    public DictTypeDto createType(CreateDictTypeRequest request) {
        if (existsByTypeCode(request.typeCode())) {
            throw new ConflictException("Dictionary type with code '" + request.typeCode() + "' already exists");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        DictType entity = new DictType();
        entity.setTypeCode(request.typeCode());
        entity.setTypeName(request.typeName());
        entity.setDescription(request.description());
        entity.setStatus("DRAFT");
        entity.setVersion(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        dictTypeRepository.insert(entity);
        audit("dict.type.create", RESOURCE_TYPE, request.typeCode(), "SUCCESS", null, toJson(entity));
        log.info("Created dictionary type {}", request.typeCode());
        return toDto(entity);
    }

    @Transactional
    public DictTypeDto updateType(String typeCode, UpdateDictTypeRequest request) {
        DictType entity = findTypeByCode(typeCode);
        String payloadBefore = toJson(entity);
        if (request.typeName() != null) {
            entity.setTypeName(request.typeName());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        dictTypeRepository.updateById(entity);
        audit("dict.type.update", RESOURCE_TYPE, typeCode, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Updated dictionary type {}", typeCode);
        return toDto(entity);
    }

    @Transactional
    public void deleteType(String typeCode) {
        DictType entity = findTypeByCode(typeCode);
        if ("PUBLISHED".equals(entity.getStatus())) {
            throw new ConflictException("Cannot delete a PUBLISHED dictionary type; archive it first");
        }
        String payloadBefore = toJson(entity);
        entity.setStatus("ARCHIVED");
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        dictTypeRepository.updateById(entity);
        audit("dict.type.delete", RESOURCE_TYPE, typeCode, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Archived dictionary type {}", typeCode);
    }

    @Transactional
    public DictTypeDto publishType(String typeCode) {
        DictType entity = findTypeByCode(typeCode);
        if ("PUBLISHED".equals(entity.getStatus())
                && entity.getPublishedVersion() != null
                && entity.getPublishedVersion().equals(entity.getVersion())) {
            throw new ConflictException("Dictionary type '" + typeCode + "' is already published at version " + entity.getVersion());
        }
        String payloadBefore = toJson(entity);
        entity.setStatus("PUBLISHED");
        entity.setPublishedVersion(entity.getVersion());
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        dictTypeRepository.updateById(entity);
        writeDictChangedEvent(entity, "PUBLISHED");
        audit("dict.type.publish", RESOURCE_TYPE, typeCode, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Published dictionary type {} at version {}", typeCode, entity.getVersion());
        return toDto(entity);
    }

    public PageResult<DictItemDto> listItems(String typeCode, PageRequest pageRequest, String status) {
        findTypeByCode(typeCode);
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictItem::getTypeCode, typeCode)
                .eq(status != null && !status.isBlank(), DictItem::getStatus, status)
                .orderByAsc(DictItem::getSortOrder);
        Page<DictItem> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<DictItem> result = dictItemRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(DictService::toItemDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    @Transactional
    public DictItemDto createItem(String typeCode, CreateDictItemRequest request) {
        findTypeByCode(typeCode);
        if (existsItem(typeCode, request.itemCode())) {
            throw new ConflictException("Dictionary item '" + request.itemCode() + "' already exists in type '" + typeCode + "'");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DictItem entity = new DictItem();
        entity.setTypeCode(typeCode);
        entity.setItemCode(request.itemCode());
        entity.setItemValue(request.itemValue());
        entity.setLabelZhCn(request.labelZhCn());
        entity.setLabelEnUs(request.labelEnUs());
        entity.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        entity.setStatus("ACTIVE");
        entity.setExtra(request.extra());
        entity.setVersion(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        dictItemRepository.insert(entity);
        audit("dict.item.create", ITEM_RESOURCE_TYPE, typeCode + "/" + request.itemCode(), "SUCCESS", null, toJson(entity));
        log.info("Created dictionary item {}/{}", typeCode, request.itemCode());
        return toItemDto(entity);
    }

    @Transactional
    public DictItemDto updateItem(String typeCode, String itemCode, UpdateDictItemRequest request) {
        DictItem entity = findItem(typeCode, itemCode);
        String payloadBefore = toJson(entity);
        if (request.itemValue() != null) {
            entity.setItemValue(request.itemValue());
        }
        if (request.labelZhCn() != null) {
            entity.setLabelZhCn(request.labelZhCn());
        }
        if (request.labelEnUs() != null) {
            entity.setLabelEnUs(request.labelEnUs());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.extra() != null) {
            entity.setExtra(request.extra());
        }
        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        dictItemRepository.updateById(entity);
        audit("dict.item.update", ITEM_RESOURCE_TYPE, typeCode + "/" + itemCode, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Updated dictionary item {}/{}", typeCode, itemCode);
        return toItemDto(entity);
    }

    @Transactional
    public void deleteItem(String typeCode, String itemCode) {
        DictItem entity = findItem(typeCode, itemCode);
        String payloadBefore = toJson(entity);
        entity.setStatus("ARCHIVED");
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        dictItemRepository.updateById(entity);
        audit("dict.item.delete", ITEM_RESOURCE_TYPE, typeCode + "/" + itemCode, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Archived dictionary item {}/{}", typeCode, itemCode);
    }

    private DictType findTypeByCode(String typeCode) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictType::getTypeCode, typeCode);
        DictType entity = dictTypeRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Dictionary type '" + typeCode + "' not found");
        }
        return entity;
    }

    private DictItem findItem(String typeCode, String itemCode) {
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictItem::getTypeCode, typeCode)
                .eq(DictItem::getItemCode, itemCode);
        DictItem entity = dictItemRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Dictionary item '" + itemCode + "' not found in type '" + typeCode + "'");
        }
        return entity;
    }

    private boolean existsByTypeCode(String typeCode) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictType::getTypeCode, typeCode);
        return dictTypeRepository.selectCount(wrapper) > 0;
    }

    private boolean existsItem(String typeCode, String itemCode) {
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictItem::getTypeCode, typeCode)
                .eq(DictItem::getItemCode, itemCode);
        return dictItemRepository.selectCount(wrapper) > 0;
    }

    private void writeDictChangedEvent(DictType entity, String action) {
        Map<String, Object> payload = Map.of(
                "action", action,
                "type_code", entity.getTypeCode(),
                "version", entity.getVersion(),
                "published_version", entity.getPublishedVersion() != null ? entity.getPublishedVersion() : 0
        );
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                DICT_CHANGED_EVENT,
                "1",
                RESOURCE_TYPE,
                entity.getTypeCode(),
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

    private static DictTypeDto toDto(DictType entity) {
        return new DictTypeDto(
                entity.getTypeCode(),
                entity.getTypeName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getPublishedVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy()
        );
    }

    private static DictItemDto toItemDto(DictItem entity) {
        return new DictItemDto(
                entity.getTypeCode(),
                entity.getItemCode(),
                entity.getItemValue(),
                entity.getLabelZhCn(),
                entity.getLabelEnUs(),
                entity.getSortOrder(),
                entity.getStatus(),
                entity.getExtra(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
