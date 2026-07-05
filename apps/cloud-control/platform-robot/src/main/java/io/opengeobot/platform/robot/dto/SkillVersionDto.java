/*
 * Function: SkillVersion DTO — API response model for skill version snapshots
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a skill version. Maps the
 * {@code skill_registry.skill_version} entity to the OpenAPI contract
 * {@code SkillVersion} schema. Jackson serialises field names in snake_case
 * globally.
 *
 * @param skillId      identifier of the parent skill
 * @param version      monotonic version number
 * @param status       lifecycle status (PUBLISHED, DEPRECATED)
 * @param changelog    human-readable summary of changes
 * @param inputSchema  JSON Schema captured at publish time
 * @param outputSchema JSON Schema captured at publish time
 * @param publishedAt  UTC timestamp of publication
 * @param publishedBy  actor that published this version
 * @param createdAt    UTC timestamp when the row was created
 */
public record SkillVersionDto(
        String skillId,
        Integer version,
        String status,
        String changelog,
        String inputSchema,
        String outputSchema,
        OffsetDateTime publishedAt,
        String publishedBy,
        OffsetDateTime createdAt
) {
}
