/*
 * Function: AlarmEvent DTO — API response model for alarm events
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * API response DTO for an alarm event. Maps the {@code alarm.alarm_event}
 * entity to the OpenAPI contract {@code AlarmEvent} schema.
 *
 * @param alarmId        unique alarm event identifier
 * @param ruleId         identifier of the rule that triggered this alarm
 * @param source         source of the alarm
 * @param severity       severity level
 * @param message        human-readable description
 * @param status         lifecycle state (ACTIVE, ACKNOWLEDGED, RESOLVED)
 * @param triggeredAt    UTC timestamp when the alarm was triggered
 * @param acknowledgedBy actor that acknowledged the alarm
 * @param acknowledgedAt UTC timestamp when the alarm was acknowledged
 * @param resolvedAt     UTC timestamp when the alarm was resolved
 * @param traceId        end-to-end trace identifier
 */
public record AlarmEventDto(
        @JsonProperty("alarm_id") String alarmId,
        @JsonProperty("rule_id") String ruleId,
        String source,
        String severity,
        String message,
        String status,
        @JsonProperty("triggered_at") OffsetDateTime triggeredAt,
        @JsonProperty("acknowledged_by") String acknowledgedBy,
        @JsonProperty("acknowledged_at") OffsetDateTime acknowledgedAt,
        @JsonProperty("resolved_at") OffsetDateTime resolvedAt,
        @JsonProperty("trace_id") String traceId
) {
}
