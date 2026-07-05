/*
 * Function: Revise plan request DTO — F-MISSION-001 plan revision
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for revising the plan (steps) of a mission that is in PENDING
 * or PLANNING state. Replaces the entire step list.
 */
public record RevisePlanRequest(
        @NotEmpty
        List<MissionStepDto> steps
) {
}
