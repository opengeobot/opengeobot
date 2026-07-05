/*
 * Function: Update dictionary item request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.util.Map;

/**
 * Request body for updating a dictionary item. The {@code item_code} cannot
 * be changed.
 */
public record UpdateDictItemRequest(
        String itemValue,
        String labelZhCn,
        String labelEnUs,
        Integer sortOrder,
        String status,
        Map<String, Object> extra
) {
}
