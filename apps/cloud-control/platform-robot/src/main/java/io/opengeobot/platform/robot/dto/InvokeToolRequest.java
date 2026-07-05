/*
 * Function: Invoke MCP tool request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request body for invoking a registered MCP tool. The {@code inputParams} map
 * is validated against the tool's {@code input_schema} before execution.
 */
public record InvokeToolRequest(
        @NotNull(message = "input_params must not be null")
        Map<String, Object> inputParams
) {
}
