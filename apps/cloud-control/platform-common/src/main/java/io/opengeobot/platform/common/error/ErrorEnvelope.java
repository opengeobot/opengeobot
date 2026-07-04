/*
 * Function: ProblemDetails-style error response envelope with stable code and i18n message key
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.error;

import java.util.Map;

/**
 * Standard platform error response. Extends RFC 9457 Problem Details with a
 * stable machine-readable {@code code}, an i18n {@code messageKey} plus
 * {@code arguments}, and a {@code traceId} that links the error to the
 * end-to-end trace context. Jackson serialises field names in snake_case.
 */
public record ErrorEnvelope(
        String type,
        String title,
        int status,
        String code,
        String messageKey,
        Map<String, Object> arguments,
        String traceId,
        String instance
) {

    private static final String DEFAULT_TYPE = "about:blank";

    public ErrorEnvelope {
        if (type == null || type.isBlank()) {
            type = DEFAULT_TYPE;
        }
        if (arguments == null) {
            arguments = Map.of();
        }
    }

    /**
     * Build an envelope from a canonical {@link ErrorCode}.
     */
    public static ErrorEnvelope of(ErrorCode errorCode, String traceId, String instance) {
        return new ErrorEnvelope(
                DEFAULT_TYPE,
                errorCode.title(),
                errorCode.status(),
                errorCode.code(),
                errorCode.messageKey(),
                Map.of(),
                traceId,
                instance
        );
    }

    /**
     * Build an envelope from explicit code fields, for errors not covered by {@link ErrorCode}.
     */
    public static ErrorEnvelope of(String code, String title, int status,
                                   String messageKey, String traceId, String instance) {
        return new ErrorEnvelope(
                DEFAULT_TYPE,
                title,
                status,
                code,
                messageKey,
                Map.of(),
                traceId,
                instance
        );
    }
}
