-- Auth session hardening — EXPAND step (#880, #923, #882).
--
-- Every statement here is online-safe on PostgreSQL 16 against a live backend:
--   * ADD COLUMN without a default, or with a constant default, is a catalogue-only change (no table
--     rewrite, no scan); it takes ACCESS EXCLUSIVE only for the instant of the catalogue update.
--   * The CHECK constraint is added NOT VALID, so it binds every new or updated row immediately but
--     does not scan the existing ones. A later contract migration VALIDATEs it (SHARE UPDATE
--     EXCLUSIVE — readers and writers keep going).
--   * The two backfill UPDATEs touch small auth tables (one row per sign-in / per device).
-- The indexes that serve the new lookups are built CONCURRENTLY in V29, which Flyway must run
-- outside a transaction and so cannot share this file.
--
-- Contract step (a later V number, once every running instance writes the new columns):
--   ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;
--   ALTER TABLE devices VALIDATE CONSTRAINT ck_devices_platform;

-- ─── Refresh-token families (#880) ───────────────────────────────────────────────────────────
-- A family is one sign-in on one device: the first token and every token rotated from it. Reuse of
-- any member that has already been rotated revokes the whole family (OAuth 2.0 Security BCP 4.14).
ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
-- Why the token was retired. Set together with revoked_at when — and only when — the token was
-- exchanged for a successor, so a presented token can be told apart: rotated (someone replayed it)
-- versus revoked (logout, device revoke, account erasure).
ALTER TABLE refresh_tokens ADD COLUMN rotated_at TIMESTAMPTZ;

-- Every pre-existing token becomes the root of its own family. Rows written by the new code always
-- carry a family id; the domain also reads a NULL as "its own id", so an old instance still writing
-- during a rolling deploy cannot produce a token outside any family.
UPDATE refresh_tokens SET family_id = id WHERE family_id IS NULL;

-- ─── Access-token epoch per device (#923) ────────────────────────────────────────────────────
-- Stamped into every access token as the `tv` claim and compared on every authenticated REST
-- request. Logout, device revoke and refresh-token reuse increment it, which invalidates every
-- access token already issued to that device at once instead of after its 15-minute lifetime.
ALTER TABLE devices ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

-- ─── Stable device identity (#882) ───────────────────────────────────────────────────────────
-- A client-generated id for one installation of the app. A device used to be found by
-- (user_id, platform), so a second Android phone silently took over the first one's row, push
-- token and sessions. NULL for rows written before the client sent one.
ALTER TABLE devices ADD COLUMN installation_id UUID;

-- The domain now types the platform as an enum. Every client ever shipped sent lowercase, but the
-- column has never been constrained, so normalise before the constraint starts binding writes.
UPDATE devices SET platform = lower(btrim(platform)) WHERE platform <> lower(btrim(platform));

ALTER TABLE devices
    ADD CONSTRAINT ck_devices_platform CHECK (platform IN ('android', 'ios', 'web')) NOT VALID;
