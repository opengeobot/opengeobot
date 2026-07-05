/*
 * Function: JWT token provider — generates and validates access tokens and refresh tokens
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Generates and validates JWT access tokens and opaque refresh tokens. Access
 * tokens carry the user id, username and permission codes as claims. The
 * signing key is derived from the configured secret via HMAC-SHA.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String CLAIM_USERNAME = "name";
    private static final String CLAIM_PERMISSIONS = "perms";
    private static final String CLAIM_SESSION_ID = "sid";
    private static final String REFRESH_TOKEN_PREFIX = "rt_";
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final JwtProperties properties;
    private SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT signing key initialised ({} bits)", keyBytes.length * 8);
    }

    /**
     * Generate a signed JWT access token carrying the user id, username,
     * permissions and session id.
     *
     * @param userId      stable public user id (subject)
     * @param username    login name
     * @param permissions granted permission codes
     * @param sessionId   session id for revocation linkage
     * @return signed JWT string
     */
    public String generateAccessToken(String userId, String username,
                                      List<String> permissions, String sessionId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.accessTokenExpirySeconds());
        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_PERMISSIONS, permissions)
                .claim(CLAIM_SESSION_ID, sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a random opaque refresh token. The token is prefixed with
     * {@code rt_} followed by a ULID-based identifier.
     *
     * @return opaque refresh token string
     */
    public String generateRefreshToken() {
        return REFRESH_TOKEN_PREFIX + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Validate the JWT signature and expiry, returning the claims.
     *
     * @param token raw JWT string
     * @return parsed claims
     * @throws JwtException if the token is invalid or expired
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract the user id (subject) from a validated token.
     */
    public String getUserIdFromToken(String token) {
        return validateToken(token).getSubject();
    }

    /**
     * Extract the username from a validated token.
     */
    public String getUsernameFromToken(String token) {
        return validateToken(token).get(CLAIM_USERNAME, String.class);
    }

    /**
     * Extract the permission codes from a validated token.
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        List<String> perms = validateToken(token).get(CLAIM_PERMISSIONS, List.class);
        return perms != null ? perms : List.of();
    }

    /**
     * Extract the session id from a validated token.
     */
    public String getSessionIdFromToken(String token) {
        return validateToken(token).get(CLAIM_SESSION_ID, String.class);
    }

    /**
     * Return the configured access token lifetime as a {@link Duration}.
     */
    public Duration getAccessTokenExpiry() {
        return Duration.ofSeconds(properties.accessTokenExpirySeconds());
    }

    /**
     * Return the configured refresh token lifetime as a {@link Duration}.
     */
    public Duration getRefreshTokenExpiry() {
        return Duration.ofDays(properties.refreshTokenExpiryDays());
    }

    /**
     * Return the token type for response bodies.
     */
    public String getTokenType() {
        return TOKEN_TYPE_BEARER;
    }
}
