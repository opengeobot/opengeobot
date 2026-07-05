/*
 * Function: FirmwarePackage DTO — API response model for OTA packages
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for an OTA firmware/skill package. Maps the
 * {@code ota.firmware_package} entity to the OpenAPI contract
 * {@code FirmwarePackage} schema. Jackson serialises field names in
 * snake_case globally.
 *
 * @param packageId  public identifier (ULID-based, prefixed with {@code pkg_})
 * @param name       human-readable package name
 * @param version    semantic version of the package
 * @param type        package artifact type (FIRMWARE or SKILL_BUNDLE)
 * @param filePath   object path in MinIO/S3
 * @param fileSize    size of the package file in bytes
 * @param checksum   SHA-256 checksum of the package file
 * @param description optional human-readable notes
 * @param createdBy   actor that uploaded the package
 * @param createdAt   UTC timestamp of upload
 */
public record FirmwarePackageDto(
        String packageId,
        String name,
        String version,
        String type,
        String filePath,
        Long fileSize,
        String checksum,
        String description,
        String createdBy,
        OffsetDateTime createdAt
) {
}
