CREATE TABLE IF NOT EXISTS ha_owner_scope_migration_activity (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    migration_name VARCHAR(128) NOT NULL,
    migrated_at TIMESTAMP(6) NOT NULL,
    notes TEXT NOT NULL
);

ALTER TABLE ha_session_messages
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_session_messages
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_session_messages
    SET owner_scope_id = tenant_id,
        owner_id = user_id;
CREATE INDEX idx_session_messages_owner_agent_session_time
    ON ha_session_messages (owner_scope_id, owner_id, agent_id, session_id, created_at);

ALTER TABLE ha_security_audit
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_security_audit
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_security_audit
    SET owner_scope_id = tenant_id,
        owner_id = user_id;
CREATE INDEX idx_security_audit_owner_time
    ON ha_security_audit (owner_scope_id, occurred_at);
CREATE INDEX idx_security_audit_owner_resource_time
    ON ha_security_audit (owner_scope_id, resource_type, resource_id, occurred_at);
ALTER TABLE ha_security_audit RENAME TO ha_security_activity;

ALTER TABLE ha_budget_counters
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_budget_counters
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_budget_counters
    SET owner_scope_id = tenant_id,
        owner_id = user_id;
UPDATE ha_budget_counters
    SET counter_key = CONCAT('owner-scope:', owner_scope_id)
    WHERE counter_key LIKE 'tenant:%';
UPDATE ha_budget_counters
    SET counter_key = CONCAT('owner:', owner_scope_id, ':', owner_id)
    WHERE counter_key LIKE 'user:%';
UPDATE ha_budget_counters
    SET counter_key = CONCAT('agent:', owner_scope_id, ':', agent_id)
    WHERE counter_key LIKE 'agent:%';
CREATE INDEX idx_budget_counters_owner_agent_resource_time
    ON ha_budget_counters (owner_scope_id, owner_id, agent_id, resource_id, updated_at);

ALTER TABLE ha_agent_state
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_agent_state
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_agent_state
    SET owner_scope_id = tenant_id,
        owner_id = user_id;
UPDATE ha_agent_state
    SET state_key = CONCAT('owner:', owner_id, ':agent:', agent_id, ':session:', session_id, ':scope:', scope)
    WHERE state_key LIKE 'tenant:%';
CREATE INDEX idx_agent_state_owner_agent_session_scope
    ON ha_agent_state (owner_scope_id, owner_id, agent_id, session_id, scope);

ALTER TABLE ha_snapshot_metadata
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_snapshot_metadata
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_snapshot_metadata
    SET owner_scope_id = tenant_id,
        owner_id = COALESCE(
            (SELECT MIN(user_id)
               FROM ha_session_messages
              WHERE ha_session_messages.tenant_id = ha_snapshot_metadata.tenant_id
                AND ha_session_messages.agent_id = ha_snapshot_metadata.agent_id
                AND ha_session_messages.session_id = ha_snapshot_metadata.session_id),
            (SELECT MIN(user_id)
               FROM ha_agent_state
              WHERE ha_agent_state.tenant_id = ha_snapshot_metadata.tenant_id
                AND ha_agent_state.agent_id = ha_snapshot_metadata.agent_id
                AND ha_agent_state.session_id = ha_snapshot_metadata.session_id),
            tenant_id);
CREATE INDEX idx_snapshot_metadata_owner_agent_session_time
    ON ha_snapshot_metadata (owner_scope_id, owner_id, agent_id, session_id, created_at);

ALTER TABLE ha_knowledge_sources
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
UPDATE ha_knowledge_sources
    SET owner_scope_id = tenant_id;
CREATE INDEX idx_knowledge_sources_owner_agent_updated
    ON ha_knowledge_sources (owner_scope_id, owner_id, agent_id, updated_at);

ALTER TABLE ha_knowledge_chunks
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_knowledge_chunks
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE ha_knowledge_chunks
    ADD COLUMN agent_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_knowledge_chunks
    SET owner_scope_id = tenant_id,
        owner_id = COALESCE(
            (SELECT owner_id
               FROM ha_knowledge_sources
              WHERE ha_knowledge_sources.id = ha_knowledge_chunks.source_id),
            ''),
        agent_id = COALESCE(
            (SELECT agent_id
               FROM ha_knowledge_sources
              WHERE ha_knowledge_sources.id = ha_knowledge_chunks.source_id),
            '');
CREATE INDEX idx_knowledge_chunks_owner_scope
    ON ha_knowledge_chunks (owner_scope_id, owner_id, agent_id);

ALTER TABLE ha_personal_memories
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
UPDATE ha_personal_memories
    SET owner_scope_id = tenant_id;
CREATE INDEX idx_personal_memories_owner_scope_agent_updated
    ON ha_personal_memories (owner_scope_id, owner_id, agent_id, updated_at);

ALTER TABLE ha_rag_metrics
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_rag_metrics
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_rag_metrics
    SET owner_scope_id = tenant_id,
        owner_id = user_id;
CREATE INDEX idx_rag_metrics_owner_created
    ON ha_rag_metrics (owner_scope_id, owner_id, created_at);

ALTER TABLE ha_rag_feedback
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_rag_feedback
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_rag_feedback
    SET owner_scope_id = tenant_id,
        owner_id = user_id;
CREATE INDEX idx_rag_feedback_owner_created
    ON ha_rag_feedback (owner_scope_id, owner_id, created_at);

ALTER TABLE ha_tool_definitions
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
UPDATE ha_tool_definitions
    SET owner_scope_id = tenant_id;
ALTER TABLE ha_tool_definitions RENAME COLUMN audit_policy_json TO activity_policy_json;
CREATE INDEX idx_tool_definitions_owner_name
    ON ha_tool_definitions (owner_scope_id, owner_id, name);

ALTER TABLE ha_tool_audit_records
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_tool_audit_records
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_tool_audit_records
    SET owner_scope_id = tenant_id,
        owner_id = user_id;
CREATE INDEX idx_tool_audit_owner_time
    ON ha_tool_audit_records (owner_scope_id, owner_id, occurred_at);
ALTER TABLE ha_tool_audit_records RENAME TO ha_tool_activity_records;

ALTER TABLE ha_tool_idempotency_records
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_tool_idempotency_records
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE ha_tool_idempotency_records
    ADD COLUMN agent_id VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE ha_tool_idempotency_records
    ADD COLUMN session_id VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE ha_tool_idempotency_records
    ADD COLUMN tool_id VARCHAR(128) NOT NULL DEFAULT '';
CREATE INDEX idx_tool_idempotency_owner_tool
    ON ha_tool_idempotency_records (owner_scope_id, owner_id, agent_id, session_id, tool_id);

ALTER TABLE ha_tool_pending_confirmations
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_tool_pending_confirmations
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_tool_pending_confirmations
    SET owner_scope_id = tenant_id,
        owner_id = user_id;
CREATE INDEX idx_tool_pending_owner_session
    ON ha_tool_pending_confirmations (owner_scope_id, owner_id, agent_id, session_id, status, created_at);

ALTER TABLE ha_telemetry_events
    ADD COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal';
ALTER TABLE ha_telemetry_events
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '';
UPDATE ha_telemetry_events
    SET owner_scope_id = tenant_id,
        owner_id = user_id;
CREATE INDEX idx_telemetry_events_owner_agent_time
    ON ha_telemetry_events (owner_scope_id, owner_id, agent_id, occurred_at);

INSERT INTO ha_owner_scope_migration_activity (id, migration_name, migrated_at, notes)
SELECT 'V11-owner-scope', 'V11__owner_scope_persistence', CURRENT_TIMESTAMP,
       'Copied legacy scope and user dimensions into owner-scope columns while retaining rollback-compatible legacy columns.'
WHERE NOT EXISTS (
    SELECT 1 FROM ha_owner_scope_migration_activity WHERE id = 'V11-owner-scope'
);
