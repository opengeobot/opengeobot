/*
 * Function: Create organization request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new organization. The {@code orgCode} must be
 * unique. When {@code parentId} is supplied the new org is created as a child.
 * Field names are serialised in snake_case.
 *
 * @param orgName   human-readable name of the organization
 * @param orgCode   unique, stable code identifying the organization
 * @param parentId  identifier of the parent organization (null for root)
 * @param sortOrder sort order among siblings
 * @param description optional explanation of the organization purpose
 */
public record CreateOrgRequest(
        @NotBlank @Size(max = 256) String orgName,
        @NotBlank @Size(max = 128) String orgCode,
        @Size(max = 64) String parentId,
        Integer sortOrder,
        String description
) {
}
