-- #920, #750 — one history index on `messages` instead of three, with the keyset's tie-break in it,
-- and the redundant indexes elsewhere dropped.
--
-- Three indexes led with the same (conversation_id, server_timestamp DESC) under the same
-- `WHERE is_deleted = FALSE` predicate:
--   idx_messages_conversation_time     (V1)  — the key alone
--   idx_messages_conversation_latest   (V1)  — the key + INCLUDE (sender_id, content_type, content)
--   idx_messages_conv_latest_covering  (V14) — the key + five more KEY columns, content among them
-- Every insert, soft delete and edit maintained all three, two of them carrying a second and third
-- copy of the message text. None of them could serve the history keyset either: paging is now on
-- (server_timestamp, id), and only an index that ends in `id` turns the row comparison into a
-- single index condition. The replacement is that index; the queries it serves are pinned by
-- EXPLAIN in MessageHistoryIndexIntegrationTest.
--
-- idx_bots_api_token duplicates bots_api_token_key, the index the column's UNIQUE constraint
-- already created (V15). Same key, same table; one of them was pure write cost.
--
-- The other eight are the same mistake in its commonest form, found by the guard this change adds
-- (MessageHistoryIndexIntegrationTest, "no index made redundant by another"): a single-column
-- index on the leading column of a UNIQUE constraint or primary key on the same table. A b-tree on
-- (a, b) answers every lookup on a alone, including the foreign-key checks, so each of these only
-- doubled the write cost of its table:
--   idx_devices_user                    ← devices UNIQUE (user_id, push_token)
--   idx_poll_votes_message              ← poll_votes UNIQUE (message_id, user_id)
--   idx_reactions_message               ← message_reactions UNIQUE (message_id, user_id, emoji)
--   idx_user_blocks_blocker             ← user_blocks UNIQUE (blocker_id, blocked_id)
--   idx_channel_subs_channel            ← channel_subscriptions UNIQUE (channel_id, subscriber_id)
--   idx_community_groups_community      ← community_groups PRIMARY KEY (community_id, ...)
--   idx_group_call_participants_call    ← group_call_participants PRIMARY KEY (call_id, ...)
--   idx_chat_wallpapers_user            ← chat_wallpapers UNIQUE (user_id, conversation_id)
--
-- Online-safe, per docs/adr/0015-online-safe-migrations.md:
--   * CONCURRENTLY, so neither the build nor the drops block message inserts. That needs
--     executeInTransaction=false (the .sql.conf beside this file) and
--     spring.flyway.postgresql.transactional-lock=false (application.yml), without which Flyway
--     holds a transaction open for its advisory lock and CREATE INDEX CONCURRENTLY waits on it
--     forever.
--   * Every statement is idempotent, because outside a transaction a failure leaves the statements
--     before it applied and Flyway re-runs the whole script. The new index is dropped before it is
--     built: an interrupted CONCURRENTLY build leaves an INVALID index behind, and
--     `CREATE ... IF NOT EXISTS` would then skip it and leave history on an index the planner
--     ignores.
--   * The new index is built before any old one goes, so history is never left unindexed.
--   * lock_timeout bounds the wait for each lock; a busy table fails the deploy fast instead of
--     queueing every writer behind it.

SET lock_timeout = '5s';

DROP INDEX CONCURRENTLY IF EXISTS idx_messages_conv_history;

CREATE INDEX CONCURRENTLY idx_messages_conv_history
    ON messages (conversation_id, server_timestamp DESC, id DESC)
    WHERE is_deleted = FALSE;

DROP INDEX CONCURRENTLY IF EXISTS idx_messages_conversation_time;
DROP INDEX CONCURRENTLY IF EXISTS idx_messages_conversation_latest;
DROP INDEX CONCURRENTLY IF EXISTS idx_messages_conv_latest_covering;
DROP INDEX CONCURRENTLY IF EXISTS idx_bots_api_token;

DROP INDEX CONCURRENTLY IF EXISTS idx_devices_user;
DROP INDEX CONCURRENTLY IF EXISTS idx_poll_votes_message;
DROP INDEX CONCURRENTLY IF EXISTS idx_reactions_message;
DROP INDEX CONCURRENTLY IF EXISTS idx_user_blocks_blocker;
DROP INDEX CONCURRENTLY IF EXISTS idx_channel_subs_channel;
DROP INDEX CONCURRENTLY IF EXISTS idx_community_groups_community;
DROP INDEX CONCURRENTLY IF EXISTS idx_group_call_participants_call;
DROP INDEX CONCURRENTLY IF EXISTS idx_chat_wallpapers_user;

RESET lock_timeout;
