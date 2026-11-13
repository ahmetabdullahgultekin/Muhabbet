-- #897 / #878 — the database holds only the values each lifecycle can produce.
--
-- `call_history.status`, `call_history.call_type` and `message_backups.status` were free strings
-- written from string literals. The domain now writes enums (`CallOutcome`, `CallType`,
-- `BackupStatus`); these CHECKs make any other value a write error instead of a row a client
-- cannot read. Stray legacy values are folded first so the constraints can be added.

-- A call is recorded once, when it ends: the in-flight statuses V12 listed were never final.
UPDATE call_history SET status = 'ENDED'  WHERE status = 'ANSWERED';
UPDATE call_history SET status = 'MISSED' WHERE status = 'INITIATED';

ALTER TABLE call_history
    ADD CONSTRAINT ck_call_history_status CHECK (status IN ('ENDED', 'DECLINED', 'MISSED')),
    ADD CONSTRAINT ck_call_history_call_type CHECK (call_type IN ('VOICE', 'VIDEO'));

-- V15 mentioned IN_PROGRESS; nothing ever wrote it. EXPIRED is derived from expires_at, never stored.
UPDATE message_backups SET status = 'PENDING' WHERE status = 'IN_PROGRESS';

ALTER TABLE message_backups
    ADD CONSTRAINT ck_message_backups_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'));
