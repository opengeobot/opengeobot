/*
 * Function: Idempotency record entity — maps to platform_governance.sys_idempotency_record
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.domain.idempotency;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.opengeobot.platform.governance.config.JsonbStringTypeHandler;

import java.time.OffsetDateTime;

/**
 * Persistent idempotency record entity backed by the
 * {@code platform_governance.sys_idempotency_record} table. Each row caches a
 * response keyed by {@code idempotencyKey} so that duplicate requests with the
 * same key return the original response instead of creating duplicates.
 * The {@code responseBody} field is stored as JSONB.
 */
@TableName(value = "sys_idempotency_record", schema = "platform_governance", autoResultMap = true)
public class IdempotencyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String idempotencyKey;

    private String resourceType;

    private String resourceId;

    private String requestHash;

    private Integer statusCode;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String responseBody;

    private OffsetDateTime expiresAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
