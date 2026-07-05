/*
 * Function: Create dictionary item request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request body for creating a dictionary item.
 */
public record CreateDictItemRequest(
        @NotBlank(message = "item_code must not be blank")
        String itemCode,

        @NotBlank(message = "item_value must not be blank")
        String itemValue,

        String labelZhCn,
        String labelEnUs,
        Integer sortOrder,
        Map<String, Object> extra
) {
}
