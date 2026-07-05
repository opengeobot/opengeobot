/*
 * Function: MapArea DTO — API response model for map areas
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for a map area. Maps the {@code map_scene.area} entity
 * to the OpenAPI contract {@code MapArea} schema.
 *
 * @param areaId    unique area identifier
 * @param mapId     parent map identifier
 * @param name      human-readable area name
 * @param type      area type (ZONE, WAYPOINT, PATH, DOCK)
 * @param geometry  GeoJSON geometry object
 * @param properties additional area properties
 * @param createdAt UTC timestamp of creation
 */
public record MapAreaDto(
        String areaId,
        String mapId,
        String name,
        String type,
        Map<String, Object> geometry,
        Map<String, Object> properties,
        OffsetDateTime createdAt
) {
}
