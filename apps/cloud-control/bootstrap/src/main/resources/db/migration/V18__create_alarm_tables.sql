-- Function: Create alarm tables for F-ALARM-001 alarm lifecycle and notification
-- Time: 2026-07-05
-- Author: AxeXie

-- Alarm domain schema (sub-domain within ops)
CREATE SCHEMA IF NOT EXISTS alarm;

-- Alarm rule table (defines alarm conditions evaluated periodically)
CREATE TABLE alarm.alarm_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    source VARCHAR(128) NOT NULL,
    metric VARCHAR(256) NOT NULL,
    condition VARCHAR(32) NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64)
);

ALTER TABLE alarm.alarm_rule ADD CONSTRAINT uq_alarm_rule_id UNIQUE (rule_id);
CREATE INDEX idx_alarm_rule_source ON alarm.alarm_rule (source);
CREATE INDEX idx_alarm_rule_enabled ON alarm.alarm_rule (enabled);

-- Alarm event table (SM-ALARM-001 state machine: ACTIVE → ACKNOWLEDGED → RESOLVED)
CREATE TABLE alarm.alarm_event (
    id BIGSERIAL PRIMARY KEY,
    alarm_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    source VARCHAR(128) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_by VARCHAR(64),
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    trace_id VARCHAR(64)
);

ALTER TABLE alarm.alarm_event ADD CONSTRAINT uq_alarm_event_id UNIQUE (alarm_id);
CREATE INDEX idx_alarm_event_status ON alarm.alarm_event (status);
CREATE INDEX idx_alarm_event_severity ON alarm.alarm_event (severity);
CREATE INDEX idx_alarm_event_source ON alarm.alarm_event (source);
CREATE INDEX idx_alarm_event_triggered ON alarm.alarm_event (triggered_at);
CREATE INDEX idx_alarm_event_rule ON alarm.alarm_event (rule_id);

-- Notification channel table (in-app, webhook, email)
CREATE TABLE alarm.notification_channel (
    id BIGSERIAL PRIMARY KEY,
    channel_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    type VARCHAR(32) NOT NULL,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE alarm.notification_channel ADD CONSTRAINT uq_notification_channel_id UNIQUE (channel_id);

-- Notification log table (tracks notification delivery per alarm/channel)
CREATE TABLE alarm.notification_log (
    id BIGSERIAL PRIMARY KEY,
    alarm_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMPTZ,
    error_message TEXT
);

CREATE INDEX idx_notification_log_alarm ON alarm.notification_log (alarm_id);
CREATE INDEX idx_notification_log_channel ON alarm.notification_log (channel_id);

-- Seed: default in-app notification channel
INSERT INTO alarm.notification_channel (channel_id, name, type, config, enabled)
VALUES ('nch_00000000000000000000000001', 'in-app', 'in-app', '{}'::jsonb, TRUE);

-- Seed: robot_offline alarm rule
INSERT INTO alarm.alarm_rule (rule_id, name, source, metric, condition, threshold, severity, enabled, created_by)
VALUES ('alr_000000000000000000000001', 'robot_offline', 'robot_registry', 'robot.offline_duration', 'GREATER_THAN', 300, 'HIGH', TRUE, 'system');

-- Seed: mission_failure_rate alarm rule
INSERT INTO alarm.alarm_rule (rule_id, name, source, metric, condition, threshold, severity, enabled, created_by)
VALUES ('alr_000000000000000000000002', 'mission_failure_rate', 'mission', 'mission.failure_rate', 'GREATER_THAN', 0.2, 'MEDIUM', TRUE, 'system');
