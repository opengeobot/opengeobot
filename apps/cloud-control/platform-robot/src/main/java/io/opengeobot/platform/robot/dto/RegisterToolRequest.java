/*
 * Function: Register MCP tool request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for registering a new MCP tool. The {@code name} must be
 * unique; a duplicate returns {@code TOOL_ALREADY_EXISTS}. A freshly created
 * tool starts in {@code DRAFT} status. Jackson serialises field names in
 * snake_case globally.
 */
public record RegisterToolRequest(
        @NotBlank(message = "name must not be blank")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "name must be snake_case")
        String name,

        String description,

        String inputSchema,

        String outputSchema,

        @Min(value = 0, message = "canary_percent must be between 0 and 100")
        @Max(value = 100, message = "canary_percent must be between 0 and 100")
        Integer canaryPercent
) {
}
