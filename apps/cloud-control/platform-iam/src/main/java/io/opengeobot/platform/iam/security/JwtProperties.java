/*
 * Function: JWT configuration properties — binds platform.jwt.* from application.yml
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from {@code platform.jwt.*} properties. The secret
 * must be at least 256 bits (32 bytes) for HS256. In production the secret is
 * injected via the {@code JWT_SECRET} environment variable.
 *
 * @param secret                   HMAC-SHA signing key material
 * @param accessTokenExpirySeconds access token lifetime in seconds
 * @param refreshTokenExpiryDays    refresh token lifetime in days
 */
@ConfigurationProperties(prefix = "platform.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirySeconds,
        long refreshTokenExpiryDays
) {
}
