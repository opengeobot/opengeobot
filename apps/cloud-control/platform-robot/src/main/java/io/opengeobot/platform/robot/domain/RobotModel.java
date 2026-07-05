/*
 * Function: Robot model entity — maps to robot_registry.robot_model table for F-ROBOT-002
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.opengeobot.platform.robot.config.JsonbStringTypeHandler;

import java.time.OffsetDateTime;

/**
 * Persistent robot model entity backed by the {@code robot_registry.robot_model}
 * table. A model is a catalogue entry describing supported hardware (e.g.
 * Unitree Go2). The {@code capabilities} column stores a JSON array of
 * capability descriptors as jsonb, declaring which capabilities are typically
 * available for this model.
 */
@TableName(value = "robot_model", schema = "robot_registry", autoResultMap = true)
public class RobotModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelId;

    private String modelName;

    private String manufacturer;

    private String description;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String capabilities;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
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
