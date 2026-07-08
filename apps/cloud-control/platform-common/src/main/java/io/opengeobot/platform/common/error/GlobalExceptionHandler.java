/*
 * Function: Global exception handler producing ErrorEnvelope (ProblemDetails) responses
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Translates exceptions into the platform {@link ErrorEnvelope} ProblemDetails format.
 * Every error response carries a {@code traceId} so it can be correlated with logs,
 * metrics and audit records across the end-to-end trace context.
 */
@RestControllerAdvice
@ConditionalOnWebApplication
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.VALIDATION_FAILED, resolveTraceId(), request.getRequestURI());
        return buildResponse(envelope, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ErrorEnvelope> handlePlatformException(
            PlatformException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("Platform exception: {} {}", errorCode.code(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of(errorCode, resolveTraceId(), request.getRequestURI());
        return buildResponse(envelope, HttpStatus.valueOf(errorCode.status()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.RESOURCE_NOT_FOUND, resolveTraceId(), request.getRequestURI());
        return buildResponse(envelope, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.debug("Access denied: {}", ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.PERMISSION_DENIED, resolveTraceId(), request.getRequestURI());
        return buildResponse(envelope, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.INTERNAL_ERROR, resolveTraceId(), request.getRequestURI());
        return buildResponse(envelope, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorEnvelope> buildResponse(ErrorEnvelope envelope, HttpStatus status) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(envelope);
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            return "n/a";
        }
        return traceId;
    }
}
