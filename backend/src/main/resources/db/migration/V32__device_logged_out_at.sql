-- #747 — a logout must make the access token the device already holds unusable, not only its
-- refresh tokens. The token is valid for its whole lifetime (15 min) and nothing else refuses it:
-- after a logout the WebSocket was closed and the client simply reconnected with the same token.
--
-- The REST filter and the WebSocket handshake refuse a token whose `iat` is at or before this
-- instant. A later sign-in on the same device issues tokens with a later `iat`, which pass.
--
-- Deliberately not mapped on DeviceJpaEntity: that entity is rebuilt from the domain model on every
-- save, so a mapped field would be reset by any unrelated device write. Unmapped, no save touches it. Only SessionStatusPersistenceAdapter reads and writes it.
ALTER TABLE devices ADD COLUMN logged_out_at TIMESTAMPTZ;
