/*
 * Function: Create skill request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for registering a new skill. The {@code name} must be unique;
 * a duplicate returns {@code SKILL_ALREADY_EXISTS}. A freshly created skill
 * starts in {@code DRAFT} status with {@code current_version} 0. Jackson
 * serialises field names in snake_case globally.
 */
public record CreateSkillRequest(
        @NotBlank(message = "name must not be blank")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "name must be snake_case")
        String name,

        @NotBlank(message = "module must not be blank")
        String module,

        String description,

        String inputSchema,

        String outputSchema
) {
}
