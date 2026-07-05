/*
 * Function: Dictionary item entity — maps to platform_governance.sys_dict_item
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dict.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.opengeobot.platform.governance.config.JsonbMapTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Persistent dictionary item entity backed by the
 * {@code platform_governance.sys_dict_item} table. Each item belongs to a
 * {@link DictType} via {@code typeCode} and carries the canonical value plus
 * localised labels. The {@code extra} JSONB payload is mapped through
 * {@link JsonbMapTypeHandler} for correct PostgreSQL round-tripping.
 */
@TableName(value = "sys_dict_item", schema = "platform_governance", autoResultMap = true)
public class DictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String typeCode;

    private String itemCode;

    private String itemValue;

    private String labelZhCn;

    private String labelEnUs;

    private Integer sortOrder;

    private String status;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> extra;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemValue() {
        return itemValue;
    }

    public void setItemValue(String itemValue) {
        this.itemValue = itemValue;
    }

    public String getLabelZhCn() {
        return labelZhCn;
    }

    public void setLabelZhCn(String labelZhCn) {
        this.labelZhCn = labelZhCn;
    }

    public String getLabelEnUs() {
        return labelEnUs;
    }

    public void setLabelEnUs(String labelEnUs) {
        this.labelEnUs = labelEnUs;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
