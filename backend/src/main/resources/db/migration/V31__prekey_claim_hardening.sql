-- #749: one-time prekeys are now claimed by a single
--   UPDATE ... WHERE id = (SELECT ... WHERE used = false ORDER BY created_at, id
--                          LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING *
-- statement. Two things in V11 work against it.
--
-- 1. `used` was nullable (DEFAULT false, but no NOT NULL). A NULL row is neither used nor
--    `used = false`, so the claim would never hand it out and the unused count would never see it.
--    Backfill, then forbid it. NULL can only mean "never claimed": every claim path sets true.
UPDATE one_time_pre_keys SET used = false WHERE used IS NULL;
ALTER TABLE one_time_pre_keys ALTER COLUMN used SET NOT NULL;
ALTER TABLE one_time_pre_keys ALTER COLUMN used SET DEFAULT false;

-- 2. The partial index covered (user_id, used); the claim orders by created_at. Index exactly the
--    rows the claim and the unused count read, in the order the claim takes them.
DROP INDEX IF EXISTS idx_otpk_user_unused;
CREATE INDEX idx_otpk_user_unused_created ON one_time_pre_keys (user_id, created_at, id) WHERE used = false;
