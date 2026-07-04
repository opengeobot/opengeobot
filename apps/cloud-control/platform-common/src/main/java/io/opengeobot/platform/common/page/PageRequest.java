/*
 * Function: Pagination request model — bounded page size, optional sort
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.page;

/**
 * Immutable pagination request. Page numbers are 1-based. The page size is
 * clamped to a maximum of {@value #MAX_PAGE_SIZE} and defaults to
 * {@value #DEFAULT_PAGE_SIZE}.
 */
public record PageRequest(int pageNumber, int pageSize, String sortBy, String sortDirection) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public PageRequest {
        if (pageNumber < 1) {
            pageNumber = 1;
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        if (sortBy != null && sortBy.isBlank()) {
            sortBy = null;
        }
        if (sortDirection == null || sortDirection.isBlank()) {
            sortDirection = "asc";
        } else {
            sortDirection = sortDirection.toLowerCase();
        }
    }

    public static PageRequest of(int pageNumber, int pageSize) {
        return new PageRequest(pageNumber, pageSize, null, null);
    }

    public static PageRequest of(int pageNumber, int pageSize, String sortBy, String sortDirection) {
        return new PageRequest(pageNumber, pageSize, sortBy, sortDirection);
    }

    public long offset() {
        return (long) (pageNumber - 1) * pageSize;
    }
}
