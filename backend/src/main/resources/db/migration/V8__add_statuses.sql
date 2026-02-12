-- Status/Stories: 24-hour ephemeral content

CREATE TABLE statuses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    content     TEXT,
    media_url   TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '24 hours'
);

CREATE INDEX idx_statuses_user ON statuses(user_id);
CREATE INDEX idx_statuses_expires ON statuses(expires_at);
