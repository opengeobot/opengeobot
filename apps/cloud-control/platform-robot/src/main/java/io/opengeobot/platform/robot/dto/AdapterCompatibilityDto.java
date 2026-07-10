/*
 * Function: Adapter compatibility DTO - API response model for adapter compatibility entries
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for an adapter compatibility entry. Maps the
 * {@code robot_registry.adapter_compatibility} entity to the OpenAPI contract.
 * The {@code adapter_type} and {@code health_status} fields are platform code
 * contracts. Jackson serialises field names in snake_case globally.
 *
 * @param adapterId      public identifier (ULID-based, prefixed with {@code adp_})
 * @param robotModelId   public identifier of the robot model this entry relates to
 * @param adapterType    protocol adapter type (ros2 / ros1 / unitree / custom)
 * @param rosVersion     ROS distribution version (e.g. {@code humble}, {@code noetic})
 * @param controlProtocol native control protocol (e.g. {@code zenoh}, {@code rosbridge})
 * @param compatible     whether the combination is verified compatible
 * @param healthStatus   runtime health status (HEALTHY / DEGRADED / UNHEALTHY / UNKNOWN)
 * @param createdAt      UTC timestamp of creation
 * @param updatedAt      UTC timestamp of last update
 */
public record AdapterCompatibilityDto(
        String adapterId,
        String robotModelId,
        String adapterType,
        String rosVersion,
        String controlProtocol,
        Boolean compatible,
        String healthStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
