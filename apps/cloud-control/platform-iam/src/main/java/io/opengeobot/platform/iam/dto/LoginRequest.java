/*
 * Function: Login request DTO — credentials submitted to start a session
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials submitted by a user to authenticate. Jackson serialises field
 * names in snake_case via the global {@code SNAKE_CASE} property naming
 * strategy.
 *
 * @param username unique login name of the user
 * @param password plain-text password; never persisted or logged
 */
public record LoginRequest(
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 256) String password
) {
}
