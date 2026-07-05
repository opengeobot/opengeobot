/*
 * Function: CreateCampaignRequest DTO — request body for creating an OTA campaign
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for creating a release campaign. The {@code packageId} must
 * reference an existing firmware/skill package. {@code targetRobots} lists the
 * robots to deploy to; {@code canaryPercent} controls the canary wave size.
 * Jackson serialises field names in snake_case globally.
 */
public record CreateCampaignRequest(
        @NotBlank(message = "package_id must not be blank")
        String packageId,

        @NotEmpty(message = "target_robots must not be empty")
        List<String> targetRobots,

        @Min(value = 0, message = "canary_percent must be at least 0")
        @Max(value = 100, message = "canary_percent must be at most 100")
        Integer canaryPercent
) {
}
