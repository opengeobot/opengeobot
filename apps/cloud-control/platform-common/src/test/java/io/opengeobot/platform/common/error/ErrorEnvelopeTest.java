/*
 * Function: Error envelope unit tests — ProblemDetails construction and defaults
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.error;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ErrorEnvelope}. Verifies compact constructor defaults,
 * {@link ErrorCode} mapping, and explicit construction.
 */
class ErrorEnvelopeTest {

    @Test
    void of_errorCode_mapsAllFields() {
        ErrorEnvelope env = ErrorEnvelope.of(ErrorCode.RESOURCE_NOT_FOUND, "trace-001", "/api/robots/rbt_001");

        assertEquals("about:blank", env.type());
        assertEquals("Resource not found", env.title());
        assertEquals(404, env.status());
        assertEquals("RESOURCE_NOT_FOUND", env.code());
        assertEquals("error.resource_not_found", env.messageKey());
        assertEquals("trace-001", env.traceId());
        assertEquals("/api/robots/rbt_001", env.instance());
        assertTrue(env.arguments().isEmpty());
    }

    @Test
    void of_errorCode_permissionDenied() {
        ErrorEnvelope env = ErrorEnvelope.of(ErrorCode.PERMISSION_DENIED, "trace-002", "/api/missions");

        assertEquals(403, env.status());
        assertEquals("PERMISSION_DENIED", env.code());
        assertEquals("error.permission_denied", env.messageKey());
    }

    @Test
    void of_explicitFields_setsAllFields() {
        ErrorEnvelope env = ErrorEnvelope.of(
                "CUSTOM_ERROR", "Custom error title", 422,
                "error.custom", "trace-003", "/api/custom"
        );

        assertEquals("about:blank", env.type());
        assertEquals("Custom error title", env.title());
        assertEquals(422, env.status());
        assertEquals("CUSTOM_ERROR", env.code());
        assertEquals("error.custom", env.messageKey());
        assertEquals("trace-003", env.traceId());
        assertEquals("/api/custom", env.instance());
    }

    @Test
    void compactConstructor_blankTypeDefaultsToAboutBlank() {
        ErrorEnvelope env = new ErrorEnvelope(
                "   ", "Title", 400, "CODE", "key",
                Map.of(), "trace", "/inst"
        );

        assertEquals("about:blank", env.type());
    }

    @Test
    void compactConstructor_nullTypeDefaultsToAboutBlank() {
        ErrorEnvelope env = new ErrorEnvelope(
                null, "Title", 400, "CODE", "key",
                null, "trace", "/inst"
        );

        assertEquals("about:blank", env.type());
    }

    @Test
    void compactConstructor_nullArgumentsDefaultsToEmptyMap() {
        ErrorEnvelope env = new ErrorEnvelope(
                "custom", "Title", 400, "CODE", "key",
                null, "trace", "/inst"
        );

        assertNotNull(env.arguments());
        assertTrue(env.arguments().isEmpty());
    }

    @Test
    void compactConstructor_preservesProvidedArguments() {
        Map<String, Object> args = Map.of("field", "name", "value", "invalid");
        ErrorEnvelope env = new ErrorEnvelope(
                "custom", "Title", 400, "CODE", "key",
                args, "trace", "/inst"
        );

        assertEquals(args, env.arguments());
    }

    @Test
    void of_allErrorCodesHaveValidStatusAndKey() {
        for (ErrorCode code : ErrorCode.values()) {
            ErrorEnvelope env = ErrorEnvelope.of(code, "trace", "/inst");

            assertTrue(env.status() >= 400 && env.status() < 600,
                    "Status should be HTTP error range for " + code);
            assertNotNull(env.code());
            assertFalse(env.code().isBlank());
            assertNotNull(env.messageKey());
            assertFalse(env.messageKey().isBlank());
        }
    }
}
