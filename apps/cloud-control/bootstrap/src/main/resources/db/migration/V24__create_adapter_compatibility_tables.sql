-- Function: Create adapter compatibility table for adapter domain
-- Time: 2026-07-09
-- Author: AxeXie

-- Adapter compatibility table (maps robot hardware models to protocol adapters)
CREATE TABLE robot_registry.adapter_compatibility (
    id BIGSERIAL PRIMARY KEY,
    adapter_id VARCHAR(64) NOT NULL,
    robot_model_id VARCHAR(64),
    adapter_type VARCHAR(128),
    ros_version VARCHAR(64),
    control_protocol VARCHAR(128),
    compatible BOOLEAN,
    health_status VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE robot_registry.adapter_compatibility ADD CONSTRAINT uq_adapter_compatibility_adapter_id UNIQUE (adapter_id);
CREATE INDEX idx_adapter_compatibility_robot_model ON robot_registry.adapter_compatibility (robot_model_id);
CREATE INDEX idx_adapter_compatibility_type ON robot_registry.adapter_compatibility (adapter_type);

-- Foreign key to robot_model(model_id) - both are VARCHAR(64)
ALTER TABLE robot_registry.adapter_compatibility
    ADD CONSTRAINT fk_adapter_compatibility_robot_model
    FOREIGN KEY (robot_model_id) REFERENCES robot_registry.robot_model (model_id);
