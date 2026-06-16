ALTER TABLE ha_session_messages
    ADD COLUMN content_blocks_json TEXT NOT NULL DEFAULT '[]';
