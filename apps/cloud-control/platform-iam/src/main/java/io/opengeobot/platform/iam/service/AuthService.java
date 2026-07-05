/*
 * Function: Authentication service — login, refresh and logout with audit and outbox events
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.error.ErrorCode;
import io.opengeobot.platform.common.error.PlatformException;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.iam.SessionState;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.id.Ulid;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.iam.domain.RefreshToken;
import io.opengeobot.platform.iam.domain.Session;
import io.opengeobot.platform.iam.domain.User;
import io.opengeobot.platform.iam.domain.UserStatus;
import io.opengeobot.platform.iam.dto.LoginRequest;
import io.opengeobot.platform.iam.dto.RefreshRequest;
import io.opengeobot.platform.iam.dto.TokenResponse;
import io.opengeobot.platform.iam.repository.RefreshTokenRepository;
import io.opengeobot.platform.iam.repository.SessionRepository;
import io.opengeobot.platform.iam.repository.UserRepository;
import io.opengeobot.platform.iam.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application service for authentication. Handles login (credential
 * verification, session creation, token issuance), refresh (token rotation,
 * session update) and logout (revocation). Each operation writes an audit
 * event and an outbox event in the same transaction, linked by a trace_id.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String EVENT_USER_LOGGED_IN = "iam.user_logged_in.v1";
    private static final String EVENT_SESSION_REFRESHED = "iam.session_refreshed.v1";
    private static final String EVENT_SESSION_REVOKED = "iam.session_revoked.v1";
    private static final String AGGREGATE_USER = "user";
    private static final String AGGREGATE_SESSION = "session";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final OutboxRepository outboxRepository;
    private final PublicIdGenerator idGenerator;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       SessionRepository sessionRepository,
                       JwtTokenProvider tokenProvider,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService,
                       OutboxRepository outboxRepository,
                       PublicIdGenerator idGenerator,
                       ClockProvider clockProvider,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.outboxRepository = outboxRepository;
        this.idGenerator = idGenerator;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String sourceIp, String userAgent) {
        String traceId = Ulid.next();
        MDC.put("traceId", traceId);
        try {
            User user = userRepository.findByUsername(request.username());

            if (user == null) {
                log.debug("Login failed: user not found for username");
                recordAudit("anonymous", "LOGIN", null, "FAILURE",
                        "USER_NOT_FOUND", sourceIp, userAgent, traceId);
                throw new PlatformException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            }

            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                log.debug("Login failed: password mismatch for userId={}", user.getUserId());
                recordAudit(user.getUserId(), "LOGIN", user.getUserId(), "FAILURE",
                        "INVALID_PASSWORD", sourceIp, userAgent, traceId);
                throw new PlatformException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            }

            if (UserStatus.LOCKED.name().equals(user.getStatus())) {
                log.debug("Login blocked: account locked for userId={}", user.getUserId());
                throw new PlatformException(ErrorCode.AUTH_ACCOUNT_LOCKED);
            }

            if (UserStatus.DISABLED.name().equals(user.getStatus())) {
                log.debug("Login blocked: account disabled for userId={}", user.getUserId());
                throw new PlatformException(ErrorCode.AUTH_ACCOUNT_DISABLED);
            }

            List<String> permissions = userRepository.findPermissionCodesByUserId(user.getUserId());

            Instant now = instantNow();
            OffsetDateTime nowOffset = offsetNow();
            OffsetDateTime expiry = nowOffset.plus(tokenProvider.getRefreshTokenExpiry());

            String sessionId = idGenerator.generate("sess");

            Session session = new Session();
            session.setSessionId(sessionId);
            session.setUserId(user.getUserId());
            session.setState(SessionState.ACTIVE.name());
            session.setSourceIp(sourceIp);
            session.setUserAgent(userAgent);
            session.setIssuedAt(nowOffset);
            session.setExpiresAt(expiry);
            session.setTraceId(traceId);
            sessionRepository.insert(session);

            String refreshToken = tokenProvider.generateRefreshToken();

            RefreshToken tokenEntity = new RefreshToken();
            tokenEntity.setTokenId(refreshToken);
            tokenEntity.setUserId(user.getUserId());
            tokenEntity.setSessionId(sessionId);
            tokenEntity.setTokenHash(sha256(refreshToken));
            tokenEntity.setExpiresAt(expiry);
            tokenEntity.setRevoked(false);
            tokenEntity.setCreatedAt(nowOffset);
            refreshTokenRepository.insert(tokenEntity);

            String accessToken = tokenProvider.generateAccessToken(
                    user.getUserId(), user.getUsername(), permissions, sessionId);

            user.setLastLoginAt(nowOffset);
            user.setLastLoginIp(sourceIp);
            userRepository.updateById(user);

            recordAudit(user.getUserId(), "LOGIN", user.getUserId(), "SUCCESS",
                    null, sourceIp, userAgent, traceId);

            publishOutboxEvent(EVENT_USER_LOGGED_IN, AGGREGATE_USER, user.getUserId(),
                    buildLoginPayload(user, sessionId, sourceIp, userAgent, now, traceId), now, traceId);

            log.info("User logged in: userId={} sessionId={}", user.getUserId(), sessionId);

            return new TokenResponse(
                    accessToken, refreshToken, tokenProvider.getTokenType(),
                    tokenProvider.getAccessTokenExpiry().getSeconds());
        } finally {
            MDC.remove("traceId");
        }
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String traceId = Ulid.next();
        MDC.put("traceId", traceId);
        try {
            RefreshToken token = refreshTokenRepository.findByTokenId(request.refreshToken());

            if (token == null || Boolean.TRUE.equals(token.getRevoked())
                    || token.getExpiresAt().isBefore(offsetNow())) {
                log.debug("Refresh failed: token invalid, revoked or expired");
                throw new PlatformException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
            }

            token.setRevoked(true);
            token.setRevokedAt(offsetNow());
            token.setRevokedReason("TOKEN_ROTATED");
            refreshTokenRepository.updateById(token);

            User user = userRepository.findByUserId(token.getUserId());
            if (user == null || !UserStatus.ACTIVE.name().equals(user.getStatus())) {
                throw new PlatformException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
            }

            List<String> permissions = userRepository.findPermissionCodesByUserId(user.getUserId());

            OffsetDateTime nowOffset = offsetNow();

            Session session = sessionRepository.findBySessionId(token.getSessionId());
            if (session != null) {
                session.setLastRefreshedAt(nowOffset);
                sessionRepository.updateById(session);
            }

            String newRefreshToken = tokenProvider.generateRefreshToken();

            RefreshToken newTokenEntity = new RefreshToken();
            newTokenEntity.setTokenId(newRefreshToken);
            newTokenEntity.setUserId(user.getUserId());
            newTokenEntity.setSessionId(token.getSessionId());
            newTokenEntity.setTokenHash(sha256(newRefreshToken));
            newTokenEntity.setExpiresAt(nowOffset.plus(tokenProvider.getRefreshTokenExpiry()));
            newTokenEntity.setRevoked(false);
            newTokenEntity.setCreatedAt(nowOffset);
            refreshTokenRepository.insert(newTokenEntity);

            String accessToken = tokenProvider.generateAccessToken(
                    user.getUserId(), user.getUsername(), permissions, token.getSessionId());

            Instant now = instantNow();
            publishOutboxEvent(EVENT_SESSION_REFRESHED, AGGREGATE_SESSION, token.getSessionId(),
                    buildRefreshPayload(user.getUserId(), token.getSessionId(), now, traceId), now, traceId);

            log.info("Session refreshed: userId={} sessionId={}", user.getUserId(), token.getSessionId());

            return new TokenResponse(
                    accessToken, newRefreshToken, tokenProvider.getTokenType(),
                    tokenProvider.getAccessTokenExpiry().getSeconds());
        } finally {
            MDC.remove("traceId");
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        String traceId = Ulid.next();
        MDC.put("traceId", traceId);
        try {
            RefreshToken token = refreshTokenRepository.findByTokenId(refreshToken);
            if (token == null) {
                log.debug("Logout: refresh token not found, treating as idempotent no-op");
                return;
            }

            token.setRevoked(true);
            token.setRevokedAt(offsetNow());
            token.setRevokedReason("USER_LOGOUT");
            refreshTokenRepository.updateById(token);

            Session session = sessionRepository.findBySessionId(token.getSessionId());
            if (session != null) {
                session.setState(SessionState.REVOKED.name());
                session.setRevokedAt(offsetNow());
                session.setRevokedReason("USER_LOGOUT");
                sessionRepository.updateById(session);
            }

            Instant now = instantNow();
            publishOutboxEvent(EVENT_SESSION_REVOKED, AGGREGATE_SESSION, token.getSessionId(),
                    buildRevokePayload(token.getUserId(), token.getSessionId(), "USER_LOGOUT", now, traceId),
                    now, traceId);

            log.info("Session revoked: userId={} sessionId={}", token.getUserId(), token.getSessionId());
        } finally {
            MDC.remove("traceId");
        }
    }

    private void recordAudit(String actorId, String action, String resourceId,
                            String result, String reasonCode, String sourceIp,
                            String userAgent, String traceId) {
        auditService.record(new AuditEvent(
                "user", actorId, action, "user", resourceId, result,
                reasonCode, sourceIp, userAgent, traceId, null,
                instantNow(), null, null
        ));
    }

    private void publishOutboxEvent(String eventType, String aggregateType, String aggregateId,
                                   String payload, Instant occurredAt, String traceId) {
        outboxRepository.save(new OutboxEvent(
                null,
                Ulid.next(),
                eventType,
                "1",
                aggregateType,
                aggregateId,
                1L,
                payload,
                occurredAt,
                traceId,
                false,
                null,
                0
        ));
    }

    private String buildLoginPayload(User user, String sessionId, String sourceIp,
                                     String userAgent, Instant occurredAt, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_id", Ulid.next());
        data.put("user_id", user.getUserId());
        data.put("username", user.getUsername());
        data.put("session_id", sessionId);
        data.put("occurred_at", occurredAt.toString());
        data.put("trace_id", traceId);
        if (sourceIp != null) {
            data.put("source_ip", sourceIp);
        }
        if (userAgent != null) {
            data.put("user_agent", userAgent);
        }
        return toJson(data);
    }

    private String buildRefreshPayload(String userId, String sessionId,
                                       Instant occurredAt, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_id", Ulid.next());
        data.put("user_id", userId);
        data.put("session_id", sessionId);
        data.put("occurred_at", occurredAt.toString());
        data.put("trace_id", traceId);
        return toJson(data);
    }

    private String buildRevokePayload(String userId, String sessionId, String reason,
                                      Instant occurredAt, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_id", Ulid.next());
        data.put("user_id", userId);
        data.put("session_id", sessionId);
        data.put("reason", reason);
        data.put("occurred_at", occurredAt.toString());
        data.put("trace_id", traceId);
        return toJson(data);
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload", e);
            throw new PlatformException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private Instant instantNow() {
        return Instant.now(clockProvider.getClock());
    }

    private OffsetDateTime offsetNow() {
        return OffsetDateTime.now(clockProvider.getClock());
    }
}
