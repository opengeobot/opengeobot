/*
 * Function: ReportRecord entity — maps to ops.report_record for F-OPS-001
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
 * Persistent operations report record backed by the
 * {@code ops.report_record} table. Summarises historical data for a given
 * period (daily, weekly, monthly).
 */
@TableName(value = "report_record", schema = "ops", autoResultMap = true)
public class ReportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reportType;

    private OffsetDateTime periodStart;

    private OffsetDateTime periodEnd;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> summary;

    private OffsetDateTime generatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public OffsetDateTime getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(OffsetDateTime periodStart) {
        this.periodStart = periodStart;
    }

    public OffsetDateTime getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(OffsetDateTime periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
