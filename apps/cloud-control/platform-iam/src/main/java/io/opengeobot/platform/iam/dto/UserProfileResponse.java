/*
 * Function: User profile response DTO — public profile of the authenticated user
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

/**
 * Public profile of the authenticated user. Sensitive fields such as the
 * password hash are never exposed. Field names are serialised in snake_case.
 *
 * @param userId      stable public identifier of the user
 * @param username    unique login name of the user
 * @param displayName human-friendly display name shown in the UI
 * @param email       contact email address of the user
 * @param phone       contact phone number of the user
 * @param avatar      URI reference to the user's avatar image
 * @param status      account status — ACTIVE, DISABLED or LOCKED
 */
public record UserProfileResponse(
        String userId,
        String username,
        String displayName,
        String email,
        String phone,
        String avatar,
        String status
) {
}
