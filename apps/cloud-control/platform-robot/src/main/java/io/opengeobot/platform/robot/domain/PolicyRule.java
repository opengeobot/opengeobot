/*
 * Function: PolicyRule entity — maps to policy.policy_rule table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent policy rule entity backed by the {@code policy.policy_rule}
 * table. Each rule belongs to a policy at a specific version. Rules are
 * immutable once published; editing a draft policy replaces the rule rows
 * for the draft version (version 0). The {@code condition} is stored as jsonb.
 */
@TableName(value = "policy_rule", schema = "policy", autoResultMap = true)
public class PolicyRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String policyId;

    private Integer version;

    private String ruleType;

    @TableField(typeHandler = io.opengeobot.platform.robot.config.JsonbStringTypeHandler.class)
    private String condition;

    private String action;

    private Integer priority;

    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
