/*
 * Function: Skill DTO — API response model for skill entries
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a skill. Maps the {@code skill_registry.skill} entity
 * to the OpenAPI contract {@code Skill} schema. Jackson serialises field names
 * in snake_case globally.
 *
 * @param skillId        public identifier (ULID-based, prefixed with {@code skl_})
 * @param name           unique skill name in snake_case
 * @param module         logical module that owns the skill
 * @param description    human-readable explanation
 * @param status         lifecycle status (DRAFT, PUBLISHED, DEPRECATED, DISABLED)
 * @param currentVersion active published version number (0 for a draft)
 * @param inputSchema    JSON Schema document describing input parameters
 * @param outputSchema   JSON Schema document describing output
 * @param createdBy       actor that created the skill
 * @param updatedBy       actor that last updated the skill
 * @param createdAt      UTC timestamp of creation
 * @param updatedAt      UTC timestamp of last update
 */
public record SkillDto(
        String skillId,
        String name,
        String module,
        String description,
        String status,
        Integer currentVersion,
        String inputSchema,
        String outputSchema,
        String createdBy,
        String updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
