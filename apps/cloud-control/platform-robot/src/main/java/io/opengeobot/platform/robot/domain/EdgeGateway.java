/*
 * Function: Edge gateway entity — maps to robot_registry.edge_gateway for F-EDGE-001
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent edge gateway entity backed by {@code robot_registry.edge_gateway}.
 * Tracks identity status, certificate summary, runtime version and optional
 * robot binding.
 */
@TableName(value = "edge_gateway", schema = "robot_registry")
public class EdgeGateway {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String gatewayId;

    private String name;

    private String orgId;

    private String status;

    private String certificateFingerprint;

    private OffsetDateTime certificateExpiresAt;

    private String runtimeVersion;

    private String boundRobotId;

    private OffsetDateTime lastHeartbeatAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCertificateFingerprint() {
        return certificateFingerprint;
    }

    public void setCertificateFingerprint(String certificateFingerprint) {
        this.certificateFingerprint = certificateFingerprint;
    }

    public OffsetDateTime getCertificateExpiresAt() {
        return certificateExpiresAt;
    }

    public void setCertificateExpiresAt(OffsetDateTime certificateExpiresAt) {
        this.certificateExpiresAt = certificateExpiresAt;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public String getBoundRobotId() {
        return boundRobotId;
    }

    public void setBoundRobotId(String boundRobotId) {
        this.boundRobotId = boundRobotId;
    }

    public OffsetDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
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
