/*
 * Function: PolicyRule DTO — API model for a single policy rule
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * API model for a single rule within a policy. Each rule has a {@code ruleType}
 * (e.g. restricted_area, speed_limit), a {@code condition} (JSON string), an
 * {@code action} (ALLOW, DENY, WARN), and a {@code priority} for evaluation
 * order. Jackson serialises field names in snake_case globally.
 *
 * @param ruleType  the type of rule, determines how condition is interpreted
 * @param condition rule condition payload as a JSON string
 * @param action    the action taken when the rule matches
 * @param priority  evaluation priority (lower = first)
 */
public record PolicyRuleDto(
        String ruleType,
        String condition,
        String action,
        Integer priority
) {
}
