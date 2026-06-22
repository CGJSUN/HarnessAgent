ALTER TABLE ha_knowledge_sources MODIFY COLUMN agent_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Personal Agent id when the source is scoped to one Agent; blank means owner-level source.';
ALTER TABLE ha_knowledge_sources MODIFY COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'INLINE_TEXT' COMMENT 'Personal knowledge source type such as inline text, local file, local directory, URL, or memory.';
ALTER TABLE ha_knowledge_sources MODIFY COLUMN source_uri VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'Personal source URI or workspace-relative reference used in citations and export.';
ALTER TABLE ha_knowledge_sources MODIFY COLUMN index_status VARCHAR(32) NOT NULL DEFAULT 'INDEXED' COMMENT 'Index lifecycle status for the searchable projection.';
ALTER TABLE ha_knowledge_sources MODIFY COLUMN indexed_at TIMESTAMP(6) NULL COMMENT 'Timestamp when the source was last indexed successfully.';

ALTER TABLE ha_knowledge_chunks MODIFY COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'INLINE_TEXT' COMMENT 'Knowledge source type copied to the chunk for citation reconstruction.';
ALTER TABLE ha_knowledge_chunks MODIFY COLUMN source_uri VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'Source URI copied to the chunk for citation reconstruction.';

ALTER TABLE ha_personal_memories COMMENT = 'Explicit personal memory write records with confirmation, rejection, deletion, and RAG projection linkage.';
ALTER TABLE ha_personal_memories MODIFY COLUMN id VARCHAR(128) NOT NULL COMMENT 'Application generated personal memory id.';
ALTER TABLE ha_personal_memories MODIFY COLUMN tenant_id VARCHAR(128) NOT NULL COMMENT 'Compatibility tenant isolation key for personal memory.';
ALTER TABLE ha_personal_memories MODIFY COLUMN owner_id VARCHAR(128) NOT NULL COMMENT 'Personal owner id for the memory.';
ALTER TABLE ha_personal_memories MODIFY COLUMN agent_id VARCHAR(128) NOT NULL COMMENT 'Personal Agent id associated with the memory.';
ALTER TABLE ha_personal_memories MODIFY COLUMN session_id VARCHAR(128) NOT NULL COMMENT 'Session where the memory write was requested.';
ALTER TABLE ha_personal_memories MODIFY COLUMN layer_name VARCHAR(64) NOT NULL COMMENT 'Memory layer such as session context, Agent memory file, or fact ledger.';
ALTER TABLE ha_personal_memories MODIFY COLUMN title VARCHAR(512) NOT NULL COMMENT 'User-visible memory summary title.';
ALTER TABLE ha_personal_memories MODIFY COLUMN content TEXT NOT NULL COMMENT 'Memory content projected to RAG only after confirmation.';
ALTER TABLE ha_personal_memories MODIFY COLUMN status VARCHAR(64) NOT NULL COMMENT 'Memory write lifecycle status.';
ALTER TABLE ha_personal_memories MODIFY COLUMN source_id VARCHAR(128) NOT NULL COMMENT 'Linked knowledge source id when the memory is confirmed.';
ALTER TABLE ha_personal_memories MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL COMMENT 'Memory record creation timestamp.';
ALTER TABLE ha_personal_memories MODIFY COLUMN updated_at TIMESTAMP(6) NOT NULL COMMENT 'Memory record update timestamp.';
