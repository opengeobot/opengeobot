/*
 * Function: Create mission request DTO — F-MISSION-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Request body for creating a new mission. The mission is created in the
 * PENDING state with the supplied ordered steps. Field names follow the
 * snake_case platform contract.
 */
public record CreateMissionRequest(
        @NotBlank String name,
        String description,
        @JsonProperty("robot_id")
        @NotBlank
        String robotId,
        String priority,
        @JsonProperty("scheduled_at")
        OffsetDateTime scheduledAt,
        @NotEmpty
        List<MissionStepDto> steps
) {
}
