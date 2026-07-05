/*
 * Function: MyBatis-Plus entity for platform_governance.sys_operation_audit table
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.common.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent entity for the operation audit table. Maps all columns of
 * {@code platform_governance.sys_operation_audit} including audit_id, reason
 * detail and mission/robot context that are not part of the {@link AuditEvent}
 * record.
 */
@TableName(value = "sys_operation_audit", schema = "platform_governance")
public class AuditEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String auditId;

    private OffsetDateTime occurredAt;

    private String actorType;

    private String actorId;

    private String action;

    private String resourceType;

    private String resourceId;

    private String result;

    private String reasonCode;

    private String reasonDetail;

    private String sourceIp;

    private String userAgent;

    private String traceId;

    private String requestId;

    private String missionId;

    private String robotId;

    private String payloadBefore;

    private String payloadAfter;

    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public void setReasonDetail(String reasonDetail) {
        this.reasonDetail = reasonDetail;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMissionId() {
        return missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getPayloadBefore() {
        return payloadBefore;
    }

    public void setPayloadBefore(String payloadBefore) {
        this.payloadBefore = payloadBefore;
    }

    public String getPayloadAfter() {
        return payloadAfter;
    }

    public void setPayloadAfter(String payloadAfter) {
        this.payloadAfter = payloadAfter;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
