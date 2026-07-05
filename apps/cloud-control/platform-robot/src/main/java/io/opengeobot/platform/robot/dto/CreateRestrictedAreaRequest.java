/*
 * Function: CreateRestrictedAreaRequest DTO — request body for creating a restricted area
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Request body for creating a restricted area.
 *
 * @param name            human-readable name
 * @param restrictionType type of restriction (NO_ENTRY, SPEED_LIMIT, TIME_WINDOW)
 * @param geometry        GeoJSON geometry object
 * @param properties      additional restriction properties
 * @param effectiveFrom   UTC timestamp from which the restriction is active
 * @param effectiveTo     optional UTC timestamp after which the restriction expires
 */
public record CreateRestrictedAreaRequest(
        String name,
        String restrictionType,
        Map<String, Object> geometry,
        Map<String, Object> properties,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo
) {
}
