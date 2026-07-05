/*
 * Function: RestrictedArea DTO — API response model for restricted areas
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for a restricted area. Maps the
 * {@code map_scene.restricted_area} entity to the OpenAPI contract
 * {@code RestrictedArea} schema.
 *
 * @param areaId          unique restricted area identifier
 * @param mapId           parent map identifier
 * @param name            human-readable name
 * @param restrictionType type of restriction (NO_ENTRY, SPEED_LIMIT, TIME_WINDOW)
 * @param geometry        GeoJSON geometry object
 * @param properties      additional restriction properties
 * @param effectiveFrom   UTC timestamp from which the restriction is active
 * @param effectiveTo     optional UTC timestamp after which the restriction expires
 * @param createdAt       UTC timestamp of creation
 */
public record RestrictedAreaDto(
        String areaId,
        String mapId,
        String name,
        String restrictionType,
        Map<String, Object> geometry,
        Map<String, Object> properties,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        OffsetDateTime createdAt
) {
}
