/*
 * Function: Policy DTO — API response model for policy entries
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API response DTO for a policy. Maps the {@code policy.policy} entity to the
 * OpenAPI contract {@code Policy} schema. Jackson serialises field names in
 * snake_case globally.
 *
 * @param policyId       public identifier (ULID-based, prefixed with {@code pol_})
 * @param name            unique policy name
 * @param description     human-readable explanation
 * @param status          lifecycle status (DRAFT, PUBLISHED, ARCHIVED)
 * @param currentVersion  active published version number (0 for a draft)
 * @param rules           the rules attached to the current version
 * @param scope           applicability scope (robot, fleet, org)
 * @param createdBy        actor that created the policy
 * @param updatedBy        actor that last updated the policy
 * @param createdAt       UTC timestamp of creation
 * @param updatedAt       UTC timestamp of last update
 */
public record PolicyDto(
        String policyId,
        String name,
        String description,
        String status,
        Integer currentVersion,
        List<PolicyRuleDto> rules,
        String scope,
        String createdBy,
        String updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
