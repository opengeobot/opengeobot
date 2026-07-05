/*
 * Function: Robot model DTO — API response model for robot model entries
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a robot model. Maps the {@code robot_registry.robot_model}
 * entity to the OpenAPI contract. The {@code capabilities} field is a JSON
 * array string describing the capabilities typically available for this model.
 * Jackson serialises field names in snake_case globally.
 *
 * @param modelId      public identifier (ULID-based, prefixed with {@code mdl_})
 * @param modelName    human-friendly model name
 * @param manufacturer hardware manufacturer
 * @param description  human-readable description
 * @param capabilities JSON array string of capability descriptors
 * @param createdAt    UTC timestamp of creation
 * @param updatedAt    UTC timestamp of last update
 */
public record RobotModelDto(
        String modelId,
        String modelName,
        String manufacturer,
        String description,
        String capabilities,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
