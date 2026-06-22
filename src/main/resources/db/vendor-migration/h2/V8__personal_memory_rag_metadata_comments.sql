COMMENT ON COLUMN ha_knowledge_sources.agent_id IS 'Personal Agent id when the source is scoped to one Agent; blank means owner-level source.';
COMMENT ON COLUMN ha_knowledge_sources.source_type IS 'Personal knowledge source type such as inline text, local file, local directory, URL, or memory.';
COMMENT ON COLUMN ha_knowledge_sources.source_uri IS 'Personal source URI or workspace-relative reference used in citations and export.';
COMMENT ON COLUMN ha_knowledge_sources.index_status IS 'Index lifecycle status for the searchable projection.';
COMMENT ON COLUMN ha_knowledge_sources.indexed_at IS 'Timestamp when the source was last indexed successfully.';

COMMENT ON COLUMN ha_knowledge_chunks.source_type IS 'Knowledge source type copied to the chunk for citation reconstruction.';
COMMENT ON COLUMN ha_knowledge_chunks.source_uri IS 'Source URI copied to the chunk for citation reconstruction.';

COMMENT ON TABLE ha_personal_memories IS 'Explicit personal memory write records with confirmation, rejection, deletion, and RAG projection linkage.';
COMMENT ON COLUMN ha_personal_memories.id IS 'Application generated personal memory id.';
COMMENT ON COLUMN ha_personal_memories.tenant_id IS 'Compatibility tenant isolation key for personal memory.';
COMMENT ON COLUMN ha_personal_memories.owner_id IS 'Personal owner id for the memory.';
COMMENT ON COLUMN ha_personal_memories.agent_id IS 'Personal Agent id associated with the memory.';
COMMENT ON COLUMN ha_personal_memories.session_id IS 'Session where the memory write was requested.';
COMMENT ON COLUMN ha_personal_memories.layer_name IS 'Memory layer such as session context, Agent memory file, or fact ledger.';
COMMENT ON COLUMN ha_personal_memories.title IS 'User-visible memory summary title.';
COMMENT ON COLUMN ha_personal_memories.content IS 'Memory content projected to RAG only after confirmation.';
COMMENT ON COLUMN ha_personal_memories.status IS 'Memory write lifecycle status.';
COMMENT ON COLUMN ha_personal_memories.source_id IS 'Linked knowledge source id when the memory is confirmed.';
COMMENT ON COLUMN ha_personal_memories.created_at IS 'Memory record creation timestamp.';
COMMENT ON COLUMN ha_personal_memories.updated_at IS 'Memory record update timestamp.';
