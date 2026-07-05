/*
 * Function: Token response DTO — access and refresh tokens issued after login or refresh
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

/**
 * Tokens issued after a successful login or refresh. The access token is a
 * short-lived JWT; the refresh token is a long-lived opaque token. Field names
 * are serialised in snake_case.
 *
 * @param accessToken  short-lived JWT used for API authorization
 * @param refreshToken long-lived opaque token used to obtain new access tokens
 * @param tokenType    token type indicating how the access token should be used
 * @param expiresIn    lifetime of the access token in seconds
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
