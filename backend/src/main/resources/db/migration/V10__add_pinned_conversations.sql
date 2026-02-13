-- Pinned conversations
ALTER TABLE conversation_members ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE conversation_members ADD COLUMN pinned_at TIMESTAMP WITH TIME ZONE;
