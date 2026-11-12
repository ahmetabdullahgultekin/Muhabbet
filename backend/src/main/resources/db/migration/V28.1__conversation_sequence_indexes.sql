-- #750 (ADR 0014) — the uniqueness half of V28, split out per data-model.md §3.1 / rule 6.
--
-- A double allocation must be a constraint violation, not silent corruption. CONCURRENTLY builds
-- without the SHARE lock a plain CREATE INDEX holds on `messages` for the whole build, so sends are
-- not blocked while it runs. It cannot run inside a transaction; Flyway sees the CONCURRENTLY and
-- runs this file non-transactionally, which is why it holds nothing else.
--
-- A plain unique index already admits any number of NULLs, so unreleased scheduled rows (seq NULL)
-- need no partial predicate.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_messages_conv_seq ON messages (conversation_id, seq);
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_messages_conv_change_seq ON messages (conversation_id, change_seq);
