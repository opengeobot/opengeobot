/*
 * Function: User-Organization association entity — maps to platform_iam.sys_user_org table
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent association between a user and an organization. Backed by the
 * {@code platform_iam.sys_user_org} table. The {@code isPrimary} flag marks
 * the user's primary organization.
 */
@TableName(value = "sys_user_org", schema = "platform_iam")
public class UserOrg {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String orgId;

    private Boolean isPrimary;

    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
