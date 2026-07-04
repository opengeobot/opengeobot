/*
 * Function: Pagination result model — generic page of items with total count
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.page;

import java.util.List;

/**
 * Immutable page of results. Carries the items, total count and the original
 * page coordinates so callers can render pagination controls.
 */
public record PageResult<T>(List<T> items, long total, int pageNumber, int pageSize) {

    public int totalPages() {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / pageSize);
    }

    public boolean hasNext() {
        return (long) pageNumber * pageSize < total;
    }

    public boolean hasPrevious() {
        return pageNumber > 1;
    }
}
