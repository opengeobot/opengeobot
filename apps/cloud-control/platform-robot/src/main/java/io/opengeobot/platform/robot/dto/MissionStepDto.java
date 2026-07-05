/*
 * Function: Mission step DTO — step definition shared by create/revise requests and detail responses
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Data-transfer object for a mission step. Used both as the request shape when
 * creating or revising a plan and as the response shape in mission detail. Field
 * names follow the snake_case platform contract; Jackson maps them via
 * {@code @JsonProperty}.
 */
public record MissionStepDto(
        @JsonProperty("step_id") String stepId,
        @JsonProperty("mission_id") String missionId,
        @JsonProperty("skill_id") String skillId,
        @JsonProperty("step_order") Integer stepOrder,
        @JsonProperty("input_params") Map<String, Object> inputParams,
        @JsonProperty("output_result") Map<String, Object> outputResult,
        String status,
        @JsonProperty("started_at") OffsetDateTime startedAt,
        @JsonProperty("completed_at") OffsetDateTime completedAt,
        @JsonProperty("error_message") String errorMessage
) {
}
