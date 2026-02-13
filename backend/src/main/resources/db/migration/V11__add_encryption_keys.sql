-- E2E encryption key storage (Signal Protocol key bundles)
CREATE TABLE encryption_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    identity_key TEXT NOT NULL,
    signed_pre_key TEXT NOT NULL,
    signed_pre_key_id INT NOT NULL,
    registration_id INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE one_time_pre_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    key_id INT NOT NULL,
    public_key TEXT NOT NULL,
    used BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX idx_encryption_keys_user ON encryption_keys(user_id);
CREATE INDEX idx_otpk_user_unused ON one_time_pre_keys(user_id, used) WHERE used = false;
