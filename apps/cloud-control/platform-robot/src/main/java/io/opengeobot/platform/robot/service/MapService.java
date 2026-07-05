/*
 * Function: Map service — CRUD, publish and area management for F-MAP-001
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
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.MapArea;
import io.opengeobot.platform.robot.domain.MapInfo;
import io.opengeobot.platform.robot.domain.RestrictedArea;
import io.opengeobot.platform.robot.dto.CreateMapAreaRequest;
import io.opengeobot.platform.robot.dto.CreateMapRequest;
import io.opengeobot.platform.robot.dto.CreateRestrictedAreaRequest;
import io.opengeobot.platform.robot.dto.MapAreaDto;
import io.opengeobot.platform.robot.dto.MapInfoDto;
import io.opengeobot.platform.robot.dto.RestrictedAreaDto;
import io.opengeobot.platform.robot.dto.UpdateMapRequest;
import io.opengeobot.platform.robot.repository.MapAreaRepository;
import io.opengeobot.platform.robot.repository.MapInfoRepository;
import io.opengeobot.platform.robot.repository.RestrictedAreaRepository;
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
 * Service for map and area management (F-MAP-001). Maps follow the
 * SM-MAP-001 state machine (DRAFT → PUBLISHED → ARCHIVED). Publishing a map
 * increments the version and transitions it to PUBLISHED. All mutations are
 * recorded in the audit trail.
 */
@Service
public class MapService {

    private static final Logger log = LoggerFactory.getLogger(MapService.class);
    private static final String RESOURCE_TYPE = "map";
    private static final String AREA_RESOURCE_TYPE = "map_area";
    private static final String RESTRICTED_AREA_RESOURCE_TYPE = "restricted_area";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final MapInfoRepository mapInfoRepository;
    private final MapAreaRepository mapAreaRepository;
    private final RestrictedAreaRepository restrictedAreaRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public MapService(MapInfoRepository mapInfoRepository,
                      MapAreaRepository mapAreaRepository,
                      RestrictedAreaRepository restrictedAreaRepository,
                      AuditService auditService,
                      ActorResolver actorResolver,
                      ClockProvider clockProvider,
                      PublicIdGenerator idGenerator,
                      ObjectMapper objectMapper) {
        this.mapInfoRepository = mapInfoRepository;
        this.mapAreaRepository = mapAreaRepository;
        this.restrictedAreaRepository = restrictedAreaRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<MapInfoDto> listMaps(String status, PageRequest pageRequest) {
        LambdaQueryWrapper<MapInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), MapInfo::getStatus, status)
                .orderByDesc(MapInfo::getCreatedAt);
        Page<MapInfo> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<MapInfo> result = mapInfoRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(MapService::toMapDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public MapInfoDto getMap(String mapId) {
        return toMapDto(findByMapId(mapId));
    }

    @Transactional
    public MapInfoDto createMap(CreateMapRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        MapInfo entity = new MapInfo();
        entity.setMapId(idGenerator.generate("map"));
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setVersion(0);
        entity.setStatus(STATUS_DRAFT);
        entity.setMetadata(request.metadata());
        entity.setCreatedBy(actor);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(actor);
        mapInfoRepository.insert(entity);
        audit("map.create", RESOURCE_TYPE, entity.getMapId(), "SUCCESS", null, toJson(entity));
        log.info("Created map {} ({})", entity.getMapId(), entity.getName());
        return toMapDto(entity);
    }

    @Transactional
    public MapInfoDto updateMap(String mapId, UpdateMapRequest request) {
        MapInfo entity = findByMapId(mapId);
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ConflictException("Cannot update a non-DRAFT map; status is " + entity.getStatus());
        }
        String payloadBefore = toJson(entity);
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.metadata() != null) {
            entity.setMetadata(request.metadata());
        }
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mapInfoRepository.updateById(entity);
        audit("map.update", RESOURCE_TYPE, mapId, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Updated map {}", mapId);
        return toMapDto(entity);
    }

    @Transactional
    public MapInfoDto publishMap(String mapId) {
        MapInfo entity = findByMapId(mapId);
        if (STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new ConflictException("Map '" + mapId + "' is already published");
        }
        if (STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw new ConflictException("Cannot publish an ARCHIVED map");
        }
        String payloadBefore = toJson(entity);
        int newVersion = (entity.getVersion() != null ? entity.getVersion() : 0) + 1;
        entity.setVersion(newVersion);
        entity.setStatus(STATUS_PUBLISHED);
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mapInfoRepository.updateById(entity);
        audit("map.publish", RESOURCE_TYPE, mapId, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Published map {} at version {}", mapId, newVersion);
        return toMapDto(entity);
    }

    public PageResult<MapAreaDto> listAreas(String mapId, PageRequest pageRequest) {
        if (!existsByMapId(mapId)) {
            throw new ResourceNotFoundException("Map '" + mapId + "' not found");
        }
        LambdaQueryWrapper<MapArea> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MapArea::getMapId, mapId)
                .orderByAsc(MapArea::getName);
        Page<MapArea> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<MapArea> result = mapAreaRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(MapService::toAreaDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    @Transactional
    public MapAreaDto createArea(String mapId, CreateMapAreaRequest request) {
        if (!existsByMapId(mapId)) {
            throw new ResourceNotFoundException("Map '" + mapId + "' not found");
        }
        MapArea entity = new MapArea();
        entity.setAreaId(idGenerator.generate("area"));
        entity.setMapId(mapId);
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setGeometry(request.geometry());
        entity.setProperties(request.properties());
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mapAreaRepository.insert(entity);
        audit("map_area.create", AREA_RESOURCE_TYPE, entity.getAreaId(), "SUCCESS", null, toJson(entity));
        log.info("Created area {} for map {}", entity.getAreaId(), mapId);
        return toAreaDto(entity);
    }

    public PageResult<RestrictedAreaDto> listRestrictedAreas(String mapId, PageRequest pageRequest) {
        if (!existsByMapId(mapId)) {
            throw new ResourceNotFoundException("Map '" + mapId + "' not found");
        }
        LambdaQueryWrapper<RestrictedArea> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RestrictedArea::getMapId, mapId)
                .orderByDesc(RestrictedArea::getEffectiveFrom);
        Page<RestrictedArea> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<RestrictedArea> result = restrictedAreaRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(MapService::toRestrictedAreaDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    @Transactional
    public RestrictedAreaDto createRestrictedArea(String mapId, CreateRestrictedAreaRequest request) {
        if (!existsByMapId(mapId)) {
            throw new ResourceNotFoundException("Map '" + mapId + "' not found");
        }
        RestrictedArea entity = new RestrictedArea();
        entity.setAreaId(idGenerator.generate("rarea"));
        entity.setMapId(mapId);
        entity.setName(request.name());
        entity.setRestrictionType(request.restrictionType());
        entity.setGeometry(request.geometry());
        entity.setProperties(request.properties());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        restrictedAreaRepository.insert(entity);
        audit("restricted_area.create", RESTRICTED_AREA_RESOURCE_TYPE, entity.getAreaId(), "SUCCESS", null, toJson(entity));
        log.info("Created restricted area {} for map {}", entity.getAreaId(), mapId);
        return toRestrictedAreaDto(entity);
    }

    private MapInfo findByMapId(String mapId) {
        LambdaQueryWrapper<MapInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MapInfo::getMapId, mapId);
        MapInfo entity = mapInfoRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Map '" + mapId + "' not found");
        }
        return entity;
    }

    private boolean existsByMapId(String mapId) {
        LambdaQueryWrapper<MapInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MapInfo::getMapId, mapId);
        return mapInfoRepository.selectCount(wrapper) > 0;
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

    private static MapInfoDto toMapDto(MapInfo entity) {
        return new MapInfoDto(
                entity.getMapId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getMetadata(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static MapAreaDto toAreaDto(MapArea entity) {
        return new MapAreaDto(
                entity.getAreaId(),
                entity.getMapId(),
                entity.getName(),
                entity.getType(),
                entity.getGeometry(),
                entity.getProperties(),
                entity.getCreatedAt()
        );
    }

    private static RestrictedAreaDto toRestrictedAreaDto(RestrictedArea entity) {
        return new RestrictedAreaDto(
                entity.getAreaId(),
                entity.getMapId(),
                entity.getName(),
                entity.getRestrictionType(),
                entity.getGeometry(),
                entity.getProperties(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                entity.getCreatedAt()
        );
    }
}
