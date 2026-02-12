-- V3: Add duration_seconds to media_files for voice messages
ALTER TABLE media_files ADD COLUMN duration_seconds INT;
