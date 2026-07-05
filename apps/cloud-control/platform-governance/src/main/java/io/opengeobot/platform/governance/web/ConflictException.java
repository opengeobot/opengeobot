/*
 * Function: Governance conflict exception — duplicate key or invalid state transition
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.web;

/**
 * Thrown when a governance write violates a uniqueness constraint or attempts
 * an invalid state transition (e.g. publishing an already-published dictionary
 * version). Mapped to HTTP 409 with the {@code CONFLICT} error code.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
