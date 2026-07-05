-- Function: Create ops tables for F-OPS-001 metrics, health and operations dashboard
-- Time: 2026-07-05
-- Author: AxeXie

-- Metric snapshot table (time-series metric data points)
CREATE TABLE ops.metric_snapshot (
    id BIGSERIAL PRIMARY KEY,
    metric_name VARCHAR(256) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(64),
    tags JSONB,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_metric_snapshot_name ON ops.metric_snapshot (metric_name);
CREATE INDEX idx_metric_snapshot_time ON ops.metric_snapshot (timestamp);
CREATE INDEX idx_metric_snapshot_name_time ON ops.metric_snapshot (metric_name, timestamp);

-- Health check table (component health check results)
CREATE TABLE ops.health_check (
    id BIGSERIAL PRIMARY KEY,
    component VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'HEALTHY',
    latency_ms BIGINT,
    error_message TEXT,
    last_check_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_health_check_component ON ops.health_check (component);
CREATE INDEX idx_health_check_status ON ops.health_check (status);

-- Report record table (generated operations reports)
CREATE TABLE ops.report_record (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(32) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    summary JSONB,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_record_type ON ops.report_record (report_type);
CREATE INDEX idx_report_record_period ON ops.report_record (period_start, period_end);
