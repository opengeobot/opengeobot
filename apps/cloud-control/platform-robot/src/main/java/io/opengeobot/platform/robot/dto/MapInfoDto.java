/*
 * Function: MapInfo DTO — API response model for map entries
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for a map. Maps the {@code map_scene.map} entity to the
 * OpenAPI contract {@code MapInfo} schema.
 *
 * @param mapId       public identifier (ULID-based, prefixed with {@code map_})
 * @param name        human-readable map name
 * @param description optional description
 * @param version     published version number (0 for a draft)
 * @param status      lifecycle status (DRAFT, PUBLISHED, ARCHIVED)
 * @param metadata    additional map metadata (resolution, origin, etc.)
 * @param createdBy   actor that created the map
 * @param createdAt   UTC timestamp of creation
 * @param updatedAt   UTC timestamp of last update
 */
public record MapInfoDto(
        String mapId,
        String name,
        String description,
        Integer version,
        String status,
        Map<String, Object> metadata,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
