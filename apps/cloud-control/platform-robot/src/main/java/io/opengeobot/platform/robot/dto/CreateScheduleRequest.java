/*
 * Function: CreateScheduleRequest DTO — assign a mission to a robot in a time window
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Request body for creating a fleet schedule. Assigns a mission to a robot
 * within a planned time window. After creation, the scheduler runs conflict
 * detection against existing schedules. Jackson serialises field names in
 * snake_case globally.
 */
public record CreateScheduleRequest(
        @NotBlank(message = "mission_id must not be blank")
        String missionId,

        @NotBlank(message = "robot_id must not be blank")
        String robotId,

        @NotNull(message = "planned_start must not be null")
        OffsetDateTime plannedStart,

        @NotNull(message = "planned_end must not be null")
        OffsetDateTime plannedEnd,

        String priority
) {
}
