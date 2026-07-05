/*
 * Function: Generic paginated API response wrapper for the robot module
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.web;

import io.opengeobot.platform.common.page.PageResult;

import java.util.List;

/**
 * Paginated response envelope matching the platform OpenAPI contract
 * ({@code items}, {@code total}, {@code page}, {@code page_size},
 * {@code total_pages}). Wraps the domain {@link PageResult} so services can
 * keep using the canonical page model while controllers return the contract
 * shape. Jackson serialises field names in snake_case globally.
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int pageSize,
        int totalPages
) {

    public static <T> PageResponse<T> of(PageResult<T> result) {
        return new PageResponse<>(
                result.items(),
                result.total(),
                result.pageNumber(),
                result.pageSize(),
                result.totalPages()
        );
    }
}
