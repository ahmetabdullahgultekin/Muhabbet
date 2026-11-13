-- #908 — a push token belongs to exactly one device. Step 2 of 2: enforce it.
--
-- V28 left at most one holder per token; this makes the database refuse a second one, whatever
-- code path writes the column next. Application code moves a token between rows through
-- DeviceRepository.detachPushTokenFromOtherDevices, which serialises on the token, so a correct
-- caller never hits this index — it exists for the caller that is not.
--
-- Partial (NOT NULL): most rows have no token, and NULLs are distinct under a unique index anyway,
-- so indexing them would only cost space. The index also serves the two lookups by token (detach on
-- registration, clear on a provider rejection).
--
-- CONCURRENTLY, so building it does not block writes to `devices`, which every sign-in performs.
-- That makes this a non-transactional migration, which is why it is alone in its file. The DROP
-- first makes a retry safe: a concurrent build that fails leaves an INVALID index behind under this
-- name, and IF NOT EXISTS would then silently keep the invalid one.
--
-- The old UNIQUE(user_id, push_token) from V1 is now implied by this one and is left in place
-- (expand before contract); dropping it is a separate, later change.

DROP INDEX CONCURRENTLY IF EXISTS uq_devices_push_token;

CREATE UNIQUE INDEX CONCURRENTLY uq_devices_push_token
    ON devices (push_token)
    WHERE push_token IS NOT NULL;
