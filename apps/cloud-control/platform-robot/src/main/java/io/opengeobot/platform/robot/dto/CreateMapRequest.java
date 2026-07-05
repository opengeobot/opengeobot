/*
 * Function: CreateMapRequest DTO — request body for creating a map
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.util.Map;

/**
 * Request body for creating a new map.
 *
 * @param name        human-readable map name
 * @param description optional human-readable description
 * @param metadata    optional map metadata (resolution, origin, etc.)
 */
public record CreateMapRequest(
        String name,
        String description,
        Map<String, Object> metadata
) {
}
