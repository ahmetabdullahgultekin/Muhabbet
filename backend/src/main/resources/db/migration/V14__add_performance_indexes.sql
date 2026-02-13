-- =====================================================
-- Muhabbet — V14: Performance Indexes
-- Addresses N+1 query patterns and missing indexes
-- =====================================================

-- Index for delivery status lookups by user (used in countUnread, markAllAsRead)
CREATE INDEX idx_delivery_status_user_message
    ON message_delivery_status(user_id, message_id, status);

-- Index for message sender lookups (used in unread count subqueries)
CREATE INDEX idx_messages_sender
    ON messages(sender_id)
    WHERE is_deleted = FALSE;

-- Index for media queries by conversation + content type
CREATE INDEX idx_messages_conv_content_type
    ON messages(conversation_id, content_type, server_timestamp DESC)
    WHERE is_deleted = FALSE;

-- Index for media files by uploader (used in storage usage queries)
CREATE INDEX idx_media_uploader
    ON media_files(uploader_id, content_type);

-- Index for batch last-message queries (conversation inbox optimization)
CREATE INDEX idx_messages_conv_latest_covering
    ON messages(conversation_id, server_timestamp DESC, sender_id, content_type, content, media_url, thumbnail_url)
    WHERE is_deleted = FALSE;

-- Index for expired messages cleanup job
CREATE INDEX idx_messages_expires_at
    ON messages(expires_at)
    WHERE expires_at IS NOT NULL AND is_deleted = FALSE;

-- Trigram index for efficient LIKE-based message search (replaces full table scan)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_messages_content_trgm
    ON messages USING GIN (content gin_trgm_ops)
    WHERE is_deleted = FALSE;
