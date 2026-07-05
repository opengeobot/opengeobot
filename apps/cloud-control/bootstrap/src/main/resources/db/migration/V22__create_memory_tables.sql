-- Function: Create task memory tables for F-MEMORY-001 failure case and improvement
-- Time: 2026-07-05
-- Author: AxeXie

-- memory schema already created in V1; add domain tables.

-- Task case table (recorded after mission step execution)
CREATE TABLE memory.task_case (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    robot_id VARCHAR(64) NOT NULL,
    skill_id VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    duration_ms BIGINT,
    context JSONB,
    error_message TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(64)
);

ALTER TABLE memory.task_case ADD CONSTRAINT uq_memory_task_case_id UNIQUE (case_id);
CREATE INDEX idx_memory_task_case_result ON memory.task_case (result);
CREATE INDEX idx_memory_task_case_robot ON memory.task_case (robot_id);
CREATE INDEX idx_memory_task_case_skill ON memory.task_case (skill_id);
CREATE INDEX idx_memory_task_case_mission ON memory.task_case (mission_id);
CREATE INDEX idx_memory_task_case_occurred ON memory.task_case (occurred_at);

-- Failure case table (root-cause analysis for failed task cases)
CREATE TABLE memory.failure_case (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL,
    failure_type VARCHAR(64) NOT NULL,
    root_cause TEXT,
    environment JSONB,
    similar_case_ids text[]
);

ALTER TABLE memory.failure_case ADD CONSTRAINT uq_memory_failure_case_id UNIQUE (case_id);
CREATE INDEX idx_memory_failure_case_type ON memory.failure_case (failure_type);

-- Improvement suggestion table (SM-IMPROVE-001: PENDING → ACCEPTED / REJECTED / APPLIED)
CREATE TABLE memory.improvement_suggestion (
    id BIGSERIAL PRIMARY KEY,
    suggestion_id VARCHAR(64) NOT NULL,
    case_id VARCHAR(64) NOT NULL,
    suggestion_text TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    feedback TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE memory.improvement_suggestion ADD CONSTRAINT uq_memory_suggestion_id UNIQUE (suggestion_id);
CREATE INDEX idx_memory_suggestion_case ON memory.improvement_suggestion (case_id);
CREATE INDEX idx_memory_suggestion_status ON memory.improvement_suggestion (status);
