/*
 * Function: AlarmRule DTO — API response model for alarm rules
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * API response DTO for an alarm rule. Maps the {@code alarm.alarm_rule}
 * entity to the OpenAPI contract {@code AlarmRule} schema.
 *
 * @param ruleId    unique rule identifier
 * @param name      human-readable rule name
 * @param source    source domain of the metric
 * @param metric    metric identifier evaluated by this rule
 * @param condition comparison operator
 * @param threshold threshold value
 * @param severity  severity level (CRITICAL, HIGH, MEDIUM, LOW)
 * @param enabled   whether the rule is active
 * @param createdAt UTC timestamp when the rule was created
 * @param updatedAt UTC timestamp when the rule was last updated
 * @param createdBy actor that created the rule
 */
public record AlarmRuleDto(
        @JsonProperty("rule_id") String ruleId,
        String name,
        String source,
        String metric,
        String condition,
        Double threshold,
        String severity,
        Boolean enabled,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("created_by") String createdBy
) {
}
