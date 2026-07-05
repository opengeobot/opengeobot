/*
 * Function: McpTool entity — maps to skill_registry.mcp_tool table
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
 * Persistent MCP tool entity backed by the {@code skill_registry.mcp_tool}
 * table. Tools expose a typed input/output contract (JSON Schema) and may
 * carry a {@code canaryPercent} to route a fraction of invocations to a
 * candidate build. The {@code status} field follows the lifecycle
 * (DRAFT, ACTIVE, DEPRECATED, DISABLED).
 */
@TableName(value = "mcp_tool", schema = "skill_registry", autoResultMap = true)
public class McpTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolId;

    private String name;

    private String description;

    @TableField(typeHandler = io.opengeobot.platform.robot.config.JsonbStringTypeHandler.class)
    private String inputSchema;

    @TableField(typeHandler = io.opengeobot.platform.robot.config.JsonbStringTypeHandler.class)
    private String outputSchema;

    private Integer canaryPercent;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    public String getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(String outputSchema) {
        this.outputSchema = outputSchema;
    }

    public Integer getCanaryPercent() {
        return canaryPercent;
    }

    public void setCanaryPercent(Integer canaryPercent) {
        this.canaryPercent = canaryPercent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
