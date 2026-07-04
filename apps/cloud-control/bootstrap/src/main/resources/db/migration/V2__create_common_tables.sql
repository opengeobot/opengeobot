-- Function: Create common outbox, inbox, and audit tables
-- Time: 2026-07-03
-- Author: AxeXie

-- Outbox event table for transactional outbox pattern
CREATE TABLE platform_governance.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    aggregate_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    producer VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64),
    correlation_id VARCHAR(64),
    causation_id VARCHAR(64),
    actor_type VARCHAR(32),
    actor_id VARCHAR(64),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique constraint on event_id
ALTER TABLE platform_governance.outbox_event ADD CONSTRAINT uq_outbox_event_id UNIQUE (event_id);

-- Index for finding unpublished events
CREATE INDEX idx_outbox_unpublished ON platform_governance.outbox_event (published, next_retry_at) WHERE published = FALSE;

-- Index for querying by aggregate
CREATE INDEX idx_outbox_aggregate ON platform_governance.outbox_event (aggregate_type, aggregate_id);

-- Index for querying by trace_id
CREATE INDEX idx_outbox_trace ON platform_governance.outbox_event (trace_id);

-- Inbox event table for idempotent consumption
CREATE TABLE platform_governance.inbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSED',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique constraint: same event should only be processed once per consumer
ALTER TABLE platform_governance.inbox_event ADD CONSTRAINT uq_inbox_event_consumer UNIQUE (event_id, consumer_name);

-- Operation audit table (append-only)
CREATE TABLE platform_governance.sys_operation_audit (
    id BIGSERIAL PRIMARY KEY,
    audit_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64),
    result VARCHAR(32) NOT NULL,
    reason_code VARCHAR(128),
    reason_detail TEXT,
    source_ip VARCHAR(45),
    user_agent TEXT,
    trace_id VARCHAR(64),
    request_id VARCHAR(64),
    mission_id VARCHAR(64),
    robot_id VARCHAR(64),
    payload_before JSONB,
    payload_after JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique constraint on audit_id
ALTER TABLE platform_governance.sys_operation_audit ADD CONSTRAINT uq_audit_id UNIQUE (audit_id);

-- Index for querying by trace_id
CREATE INDEX idx_audit_trace ON platform_governance.sys_operation_audit (trace_id);

-- Index for querying by resource
CREATE INDEX idx_audit_resource ON platform_governance.sys_operation_audit (resource_type, resource_id);

-- Index for querying by actor
CREATE INDEX idx_audit_actor ON platform_governance.sys_operation_audit (actor_type, actor_id);

-- Index for querying by occurred_at (for time-range queries)
CREATE INDEX idx_audit_occurred_at ON platform_governance.sys_operation_audit (occurred_at);

-- Idempotency record table
CREATE TABLE platform_governance.sys_idempotency_record (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64),
    request_hash VARCHAR(64) NOT NULL,
    status_code INTEGER NOT NULL,
    response_body JSONB,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique constraint on idempotency_key
ALTER TABLE platform_governance.sys_idempotency_record ADD CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key);

-- Index for expiry cleanup
CREATE INDEX idx_idempotency_expires ON platform_governance.sys_idempotency_record (expires_at);
