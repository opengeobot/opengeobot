/*
 * Function: Refresh token domain entity — maps to platform_iam.sys_refresh_token table
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent refresh token entity backed by the
 * {@code platform_iam.sys_refresh_token} table. The {@code tokenHash} stores
 * a SHA-256 hash of the issued JWT refresh token; the raw token is never
 * persisted. Tokens are rotated on every refresh and can be revoked on logout.
 */
@TableName(value = "sys_refresh_token", schema = "platform_iam")
public class RefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tokenId;

    private String userId;

    private String sessionId;

    private String tokenHash;

    private OffsetDateTime expiresAt;

    private Boolean revoked;

    private OffsetDateTime revokedAt;

    private String revokedReason;

    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getRevoked() {
        return revoked;
    }

    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
