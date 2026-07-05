/*
 * Function: Idempotency service — checks and caches responses for idempotent requests
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.opengeobot.platform.governance.domain.idempotency.IdempotencyRecord;
import io.opengeobot.platform.governance.dto.CachedResponse;
import io.opengeobot.platform.governance.idempotency.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Service for idempotent request handling. When a request carries an
 * {@code Idempotency-Key} header, the filter calls {@link #checkAndRecord} to
 * look up a cached response. If none exists, the request proceeds and the
 * filter calls {@link #recordResponse} to persist the response for future
 * duplicate requests.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Checks whether a cached response exists for the given idempotency key.
     * Returns the cached response if the record exists and has not expired.
     *
     * @param idempotencyKey the idempotency key from the request header
     * @param resourceType   the resource type (typically the request URI)
     * @param requestHash    hash of the request body for auditing
     * @return the cached response, or empty if no record exists
     */
    public Optional<CachedResponse> checkAndRecord(String idempotencyKey, String resourceType, String requestHash) {
        LambdaQueryWrapper<IdempotencyRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IdempotencyRecord::getIdempotencyKey, idempotencyKey);
        IdempotencyRecord record = repository.selectOne(wrapper);
        if (record == null) {
            return Optional.empty();
        }
        if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            log.debug("Idempotency record {} has expired, ignoring", idempotencyKey);
            return Optional.empty();
        }
        return Optional.of(new CachedResponse(record.getStatusCode(), record.getResponseBody()));
    }

    /**
     * Persists the response for an idempotent request so that duplicate
     * requests with the same key return the cached response.
     *
     * @param idempotencyKey the idempotency key from the request header
     * @param resourceType   the resource type (typically the request URI)
     * @param statusCode     the HTTP status code of the original response
     * @param responseBody   the response body as a JSON string
     * @param expiresAt      when the cached response should expire
     */
    @Transactional
    public void recordResponse(String idempotencyKey, String resourceType,
                               int statusCode, String responseBody, OffsetDateTime expiresAt) {
        LambdaQueryWrapper<IdempotencyRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IdempotencyRecord::getIdempotencyKey, idempotencyKey);
        IdempotencyRecord existing = repository.selectOne(wrapper);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (existing != null) {
            existing.setStatusCode(statusCode);
            existing.setResponseBody(responseBody);
            existing.setExpiresAt(expiresAt);
            existing.setUpdatedAt(now);
            repository.updateById(existing);
        } else {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(idempotencyKey);
            record.setResourceType(resourceType);
            record.setStatusCode(statusCode);
            record.setResponseBody(responseBody);
            record.setExpiresAt(expiresAt);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            repository.insert(record);
        }
        log.debug("Recorded idempotency response for key {} status {}", idempotencyKey, statusCode);
    }
}
