/*
 * Function: Request DTOs for dictionary type CRUD operations
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a dictionary type.
 */
public record CreateDictTypeRequest(
        @NotBlank(message = "type_code must not be blank")
        String typeCode,

        @NotBlank(message = "type_name must not be blank")
        String typeName,

        String description
) {
}
