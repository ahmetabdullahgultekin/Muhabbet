-- #884 — device-link tokens are stored as SHA-256 hashes, never in the clear.
--
-- A link token is a bearer credential for 120 seconds: whoever presents it gets a device on the
-- account. Refresh tokens have always been stored hashed; link tokens were not, so a read of this
-- table leaked every live one.
--
-- EXPAND: add `token_hash`, backfill it, and stop requiring `link_token`. The application now
-- writes and reads only `token_hash`. The plain column is cleared here rather than kept "for
-- rollback": a rolled-back build reading it would only see tokens that expire within two minutes
-- anyway, and keeping live secrets around to make that window work is the wrong trade.
-- CONTRACT (a later migration): drop `link_token` once no deployed build references it.

ALTER TABLE device_link_sessions ADD COLUMN token_hash VARCHAR(64);

UPDATE device_link_sessions
SET token_hash = encode(sha256(convert_to(link_token, 'UTF8')), 'hex')
WHERE link_token IS NOT NULL;

ALTER TABLE device_link_sessions ALTER COLUMN token_hash SET NOT NULL;
ALTER TABLE device_link_sessions ADD CONSTRAINT uq_device_link_sessions_token_hash UNIQUE (token_hash);

ALTER TABLE device_link_sessions ALTER COLUMN link_token DROP NOT NULL;
UPDATE device_link_sessions SET link_token = NULL;

-- The old partial index keyed lookups on the plaintext column; the unique constraint above now
-- serves lookups by hash.
DROP INDEX IF EXISTS idx_link_sessions_token;
