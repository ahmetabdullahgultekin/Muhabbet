-- V16: WhatsApp feature parity — 2FA, archive, view-once, invite links, communities,
--      announcement mode, group events, login approval, group calls, security notifications

-- ─── Two-Step Verification (2FA PIN) ─────────────────────────────────

ALTER TABLE users ADD COLUMN two_step_pin_hash VARCHAR(256);
ALTER TABLE users ADD COLUMN two_step_email VARCHAR(256);
ALTER TABLE users ADD COLUMN two_step_enabled_at TIMESTAMP WITH TIME ZONE;

-- ─── Chat Archive ────────────────────────────────────────────────────

ALTER TABLE conversation_members ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE conversation_members ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_conv_members_archived ON conversation_members(user_id, archived)
    WHERE archived = true;

-- ─── View-Once Media ─────────────────────────────────────────────────

ALTER TABLE messages ADD COLUMN view_once BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE messages ADD COLUMN viewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE messages ADD COLUMN viewed_by UUID;

-- ─── Group Invite Links ──────────────────────────────────────────────

CREATE TABLE group_invite_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    invite_token VARCHAR(64) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES users(id),
    requires_approval BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    max_uses INT,
    use_count INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_invite_links_token ON group_invite_links(invite_token) WHERE is_active = true;
CREATE INDEX idx_invite_links_conversation ON group_invite_links(conversation_id);

-- ─── Group Join Requests (Admin Approval) ────────────────────────────

CREATE TABLE group_join_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    invite_link_id UUID REFERENCES group_invite_links(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
    reviewed_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(conversation_id, user_id, status)
);

CREATE INDEX idx_join_requests_pending ON group_join_requests(conversation_id, status)
    WHERE status = 'PENDING';

-- ─── Announcement Mode & Group Settings ──────────────────────────────

ALTER TABLE conversations ADD COLUMN announcement_only BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE conversations ADD COLUMN max_members INT NOT NULL DEFAULT 1024;

-- ─── Group Events ────────────────────────────────────────────────────

CREATE TABLE group_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    created_by UUID NOT NULL REFERENCES users(id),
    title VARCHAR(256) NOT NULL,
    description TEXT,
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    location VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE group_event_rsvps (
    event_id UUID NOT NULL REFERENCES group_events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'GOING',  -- GOING, NOT_GOING, MAYBE
    responded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, user_id)
);

CREATE INDEX idx_group_events_conversation ON group_events(conversation_id, event_time DESC);

-- ─── Communities (Meta-Groups) ───────────────────────────────────────

CREATE TABLE communities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(256) NOT NULL,
    description TEXT,
    avatar_url TEXT,
    created_by UUID NOT NULL REFERENCES users(id),
    announcement_group_id UUID REFERENCES conversations(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE community_groups (
    community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (community_id, conversation_id)
);

CREATE TABLE community_members (
    community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',  -- OWNER, ADMIN, MEMBER
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (community_id, user_id)
);

CREATE INDEX idx_community_members_user ON community_members(user_id);
CREATE INDEX idx_community_groups_community ON community_groups(community_id);

-- ─── Login Approval ──────────────────────────────────────────────────

CREATE TABLE login_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    device_name VARCHAR(256),
    platform VARCHAR(50),
    ip_address VARCHAR(45),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, DENIED, EXPIRED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_login_approvals_pending ON login_approvals(user_id, status)
    WHERE status = 'PENDING';

-- ─── Security Notifications (Key Change Tracking) ───────────────────

ALTER TABLE encryption_keys ADD COLUMN key_version INT NOT NULL DEFAULT 1;
ALTER TABLE encryption_keys ADD COLUMN previous_identity_key TEXT;
ALTER TABLE encryption_keys ADD COLUMN key_changed_at TIMESTAMP WITH TIME ZONE;

-- ─── Group Call Participants ─────────────────────────────────────────

CREATE TABLE group_call_participants (
    call_id VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    left_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (call_id, user_id)
);

ALTER TABLE call_history ADD COLUMN conversation_id UUID REFERENCES conversations(id);
ALTER TABLE call_history ADD COLUMN is_group_call BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE call_history ADD COLUMN participant_count INT NOT NULL DEFAULT 2;

CREATE INDEX idx_group_call_participants_call ON group_call_participants(call_id);

-- ─── Chat Wallpaper Preferences ──────────────────────────────────────

CREATE TABLE chat_wallpapers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    conversation_id UUID REFERENCES conversations(id),  -- NULL = global default
    wallpaper_type VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',  -- DEFAULT, SOLID, CUSTOM
    wallpaper_value TEXT,  -- color hex or media URL
    dark_mode_value TEXT,  -- separate value for dark mode
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(user_id, conversation_id)
);

CREATE INDEX idx_chat_wallpapers_user ON chat_wallpapers(user_id);

-- ─── App Lock / Chat Lock ────────────────────────────────────────────

ALTER TABLE users ADD COLUMN app_lock_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN app_lock_timeout_seconds INT NOT NULL DEFAULT 0;  -- 0 = immediate

ALTER TABLE conversation_members ADD COLUMN locked BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE conversation_members ADD COLUMN locked_at TIMESTAMP WITH TIME ZONE;

-- ─── Read Receipt & Online Status Privacy ────────────────────────────

ALTER TABLE users ADD COLUMN read_receipts_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN online_status_visibility VARCHAR(20) NOT NULL DEFAULT 'everyone';
ALTER TABLE users ADD COLUMN about_visibility VARCHAR(20) NOT NULL DEFAULT 'everyone';

-- ─── Message Scheduling ─────────────────────────────────────────────

ALTER TABLE messages ADD COLUMN scheduled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE messages ADD COLUMN is_scheduled BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_messages_scheduled ON messages(scheduled_at)
    WHERE is_scheduled = true AND scheduled_at IS NOT NULL;

-- ─── Status Audience ────────────────────────────────────────────────

ALTER TABLE statuses ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'everyone';
ALTER TABLE statuses ADD COLUMN excluded_user_ids UUID[] DEFAULT '{}';
ALTER TABLE statuses ADD COLUMN included_user_ids UUID[] DEFAULT '{}';

-- ─── Broadcast Lists ────────────────────────────────────────────────

CREATE TABLE broadcast_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(256) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE broadcast_list_members (
    broadcast_list_id UUID NOT NULL REFERENCES broadcast_lists(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (broadcast_list_id, user_id)
);

CREATE INDEX idx_broadcast_lists_owner ON broadcast_lists(owner_id);

-- ─── Performance indexes for new features ───────────────────────────

CREATE INDEX idx_messages_view_once ON messages(conversation_id)
    WHERE view_once = true AND viewed_at IS NULL;
CREATE INDEX idx_conv_members_locked ON conversation_members(user_id)
    WHERE locked = true;
