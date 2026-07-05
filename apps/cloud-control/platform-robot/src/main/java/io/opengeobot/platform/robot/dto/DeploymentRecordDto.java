/*
 * Function: DeploymentRecord DTO — API response model for per-robot OTA deployments
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a per-robot deployment record. Maps the
 * {@code ota.deployment_record} entity to the OpenAPI contract
 * {@code DeploymentRecord} schema. Jackson serialises field names in
 * snake_case globally.
 *
 * @param recordId    public identifier (ULID-based, prefixed with {@code dep_})
 * @param campaignId   identifier of the owning campaign
 * @param robotId      identifier of the target robot
 * @param status        deployment state for this robot
 * @param startedAt    UTC timestamp when deployment started
 * @param completedAt  UTC timestamp when deployment finished
 * @param error         error message when the deployment failed
 */
public record DeploymentRecordDto(
        String recordId,
        String campaignId,
        String robotId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String error
) {
}
