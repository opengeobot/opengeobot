/*
 * Function: User DTO — full user representation with org and role assignments
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full user account representation including organization and role assignments.
 * Sensitive fields such as the password hash are never exposed. Field names are
 * serialised in snake_case.
 *
 * @param userId      stable public identifier of the user
 * @param username    unique login name of the user
 * @param displayName human-friendly display name shown in the UI
 * @param email       contact email address of the user
 * @param phone       contact phone number of the user
 * @param avatar      URI reference to the user's avatar image
 * @param status      account status — ACTIVE, DISABLED or LOCKED
 * @param orgId       primary organization identifier of the user
 * @param orgName     primary organization name of the user
 * @param roleIds     role identifiers assigned to the user
 * @param createdAt   UTC timestamp when the user account was created
 * @param updatedAt   UTC timestamp when the user account was last updated
 */
public record UserDto(
        String userId,
        String username,
        String displayName,
        String email,
        String phone,
        String avatar,
        String status,
        String orgId,
        String orgName,
        List<String> roleIds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
