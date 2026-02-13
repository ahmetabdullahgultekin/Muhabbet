-- V15: Content moderation, channel analytics, bot platform, message backup
-- Covers Phase 2 (moderation), Phase 4 (backup), Phase 6 (analytics, bots)

-- ─── Content Moderation ──────────────────────────────────────────

CREATE TABLE user_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id),
    reported_user_id UUID REFERENCES users(id),
    reported_message_id UUID REFERENCES messages(id),
    reported_conversation_id UUID REFERENCES conversations(id),
    reason VARCHAR(50) NOT NULL,          -- SPAM, HARASSMENT, ILLEGAL_CONTENT, HATE_SPEECH, OTHER
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, REVIEWED, RESOLVED, DISMISSED
    reviewed_by UUID REFERENCES users(id),
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE user_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES users(id),
    blocked_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(blocker_id, blocked_id)
);

CREATE INDEX idx_user_reports_status ON user_reports(status) WHERE status = 'PENDING';
CREATE INDEX idx_user_reports_reporter ON user_reports(reporter_id);
CREATE INDEX idx_user_reports_reported_user ON user_reports(reported_user_id);
CREATE INDEX idx_user_blocks_blocker ON user_blocks(blocker_id);
CREATE INDEX idx_user_blocks_blocked ON user_blocks(blocked_id);

-- ─── Channel Analytics ───────────────────────────────────────────

CREATE TABLE channel_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID NOT NULL REFERENCES conversations(id),
    date DATE NOT NULL,
    message_count INT NOT NULL DEFAULT 0,
    view_count INT NOT NULL DEFAULT 0,
    subscriber_gained INT NOT NULL DEFAULT 0,
    subscriber_lost INT NOT NULL DEFAULT 0,
    reaction_count INT NOT NULL DEFAULT 0,
    UNIQUE(channel_id, date)
);

CREATE TABLE channel_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID NOT NULL REFERENCES conversations(id),
    subscriber_id UUID NOT NULL REFERENCES users(id),
    tier VARCHAR(20) NOT NULL DEFAULT 'FREE',  -- FREE, PREMIUM
    subscribed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(channel_id, subscriber_id)
);

CREATE INDEX idx_channel_analytics_channel_date ON channel_analytics(channel_id, date DESC);
CREATE INDEX idx_channel_subs_channel ON channel_subscriptions(channel_id);

-- ─── Bot Platform ────────────────────────────────────────────────

CREATE TABLE bots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    user_id UUID NOT NULL REFERENCES users(id),      -- bot's user account
    name VARCHAR(100) NOT NULL,
    description TEXT,
    api_token VARCHAR(256) NOT NULL UNIQUE,
    webhook_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    permissions JSONB NOT NULL DEFAULT '["SEND_MESSAGE","READ_MESSAGE"]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_bots_owner ON bots(owner_id);
CREATE INDEX idx_bots_api_token ON bots(api_token);

-- ─── Message Backup ──────────────────────────────────────────────

CREATE TABLE message_backups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, IN_PROGRESS, COMPLETED, FAILED
    backup_url TEXT,                                  -- MinIO pre-signed URL for download
    file_size_bytes BIGINT,
    message_count INT,
    conversation_count INT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT
);

CREATE INDEX idx_message_backups_user ON message_backups(user_id);
