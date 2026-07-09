/*
 * Function: Robot module exception handler — maps domain exceptions to ErrorEnvelope ProblemDetails
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.web;

import io.opengeobot.platform.common.error.ErrorCode;
import io.opengeobot.platform.common.error.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates robot-domain exceptions into the platform {@link ErrorEnvelope}
 * ProblemDetails format. More specific handlers here take precedence over the
 * generic {@code Exception} handler in {@code platform-common}, so domain
 * not-found and conflict conditions surface as 404/409 instead of 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RobotExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RobotExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.debug("Resource not found: {}", ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of(ErrorCode.RESOURCE_NOT_FOUND, resolveTraceId(), request.getRequestURI());
        return buildResponse(envelope, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.debug("Conflict: {}", ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of(ErrorCode.CONFLICT, resolveTraceId(), request.getRequestURI());
        return buildResponse(envelope, HttpStatus.CONFLICT);
    }

    private ResponseEntity<ErrorEnvelope> buildResponse(ErrorEnvelope envelope, HttpStatus status) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(envelope);
    }

    private String resolveTraceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        return (traceId == null || traceId.isBlank()) ? "n/a" : traceId;
    }
}
