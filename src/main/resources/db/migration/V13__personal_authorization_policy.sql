ALTER TABLE ha_knowledge_sources
    ADD COLUMN allowed_owners_json TEXT NOT NULL DEFAULT '[]';

UPDATE ha_knowledge_sources
    SET allowed_owners_json = allowed_users_json;

CREATE INDEX idx_knowledge_sources_owner_grants_updated
    ON ha_knowledge_sources (owner_scope_id, owner_id, agent_id, updated_at);

INSERT INTO ha_owner_scope_migration_activity (id, migration_name, migrated_at, notes)
SELECT 'V13-personal-authorization', 'V13__personal_authorization_policy', CURRENT_TIMESTAMP,
       'Replaced active knowledge ACL grants with owner grants; historical role and department columns remain migration-only.'
WHERE NOT EXISTS (
    SELECT 1 FROM ha_owner_scope_migration_activity WHERE id = 'V13-personal-authorization'
);
