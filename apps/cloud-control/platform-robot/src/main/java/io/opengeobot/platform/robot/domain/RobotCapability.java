/*
 * Function: Robot capability entity — maps to robot_registry.robot_capability table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.opengeobot.platform.robot.config.JsonbMapTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Persistent robot capability entity backed by the
 * {@code robot_registry.robot_capability} table. Each row declares a single
 * capability for a robot, identified by the stable {@code capabilityType}
 * code (e.g. navigation, perception). The {@code details} column carries
 * capability-specific structured metadata as jsonb.
 */
@TableName(value = "robot_capability", schema = "robot_registry", autoResultMap = true)
public class RobotCapability {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String robotId;

    private String capabilityType;

    private String capabilityValue;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> details;

    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getCapabilityType() {
        return capabilityType;
    }

    public void setCapabilityType(String capabilityType) {
        this.capabilityType = capabilityType;
    }

    public String getCapabilityValue() {
        return capabilityValue;
    }

    public void setCapabilityValue(String capabilityValue) {
        this.capabilityValue = capabilityValue;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
