/*
 * Function: Update policy request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Request body for updating a policy's metadata and rules. The {@code status}
 * field is not set directly here; use the publish endpoint for state
 * transitions. Only {@code DRAFT} policies can be updated. Jackson serialises
 * field names in snake_case globally.
 */
public record UpdatePolicyRequest(
        String name,

        String description,

        @Valid
        List<PolicyRuleDto> rules,

        String scope
) {
}
