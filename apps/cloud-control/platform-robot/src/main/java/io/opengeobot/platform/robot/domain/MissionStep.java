/*
 * Function: Mission step entity — maps to mission.mission_step
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
 * Persistent mission step entity backed by the {@code mission.mission_step}
 * table. The {@code status} field follows the SM-MISSION-002 state machine
 * (PENDING, EXECUTING, COMPLETED, FAILED, SKIPPED) and is a code contract. The
 * {@code input_params} and {@code output_result} fields are stored as JSONB.
 */
@TableName(value = "mission_step", schema = "mission", autoResultMap = true)
public class MissionStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String stepId;

    private String missionId;

    private String skillId;

    private Integer stepOrder;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> inputParams;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> outputResult;

    private String status;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    private String errorMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getMissionId() {
        return missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public Map<String, Object> getInputParams() {
        return inputParams;
    }

    public void setInputParams(Map<String, Object> inputParams) {
        this.inputParams = inputParams;
    }

    public Map<String, Object> getOutputResult() {
        return outputResult;
    }

    public void setOutputResult(Map<String, Object> outputResult) {
        this.outputResult = outputResult;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
