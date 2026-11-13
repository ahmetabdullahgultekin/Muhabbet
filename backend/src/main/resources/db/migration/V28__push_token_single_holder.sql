-- #908 — a push token belongs to exactly one device. Step 1 of 2: make the data obey the rule.
--
-- A push token addresses one app install on one phone, but `devices` only ever had
-- UNIQUE(user_id, push_token): unique per account, not globally. Signing in as a second account on
-- a phone left the token on the first account's row as well, so the first account's notifications
-- — sender and message preview — kept arriving on a phone someone else was now holding. Logout and
-- companion revocation never cleared the token either.
--
-- V29 adds the global unique index. It is built CONCURRENTLY so the table is not locked against
-- writes while it builds, and CONCURRENTLY cannot run inside a transaction, so the clean-up that
-- must precede it lives here, in an ordinary transactional migration. Splitting them also means a
-- failed index build cannot leave this half applied.
--
-- Nothing here deletes a device row. A token is only ever *unset*, and an unset token is what every
-- device had before it first registered one; the app registers again on its next sign-in or token
-- rotation.

-- An empty or whitespace-only token was never deliverable, and the index would treat every one of
-- them as the same value.
UPDATE devices
SET push_token = NULL
WHERE push_token IS NOT NULL
  AND btrim(push_token) = '';

-- A revoked companion is out of the account's device set; its token is not a delivery address.
UPDATE devices
SET push_token = NULL
WHERE push_token IS NOT NULL
  AND revoked_at IS NOT NULL;

-- One holder per token: keep the device most recently active, since that is the account signed in
-- on the phone now. Ties fall back to the newest row, then to the id so the result is deterministic.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY push_token
               ORDER BY last_active_at DESC NULLS LAST, created_at DESC, id
           ) AS holder_rank
    FROM devices
    WHERE push_token IS NOT NULL
)
UPDATE devices d
SET push_token = NULL
FROM ranked r
WHERE d.id = r.id
  AND r.holder_rank > 1;
