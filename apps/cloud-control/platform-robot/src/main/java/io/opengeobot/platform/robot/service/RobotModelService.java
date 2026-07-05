/*
 * Function: Robot model service — CRUD for robot hardware models (F-ROBOT-002)
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
import io.opengeobot.platform.robot.domain.RobotModel;
import io.opengeobot.platform.robot.dto.CreateRobotModelRequest;
import io.opengeobot.platform.robot.dto.RobotModelDto;
import io.opengeobot.platform.robot.dto.UpdateRobotModelRequest;
import io.opengeobot.platform.robot.repository.RobotModelRepository;
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
 * Application service for robot model management (F-ROBOT-002). Models are
 * catalogue entries describing supported hardware. All mutations are recorded
 * in the audit trail.
 */
@Service
public class RobotModelService {

    private static final Logger log = LoggerFactory.getLogger(RobotModelService.class);
    private static final String RESOURCE_TYPE = "robot_model";

    private final RobotModelRepository modelRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public RobotModelService(RobotModelRepository modelRepository,
                             AuditService auditService,
                             ActorResolver actorResolver,
                             ClockProvider clockProvider,
                             PublicIdGenerator idGenerator,
                             ObjectMapper objectMapper) {
        this.modelRepository = modelRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<RobotModelDto> list(PageRequest pageRequest) {
        LambdaQueryWrapper<RobotModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(RobotModel::getModelName);
        Page<RobotModel> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<RobotModel> result = modelRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(RobotModelService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public RobotModelDto getByModelId(String modelId) {
        return toDto(requireModel(modelId));
    }

    @Transactional
    public RobotModelDto create(CreateRobotModelRequest request) {
        if (existsByModelName(request.modelName())) {
            throw new ConflictException("Robot model with name '" + request.modelName() + "' already exists");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        RobotModel entity = new RobotModel();
        entity.setModelId(idGenerator.generate("mdl"));
        entity.setModelName(request.modelName());
        entity.setManufacturer(request.manufacturer());
        entity.setDescription(request.description());
        entity.setCapabilities(request.capabilities() != null ? request.capabilities() : "[]");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        modelRepository.insert(entity);
        audit("robot_model.create", entity.getModelId(), null, toJson(entity));
        log.info("Created robot model {} ({})", entity.getModelId(), entity.getModelName());
        return toDto(entity);
    }

    @Transactional
    public RobotModelDto update(String modelId, UpdateRobotModelRequest request) {
        RobotModel entity = requireModel(modelId);
        String payloadBefore = toJson(entity);
        if (request.manufacturer() != null) {
            entity.setManufacturer(request.manufacturer());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.capabilities() != null) {
            entity.setCapabilities(request.capabilities());
        }
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        modelRepository.updateById(entity);
        audit("robot_model.update", modelId, payloadBefore, toJson(entity));
        log.info("Updated robot model {}", modelId);
        return toDto(entity);
    }

    @Transactional
    public void delete(String modelId) {
        RobotModel entity = requireModel(modelId);
        String payloadBefore = toJson(entity);
        modelRepository.deleteById(entity.getId());
        audit("robot_model.delete", modelId, payloadBefore, null);
        log.info("Deleted robot model {}", modelId);
    }

    // ----- helpers -----

    private RobotModel requireModel(String modelId) {
        RobotModel entity = modelRepository.findByModelId(modelId);
        if (entity == null) {
            throw new ResourceNotFoundException("Robot model '" + modelId + "' not found");
        }
        return entity;
    }

    private boolean existsByModelName(String modelName) {
        LambdaQueryWrapper<RobotModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotModel::getModelName, modelName);
        return modelRepository.selectCount(wrapper) > 0;
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

    private static RobotModelDto toDto(RobotModel entity) {
        return new RobotModelDto(
                entity.getModelId(),
                entity.getModelName(),
                entity.getManufacturer(),
                entity.getDescription(),
                entity.getCapabilities(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
