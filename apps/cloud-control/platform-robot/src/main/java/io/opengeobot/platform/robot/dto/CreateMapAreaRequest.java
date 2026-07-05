/*
 * Function: CreateMapAreaRequest DTO — request body for creating a map area
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.util.Map;

/**
 * Request body for creating a map area.
 *
 * @param name       human-readable area name
 * @param type       area type (ZONE, WAYPOINT, PATH, DOCK)
 * @param geometry   GeoJSON geometry object
 * @param properties additional area properties
 */
public record CreateMapAreaRequest(
        String name,
        String type,
        Map<String, Object> geometry,
        Map<String, Object> properties
) {
}
