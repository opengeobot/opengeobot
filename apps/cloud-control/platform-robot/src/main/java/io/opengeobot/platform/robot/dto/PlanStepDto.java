/*
 * Function: Plan step DTO - a single step in a QwenPaw-generated plan proposal
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * A single step in an UNTRUSTED plan proposal returned by the agent-runtime.
 * Field names follow the snake_case platform contract, matching the Python
 * {@code PlanStep} pydantic model.
 *
 * @param stepId           unique step identifier assigned by the agent
 * @param stepOrder        ordinal position within the plan (0-based)
 * @param skillId          registered skill identifier to execute
 * @param params           input parameters for the skill
 * @param description      human-readable description of the step
 * @param valid            whether the step passed schema validation
 * @param validationError  validation error message if {@code valid} is false
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanStepDto(
        String stepId,
        int stepOrder,
        String skillId,
        Map<String, Object> params,
        String description,
        boolean valid,
        String validationError
) {
}
