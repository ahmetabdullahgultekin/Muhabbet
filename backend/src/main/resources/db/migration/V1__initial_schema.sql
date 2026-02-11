-- =====================================================
-- Muhabbet — V1: Initial Schema
-- Modules: auth, user, messaging, media
-- =====================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── AUTH MODULE ─────────────────────────────────────

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(15) NOT NULL UNIQUE,     -- E.164: +905XXXXXXXXX
    display_name    VARCHAR(64),
    avatar_url      TEXT,
    about           VARCHAR(256),
    status          VARCHAR(20) NOT NULL DEFAULT 'active',  -- active, suspended, deleted
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ                      -- soft delete (KVKK right to erasure)
);

CREATE INDEX idx_users_phone ON users(phone_number) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_status ON users(status) WHERE deleted_at IS NULL;

CREATE TABLE devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform        VARCHAR(10) NOT NULL,            -- android, ios, web
    device_name     VARCHAR(128),
    push_token      TEXT,
    last_active_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(user_id, push_token)
);

CREATE INDEX idx_devices_user ON devices(user_id);

CREATE TABLE otp_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(15) NOT NULL,
    otp_hash        VARCHAR(128) NOT NULL,           -- bcrypt hash of OTP
    attempts        INT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    verified        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_phone_active ON otp_requests(phone_number, verified, expires_at);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id       UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,    -- SHA-256 hash of refresh token
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id) WHERE revoked_at IS NULL;

-- ─── USER MODULE ─────────────────────────────────────

CREATE TABLE contacts (
    owner_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nickname        VARCHAR(64),
    is_blocked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (owner_id, contact_id)
);

CREATE INDEX idx_contacts_owner ON contacts(owner_id) WHERE is_blocked = FALSE;

-- Phone hash table for contact matching
CREATE TABLE phone_hashes (
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone_hash      VARCHAR(64) NOT NULL,            -- SHA-256 of phone number
    PRIMARY KEY (user_id),
    UNIQUE(phone_hash)
);

-- ─── MESSAGING MODULE ────────────────────────────────

CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(10) NOT NULL,            -- 'direct', 'group'
    name            VARCHAR(128),                    -- NULL for direct messages
    avatar_url      TEXT,
    description     TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE conversation_members (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(10) NOT NULL DEFAULT 'member', -- owner, admin, member
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    muted_until     TIMESTAMPTZ,
    last_read_at    TIMESTAMPTZ,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX idx_conv_members_user ON conversation_members(user_id);

-- For direct conversations: ensure uniqueness (user A ↔ user B = one conversation)
-- We store the pair as a sorted composite to prevent duplicates
CREATE TABLE direct_conversation_lookup (
    user_id_low     UUID NOT NULL,                   -- lower UUID (lexicographic)
    user_id_high    UUID NOT NULL,                   -- higher UUID
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    PRIMARY KEY (user_id_low, user_id_high),
    CHECK (user_id_low < user_id_high)
);

CREATE TABLE messages (
    id              UUID PRIMARY KEY,                -- UUIDv7, client-generated
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    sender_id       UUID NOT NULL REFERENCES users(id),
    content_type    VARCHAR(20) NOT NULL DEFAULT 'text',
    content         TEXT NOT NULL,                   -- plaintext MVP, encrypted blob Phase 2
    reply_to_id     UUID REFERENCES messages(id),
    media_url       TEXT,
    thumbnail_url   TEXT,
    server_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    client_timestamp TIMESTAMPTZ NOT NULL,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ
);

-- Primary query pattern: messages for a conversation, ordered by time
CREATE INDEX idx_messages_conversation_time
    ON messages(conversation_id, server_timestamp DESC)
    WHERE is_deleted = FALSE;

-- For "last message per conversation" (inbox view)
CREATE INDEX idx_messages_conversation_latest
    ON messages(conversation_id, server_timestamp DESC)
    INCLUDE (sender_id, content_type, content)
    WHERE is_deleted = FALSE;

-- Delivery status tracking
CREATE TABLE message_delivery_status (
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    status          VARCHAR(15) NOT NULL DEFAULT 'sent', -- sent, delivered, read
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (message_id, user_id)
);

-- ─── MEDIA MODULE ────────────────────────────────────

CREATE TABLE media_files (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id     UUID NOT NULL REFERENCES users(id),
    conversation_id UUID REFERENCES conversations(id),
    file_key        TEXT NOT NULL,                   -- S3/MinIO object key
    content_type    VARCHAR(50) NOT NULL,            -- MIME type
    size_bytes      BIGINT NOT NULL,
    thumbnail_key   TEXT,                            -- S3 key for thumbnail
    original_filename VARCHAR(256),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ                      -- for auto-cleanup
);

CREATE INDEX idx_media_expiry ON media_files(expires_at) WHERE expires_at IS NOT NULL;

-- ─── UPDATED_AT TRIGGER ──────────────────────────────

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_conversations_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
