/*
 * Function: Mission monitor info DTO — real-time mission progress snapshot for F-MONITOR-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Real-time progress snapshot of a single mission. `progress_percent` is
 * derived from the count of completed steps over the total step count. Field
 * names follow the snake_case platform contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MissionMonitorInfo(
        @JsonProperty("mission_id") String missionId,
        String name,
        @JsonProperty("robot_id") String robotId,
        String status,
        @JsonProperty("current_step") int currentStep,
        @JsonProperty("total_steps") int totalSteps,
        @JsonProperty("progress_percent") int progressPercent,
        @JsonProperty("started_at") OffsetDateTime startedAt,
        OffsetDateTime eta
) {
}
