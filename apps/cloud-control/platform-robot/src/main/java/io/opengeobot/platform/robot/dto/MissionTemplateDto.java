/*
 * Function: Mission template DTO — response shape for mission templates
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Data-transfer object for a mission template. The {@code steps} field carries
 * the blueprint step list. Field names follow the snake_case platform contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MissionTemplateDto(
        @JsonProperty("template_id") String templateId,
        String name,
        String description,
        List<MissionStepDto> steps,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {
}
