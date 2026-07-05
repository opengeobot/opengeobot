/*
 * Function: Mission DTO — response shape for a mission entry in list and detail responses
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Data-transfer object for a mission. The list endpoint returns this without
 * {@code steps}; the detail endpoint returns this with {@code steps} populated.
 * Field names follow the snake_case platform contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MissionDto(
        @JsonProperty("mission_id") String missionId,
        String name,
        String description,
        @JsonProperty("robot_id") String robotId,
        String status,
        String priority,
        @JsonProperty("scheduled_at") OffsetDateTime scheduledAt,
        @JsonProperty("started_at") OffsetDateTime startedAt,
        @JsonProperty("completed_at") OffsetDateTime completedAt,
        @JsonProperty("failed_reason") String failedReason,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("trace_id") String traceId,
        List<MissionStepDto> steps
) {
}
