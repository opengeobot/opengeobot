/*
 * Function: Create mission template request DTO — F-MISSION-002
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for creating a new mission template with a blueprint step list.
 */
public record CreateMissionTemplateRequest(
        @NotBlank
        String name,
        String description,
        @NotEmpty
        List<MissionStepDto> steps
) {
}
