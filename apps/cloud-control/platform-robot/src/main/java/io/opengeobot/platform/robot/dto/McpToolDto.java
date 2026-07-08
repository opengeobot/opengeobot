/*
 * Function: MCP tool DTO — API response model for MCP tool entries
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for an MCP tool. Maps the {@code skill_registry.mcp_tool}
 * entity to the OpenAPI contract {@code McpTool} schema. Jackson serialises
 * field names in snake_case globally.
 *
 * @param toolId        public identifier (ULID-based, prefixed with {@code mcp_})
 * @param name           unique tool name in snake_case
 * @param description    human-readable explanation
 * @param inputSchema    JSON Schema document describing input parameters
 * @param outputSchema   JSON Schema document describing output
 * @param canaryPercent  percentage of invocations routed to canary
 * @param status         lifecycle status (DRAFT, ACTIVE, DEPRECATED, DISABLED)
 * @param createdBy       actor that created the tool
 * @param createdAt      UTC timestamp of creation
 * @param updatedAt      UTC timestamp of last update
 */
public record McpToolDto(
        String toolId,
        String name,
        String description,
        String inputSchema,
        String outputSchema,
        Integer canaryPercent,
        String status,
        String handlerType,
        String handlerEndpoint,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
