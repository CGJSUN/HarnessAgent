ALTER TABLE ha_owner_scope_migration_activity COMMENT = 'Owner-scope migration metadata for the personal Agent persistence transition.';
ALTER TABLE ha_owner_scope_migration_activity
    MODIFY COLUMN id VARCHAR(64) NOT NULL COMMENT 'Stable migration metadata id.',
    MODIFY COLUMN migration_name VARCHAR(128) NOT NULL COMMENT 'Flyway migration name that created the owner-scope projection.',
    MODIFY COLUMN migrated_at TIMESTAMP(6) NOT NULL COMMENT 'Timestamp when the owner-scope projection migration ran.',
    MODIFY COLUMN notes TEXT NOT NULL COMMENT 'Operational notes for backup, rollback, and compatibility review.';

ALTER TABLE ha_session_messages
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the historical user column.';
ALTER TABLE ha_security_activity
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the historical user column.';
ALTER TABLE ha_budget_counters
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the historical user column.';
ALTER TABLE ha_agent_state
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the historical user column.';
ALTER TABLE ha_snapshot_metadata
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Best-effort owner id for historical snapshots; new writes store the personal owner.';
ALTER TABLE ha_knowledge_sources
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.';
ALTER TABLE ha_knowledge_chunks
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the source metadata.',
    MODIFY COLUMN agent_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Agent id copied from the source metadata.';
ALTER TABLE ha_personal_memories
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.';
ALTER TABLE ha_rag_metrics
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the historical user column.';
ALTER TABLE ha_rag_feedback
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the historical user column.';
ALTER TABLE ha_tool_definitions
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.';
ALTER TABLE ha_tool_activity_records
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the historical user column.';
ALTER TABLE ha_tool_idempotency_records
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id for personal tool idempotency records.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id for personal tool idempotency records.',
    MODIFY COLUMN agent_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Agent id for personal tool idempotency records.',
    MODIFY COLUMN session_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Session id for personal tool idempotency records.',
    MODIFY COLUMN tool_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Tool id for personal tool idempotency records.';
ALTER TABLE ha_tool_pending_confirmations
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the historical user column.';
ALTER TABLE ha_telemetry_events
    MODIFY COLUMN owner_scope_id VARCHAR(128) NOT NULL DEFAULT 'personal' COMMENT 'Owner scope id copied from the historical isolation column.',
    MODIFY COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal owner id copied from the historical user column.';
