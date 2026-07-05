/*
 * Function: Invocation result DTO — API response model for tool invocation results
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for a tool invocation result. Maps the
 * {@code skill_registry.mcp_invocation_log} entity to the OpenAPI contract
 * {@code InvocationResult} schema. Jackson serialises field names in
 * snake_case globally.
 *
 * @param invocationId  unique identifier of the invocation log entry
 * @param toolId        identifier of the invoked tool
 * @param status        outcome of the invocation (SUCCESS, FAILED, TIMEOUT)
 * @param output         the tool's output payload
 * @param error         error message, present when status is not SUCCESS
 * @param durationMs    wall-clock execution duration in milliseconds
 * @param invokedBy     actor that invoked the tool
 * @param invokedAt     UTC timestamp when the invocation was started
 * @param traceId       end-to-end trace identifier
 */
public record InvocationResultDto(
        String invocationId,
        String toolId,
        String status,
        Map<String, Object> output,
        String error,
        Integer durationMs,
        String invokedBy,
        OffsetDateTime invokedAt,
        String traceId
) {
}
