/*
 * Function: Adapter compatibility entity - maps to robot_registry.adapter_compatibility
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain.adapter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent adapter compatibility entity backed by the
 * {@code robot_registry.adapter_compatibility} table. Each row describes the
 * compatibility of a robot hardware model with a protocol adapter type
 * (ROS2 / ROS1 / Unitree / custom), including the ROS version, the native
 * control protocol, whether the combination is compatible, and the runtime
 * health status of the adapter. The {@code adapter_type} and
 * {@code health_status} fields are platform code contracts, not editable
 * dictionary data.
 */
@TableName(value = "adapter_compatibility", schema = "robot_registry")
public class AdapterCompatibility {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String adapterId;

    private String robotModelId;

    private String adapterType;

    private String rosVersion;

    private String controlProtocol;

    private Boolean compatible;

    private String healthStatus;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAdapterId() {
        return adapterId;
    }

    public void setAdapterId(String adapterId) {
        this.adapterId = adapterId;
    }

    public String getRobotModelId() {
        return robotModelId;
    }

    public void setRobotModelId(String robotModelId) {
        this.robotModelId = robotModelId;
    }

    public String getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(String adapterType) {
        this.adapterType = adapterType;
    }

    public String getRosVersion() {
        return rosVersion;
    }

    public void setRosVersion(String rosVersion) {
        this.rosVersion = rosVersion;
    }

    public String getControlProtocol() {
        return controlProtocol;
    }

    public void setControlProtocol(String controlProtocol) {
        this.controlProtocol = controlProtocol;
    }

    public Boolean getCompatible() {
        return compatible;
    }

    public void setCompatible(Boolean compatible) {
        this.compatible = compatible;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
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
