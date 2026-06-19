-- V19: Chat folders / custom lists — user-defined conversation groupings (WhatsApp "Lists" parity).
-- Additive only: two new tables, no change to existing tables. A folder is an organizational overlay
-- owned by one user, grouping a subset of that user's conversations.

CREATE TABLE chat_folders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(50) NOT NULL,
    position    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_folders_owner ON chat_folders(owner_id, position);

CREATE TABLE chat_folder_entries (
    folder_id        UUID NOT NULL REFERENCES chat_folders(id) ON DELETE CASCADE,
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    PRIMARY KEY (folder_id, conversation_id)
);

CREATE INDEX idx_chat_folder_entries_conversation ON chat_folder_entries(conversation_id);
