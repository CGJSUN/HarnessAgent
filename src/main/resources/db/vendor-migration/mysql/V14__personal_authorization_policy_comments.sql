ALTER TABLE ha_knowledge_sources
    MODIFY COLUMN allowed_owners_json TEXT NOT NULL COMMENT 'Personal owner grant list used with source visibility and Agent scope.';
