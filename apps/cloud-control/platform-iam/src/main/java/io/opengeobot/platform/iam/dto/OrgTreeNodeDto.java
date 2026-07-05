/*
 * Function: Organization tree node DTO — org enriched with children for tree rendering
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import java.util.List;

/**
 * Organization node enriched with its children, used to build the org tree in
 * a single response. Field names are serialised in snake_case.
 *
 * @param orgId       stable public identifier of the organization
 * @param orgName     human-readable name of the organization
 * @param orgCode     unique, stable code identifying the organization
 * @param parentId    identifier of the parent organization (null for root)
 * @param description optional explanation of the organization purpose
 * @param sortOrder   sort order among siblings
 * @param status      lifecycle status — ACTIVE or DISABLED
 * @param children    child organization nodes
 */
public record OrgTreeNodeDto(
        String orgId,
        String orgName,
        String orgCode,
        String parentId,
        String description,
        Integer sortOrder,
        String status,
        List<OrgTreeNodeDto> children
) {
}
