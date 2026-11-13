-- Indexes for V28's columns, built without blocking writes (#880, #882).
--
-- CREATE INDEX CONCURRENTLY cannot run inside a transaction block. Flyway's PostgreSQL parser
-- recognises the statement and executes this migration non-transactionally, which is why these live
-- apart from V28 (Flyway refuses a migration that mixes transactional and non-transactional
-- statements). IF NOT EXISTS makes a retry after an interrupted build safe; an interrupted
-- CONCURRENTLY build leaves an INVALID index that must be dropped by hand before re-running.

-- Family revocation: "revoke every token of family F" must not scan the table.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refresh_tokens_family
    ON refresh_tokens (family_id);

-- One *active* device row per installation per account. Partial twice over: rows written before
-- clients sent an installation id (NULL) are not forced into a uniqueness they cannot satisfy, and a
-- revoked row keeps its installation id as history without blocking the new row that the same
-- installation gets when it signs in again (a revoked device is never resurrected).
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_devices_user_installation
    ON devices (user_id, installation_id)
    WHERE installation_id IS NOT NULL AND revoked_at IS NULL;
