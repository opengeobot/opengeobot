/*
 * Function: MediaObject DTO — API response model for media objects
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a media object. Maps the {@code media.media_object}
 * entity to the OpenAPI contract {@code MediaObject} schema.
 *
 * @param mediaId     public identifier (ULID-based, prefixed with {@code med_})
 * @param fileName    original file name
 * @param filePath    object path within the MinIO/S3 bucket
 * @param fileSize    file size in bytes
 * @param contentType MIME content type
 * @param mediaType   logical media type (IMAGE, VIDEO, LOG, MAP, DOCUMENT)
 * @param robotId     optional robot identifier
 * @param missionId   optional mission identifier
 * @param uploadedBy  actor that uploaded the file
 * @param uploadedAt  UTC timestamp of upload
 * @param expiresAt   optional UTC timestamp after which the media expires
 */
public record MediaObjectDto(
        String mediaId,
        String fileName,
        String filePath,
        Long fileSize,
        String contentType,
        String mediaType,
        String robotId,
        String missionId,
        String uploadedBy,
        OffsetDateTime uploadedAt,
        OffsetDateTime expiresAt
) {
}
