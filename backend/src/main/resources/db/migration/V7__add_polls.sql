-- Poll options stored as JSON in message content
-- Poll votes tracked in separate table

CREATE TABLE poll_votes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id    UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id       UUID NOT NULL,
    option_index  INT NOT NULL,
    voted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(message_id, user_id)
);

CREATE INDEX idx_poll_votes_message ON poll_votes(message_id);
