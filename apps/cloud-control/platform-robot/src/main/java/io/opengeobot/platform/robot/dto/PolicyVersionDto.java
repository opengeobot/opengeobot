/*
 * Function: Policy version DTO — API model for a published policy version snapshot
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API model for a published policy version snapshot. Each version corresponds
 * to a set of immutable rule rows in {@code policy.policy_rule}. Jackson
 * serialises field names in snake_case globally.
 *
 * @param policyId  identifier of the parent policy
 * @param version   monotonic version number (starts at 1)
 * @param status    status of this version
 * @param rules     the rules captured at this version
 * @param createdAt UTC timestamp when this version was published
 */
public record PolicyVersionDto(
        String policyId,
        Integer version,
        String status,
        List<PolicyRuleDto> rules,
        OffsetDateTime createdAt
) {
}
