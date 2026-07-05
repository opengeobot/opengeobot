/*
 * Function: OpsDashboard DTO — aggregated operations dashboard overview for F-OPS-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Operations dashboard overview aggregating system health, robot statistics,
 * mission statistics and alarm statistics into a single snapshot.
 *
 * @param systemHealth overall system health with per-component snapshots
 * @param robotStats    robot fleet statistics
 * @param missionStats  mission statistics
 * @param alarmStats    alarm statistics
 */
public record OpsDashboard(
        @JsonProperty("system_health") SystemHealth systemHealth,
        @JsonProperty("robot_stats") RobotStats robotStats,
        @JsonProperty("mission_stats") MissionStats missionStats,
        @JsonProperty("alarm_stats") AlarmStats alarmStats
) {
    public record SystemHealth(
            String overall,
            List<HealthCheckDto> components
    ) {
    }

    public record RobotStats(
            long total,
            long online,
            long offline,
            long busy,
            long error
    ) {
    }

    public record MissionStats(
            long total,
            long active,
            long completed,
            long failed
    ) {
    }

    public record AlarmStats(
            long active,
            long acknowledged,
            long resolved
    ) {
    }
}
