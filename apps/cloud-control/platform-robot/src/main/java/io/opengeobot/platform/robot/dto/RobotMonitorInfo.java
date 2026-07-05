/*
 * Function: Robot monitor info DTO — real-time robot monitoring snapshot for F-MONITOR-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Real-time monitoring snapshot of a single robot. Aggregates identity,
 * operational status, the mission currently executing on the robot, the
 * latest reported position and battery telemetry, plus the last heartbeat
 * timestamp. Field names follow the snake_case platform contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RobotMonitorInfo(
        @JsonProperty("robot_id") String robotId,
        String name,
        String status,
        @JsonProperty("current_mission_id") String currentMissionId,
        Map<String, Object> position,
        Map<String, Object> battery,
        @JsonProperty("last_seen_at") OffsetDateTime lastSeenAt
) {
}
