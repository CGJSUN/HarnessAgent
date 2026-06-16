ALTER TABLE ha_session_messages MODIFY COLUMN content_blocks_json TEXT NOT NULL COMMENT 'Structured content blocks for text, file, media, thinking, and tool result content.';
