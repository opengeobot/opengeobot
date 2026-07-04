/*
 * Function: VersionInfo response DTO — build and runtime version information
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.bootstrap.health;

/**
 * Build and runtime version information returned by {@code GET /health/info}.
 * Field names are serialised in snake_case to match the OpenAPI contract.
 */
public record VersionInfo(
        String serviceName,
        String version,
        String buildCommit,
        String buildTime,
        int javaVersion,
        String springBootVersion
) {
}
