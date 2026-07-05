/*
 * Function: Update profile request DTO — mutable profile fields the user may update
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Mutable profile fields that the authenticated user may update. Identity
 * fields ({@code id}, {@code username}) are not editable through this request.
 * Only supplied (non-null) fields are updated. Field names are serialised in
 * snake_case.
 *
 * @param displayName human-friendly display name shown in the UI
 * @param email       contact email address of the user
 * @param phone       contact phone number of the user
 * @param avatar      URI reference to the user's avatar image
 */
public record UpdateProfileRequest(
        @Size(max = 256) String displayName,
        @Email @Size(max = 256) String email,
        @Size(max = 32) String phone,
        @Size(max = 512) String avatar
) {
}
