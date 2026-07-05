/*
 * Function: Update organization request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.Size;

/**
 * Mutable fields of an organization. The {@code orgCode} cannot be changed.
 * Field names are serialised in snake_case.
 *
 * @param orgName     human-readable name of the organization
 * @param sortOrder   sort order among siblings
 * @param status      lifecycle status — ACTIVE or DISABLED
 * @param description optional explanation of the organization purpose
 */
public record UpdateOrgRequest(
        @Size(max = 256) String orgName,
        Integer sortOrder,
        String status,
        String description
) {
}
