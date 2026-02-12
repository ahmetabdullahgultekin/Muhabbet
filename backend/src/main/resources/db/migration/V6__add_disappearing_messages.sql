-- Disappearing messages: auto-delete after TTL
-- Timer set per conversation; messages inherit the TTL at send time

-- Add timer setting to conversations
ALTER TABLE conversations ADD COLUMN disappear_after_seconds INTEGER;

-- Add expiry timestamp to individual messages
ALTER TABLE messages ADD COLUMN expires_at TIMESTAMPTZ;

CREATE INDEX idx_messages_expires_at ON messages(expires_at) WHERE expires_at IS NOT NULL;
