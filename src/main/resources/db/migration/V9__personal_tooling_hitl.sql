ALTER TABLE ha_tool_definitions
    ADD COLUMN output_schema_json VARCHAR(4096) NOT NULL DEFAULT '{}';

CREATE TABLE IF NOT EXISTS ha_tool_pending_confirmations (
    confirmation_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    tool_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(256) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    status VARCHAR(64) NOT NULL,
    parameters_json TEXT NOT NULL,
    sanitized_input_json TEXT NOT NULL,
    operation_summary_json TEXT NOT NULL,
    parameter_fingerprint TEXT NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    decided_at TIMESTAMP(6),
    decision_reason TEXT NOT NULL
);

CREATE INDEX idx_tool_pending_session
    ON ha_tool_pending_confirmations (tenant_id, user_id, agent_id, session_id, status, created_at);

CREATE INDEX idx_tool_pending_tool
    ON ha_tool_pending_confirmations (tenant_id, tool_id, status, created_at);

CREATE INDEX idx_tool_pending_idempotency
    ON ha_tool_pending_confirmations (idempotency_key);
