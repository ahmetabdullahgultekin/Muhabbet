-- Add signed pre-key signature to encryption_keys table.
-- Required for Signal Protocol session initialization (X3DH):
-- the recipient's client must verify the signed pre-key signature
-- before building a PreKeyBundle, preventing invalid sessions.
--
-- Nullable: existing rows do not have a valid signature.
-- Clients will be required to re-register their key bundle on next login,
-- which will populate this column with the correct signature.
ALTER TABLE encryption_keys
    ADD COLUMN IF NOT EXISTS signed_pre_key_signature TEXT;
