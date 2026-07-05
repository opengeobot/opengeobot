/*
 * Function: User list response DTO — wrapper for paginated user results
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import java.util.List;

/**
 * Paginated response wrapper for user list queries. Carries the items, total
 * count and page coordinates. Field names are serialised in snake_case.
 *
 * @param items    user entries on the current page
 * @param total    total number of matching records
 * @param page     current page number
 * @param pageSize number of items per page
 */
public record UserListResponse(
        List<UserDto> items,
        long total,
        int page,
        int pageSize
) {
}
