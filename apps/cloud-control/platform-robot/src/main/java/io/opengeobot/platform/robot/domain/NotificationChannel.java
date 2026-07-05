/*
 * Function: NotificationChannel entity — maps to alarm.notification_channel for F-ALARM-001
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
 * Persistent notification channel entity backed by the
 * {@code alarm.notification_channel} table. Channels define how alarm
 * notifications are delivered: {@code in-app} stores notifications in the
 * database, {@code webhook} sends an HTTP POST, and {@code email} sends an
 * email. The {@code config} column stores channel-specific configuration as
 * jsonb.
 */
@TableName(value = "notification_channel", schema = "alarm", autoResultMap = true)
public class NotificationChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channelId;

    private String name;

    private String type;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> config;

    private Boolean enabled;

    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
