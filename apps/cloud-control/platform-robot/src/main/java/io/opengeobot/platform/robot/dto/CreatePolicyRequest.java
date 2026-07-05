/*
 * Function: Create policy request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request body for creating a new policy. The {@code name} must be unique;
 * a duplicate returns {@code POLICY_ALREADY_EXISTS}. A freshly created policy
 * starts in {@code DRAFT} status with {@code current_version} 0. Jackson
 * serialises field names in snake_case globally.
 */
public record CreatePolicyRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        String description,

        @Valid
        List<PolicyRuleDto> rules,

        String scope
) {
}
