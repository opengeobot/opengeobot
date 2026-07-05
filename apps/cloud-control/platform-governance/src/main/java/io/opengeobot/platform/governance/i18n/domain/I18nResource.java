/*
 * Function: I18n resource entity — maps to platform_governance.sys_i18n_resource
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.i18n.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent i18n translation entry backed by the
 * {@code platform_governance.sys_i18n_resource} table. Each row is the
 * translation for a {@code resourceKey} in a specific {@code locale}; backends
 * return stable {@code messageKey} references that clients resolve through
 * these entries.
 */
@TableName(value = "sys_i18n_resource", schema = "platform_governance")
public class I18nResource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String resourceKey;

    private String locale;

    private String resourceValue;

    private String module;

    private String description;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public void setResourceKey(String resourceKey) {
        this.resourceKey = resourceKey;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getResourceValue() {
        return resourceValue;
    }

    public void setResourceValue(String resourceValue) {
        this.resourceValue = resourceValue;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
