/*
 * Function: UpdateMapRequest DTO — request body for updating a map
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.util.Map;

/**
 * Request body for updating a map's metadata. Only {@code DRAFT} maps can
 * be updated.
 *
 * @param name        updated map name
 * @param description updated description
 * @param metadata    updated map metadata
 */
public record UpdateMapRequest(
        String name,
        String description,
        Map<String, Object> metadata
) {
}
