/*
 * Function: Organization DTO — flat org representation for API responses
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import java.time.OffsetDateTime;

/**
 * Flat organization representation returned by single-item endpoints.
 * Field names are serialised in snake_case.
 *
 * @param orgId       stable public identifier of the organization
 * @param orgName     human-readable name of the organization
 * @param orgCode     unique, stable code identifying the organization
 * @param parentId    identifier of the parent organization (null for root)
 * @param description optional explanation of the organization purpose
 * @param sortOrder   sort order among siblings
 * @param status      lifecycle status — ACTIVE or DISABLED
 * @param path        materialized path from the root to this organization
 * @param createdAt   UTC timestamp when the organization was created
 * @param updatedAt   UTC timestamp when the organization was last updated
 */
public record OrgDto(
        String orgId,
        String orgName,
        String orgCode,
        String parentId,
        String description,
        Integer sortOrder,
        String status,
        String path,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
