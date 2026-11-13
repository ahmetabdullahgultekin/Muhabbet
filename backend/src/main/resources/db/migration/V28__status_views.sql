-- #787 — who has seen which status, so the Updates tab can tell new statuses from seen ones.
--
-- Additive only (expand): a new table, nothing existing changes. Until this existed the client had
-- no way to record a view and no way to read one, so every contact drew the same "unseen" ring
-- forever.
--
-- Least data that does the job: which status, which viewer, when. One row per (status, viewer) —
-- the unique constraint is what makes `POST /statuses/{id}/views` idempotent, so a viewer that
-- re-opens a story, or a retry after a dropped response, never adds a second row.
--
-- Lifetime follows the status. Expired statuses are purged hourly by StatusCleanupJob with a bulk
-- DELETE, and ON DELETE CASCADE takes their views with them, so a view record lives at most as long
-- as the status it is about (24 h plus one cleanup interval). Account erasure also deletes the
-- viewer's rows immediately (UserDataQueryPersistenceAdapter.ERASURE_STATEMENTS).
CREATE TABLE status_views (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status_id  UUID NOT NULL REFERENCES statuses(id) ON DELETE CASCADE,
    viewer_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_status_views_status_viewer UNIQUE (status_id, viewer_id)
);

-- The read is "which of these statuses has this viewer seen", once per Updates tab load.
CREATE INDEX idx_status_views_viewer ON status_views(viewer_id);
