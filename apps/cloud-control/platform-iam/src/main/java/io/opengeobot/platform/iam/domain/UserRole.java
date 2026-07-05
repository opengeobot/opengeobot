/*
 * Function: User-Role association entity — maps to platform_iam.sys_user_role table
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent association between a user and a role. Backed by the
 * {@code platform_iam.sys_user_role} table. The {@code expiresAt} field
 * optionally limits the duration of the role assignment.
 */
@TableName(value = "sys_user_role", schema = "platform_iam")
public class UserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String roleId;

    private OffsetDateTime assignedAt;

    private String assignedBy;

    private OffsetDateTime expiresAt;

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

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(OffsetDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(String assignedBy) {
        this.assignedBy = assignedBy;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
