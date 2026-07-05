-- Function: Create trace tables for F-TRACE-001 trace span and fact event storage
-- Time: 2026-07-05
-- Author: AxeXie

-- Trace span table (distributed tracing spans)
CREATE TABLE trace.trace_span (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(64) NOT NULL,
    parent_span_id VARCHAR(64),
    operation VARCHAR(256) NOT NULL,
    service VARCHAR(128) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    duration_ms BIGINT,
    tags JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'OK',
    robot_id VARCHAR(64),
    mission_id VARCHAR(64)
);

ALTER TABLE trace.trace_span ADD CONSTRAINT uq_trace_span_id UNIQUE (span_id);
CREATE INDEX idx_trace_span_trace ON trace.trace_span (trace_id);
CREATE INDEX idx_trace_span_robot ON trace.trace_span (robot_id);
CREATE INDEX idx_trace_span_mission ON trace.trace_span (mission_id);
CREATE INDEX idx_trace_span_start ON trace.trace_span (start_time);
CREATE INDEX idx_trace_span_service ON trace.trace_span (service);

-- Fact event table (immutable events for trace replay)
CREATE TABLE trace.fact_event (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id VARCHAR(64),
    robot_id VARCHAR(64),
    mission_id VARCHAR(64)
);

CREATE INDEX idx_fact_event_trace ON trace.fact_event (trace_id);
CREATE INDEX idx_fact_event_type ON trace.fact_event (event_type);
CREATE INDEX idx_fact_event_robot ON trace.fact_event (robot_id);
CREATE INDEX idx_fact_event_mission ON trace.fact_event (mission_id);
CREATE INDEX idx_fact_event_occurred ON trace.fact_event (occurred_at);
