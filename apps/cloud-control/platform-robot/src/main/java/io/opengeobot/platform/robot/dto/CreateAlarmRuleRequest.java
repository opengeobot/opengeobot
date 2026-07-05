/*
 * Function: CreateAlarmRuleRequest DTO — request body for creating alarm rules
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a new alarm rule. Field names follow the
 * snake_case platform contract.
 *
 * @param name      human-readable rule name
 * @param source    source domain of the metric
 * @param metric    metric identifier
 * @param condition comparison operator
 * @param threshold threshold value
 * @param severity  severity level
 * @param enabled   whether the rule is active (defaults to true)
 */
public record CreateAlarmRuleRequest(
        @NotBlank String name,
        @NotBlank String source,
        @NotBlank String metric,
        @NotBlank String condition,
        Double threshold,
        String severity,
        Boolean enabled
) {
}
