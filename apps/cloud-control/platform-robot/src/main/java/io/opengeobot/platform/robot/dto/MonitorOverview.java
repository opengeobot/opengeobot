/*
 * Function: Monitor overview DTO — fleet-wide dashboard counts for F-MONITOR-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Dashboard overview aggregating fleet-wide counts for the real-time
 * monitoring console. Counts are derived from current robot and mission
 * state. Field names follow the snake_case platform contract.
 */
public record MonitorOverview(
        @JsonProperty("total_robots") long totalRobots,
        @JsonProperty("online_robots") long onlineRobots,
        @JsonProperty("busy_robots") long busyRobots,
        @JsonProperty("active_missions") long activeMissions,
        @JsonProperty("error_robots") long errorRobots,
        @JsonProperty("safety_alerts") long safetyAlerts
) {
}
