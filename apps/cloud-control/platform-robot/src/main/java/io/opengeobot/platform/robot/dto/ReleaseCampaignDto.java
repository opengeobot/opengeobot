/*
 * Function: ReleaseCampaign DTO — API response model for OTA release campaigns
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API response DTO for an OTA release campaign. Maps the
 * {@code ota.release_campaign} entity to the OpenAPI contract
 * {@code ReleaseCampaign} schema. Jackson serialises field names in
 * snake_case globally.
 *
 * @param campaignId    public identifier (ULID-based, prefixed with {@code ota_})
 * @param packageId     identifier of the package to deploy
 * @param canaryPercent percentage of target robots for the canary wave
 * @param status         campaign lifecycle status
 * @param targetRobots  list of robot identifiers targeted by the campaign
 * @param startedAt     UTC timestamp when deployment started
 * @param completedAt   UTC timestamp when the campaign finished
 * @param createdBy      actor that created the campaign
 * @param createdAt      UTC timestamp of creation
 */
public record ReleaseCampaignDto(
        String campaignId,
        String packageId,
        Integer canaryPercent,
        String status,
        List<String> targetRobots,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String createdBy,
        OffsetDateTime createdAt
) {
}
