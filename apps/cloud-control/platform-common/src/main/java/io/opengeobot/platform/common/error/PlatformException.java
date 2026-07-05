/*
 * Function: Platform exception carrying a stable ErrorCode for ProblemDetails responses
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.common.error;

/**
 * Runtime exception that carries a canonical {@link ErrorCode}. Thrown by
 * service layer code to signal a domain-specific failure; the global
 * exception handler translates it into an {@link ErrorEnvelope} ProblemDetails
 * response with the correct HTTP status and stable error code.
 */
public class PlatformException extends RuntimeException {

    private final ErrorCode errorCode;

    public PlatformException(ErrorCode errorCode) {
        super(errorCode.title());
        this.errorCode = errorCode;
    }

    public PlatformException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
