/*
 * Function: Robot module conflict exception — duplicate name or invalid state transition
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.web;

/**
 * Thrown when a robot-domain write violates a uniqueness constraint or
 * attempts an invalid state transition (e.g. publishing a skill with no
 * changes, disabling an already-disabled skill). Mapped to HTTP 409 with
 * the {@code CONFLICT} error code.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
