/*
 * Function: Create user request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for creating a new user. The {@code username} must be unique.
 * The {@code password} is hashed with BCrypt before persistence and never
 * returned. Field names are serialised in snake_case.
 *
 * @param username    unique login name of the user
 * @param displayName human-friendly display name shown in the UI
 * @param email       contact email address of the user
 * @param phone       contact phone number of the user
 * @param password    plain-text password; never persisted or logged
 * @param orgId       primary organization identifier to assign the user to
 * @param roleIds     role identifiers to assign to the user
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 128) String username,
        @Size(max = 256) String displayName,
        @Email @Size(max = 256) String email,
        @Size(max = 32) String phone,
        @NotBlank @Size(max = 256) String password,
        @Size(max = 64) String orgId,
        List<String> roleIds
) {
}
