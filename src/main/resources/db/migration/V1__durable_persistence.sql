CREATE TABLE IF NOT EXISTS ha_session_messages (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_session_messages_tenant_user_agent_session_time
    ON ha_session_messages (tenant_id, user_id, agent_id, session_id, created_at);
CREATE INDEX idx_session_messages_tenant_user_agent_time
    ON ha_session_messages (tenant_id, user_id, agent_id, created_at);

CREATE TABLE IF NOT EXISTS ha_security_audit (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    occurred_at TIMESTAMP(6) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(256) NOT NULL,
    action VARCHAR(128) NOT NULL,
    details_json TEXT NOT NULL
);

CREATE INDEX idx_security_audit_tenant_time
    ON ha_security_audit (tenant_id, occurred_at);
CREATE INDEX idx_security_audit_tenant_user_time
    ON ha_security_audit (tenant_id, user_id, occurred_at);
CREATE INDEX idx_security_audit_tenant_resource_time
    ON ha_security_audit (tenant_id, resource_type, resource_id, occurred_at);

CREATE TABLE IF NOT EXISTS ha_budget_counters (
    counter_key VARCHAR(512) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    resource_id VARCHAR(256) NOT NULL,
    requests BIGINT NOT NULL,
    tokens BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_budget_counters_tenant_user_agent_resource_time
    ON ha_budget_counters (tenant_id, user_id, agent_id, resource_id, updated_at);
CREATE INDEX idx_budget_counters_tenant_resource_time
    ON ha_budget_counters (tenant_id, resource_id, updated_at);
CREATE INDEX idx_budget_counters_updated_at
    ON ha_budget_counters (updated_at);

CREATE TABLE IF NOT EXISTS ha_agent_state (
    state_key VARCHAR(768) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    scope VARCHAR(256) NOT NULL,
    state_value TEXT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_agent_state_tenant_user_agent_session_scope
    ON ha_agent_state (tenant_id, user_id, agent_id, session_id, scope);
CREATE INDEX idx_agent_state_tenant_agent_session_time
    ON ha_agent_state (tenant_id, agent_id, session_id, updated_at);

CREATE TABLE IF NOT EXISTS ha_snapshot_metadata (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(256) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    backend_type VARCHAR(32) NOT NULL,
    location VARCHAR(1024) NOT NULL
);

CREATE TABLE IF NOT EXISTS ha_snapshot_content (
    snapshot_id VARCHAR(64) NOT NULL PRIMARY KEY,
    content BLOB NOT NULL,
    CONSTRAINT fk_snapshot_content_metadata
        FOREIGN KEY (snapshot_id) REFERENCES ha_snapshot_metadata (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_snapshot_metadata_tenant_agent_session_time
    ON ha_snapshot_metadata (tenant_id, agent_id, session_id, created_at);
CREATE INDEX idx_snapshot_metadata_tenant_task_time
    ON ha_snapshot_metadata (tenant_id, task_id, created_at);

CREATE TABLE IF NOT EXISTS ha_knowledge_sources (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    title VARCHAR(512) NOT NULL,
    version VARCHAR(128) NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    allowed_departments_json TEXT NOT NULL,
    allowed_roles_json TEXT NOT NULL,
    allowed_users_json TEXT NOT NULL,
    update_policy VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_knowledge_sources_tenant_updated
    ON ha_knowledge_sources (tenant_id, updated_at);
CREATE INDEX idx_knowledge_sources_tenant_owner_updated
    ON ha_knowledge_sources (tenant_id, owner_id, updated_at);

CREATE TABLE IF NOT EXISTS ha_knowledge_chunks (
    id VARCHAR(160) NOT NULL PRIMARY KEY,
    source_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    title VARCHAR(512) NOT NULL,
    version VARCHAR(128) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    tokens_json TEXT NOT NULL
);

CREATE INDEX idx_knowledge_chunks_source
    ON ha_knowledge_chunks (source_id, chunk_index);
CREATE INDEX idx_knowledge_chunks_tenant
    ON ha_knowledge_chunks (tenant_id);

CREATE TABLE IF NOT EXISTS ha_rag_metrics (
    id BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    query_text TEXT NOT NULL,
    hit BOOLEAN NOT NULL,
    candidate_count INT NOT NULL,
    permitted_count INT NOT NULL,
    failure_reason VARCHAR(512),
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_rag_metrics_tenant_created
    ON ha_rag_metrics (tenant_id, created_at);
CREATE INDEX idx_rag_metrics_tenant_user_created
    ON ha_rag_metrics (tenant_id, user_id, created_at);

CREATE TABLE IF NOT EXISTS ha_rag_feedback (
    id BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    query_text TEXT NOT NULL,
    helpful BOOLEAN NOT NULL,
    comment_text TEXT,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_rag_feedback_tenant_created
    ON ha_rag_feedback (tenant_id, created_at);
CREATE INDEX idx_rag_feedback_tenant_user_created
    ON ha_rag_feedback (tenant_id, user_id, created_at);

CREATE TABLE IF NOT EXISTS ha_tool_definitions (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT NOT NULL,
    owner_system VARCHAR(128) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref VARCHAR(512) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    mutating BOOLEAN NOT NULL,
    enabled BOOLEAN NOT NULL,
    parameter_schema_json TEXT NOT NULL,
    permission_policy_json TEXT NOT NULL,
    audit_policy_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_tool_definitions_tenant_name
    ON ha_tool_definitions (tenant_id, name);
CREATE INDEX idx_tool_definitions_tenant_owner
    ON ha_tool_definitions (tenant_id, owner_system, owner_id);

CREATE TABLE IF NOT EXISTS ha_tool_audit_records (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    occurred_at TIMESTAMP(6) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    tool_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(256) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    status VARCHAR(64) NOT NULL,
    sanitized_input_json TEXT NOT NULL,
    sanitized_output_json TEXT NOT NULL,
    duration_millis BIGINT NOT NULL,
    approval_id VARCHAR(128) NOT NULL,
    reviewer_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    failure_reason TEXT NOT NULL
);

CREATE INDEX idx_tool_audit_tenant_time
    ON ha_tool_audit_records (tenant_id, occurred_at);
CREATE INDEX idx_tool_audit_tenant_user_time
    ON ha_tool_audit_records (tenant_id, user_id, occurred_at);
CREATE INDEX idx_tool_audit_tenant_tool_time
    ON ha_tool_audit_records (tenant_id, tool_id, occurred_at);

CREATE TABLE IF NOT EXISTS ha_tool_idempotency_records (
    idempotency_key VARCHAR(512) NOT NULL PRIMARY KEY,
    parameter_fingerprint TEXT NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS ha_telemetry_events (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    occurred_at TIMESTAMP(6) NOT NULL,
    type VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    component VARCHAR(256) NOT NULL,
    duration_millis BIGINT NOT NULL,
    attributes_json TEXT NOT NULL
);

CREATE INDEX idx_telemetry_events_tenant_time
    ON ha_telemetry_events (tenant_id, occurred_at);
CREATE INDEX idx_telemetry_events_tenant_user_time
    ON ha_telemetry_events (tenant_id, user_id, occurred_at);
CREATE INDEX idx_telemetry_events_tenant_agent_time
    ON ha_telemetry_events (tenant_id, agent_id, occurred_at);
