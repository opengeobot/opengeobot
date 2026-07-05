/*
 * Function: Robot module exception types — translated to ErrorEnvelope by RobotExceptionHandler
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.web;

/**
 * Thrown when a requested robot-domain resource (skill, skill version, etc.)
 * cannot be found. Mapped to HTTP 404 with the {@code RESOURCE_NOT_FOUND}
 * error code.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
