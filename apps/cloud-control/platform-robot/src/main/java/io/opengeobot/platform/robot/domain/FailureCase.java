/*
 * Function: FailureCase entity — maps to memory.failure_case table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.opengeobot.platform.robot.config.JsonbMapTypeHandler;
import io.opengeobot.platform.robot.config.StringArrayTypeHandler;

import java.util.List;
import java.util.Map;

/**
 * Persistent failure case backed by the {@code memory.failure_case} table.
 * Adds root-cause analysis and environment context to a failed task case, and
 * links to similar historical cases. The {@code environment} column is a
 * jsonb document; {@code similarCaseIds} is a PostgreSQL {@code text[]}.
 */
@TableName(value = "failure_case", schema = "memory", autoResultMap = true)
public class FailureCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String caseId;

    private String failureType;

    private String rootCause;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> environment;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private List<String> similarCaseIds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, Object> environment) {
        this.environment = environment;
    }

    public List<String> getSimilarCaseIds() {
        return similarCaseIds;
    }

    public void setSimilarCaseIds(List<String> similarCaseIds) {
        this.similarCaseIds = similarCaseIds;
    }
}
