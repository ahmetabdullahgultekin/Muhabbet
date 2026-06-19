-- V20: Pinned messages within a conversation (WhatsApp "pin message in chat" — distinct from
-- pinning a conversation in the list, which already exists on conversation_members). Additive only.

CREATE TABLE pinned_messages (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    pinned_by       UUID NOT NULL REFERENCES users(id),
    pinned_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, message_id)
);

CREATE INDEX idx_pinned_messages_conversation ON pinned_messages(conversation_id, pinned_at DESC);
