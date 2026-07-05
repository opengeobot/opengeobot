/*
 * Function: Permission DTO — single permission code representation
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

/**
 * A single permission code. Permission codes are stable contracts checked
 * server-side by the authorization module; they are not editable dictionary
 * data. Field names are serialised in snake_case.
 *
 * @param permissionCode stable, unique permission code
 * @param permissionName  human-readable name of the permission
 * @param module          logical module that owns the permission
 * @param description     optional explanation of the permission
 * @param resourceType    resource type the permission applies to
 * @param action          action the permission grants
 */
public record PermissionDto(
        String permissionCode,
        String permissionName,
        String module,
        String description,
        String resourceType,
        String action
) {
}
