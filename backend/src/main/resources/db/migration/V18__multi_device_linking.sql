-- V18: Multi-device linked sessions — NON-CRYPTO data model (Tier 2, slice S1)
--
-- Companion-device registry + QR link-session handshake. This migration is ADDITIVE and
-- reversible-by-design: it introduces NEW tables only and adds NULLABLE columns to the
-- existing `devices` table (no destructive change, no default behaviour change). The whole
-- feature is gated behind `muhabbet.multi-device.enabled` (default OFF), so with the flag off
-- nothing reads or writes these tables and the single-device path is byte-identical to today.
--
-- Crypto (per-device Signal identity transfer / X3DH-on-link) is intentionally NOT part of this
-- migration — see DeviceLinkCrypto / SessionTransfer interfaces (NotYetImplemented) and the
-- design doc docs/design/T2-multi-device-linked-sessions.md. No key material is stored here.

-- ─── Companion-device columns on the existing devices table ──────────────────────────────
-- Additive, all NULLable. `linked_by_device_id` records which primary device approved this
-- companion; `display_name`/`last_active_at` power the "Linked devices" management screen;
-- `revoked_at` soft-tombstones a companion (forward-secrecy hook lives in the crypto slice).
ALTER TABLE devices ADD COLUMN linked_by_device_id UUID REFERENCES devices(id);
ALTER TABLE devices ADD COLUMN display_name        VARCHAR(128);
ALTER TABLE devices ADD COLUMN revoked_at          TIMESTAMPTZ;

CREATE INDEX idx_devices_linked_by ON devices(linked_by_device_id) WHERE linked_by_device_id IS NOT NULL;
CREATE INDEX idx_devices_active    ON devices(user_id) WHERE revoked_at IS NULL;

-- ─── Device-link sessions (the QR pairing handshake) ─────────────────────────────────────
-- A primary device opens a link session and renders its `link_token` in a QR code. A companion
-- scans it and completes the handshake. Tokens are single-use and short-lived. This table holds
-- ONLY the handshake state machine — never any private key material. The `public_bundle` column
-- carries the companion's PUBLIC prekey bundle (opaque blob) for the future crypto slice to
-- consume; it is nullable and unused while the crypto boundary is NotYetImplemented.
CREATE TABLE device_link_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    primary_device_id UUID NOT NULL REFERENCES devices(id),
    link_token      VARCHAR(64) NOT NULL UNIQUE,           -- random, embedded in the QR
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',-- PENDING | COMPLETED | EXPIRED | CANCELLED
    companion_platform   VARCHAR(16),                      -- web | desktop | android | ios (set on complete)
    companion_device_name VARCHAR(128),
    public_bundle   TEXT,                                  -- companion PUBLIC prekey bundle (opaque; crypto slice)
    linked_device_id UUID REFERENCES devices(id),          -- the device row created on completion
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_link_sessions_token  ON device_link_sessions(link_token) WHERE status = 'PENDING';
CREATE INDEX idx_link_sessions_user   ON device_link_sessions(user_id);
