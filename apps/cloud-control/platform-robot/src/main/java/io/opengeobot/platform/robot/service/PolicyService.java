/*
 * Function: Policy service — CRUD, publish with version, evaluation against missions
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.Policy;
import io.opengeobot.platform.robot.domain.PolicyAssignment;
import io.opengeobot.platform.robot.domain.PolicyRule;
import io.opengeobot.platform.robot.dto.CreatePolicyRequest;
import io.opengeobot.platform.robot.dto.PolicyDto;
import io.opengeobot.platform.robot.dto.PolicyRuleDto;
import io.opengeobot.platform.robot.dto.PolicyVersionDto;
import io.opengeobot.platform.robot.dto.UpdatePolicyRequest;
import io.opengeobot.platform.robot.repository.PolicyAssignmentRepository;
import io.opengeobot.platform.robot.repository.PolicyRepository;
import io.opengeobot.platform.robot.repository.PolicyRuleRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application service for policy management (F-POLICY-001). Handles CRUD,
 * versioned publishing, and basic mission compliance evaluation. Policies
 * follow the SM-POLICY-001 state machine (DRAFT → PUBLISHED → ARCHIVED).
 * All mutations emit domain events via the transactional outbox and are
 * recorded in the audit trail.
 */
@Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);
    private static final String POLICY_CREATED_EVENT = "policy.created.v1";
    private static final String POLICY_UPDATED_EVENT = "policy.updated.v1";
    private static final String POLICY_PUBLISHED_EVENT = "policy.published.v1";
    private static final String RESOURCE_TYPE = "policy";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final int DRAFT_VERSION = 0;
    private static final String RULE_TYPE_RESTRICTED_AREA = "restricted_area";
    private static final String RULE_TYPE_SPEED_LIMIT = "speed_limit";
    private static final String ACTION_DENY = "DENY";
    private static final String ACTION_WARN = "WARN";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final PolicyRepository policyRepository;
    private final PolicyRuleRepository policyRuleRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public PolicyService(PolicyRepository policyRepository,
                         PolicyRuleRepository policyRuleRepository,
                         PolicyAssignmentRepository policyAssignmentRepository,
                         OutboxRepository outboxRepository,
                         AuditService auditService,
                         ActorResolver actorResolver,
                         ClockProvider clockProvider,
                         PublicIdGenerator idGenerator,
                         ObjectMapper objectMapper) {
        this.policyRepository = policyRepository;
        this.policyRuleRepository = policyRuleRepository;
        this.policyAssignmentRepository = policyAssignmentRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    // ----- CRUD -----

    public PageResult<PolicyDto> list(String status, PageRequest pageRequest) {
        LambdaQueryWrapper<Policy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), Policy::getStatus, status)
                .orderByDesc(Policy::getUpdatedAt);
        Page<Policy> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<Policy> result = policyRepository.selectPage(page, wrapper);
        List<PolicyDto> items = result.getRecords().stream()
                .map(p -> toDto(p, loadRules(p.getPolicyId(), p.getCurrentVersion())))
                .toList();
        return new PageResult<>(items, result.getTotal(), pageRequest.pageNumber(), pageRequest.pageSize());
    }

    public PolicyDto get(String policyId) {
        Policy policy = findByPolicyId(policyId);
        return toDto(policy, loadRules(policyId, policy.getCurrentVersion()));
    }

    @Transactional
    public PolicyDto create(CreatePolicyRequest request) {
        if (existsByName(request.name())) {
            throw new ConflictException("Policy with name '" + request.name() + "' already exists");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        Policy entity = new Policy();
        entity.setPolicyId(idGenerator.generate("pol"));
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(STATUS_DRAFT);
        entity.setCurrentVersion(DRAFT_VERSION);
        entity.setScope(request.scope());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        policyRepository.insert(entity);
        persistRules(entity.getPolicyId(), DRAFT_VERSION, request.rules(), now);
        writePolicyEvent(POLICY_CREATED_EVENT, entity, now);
        audit("policy.create", RESOURCE_TYPE, entity.getPolicyId(), "SUCCESS", null, toJson(entity));
        log.info("Created policy {} ({})", entity.getPolicyId(), entity.getName());
        return toDto(entity, request.rules());
    }

    @Transactional
    public PolicyDto update(String policyId, UpdatePolicyRequest request) {
        Policy entity = findByPolicyId(policyId);
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ConflictException("Cannot update a " + entity.getStatus() + " policy; only DRAFT policies can be edited");
        }
        String payloadBefore = toJson(entity);
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.scope() != null) {
            entity.setScope(request.scope());
        }
        if (request.rules() != null) {
            replaceDraftRules(entity.getPolicyId(), request.rules());
        }
        entity.setUpdatedBy(actorResolver.currentActor());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        policyRepository.updateById(entity);
        writePolicyEvent(POLICY_UPDATED_EVENT, entity, OffsetDateTime.now(ZoneOffset.UTC));
        audit("policy.update", RESOURCE_TYPE, policyId, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Updated policy {}", policyId);
        return toDto(entity, loadRules(policyId, DRAFT_VERSION));
    }

    @Transactional
    public PolicyDto publish(String policyId) {
        Policy entity = findByPolicyId(policyId);
        if (STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw new ConflictException("Cannot publish an ARCHIVED policy");
        }
        int newVersion = (entity.getCurrentVersion() != null ? entity.getCurrentVersion() : 0) + 1;
        String actor = actorResolver.currentActor();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String payloadBefore = toJson(entity);

        List<PolicyRule> draftRules = loadRuleEntities(policyId, DRAFT_VERSION);
        for (PolicyRule rule : draftRules) {
            PolicyRule versioned = new PolicyRule();
            versioned.setPolicyId(policyId);
            versioned.setVersion(newVersion);
            versioned.setRuleType(rule.getRuleType());
            versioned.setCondition(rule.getCondition());
            versioned.setAction(rule.getAction());
            versioned.setPriority(rule.getPriority());
            versioned.setCreatedAt(now);
            policyRuleRepository.insert(versioned);
        }

        entity.setStatus(STATUS_PUBLISHED);
        entity.setCurrentVersion(newVersion);
        entity.setUpdatedBy(actor);
        entity.setUpdatedAt(now);
        policyRepository.updateById(entity);

        writePolicyEvent(POLICY_PUBLISHED_EVENT, entity, now);
        audit("policy.publish", RESOURCE_TYPE, policyId, "SUCCESS", payloadBefore, toJson(entity));
        log.info("Published policy {} at version {}", policyId, newVersion);
        return toDto(entity, loadRules(policyId, newVersion));
    }

    public PageResult<PolicyVersionDto> listVersions(String policyId, PageRequest pageRequest) {
        if (!existsByPolicyId(policyId)) {
            throw new ResourceNotFoundException("Policy '" + policyId + "' not found");
        }
        LambdaQueryWrapper<PolicyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PolicyRule::getPolicyId, policyId)
                .gt(PolicyRule::getVersion, DRAFT_VERSION)
                .orderByDesc(PolicyRule::getVersion);
        List<PolicyRule> allRules = policyRuleRepository.selectList(wrapper);
        Map<Integer, List<PolicyRule>> byVersion = new java.util.LinkedHashMap<>();
        for (PolicyRule rule : allRules) {
            byVersion.computeIfAbsent(rule.getVersion(), k -> new ArrayList<>()).add(rule);
        }
        List<PolicyVersionDto> allVersions = new ArrayList<>();
        for (Map.Entry<Integer, List<PolicyRule>> entry : byVersion.entrySet()) {
            allVersions.add(toVersionDto(policyId, entry.getKey(), entry.getValue()));
        }
        int total = allVersions.size();
        int fromIndex = (int) Math.min(pageRequest.offset(), total);
        int toIndex = Math.min(fromIndex + pageRequest.pageSize(), total);
        List<PolicyVersionDto> pageItems = allVersions.subList(fromIndex, toIndex);
        return new PageResult<>(pageItems, total, pageRequest.pageNumber(), pageRequest.pageSize());
    }

    // ----- evaluation -----

    /**
     * Evaluates whether a mission complies with active (PUBLISHED) policies.
     * For M2, performs basic checks:
     * <ul>
     *   <li>{@code restricted_area} — DENY if mission targets a restricted zone.</li>
     *   <li>{@code speed_limit} — WARN if any step exceeds the configured max speed.</li>
     * </ul>
     * Returns a list of violation descriptions; empty list means compliant.
     *
     * @param missionSteps the mission steps to evaluate
     * @param robotId      the robot the mission is assigned to
     * @return list of human-readable violation messages
     */
    public List<String> evaluate(List<io.opengeobot.platform.robot.dto.MissionStepDto> missionSteps, String robotId) {
        List<PolicyRule> activeRules = loadActiveRules();
        List<String> violations = new ArrayList<>();
        for (PolicyRule rule : activeRules) {
            if (RULE_TYPE_RESTRICTED_AREA.equals(rule.getRuleType()) && ACTION_DENY.equals(rule.getAction())) {
                checkRestrictedArea(rule, missionSteps, violations);
            } else if (RULE_TYPE_SPEED_LIMIT.equals(rule.getRuleType()) && ACTION_WARN.equals(rule.getAction())) {
                checkSpeedLimit(rule, missionSteps, violations);
            }
        }
        return violations;
    }

    private void checkRestrictedArea(PolicyRule rule, List<io.opengeobot.platform.robot.dto.MissionStepDto> steps,
                                     List<String> violations) {
        Map<String, Object> condition = parseJson(rule.getCondition());
        if (condition == null) {
            return;
        }
        Object zoneId = condition.get("zone_id");
        if (zoneId == null) {
            return;
        }
        for (io.opengeobot.platform.robot.dto.MissionStepDto step : steps) {
            Map<String, Object> inputParams = step.inputParams();
            if (inputParams != null && zoneId.equals(inputParams.get("zone_id"))) {
                violations.add("Mission step " + step.stepId()
                        + " targets restricted zone '" + zoneId + "' (policy rule: "
                        + rule.getRuleType() + ")");
            }
        }
    }

    private void checkSpeedLimit(PolicyRule rule, List<io.opengeobot.platform.robot.dto.MissionStepDto> steps,
                                 List<String> violations) {
        Map<String, Object> condition = parseJson(rule.getCondition());
        if (condition == null) {
            return;
        }
        Object maxSpeedObj = condition.get("max_speed");
        if (!(maxSpeedObj instanceof Number maxSpeed)) {
            return;
        }
        for (io.opengeobot.platform.robot.dto.MissionStepDto step : steps) {
            Map<String, Object> inputParams = step.inputParams();
            if (inputParams == null) {
                continue;
            }
            Object speedObj = inputParams.get("speed");
            if (speedObj instanceof Number speed && speed.doubleValue() > maxSpeed.doubleValue()) {
                violations.add("Mission step " + step.stepId()
                        + " exceeds speed limit " + maxSpeed + " (speed=" + speed + ")");
            }
        }
    }

    // ----- helpers -----

    private Policy findByPolicyId(String policyId) {
        LambdaQueryWrapper<Policy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Policy::getPolicyId, policyId);
        Policy entity = policyRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Policy '" + policyId + "' not found");
        }
        return entity;
    }

    private boolean existsByName(String name) {
        LambdaQueryWrapper<Policy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Policy::getName, name);
        return policyRepository.selectCount(wrapper) > 0;
    }

    private boolean existsByPolicyId(String policyId) {
        LambdaQueryWrapper<Policy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Policy::getPolicyId, policyId);
        return policyRepository.selectCount(wrapper) > 0;
    }

    private List<PolicyRule> loadRuleEntities(String policyId, int version) {
        LambdaQueryWrapper<PolicyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PolicyRule::getPolicyId, policyId)
                .eq(PolicyRule::getVersion, version)
                .orderByAsc(PolicyRule::getPriority);
        return policyRuleRepository.selectList(wrapper);
    }

    private List<PolicyRuleDto> loadRules(String policyId, Integer version) {
        if (version == null) {
            return List.of();
        }
        return loadRuleEntities(policyId, version).stream()
                .map(PolicyService::toRuleDto)
                .toList();
    }

    private List<PolicyRule> loadActiveRules() {
        LambdaQueryWrapper<Policy> policyWrapper = new LambdaQueryWrapper<>();
        policyWrapper.eq(Policy::getStatus, STATUS_PUBLISHED);
        List<Policy> activePolicies = policyRepository.selectList(policyWrapper);
        if (activePolicies.isEmpty()) {
            return List.of();
        }
        List<String> policyIds = activePolicies.stream()
                .map(Policy::getPolicyId)
                .toList();
        LambdaQueryWrapper<PolicyRule> ruleWrapper = new LambdaQueryWrapper<>();
        ruleWrapper.in(PolicyRule::getPolicyId, policyIds)
                .orderByAsc(PolicyRule::getPriority);
        List<PolicyRule> allRules = policyRuleRepository.selectList(ruleWrapper);
        List<PolicyRule> result = new ArrayList<>();
        for (Policy policy : activePolicies) {
            for (PolicyRule rule : allRules) {
                if (rule.getPolicyId().equals(policy.getPolicyId())
                        && rule.getVersion().equals(policy.getCurrentVersion())) {
                    result.add(rule);
                }
            }
        }
        return result;
    }

    private void persistRules(String policyId, int version, List<PolicyRuleDto> rules, OffsetDateTime now) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (PolicyRuleDto dto : rules) {
            PolicyRule rule = new PolicyRule();
            rule.setPolicyId(policyId);
            rule.setVersion(version);
            rule.setRuleType(dto.ruleType());
            rule.setCondition(dto.condition());
            rule.setAction(dto.action());
            rule.setPriority(dto.priority() != null ? dto.priority() : 100);
            rule.setCreatedAt(now);
            policyRuleRepository.insert(rule);
        }
    }

    private void replaceDraftRules(String policyId, List<PolicyRuleDto> rules) {
        LambdaQueryWrapper<PolicyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PolicyRule::getPolicyId, policyId)
                .eq(PolicyRule::getVersion, DRAFT_VERSION);
        policyRuleRepository.delete(wrapper);
        persistRules(policyId, DRAFT_VERSION, rules, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void writePolicyEvent(String eventType, Policy entity, OffsetDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", idGenerator.generate("evt"));
        payload.put("policy_id", entity.getPolicyId());
        payload.put("name", entity.getName());
        payload.put("status", entity.getStatus());
        payload.put("current_version", entity.getCurrentVersion() != null ? entity.getCurrentVersion() : 0);
        payload.put("occurred_at", Instant.now(clockProvider.getClock()).toString());
        payload.put("trace_id", actorResolver.currentTraceId() != null ? actorResolver.currentTraceId() : "");
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                eventType,
                "1",
                RESOURCE_TYPE,
                entity.getPolicyId(),
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

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise value to JSON for audit", e);
            return null;
        }
    }

    private static PolicyDto toDto(Policy entity, List<PolicyRuleDto> rules) {
        return new PolicyDto(
                entity.getPolicyId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCurrentVersion(),
                rules,
                entity.getScope(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static PolicyRuleDto toRuleDto(PolicyRule entity) {
        return new PolicyRuleDto(
                entity.getRuleType(),
                entity.getCondition(),
                entity.getAction(),
                entity.getPriority()
        );
    }

    private static PolicyVersionDto toVersionDto(String policyId, int version, List<PolicyRule> rules) {
        List<PolicyRuleDto> ruleDtos = rules.stream().map(PolicyService::toRuleDto).toList();
        OffsetDateTime createdAt = rules.isEmpty() ? null : rules.get(0).getCreatedAt();
        return new PolicyVersionDto(policyId, version, STATUS_PUBLISHED, ruleDtos, createdAt);
    }
}
