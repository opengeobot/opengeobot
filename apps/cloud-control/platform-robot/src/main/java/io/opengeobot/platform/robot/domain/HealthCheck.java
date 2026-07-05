/*
 * Function: HealthCheck entity — maps to ops.health_check for F-OPS-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent component health check entity backed by the
 * {@code ops.health_check} table. Each row captures the status, latency and
 * error message of a dependency check at a point in time.
 */
@TableName(value = "health_check", schema = "ops")
public class HealthCheck {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String component;

    private String status;

    private Long latencyMs;

    private String errorMessage;

    private OffsetDateTime lastCheckAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getLastCheckAt() {
        return lastCheckAt;
    }

    public void setLastCheckAt(OffsetDateTime lastCheckAt) {
        this.lastCheckAt = lastCheckAt;
    }
}
