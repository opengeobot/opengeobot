/*
 * Function: Skill service — CRUD, publish, enable/disable and version management
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
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.Skill;
import io.opengeobot.platform.robot.domain.SkillVersion;
import io.opengeobot.platform.robot.dto.CreateSkillRequest;
import io.opengeobot.platform.robot.dto.SkillDto;
import io.opengeobot.platform.robot.dto.SkillVersionDto;
import io.opengeobot.platform.robot.dto.UpdateSkillRequest;
import io.opengeobot.platform.robot.repository.SkillRepository;
import io.opengeobot.platform.robot.repository.SkillVersionRepository;
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
import java.util.Map;

/**
 * Service for skill lifecycle management. Skills follow the SM-SKILL-001 state
 * machine (DRAFT → PUBLISHED → DEPRECATED/DISABLED). Publishing a skill
 * creates an immutable version snapshot in {@code skill_version} and
 * increments {@code current_version}. All mutations emit domain events via
 * the transactional outbox and are recorded in the audit trail.
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);
    private static final String SKILL_REGISTERED_EVENT = "skill.registered.v1";
    private static final String SKILL_STATUS_CHANGED_EVENT = "skill.status_changed.v1";
    private static final String RESOURCE_TYPE = "skill";
    private static final String VERSION_RESOURCE_TYPE = "skill_version";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DEPRECATED = "DEPRECATED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public SkillService(SkillRepository skillRepository,
                        SkillVersionRepository skillVersionRepository,
                        OutboxRepository outboxRepository,
                        AuditService auditService,
                        ActorResolver actorResolver,
                        ClockProvider clockProvider,
                        PublicIdGenerator idGenerator,
                        ObjectMapper objectMapper) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<SkillDto> listSkills(String status, String module, PageRequest pageRequest) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), Skill::getStatus, status)
                .eq(module != null && !module.isBlank(), Skill::getModule, module)
                .orderByAsc(Skill::getName);
        Page<Skill> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<Skill> result = skillRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(SkillService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public SkillDto getSkill(String skillId) {
        return toDto(findBySkillId(skillId));
    }

    @Transactional
    public SkillDto createSkill(CreateSkillRequest request) {
        if (existsByName(request.name())) {
            throw new ConflictException("Skill with name '" + request.name() + "' already exists");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        Skill entity = new Skill();
        entity.setSkillId(idGenerator.generate("skl"));
        entity.setName(request.name());
        entity.setModule(request.module());
        entity.setDescription(request.description());
        entity.setStatus(STATUS_DRAFT);
        entity.setCurrentVersion(0);
        entity.setInputSchema(request.inputSchema());
        entity.setOutputSchema(request.outputSchema());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        skillRepository.insert(entity);
        writeSkillRegisteredEvent(entity);
        audit("skill.create", RESOURCE_TYPE, entity.getSkillId(), "SUCCESS", null, toJson(entity));
        log.info("Registered skill {} ({})", entity.getSkillId(), entity.getName());
        return toDto(entity);
    }

    @Transactional
    public SkillDto updateSkill(String skillId, UpdateSkillRequest request) {
        Skill entity = findBySkillId(skillId);
        if (STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new ConflictException("Cannot update a PUBLISHED skill; create a new version instead");
        }
        String payloadBefore = toJson(entity);
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.inputSchema() != null) {
            entity.setInputSchema(request.inputSchema());
        }
        if (request.outputSchema() != null) {
            entity.setOutputSchema(request.outputSchema());
        }
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        skillRepository.updateById(entity);
        audit("skill.update", RESOURCE_TYPE, skillId, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Updated skill {}", skillId);
        return toDto(entity);
    }

    @Transactional
    public SkillDto publishSkill(String skillId, String changelog) {
        Skill entity = findBySkillId(skillId);
        String oldStatus = entity.getStatus();
        if (STATUS_DISABLED.equals(oldStatus)) {
            throw new ConflictException("Cannot publish a DISABLED skill; enable it first");
        }
        int newVersion = (entity.getCurrentVersion() != null ? entity.getCurrentVersion() : 0) + 1;
        String actor = actorResolver.currentActor();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String payloadBefore = toJson(entity);

        SkillVersion version = new SkillVersion();
        version.setSkillId(skillId);
        version.setVersion(newVersion);
        version.setStatus(STATUS_PUBLISHED);
        version.setChangelog(changelog);
        version.setInputSchema(entity.getInputSchema());
        version.setOutputSchema(entity.getOutputSchema());
        version.setPublishedAt(now);
        version.setPublishedBy(actor);
        version.setCreatedAt(now);
        skillVersionRepository.insert(version);

        entity.setStatus(STATUS_PUBLISHED);
        entity.setCurrentVersion(newVersion);
        entity.setUpdatedBy(actor);
        entity.setUpdatedAt(now);
        skillRepository.updateById(entity);

        writeStatusChangedEvent(entity, oldStatus, STATUS_PUBLISHED);
        audit("skill.publish", RESOURCE_TYPE, skillId, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Published skill {} at version {}", skillId, newVersion);
        return toDto(entity);
    }

    @Transactional
    public SkillDto disableSkill(String skillId) {
        Skill entity = findBySkillId(skillId);
        String oldStatus = entity.getStatus();
        if (STATUS_DISABLED.equals(oldStatus)) {
            throw new ConflictException("Skill '" + skillId + "' is already disabled");
        }
        String payloadBefore = toJson(entity);
        entity.setStatus(STATUS_DISABLED);
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        skillRepository.updateById(entity);
        writeStatusChangedEvent(entity, oldStatus, STATUS_DISABLED);
        audit("skill.disable", RESOURCE_TYPE, skillId, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Disabled skill {}", skillId);
        return toDto(entity);
    }

    @Transactional
    public SkillDto enableSkill(String skillId) {
        Skill entity = findBySkillId(skillId);
        String oldStatus = entity.getStatus();
        if (!STATUS_DISABLED.equals(oldStatus)) {
            throw new ConflictException("Skill '" + skillId + "' is not disabled");
        }
        String newStatus = (entity.getCurrentVersion() != null && entity.getCurrentVersion() > 0)
                ? STATUS_PUBLISHED : STATUS_DRAFT;
        String payloadBefore = toJson(entity);
        entity.setStatus(newStatus);
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        skillRepository.updateById(entity);
        writeStatusChangedEvent(entity, oldStatus, newStatus);
        audit("skill.enable", RESOURCE_TYPE, skillId, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Enabled skill {} (status -> {})", skillId, newStatus);
        return toDto(entity);
    }

    public PageResult<SkillVersionDto> listVersions(String skillId, PageRequest pageRequest) {
        if (!existsBySkillId(skillId)) {
            throw new ResourceNotFoundException("Skill '" + skillId + "' not found");
        }
        LambdaQueryWrapper<SkillVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillVersion::getSkillId, skillId)
                .orderByDesc(SkillVersion::getVersion);
        Page<SkillVersion> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<SkillVersion> result = skillVersionRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(SkillService::toVersionDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    private Skill findBySkillId(String skillId) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getSkillId, skillId);
        Skill entity = skillRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Skill '" + skillId + "' not found");
        }
        return entity;
    }

    private boolean existsByName(String name) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getName, name);
        return skillRepository.selectCount(wrapper) > 0;
    }

    private boolean existsBySkillId(String skillId) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getSkillId, skillId);
        return skillRepository.selectCount(wrapper) > 0;
    }

    private void writeSkillRegisteredEvent(Skill entity) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "skill_id", entity.getSkillId(),
                "name", entity.getName(),
                "module", entity.getModule(),
                "version", entity.getCurrentVersion() != null ? entity.getCurrentVersion() : 0,
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", actorResolver.currentTraceId() != null ? actorResolver.currentTraceId() : ""
        );
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                SKILL_REGISTERED_EVENT,
                "1",
                RESOURCE_TYPE,
                entity.getSkillId(),
                entity.getCurrentVersion() != null ? entity.getCurrentVersion().longValue() : 0L,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                actorResolver.currentTraceId(),
                false,
                null,
                0
        );
        outboxRepository.save(event);
    }

    private void writeStatusChangedEvent(Skill entity, String oldStatus, String newStatus) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "skill_id", entity.getSkillId(),
                "old_status", oldStatus,
                "new_status", newStatus,
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", actorResolver.currentTraceId() != null ? actorResolver.currentTraceId() : ""
        );
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                SKILL_STATUS_CHANGED_EVENT,
                "1",
                RESOURCE_TYPE,
                entity.getSkillId(),
                entity.getCurrentVersion() != null ? entity.getCurrentVersion().longValue() : 0L,
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

    private static SkillDto toDto(Skill entity) {
        return new SkillDto(
                entity.getSkillId(),
                entity.getName(),
                entity.getModule(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCurrentVersion(),
                entity.getInputSchema(),
                entity.getOutputSchema(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static SkillVersionDto toVersionDto(SkillVersion entity) {
        return new SkillVersionDto(
                entity.getSkillId(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getChangelog(),
                entity.getInputSchema(),
                entity.getOutputSchema(),
                entity.getPublishedAt(),
                entity.getPublishedBy(),
                entity.getCreatedAt()
        );
    }
}
