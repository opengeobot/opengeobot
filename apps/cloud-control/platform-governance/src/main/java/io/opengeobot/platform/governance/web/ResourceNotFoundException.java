/*
 * Function: Governance exception types — translated to ErrorEnvelope by GovernanceExceptionHandler
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.web;

/**
 * Thrown when a requested governance resource (dictionary type, config, export,
 * etc.) cannot be found. Mapped to HTTP 404 with the
 * {@code RESOURCE_NOT_FOUND} error code.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
