-- Tier-2 group @mentions (docs/design/T2-group-mentions.md, ADR-0008).
-- ADDITIVE + reversible: unused while muhabbet.mentions.enabled = false (default).
--
-- A message can mention specific members (one row each in message_mentions) and/or @everyone
-- (the boolean column on messages). Mentions die with their message (ON DELETE CASCADE).
-- No hard FK to users(id): membership is validated in the service layer to keep the messaging
-- module decoupled from auth/user at the schema level (mirrors the media-access soft-reference).

CREATE TABLE message_mentions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id        UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    mentioned_user_id UUID NOT NULL,           -- soft reference to users(id)
    start_offset      INT  NOT NULL,           -- char offset into messages.content (for highlight)
    length            INT  NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_message_mentions_offset CHECK (start_offset >= 0 AND length > 0)
);

CREATE INDEX idx_message_mentions_message ON message_mentions(message_id);
CREATE INDEX idx_message_mentions_user    ON message_mentions(mentioned_user_id);

-- @everyone recorded once per message rather than N rows.
ALTER TABLE messages ADD COLUMN mentions_everyone BOOLEAN NOT NULL DEFAULT false;
