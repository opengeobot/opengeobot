/*
 * Function: Update dictionary type request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

/**
 * Request body for updating a dictionary type. The {@code type_code} cannot
 * be changed.
 */
public record UpdateDictTypeRequest(
        String typeName,
        String description
) {
}
