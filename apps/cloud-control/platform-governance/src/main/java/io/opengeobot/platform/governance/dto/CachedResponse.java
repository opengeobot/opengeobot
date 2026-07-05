/*
 * Function: Cached response record — idempotency replay result
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

/**
 * Represents a cached response stored in the idempotency record table. When a
 * duplicate request with the same {@code Idempotency-Key} arrives, the filter
 * returns this cached response instead of re-executing the request.
 */
public record CachedResponse(
        int statusCode,
        String responseBody
) {
}
