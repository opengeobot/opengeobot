/*
 * Function: MCP tool service — registration, invocation, canary routing and audit
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
import io.opengeobot.platform.robot.domain.McpInvocationLog;
import io.opengeobot.platform.robot.domain.McpTool;
import io.opengeobot.platform.robot.dto.InvokeToolRequest;
import io.opengeobot.platform.robot.dto.InvocationResultDto;
import io.opengeobot.platform.robot.dto.McpToolDto;
import io.opengeobot.platform.robot.dto.RegisterToolRequest;
import io.opengeobot.platform.robot.repository.McpInvocationLogRepository;
import io.opengeobot.platform.robot.repository.McpToolRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application service for the MCP tool gateway (F-MCP-001). Handles tool
 * registration, listing, retrieval and invocation with full audit and
 * transactional outbox events. For M2, invocations return a simulated result;
 * actual tool execution against edge skills is M3+. All mutations write audit
 * records and outbox events within the same transaction.
 */
@Service
public class McpToolService {

    private static final Logger log = LoggerFactory.getLogger(McpToolService.class);
    private static final String TOOL_REGISTERED_EVENT = "tool.registered.v1";
    private static final String TOOL_INVOKED_EVENT = "tool.invoked.v1";
    private static final String RESOURCE_TYPE = "mcp_tool";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DEPRECATED = "DEPRECATED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String INVOCATION_SUCCESS = "SUCCESS";
    private static final String INVOCATION_FAILED = "FAILED";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final McpToolRepository toolRepository;
    private final McpInvocationLogRepository invocationLogRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public McpToolService(McpToolRepository toolRepository,
                          McpInvocationLogRepository invocationLogRepository,
                          OutboxRepository outboxRepository,
                          AuditService auditService,
                          ActorResolver actorResolver,
                          ClockProvider clockProvider,
                          PublicIdGenerator idGenerator,
                          ObjectMapper objectMapper) {
        this.toolRepository = toolRepository;
        this.invocationLogRepository = invocationLogRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<McpToolDto> listTools(String status, PageRequest pageRequest) {
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), McpTool::getStatus, status)
                .orderByAsc(McpTool::getName);
        Page<McpTool> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<McpTool> result = toolRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(McpToolService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public McpToolDto getTool(String toolId) {
        return toDto(findByToolId(toolId));
    }

    @Transactional
    public McpToolDto registerTool(RegisterToolRequest request) {
        if (existsByName(request.name())) {
            throw new ConflictException("Tool with name '" + request.name() + "' already exists");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        McpTool entity = new McpTool();
        entity.setToolId(idGenerator.generate("mcp"));
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setInputSchema(request.inputSchema());
        entity.setOutputSchema(request.outputSchema());
        entity.setCanaryPercent(request.canaryPercent() != null ? request.canaryPercent() : 0);
        entity.setStatus(STATUS_DRAFT);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(actor);
        toolRepository.insert(entity);
        writeToolRegisteredEvent(entity);
        audit("mcp.tool.register", RESOURCE_TYPE, entity.getToolId(), "SUCCESS", null, toJson(entity));
        log.info("Registered MCP tool {} ({})", entity.getToolId(), entity.getName());
        return toDto(entity);
    }

    @Transactional
    public InvocationResultDto invoke(String toolId, InvokeToolRequest request) {
        McpTool tool = findByToolId(toolId);
        if (!STATUS_ACTIVE.equals(tool.getStatus())) {
            throw new ConflictException("Tool '" + toolId + "' is not ACTIVE and cannot be invoked");
        }
        Instant now = Instant.now(clockProvider.getClock());
        String actor = actorResolver.currentActor();
        String traceId = actorResolver.currentTraceId();
        String invocationId = idGenerator.generate("inv");

        boolean useCanary = shouldRouteToCanary(tool);

        McpInvocationLog logEntry = new McpInvocationLog();
        logEntry.setInvocationId(invocationId);
        logEntry.setToolId(toolId);
        logEntry.setInputParams(toJson(request.inputParams()));
        logEntry.setInvokedBy(actor);
        logEntry.setInvokedAt(now.atOffset(ZoneOffset.UTC));
        logEntry.setTraceId(traceId);
        logEntry.setStatus(INVOCATION_SUCCESS);

        InvocationResultDto result;
        try {
            Map<String, Object> output = executeSimulated(tool, request.inputParams(), useCanary);
            logEntry.setOutputResult(toJson(output));
            logEntry.setDurationMs((int) Duration.between(now, Instant.now(clockProvider.getClock())).toMillis());
            result = new InvocationResultDto(
                    invocationId, toolId, INVOCATION_SUCCESS, output, null,
                    logEntry.getDurationMs(), actor, logEntry.getInvokedAt(), traceId
            );
        } catch (Exception e) {
            logEntry.setStatus(INVOCATION_FAILED);
            logEntry.setErrorMessage(e.getMessage());
            logEntry.setDurationMs((int) Duration.between(now, Instant.now(clockProvider.getClock())).toMillis());
            result = new InvocationResultDto(
                    invocationId, toolId, INVOCATION_FAILED, null, e.getMessage(),
                    logEntry.getDurationMs(), actor, logEntry.getInvokedAt(), traceId
            );
        }
        invocationLogRepository.insert(logEntry);
        writeToolInvokedEvent(toolId, result, useCanary, now, traceId);
        audit("mcp.tool.invoke", RESOURCE_TYPE, toolId, result.status(), null, toJson(result));
        log.info("Invoked tool {} (invocation={}, status={}, canary={})", toolId, invocationId, result.status(), useCanary);
        return result;
    }

    public PageResult<InvocationResultDto> listInvocations(String toolId, PageRequest pageRequest) {
        if (!existsByToolId(toolId)) {
            throw new ResourceNotFoundException("Tool '" + toolId + "' not found");
        }
        LambdaQueryWrapper<McpInvocationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpInvocationLog::getToolId, toolId)
                .orderByDesc(McpInvocationLog::getInvokedAt);
        Page<McpInvocationLog> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<McpInvocationLog> result = invocationLogRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(McpToolService::toInvocationDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    // ----- helpers -----

    private McpTool findByToolId(String toolId) {
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpTool::getToolId, toolId);
        McpTool entity = toolRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Tool '" + toolId + "' not found");
        }
        return entity;
    }

    private boolean existsByName(String name) {
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpTool::getName, name);
        return toolRepository.selectCount(wrapper) > 0;
    }

    private boolean existsByToolId(String toolId) {
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpTool::getToolId, toolId);
        return toolRepository.selectCount(wrapper) > 0;
    }

    /**
     * Determines whether this invocation should be routed to the canary build.
     * Uses a simple thread-local-random percentage check against the tool's
     * {@code canary_percent}.
     */
    private boolean shouldRouteToCanary(McpTool tool) {
        int canaryPercent = tool.getCanaryPercent() != null ? tool.getCanaryPercent() : 0;
        if (canaryPercent <= 0) {
            return false;
        }
        if (canaryPercent >= 100) {
            return true;
        }
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < canaryPercent;
    }

    /**
     * For M2, tool execution is simulated. Returns a structured output that
     * echoes the tool name and input parameters. Actual tool execution against
     * edge skills is M3+ and must go through the Safety Gateway.
     */
    private Map<String, Object> executeSimulated(McpTool tool, Map<String, Object> inputParams, boolean useCanary) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("tool_name", tool.getName());
        output.put("simulated", true);
        output.put("canary", useCanary);
        output.put("echoed_input", inputParams != null ? inputParams : Map.of());
        return output;
    }

    private void writeToolRegisteredEvent(McpTool entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", idGenerator.generate("evt"));
        payload.put("tool_id", entity.getToolId());
        payload.put("name", entity.getName());
        payload.put("status", entity.getStatus());
        payload.put("occurred_at", Instant.now(clockProvider.getClock()).toString());
        payload.put("trace_id", actorResolver.currentTraceId() != null ? actorResolver.currentTraceId() : "");
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                TOOL_REGISTERED_EVENT,
                "1",
                RESOURCE_TYPE,
                entity.getToolId(),
                null,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                actorResolver.currentTraceId(),
                false,
                null,
                0
        );
        outboxRepository.save(event);
    }

    private void writeToolInvokedEvent(String toolId, InvocationResultDto result, boolean useCanary,
                                       Instant now, String traceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", idGenerator.generate("evt"));
        payload.put("tool_id", toolId);
        payload.put("invocation_id", result.invocationId());
        payload.put("status", result.status());
        payload.put("canary", useCanary);
        payload.put("duration_ms", result.durationMs());
        payload.put("occurred_at", now.toString());
        payload.put("trace_id", traceId != null ? traceId : "");
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                TOOL_INVOKED_EVENT,
                "1",
                RESOURCE_TYPE,
                toolId,
                null,
                toJson(payload),
                now,
                traceId,
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

    private static McpToolDto toDto(McpTool entity) {
        return new McpToolDto(
                entity.getToolId(),
                entity.getName(),
                entity.getDescription(),
                entity.getInputSchema(),
                entity.getOutputSchema(),
                entity.getCanaryPercent(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static InvocationResultDto toInvocationDto(McpInvocationLog entity) {
        Map<String, Object> output = null;
        if (entity.getOutputResult() != null && !entity.getOutputResult().isBlank()) {
            try {
                output = new ObjectMapper().readValue(entity.getOutputResult(), MAP_TYPE);
            } catch (JsonProcessingException e) {
                output = null;
            }
        }
        return new InvocationResultDto(
                entity.getInvocationId(),
                entity.getToolId(),
                entity.getStatus(),
                output,
                entity.getErrorMessage(),
                entity.getDurationMs(),
                entity.getInvokedBy(),
                entity.getInvokedAt(),
                entity.getTraceId()
        );
    }
}
