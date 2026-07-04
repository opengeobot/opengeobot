/*
 * Function: Stable platform error codes — machine-readable contract, not editable dictionary data
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.error;

/**
 * Stable, machine-readable error codes used across the platform.
 * Each code carries an HTTP status, a human-readable title and an i18n message key.
 * Clients must switch on {@link #code()}, never on the title or message key text.
 */
public enum ErrorCode {

    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", 500, "error.internal"),
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed", 400, "error.validation_failed"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Resource not found", 404, "error.resource_not_found"),
    PERMISSION_DENIED("PERMISSION_DENIED", "Permission denied", 403, "error.permission_denied"),
    CONFLICT("CONFLICT", "Resource conflict", 409, "error.conflict"),
    UNPROCESSABLE_ENTITY("UNPROCESSABLE_ENTITY", "Unprocessable entity", 422, "error.unprocessable_entity"),
    HEALTH_NOT_READY("HEALTH_NOT_READY", "Service not ready", 503, "error.health.not_ready");

    private final String code;
    private final String title;
    private final int status;
    private final String messageKey;

    ErrorCode(String code, String title, int status, String messageKey) {
        this.code = code;
        this.title = title;
        this.status = status;
        this.messageKey = messageKey;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public int status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }
}
