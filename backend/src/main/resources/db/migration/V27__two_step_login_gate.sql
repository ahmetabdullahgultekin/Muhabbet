-- #566 — two-step verification is enforced at sign-in, so the PIN needs an attempt budget.
--
-- A six-digit PIN is a million possibilities. BCrypt makes an *offline* sweep of a stolen hash
-- slow but not impossible, so the control that actually protects the user is the *online* one:
-- a small number of guesses, then a lock. Without it a second factor is a speed bump.
--
-- Its own table rather than columns on `users`, deliberately. `UserJpaEntity.fromDomain` rebuilds
-- the whole row from the domain model on every save, so a counter living there would be silently
-- reset by any unrelated profile write — a rate limit that resets when the attacker edits their
-- own display name is not a rate limit. Nothing else maps this table, so only the two statements
-- in SpringDataTwoStepAttemptRepository can touch it.
CREATE TABLE two_step_attempts (
    user_id         UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    -- NULL means "not locked". A lock in the past is an expired lock and the next claim clears it.
    locked_until    TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- The recovery-email column stays, and stays unused for now.
--
-- `POST /api/v1/auth/two-step/reset` compared an address the caller supplied against the stored one
-- and cleared the PIN — a verification of nothing, and a PIN reset for anyone who could guess the
-- address. It is removed in this change rather than left as a bypass of the gate the same change
-- introduces. The column is kept because a real mail round-trip is the intended recovery flow and
-- it will want an address on file; dropping and re-adding it would be churn for no gain.
COMMENT ON COLUMN users.two_step_email IS
    'Recovery address. No flow reads it yet — the fake email reset was removed in #566.';
