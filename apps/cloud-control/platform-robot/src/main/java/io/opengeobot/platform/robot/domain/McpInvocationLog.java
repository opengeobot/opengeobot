/*
 * Function: McpInvocationLog entity — maps to skill_registry.mcp_invocation_log table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent MCP invocation log entity backed by the
 * {@code skill_registry.mcp_invocation_log} table. Each row records a single
 * tool invocation: input parameters, output result, status, error, wall-clock
 * duration and the trace context. The table is append-only.
 */
@TableName(value = "mcp_invocation_log", schema = "skill_registry", autoResultMap = true)
public class McpInvocationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String invocationId;

    private String toolId;

    @TableField(typeHandler = io.opengeobot.platform.robot.config.JsonbStringTypeHandler.class)
    private String inputParams;

    @TableField(typeHandler = io.opengeobot.platform.robot.config.JsonbStringTypeHandler.class)
    private String outputResult;

    private String status;

    private String errorMessage;

    private Integer durationMs;

    private String invokedBy;

    private OffsetDateTime invokedAt;

    private String traceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvocationId() {
        return invocationId;
    }

    public void setInvocationId(String invocationId) {
        this.invocationId = invocationId;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getInputParams() {
        return inputParams;
    }

    public void setInputParams(String inputParams) {
        this.inputParams = inputParams;
    }

    public String getOutputResult() {
        return outputResult;
    }

    public void setOutputResult(String outputResult) {
        this.outputResult = outputResult;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public String getInvokedBy() {
        return invokedBy;
    }

    public void setInvokedBy(String invokedBy) {
        this.invokedBy = invokedBy;
    }

    public OffsetDateTime getInvokedAt() {
        return invokedAt;
    }

    public void setInvokedAt(OffsetDateTime invokedAt) {
        this.invokedAt = invokedAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
