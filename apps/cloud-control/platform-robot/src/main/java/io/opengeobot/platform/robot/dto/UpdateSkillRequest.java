/*
 * Function: Update skill request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * Request body for updating a skill's metadata and schemas. The {@code status}
 * field is not set directly here; use the publish/disable/enable endpoints for
 * state transitions. All fields are optional; only provided fields are
 * updated. Jackson serialises field names in snake_case globally.
 */
public record UpdateSkillRequest(
        String description,

        String inputSchema,

        String outputSchema
) {
}
