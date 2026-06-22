ALTER TABLE ha_knowledge_sources
    ADD COLUMN agent_id VARCHAR(128) NOT NULL DEFAULT '';

ALTER TABLE ha_knowledge_sources
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'INLINE_TEXT';

ALTER TABLE ha_knowledge_sources
    ADD COLUMN source_uri VARCHAR(1024) NOT NULL DEFAULT '';

ALTER TABLE ha_knowledge_sources
    ADD COLUMN index_status VARCHAR(32) NOT NULL DEFAULT 'INDEXED';

ALTER TABLE ha_knowledge_sources
    ADD COLUMN indexed_at TIMESTAMP(6);

CREATE INDEX idx_knowledge_sources_tenant_type_updated
    ON ha_knowledge_sources (tenant_id, source_type, updated_at);

CREATE INDEX idx_knowledge_sources_tenant_owner_agent_updated
    ON ha_knowledge_sources (tenant_id, owner_id, agent_id, updated_at);

ALTER TABLE ha_knowledge_chunks
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'INLINE_TEXT';

ALTER TABLE ha_knowledge_chunks
    ADD COLUMN source_uri VARCHAR(1024) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS ha_personal_memories (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    layer_name VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_personal_memories_owner_agent_updated
    ON ha_personal_memories (tenant_id, owner_id, agent_id, updated_at);

CREATE INDEX idx_personal_memories_source
    ON ha_personal_memories (source_id);
