-- V4: Add edited_at to messages for message editing support
ALTER TABLE messages ADD COLUMN edited_at TIMESTAMPTZ;
