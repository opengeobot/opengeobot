/*
 * Function: UpdateAlarmRuleRequest DTO — request body for updating alarm rules
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * Request body for updating an existing alarm rule. All fields are optional;
 * only provided fields are updated.
 *
 * @param name      human-readable rule name
 * @param condition comparison operator
 * @param threshold threshold value
 * @param severity  severity level
 * @param enabled   whether the rule is active
 */
public record UpdateAlarmRuleRequest(
        String name,
        String condition,
        Double threshold,
        String severity,
        Boolean enabled
) {
}
