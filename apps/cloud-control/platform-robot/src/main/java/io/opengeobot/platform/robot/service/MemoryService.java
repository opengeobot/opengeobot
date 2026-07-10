/*
 * Function: Memory service — case recording, similarity search and improvement suggestions
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
import io.opengeobot.platform.robot.domain.FailureCase;
import io.opengeobot.platform.robot.domain.ImprovementSuggestion;
import io.opengeobot.platform.robot.domain.TaskCase;
import io.opengeobot.platform.robot.dto.FeedbackRequest;
import io.opengeobot.platform.robot.dto.ImprovementSuggestionDto;
import io.opengeobot.platform.robot.dto.TaskCaseDto;
import io.opengeobot.platform.robot.repository.FailureCaseRepository;
import io.opengeobot.platform.robot.repository.ImprovementSuggestionRepository;
import io.opengeobot.platform.robot.repository.TaskCaseRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for task memory (F-MEMORY-001). Records task execution outcomes,
 * finds similar historical failure cases, generates rule-based improvement
 * suggestions, and accepts feedback. Cases follow the memory store; failure
 * cases are categorised by {@code failure_type}; suggestions follow
 * SM-IMPROVE-001 (PENDING → ACCEPTED / REJECTED / APPLIED). All mutations
 * emit domain events via the transactional outbox and are audited.
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final String RESOURCE_TYPE = "task_case";
    private static final String SUGGESTION_RESOURCE_TYPE = "improvement_suggestion";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILURE = "FAILURE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String FAILURE_TIMEOUT = "TIMEOUT";
    private static final String FAILURE_SKILL_ERROR = "SKILL_ERROR";
    private static final String FAILURE_SAFETY = "SAFETY_VIOLATION";
    private static final String FAILURE_HARDWARE = "HARDWARE_FAULT";
    private static final String FAILURE_UNKNOWN = "UNKNOWN";
    private static final String MEMORY_CASE_RECORDED_EVENT = "memory.case_recorded.v1";
    private static final String MEMORY_IMPROVEMENT_SUGGESTED_EVENT = "memory.improvement_suggested.v1";

    private final TaskCaseRepository taskCaseRepository;
    private final FailureCaseRepository failureCaseRepository;
    private final ImprovementSuggestionRepository suggestionRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public MemoryService(TaskCaseRepository taskCaseRepository,
                         FailureCaseRepository failureCaseRepository,
                         ImprovementSuggestionRepository suggestionRepository,
                         OutboxRepository outboxRepository,
                         AuditService auditService,
                         ActorResolver actorResolver,
                         ClockProvider clockProvider,
                         PublicIdGenerator idGenerator,
                         ObjectMapper objectMapper) {
        this.taskCaseRepository = taskCaseRepository;
        this.failureCaseRepository = failureCaseRepository;
        this.suggestionRepository = suggestionRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskCaseDto recordCase(String missionId, String robotId, String skillId,
                                  String result, Long durationMs, Map<String, Object> context,
                                  String errorMessage, String traceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskCase entity = new TaskCase();
        entity.setCaseId(idGenerator.generate("tcs"));
        entity.setMissionId(missionId);
        entity.setRobotId(robotId);
        entity.setSkillId(skillId);
        entity.setResult(result);
        entity.setDurationMs(durationMs);
        entity.setContext(context);
        entity.setErrorMessage(errorMessage);
        entity.setOccurredAt(now);
        entity.setTraceId(traceId);
        taskCaseRepository.insert(entity);
        writeCaseRecordedEvent(entity);
        audit("memory.case.record", RESOURCE_TYPE, entity.getCaseId(), "SUCCESS", null, toJson(entity));

        if (RESULT_FAILURE.equals(result)) {
            String failureType = categoriseFailure(errorMessage);
            List<String> similar = findSimilarCaseIds(skillId, failureType);
            createFailureCase(entity.getCaseId(), failureType, errorMessage, context, similar);
            generateSuggestion(entity.getCaseId(), failureType, errorMessage, skillId);
        }
        log.info("Recorded task case {} (result {}, skill {})", entity.getCaseId(), result, skillId);
        return toDto(entity);
    }

    public PageResult<TaskCaseDto> listCases(String result, String robotId, String skillId,
                                             PageRequest pageRequest) {
        LambdaQueryWrapper<TaskCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(result != null && !result.isBlank(), TaskCase::getResult, result)
                .eq(robotId != null && !robotId.isBlank(), TaskCase::getRobotId, robotId)
                .eq(skillId != null && !skillId.isBlank(), TaskCase::getSkillId, skillId)
                .orderByDesc(TaskCase::getOccurredAt);
        Page<TaskCase> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<TaskCase> resultPage = taskCaseRepository.selectPage(page, wrapper);
        return new PageResult<>(
                resultPage.getRecords().stream().map(MemoryService::toDto).toList(),
                resultPage.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public CaseDetail getCase(String caseId) {
        TaskCase taskCase = findCaseByCaseId(caseId);
        FailureCase failureCase = findFailureCaseByCaseId(caseId);
        return new CaseDetail(toDto(taskCase),
                failureCase != null ? toFailureDto(failureCase) : null);
    }

    public List<TaskCaseDto> findSimilarCases(String skillId, String failureType) {
        LambdaQueryWrapper<FailureCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FailureCase::getFailureType, failureType);
        List<FailureCase> failureCases = failureCaseRepository.selectList(wrapper);
        List<String> caseIds = new ArrayList<>();
        for (FailureCase fc : failureCases) {
            if (fc.getCaseId() != null) {
                caseIds.add(fc.getCaseId());
            }
        }
        if (caseIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<TaskCase> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(TaskCase::getCaseId, caseIds)
                .eq(TaskCase::getSkillId, skillId)
                .orderByDesc(TaskCase::getOccurredAt)
                .last("LIMIT 5");
        return taskCaseRepository.selectList(taskWrapper).stream()
                .map(MemoryService::toDto).toList();
    }

    public PageResult<ImprovementSuggestionDto> listSuggestions(String status, PageRequest pageRequest) {
        LambdaQueryWrapper<ImprovementSuggestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), ImprovementSuggestion::getStatus, status)
                .orderByDesc(ImprovementSuggestion::getCreatedAt);
        Page<ImprovementSuggestion> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<ImprovementSuggestion> result = suggestionRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(MemoryService::toSuggestionDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    @Transactional
    public ImprovementSuggestionDto generateSuggestion(String caseId, String failureType,
                                                       String errorMessage, String skillId) {
        String text = buildSuggestionText(failureType, errorMessage, skillId);
        double confidence = computeConfidence(failureType);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ImprovementSuggestion entity = new ImprovementSuggestion();
        entity.setSuggestionId(idGenerator.generate("imp"));
        entity.setCaseId(caseId);
        entity.setSuggestionText(text);
        entity.setConfidence(confidence);
        entity.setStatus(STATUS_PENDING);
        entity.setCreatedAt(now);
        suggestionRepository.insert(entity);
        writeImprovementSuggestedEvent(entity);
        audit("memory.suggestion.generate", SUGGESTION_RESOURCE_TYPE,
                entity.getSuggestionId(), "SUCCESS", null, toJson(entity));
        log.info("Generated suggestion {} for case {} (confidence {})",
                entity.getSuggestionId(), caseId, confidence);
        return toSuggestionDto(entity);
    }

    @Transactional
    public ImprovementSuggestionDto submitFeedback(FeedbackRequest request) {
        ImprovementSuggestion entity = findSuggestionBySuggestionId(request.suggestionId());
        String payloadBefore = toJson(entity);
        entity.setFeedback(request.feedback());
        if (STATUS_PENDING.equals(entity.getStatus())) {
            String decision = request.decision() == null ? "ACCEPT" : request.decision().trim().toUpperCase();
            if ("REJECT".equals(decision) || "REJECTED".equals(decision)) {
                entity.setStatus(STATUS_REJECTED);
            } else {
                // ACCEPT only marks approval — never auto-applies motion/skill changes.
                entity.setStatus(STATUS_ACCEPTED);
            }
        }
        suggestionRepository.updateById(entity);
        audit("memory.suggestion.feedback", SUGGESTION_RESOURCE_TYPE,
                entity.getSuggestionId(), "SUCCESS", payloadBefore, toJson(entity));
        log.info("Submitted feedback for suggestion {} -> {}", entity.getSuggestionId(), entity.getStatus());
        return toSuggestionDto(entity);
    }

    private List<String> findSimilarCaseIds(String skillId, String failureType) {
        LambdaQueryWrapper<FailureCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FailureCase::getFailureType, failureType);
        List<FailureCase> failureCases = failureCaseRepository.selectList(wrapper);
        List<String> caseIds = new ArrayList<>();
        for (FailureCase fc : failureCases) {
            if (fc.getCaseId() != null) {
                caseIds.add(fc.getCaseId());
            }
        }
        if (caseIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<TaskCase> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(TaskCase::getCaseId, caseIds)
                .eq(TaskCase::getSkillId, skillId)
                .orderByDesc(TaskCase::getOccurredAt)
                .last("LIMIT 5");
        return taskCaseRepository.selectList(taskWrapper).stream()
                .map(TaskCase::getCaseId).toList();
    }

    private void createFailureCase(String caseId, String failureType, String errorMessage,
                                   Map<String, Object> context, List<String> similarCaseIds) {
        FailureCase entity = new FailureCase();
        entity.setCaseId(caseId);
        entity.setFailureType(failureType);
        entity.setRootCause(errorMessage != null ? errorMessage : "Unknown failure");
        entity.setEnvironment(context);
        entity.setSimilarCaseIds(similarCaseIds);
        failureCaseRepository.insert(entity);
    }

    private String categoriseFailure(String errorMessage) {
        if (errorMessage == null) {
            return FAILURE_UNKNOWN;
        }
        String lower = errorMessage.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return FAILURE_TIMEOUT;
        }
        if (lower.contains("safety") || lower.contains("violation")) {
            return FAILURE_SAFETY;
        }
        if (lower.contains("hardware") || lower.contains("motor") || lower.contains("sensor")) {
            return FAILURE_HARDWARE;
        }
        if (lower.contains("skill") || lower.contains("execution")) {
            return FAILURE_SKILL_ERROR;
        }
        return FAILURE_UNKNOWN;
    }

    private String buildSuggestionText(String failureType, String errorMessage, String skillId) {
        return switch (failureType) {
            case FAILURE_TIMEOUT -> "Increase the execution timeout for skill '"
                    + skillId + "' and review long-running steps. Error: " + errorMessage;
            case FAILURE_SKILL_ERROR -> "Review the skill '"
                    + skillId + "' implementation for error handling and retry logic. Error: " + errorMessage;
            case FAILURE_SAFETY -> "Add a safety pre-check before invoking skill '"
                    + skillId + "'. Error: " + errorMessage;
            case FAILURE_HARDWARE -> "Schedule a hardware diagnostic for the robot before retrying skill '"
                    + skillId + "'. Error: " + errorMessage;
            default -> "Investigate the failure of skill '" + skillId + "'. Error: " + errorMessage;
        };
    }

    private double computeConfidence(String failureType) {
        return switch (failureType) {
            case FAILURE_TIMEOUT -> 0.85;
            case FAILURE_SKILL_ERROR -> 0.75;
            case FAILURE_SAFETY -> 0.90;
            case FAILURE_HARDWARE -> 0.70;
            default -> 0.50;
        };
    }

    private TaskCase findCaseByCaseId(String caseId) {
        LambdaQueryWrapper<TaskCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskCase::getCaseId, caseId);
        TaskCase entity = taskCaseRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Task case '" + caseId + "' not found");
        }
        return entity;
    }

    private FailureCase findFailureCaseByCaseId(String caseId) {
        LambdaQueryWrapper<FailureCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FailureCase::getCaseId, caseId);
        return failureCaseRepository.selectOne(wrapper);
    }

    private ImprovementSuggestion findSuggestionBySuggestionId(String suggestionId) {
        LambdaQueryWrapper<ImprovementSuggestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImprovementSuggestion::getSuggestionId, suggestionId);
        ImprovementSuggestion entity = suggestionRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Suggestion '" + suggestionId + "' not found");
        }
        return entity;
    }

    private void writeCaseRecordedEvent(TaskCase entity) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "case_id", entity.getCaseId(),
                "mission_id", entity.getMissionId(),
                "robot_id", entity.getRobotId(),
                "skill_id", entity.getSkillId(),
                "result", entity.getResult(),
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", entity.getTraceId() != null ? entity.getTraceId() : ""
        );
        saveOutboxEvent(MEMORY_CASE_RECORDED_EVENT, RESOURCE_TYPE, entity.getCaseId(), payload);
    }

    private void writeImprovementSuggestedEvent(ImprovementSuggestion entity) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "suggestion_id", entity.getSuggestionId(),
                "case_id", entity.getCaseId(),
                "suggestion_text", entity.getSuggestionText(),
                "confidence", entity.getConfidence(),
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", actorResolver.currentTraceId() != null ? actorResolver.currentTraceId() : ""
        );
        saveOutboxEvent(MEMORY_IMPROVEMENT_SUGGESTED_EVENT, SUGGESTION_RESOURCE_TYPE,
                entity.getSuggestionId(), payload);
    }

    private void saveOutboxEvent(String eventType, String aggregateType, String aggregateId,
                                Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                eventType,
                "1",
                aggregateType,
                aggregateId,
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

    private static TaskCaseDto toDto(TaskCase entity) {
        return new TaskCaseDto(
                entity.getCaseId(),
                entity.getMissionId(),
                entity.getRobotId(),
                entity.getSkillId(),
                entity.getResult(),
                entity.getDurationMs(),
                entity.getContext(),
                entity.getErrorMessage(),
                entity.getOccurredAt(),
                entity.getTraceId()
        );
    }

    private static io.opengeobot.platform.robot.dto.FailureCaseDto toFailureDto(FailureCase entity) {
        return new io.opengeobot.platform.robot.dto.FailureCaseDto(
                entity.getCaseId(),
                entity.getFailureType(),
                entity.getRootCause(),
                entity.getEnvironment(),
                entity.getSimilarCaseIds()
        );
    }

    private static ImprovementSuggestionDto toSuggestionDto(ImprovementSuggestion entity) {
        return new ImprovementSuggestionDto(
                entity.getSuggestionId(),
                entity.getCaseId(),
                entity.getSuggestionText(),
                entity.getConfidence(),
                entity.getStatus(),
                entity.getFeedback(),
                entity.getCreatedAt()
        );
    }

    /**
     * Composite response for the case detail endpoint, carrying the task case
     * and, if applicable, the analysed failure case.
     */
    public record CaseDetail(TaskCaseDto taskCase,
                             io.opengeobot.platform.robot.dto.FailureCaseDto failureCase) {
    }
}
