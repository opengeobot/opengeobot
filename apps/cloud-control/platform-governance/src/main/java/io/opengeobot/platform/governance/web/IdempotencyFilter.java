/*
 * Function: Idempotency filter — caches responses for requests with Idempotency-Key header
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.web;

import io.opengeobot.platform.governance.dto.CachedResponse;
import io.opengeobot.platform.governance.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Intercepts POST and PUT requests carrying an {@code Idempotency-Key} header.
 * If a cached response exists for the key, it is returned directly without
 * re-executing the request. Otherwise the request proceeds and the response is
 * cached for future duplicate requests. Cached responses expire after 24 hours.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final IdempotencyService idempotencyService;

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isBlank()
                || (!"POST".equals(method) && !"PUT".equals(method))) {
            filterChain.doFilter(request, response);
            return;
        }

        String resourceType = method + " " + request.getRequestURI();
        Optional<CachedResponse> cached = idempotencyService.checkAndRecord(idempotencyKey, resourceType, "");
        if (cached.isPresent()) {
            log.debug("Returning cached idempotent response for key {}", idempotencyKey);
            CachedResponse cachedResponse = cached.get();
            response.setStatus(cachedResponse.statusCode());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(cachedResponse.responseBody());
            response.getWriter().flush();
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);
        } finally {
            byte[] responseBody = responseWrapper.getContentAsByteArray();
            String bodyJson = responseBody.length > 0 ? new String(responseBody, StandardCharsets.UTF_8) : "{}";
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(CACHE_TTL);
            try {
                idempotencyService.recordResponse(idempotencyKey, resourceType,
                        responseWrapper.getStatus(), bodyJson, expiresAt);
            } catch (Exception e) {
                log.warn("Failed to cache idempotent response for key {}", idempotencyKey, e);
            }
            responseWrapper.copyBodyToResponse();
        }
    }
}
