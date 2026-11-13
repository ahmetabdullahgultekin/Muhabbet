-- #891 — abuse reports get a lifecycle and keep their evidence.
--
-- EXPAND only: new nullable columns and a CHECK; nothing existing is dropped or renamed.

-- 1. The evidence snapshot. A message report copies the message at report time so the sender
--    deleting it (soft delete, or erasing their account) cannot take the evidence with it.
--    `evidence_message_id` deliberately has no foreign key: `reported_message_id` does, and a row
--    whose evidence depended on the message still existing would defeat the point.
ALTER TABLE user_reports
    ADD COLUMN evidence_message_id      UUID,
    ADD COLUMN evidence_conversation_id UUID,
    ADD COLUMN evidence_sender_id       UUID,
    ADD COLUMN evidence_content_type    VARCHAR(20),
    ADD COLUMN evidence_content         TEXT,
    ADD COLUMN evidence_media_url       TEXT,
    ADD COLUMN evidence_sent_at         TIMESTAMP WITH TIME ZONE;

-- 2. The status set is now the lifecycle's: PENDING -> RESOLVED | DISMISSED. REVIEWED was declared
--    in V15 but nothing ever wrote it; fold any stray row into RESOLVED before constraining.
UPDATE user_reports SET status = 'RESOLVED' WHERE status = 'REVIEWED';

ALTER TABLE user_reports
    ADD CONSTRAINT ck_user_reports_status CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED'));

-- 3. The repeat-report lookup (reporter, target, PENDING) — one per target kind is enough at this
--    volume; the reporter index from V15 already narrows it.
CREATE INDEX idx_user_reports_reporter_pending
    ON user_reports (reporter_id, reported_message_id, reported_conversation_id, reported_user_id)
    WHERE status = 'PENDING';
