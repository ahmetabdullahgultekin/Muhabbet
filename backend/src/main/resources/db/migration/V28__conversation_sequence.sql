-- #750 (BE-06 / C13, ADR 0014) — a per-conversation sequence number for every message.
--
-- Messages were ordered by `server_timestamp` alone. Two rows inserted at the same instant tie, a
-- page boundary between them skips one, and no cursor built on a timestamp can tell a client it
-- has missed something. `seq` is a total, gap-detectable order inside one conversation:
--
--   conversations.last_seq  the allocation counter. A send runs
--                           `UPDATE conversations SET last_seq = last_seq + 1 ... RETURNING last_seq`
--                           inside its own transaction, so the row lock serialises allocation and
--                           values become visible in allocation order.
--   messages.seq            the message's position. NULL for a scheduled message until it is
--                           released — it gets its number then, not when it was written.
--   messages.change_seq     the last change to the row (insert, edit, delete). Allocated from the
--                           same counter, so an edit is ordered against every other event.
--
-- Additive only: the old code ignores these columns, and rows it inserts during a rollback window
-- keep `seq IS NULL`.
SET lock_timeout = '5s';

ALTER TABLE conversations ADD COLUMN last_seq BIGINT NOT NULL DEFAULT 0;
ALTER TABLE messages ADD COLUMN seq BIGINT;
ALTER TABLE messages ADD COLUMN change_seq BIGINT;

-- Every allocation is an UPDATE of the conversation row. The V1 trigger fired on any UPDATE, so
-- `updated_at` would have moved on every send, edit and delete and stopped meaning "the
-- conversation's metadata changed" (data-model.md §3.1, decided before V28 ships). It now fires
-- only when a metadata column is in the SET list; `last_seq` is deliberately absent. Redefined
-- before the backfill below so that the backfill does not touch `updated_at` either.
DROP TRIGGER update_conversations_updated_at ON conversations;
CREATE TRIGGER update_conversations_updated_at
    BEFORE UPDATE OF type, name, avatar_url, description, created_by,
                     disappear_after_seconds, announcement_only, max_members
    ON conversations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Backfill existing history, ordered by (server_timestamp, id) so rows that share an instant get a
-- deterministic order instead of an arbitrary one. Unreleased scheduled rows are skipped: they are
-- numbered when they are released. The production table is small (single-digit users), so this
-- runs as one statement; a large table would need the per-conversation, resumable `SeqSweeper`
-- that docs/architecture/multi-transport/data-model.md §3.1 describes instead.
WITH ordered AS (
    SELECT id,
           row_number() OVER (PARTITION BY conversation_id ORDER BY server_timestamp, id) AS n
    FROM messages
    WHERE is_scheduled = FALSE
)
UPDATE messages m
SET seq = o.n,
    change_seq = o.n
FROM ordered o
WHERE m.id = o.id;

UPDATE conversations c
SET last_seq = s.max_seq
FROM (
    SELECT conversation_id, MAX(seq) AS max_seq
    FROM messages
    WHERE seq IS NOT NULL
    GROUP BY conversation_id
) s
WHERE c.id = s.conversation_id;

-- The two unique indexes are V28.1 (CREATE INDEX CONCURRENTLY, which cannot run in this
-- transaction), so the build takes no SHARE lock on `messages` (data-model.md §3.1, rule 6).
--
-- Known deviations from data-model.md §3.1, acceptable only at the current row counts
-- (single-digit users):
--   * the backfill above runs inside this migration instead of in the resumable `SeqSweeper`;
--   * there is no `conversations.seq_ready`. Without the sweeper there is no window in which a new
--     send could be numbered ahead of un-numbered history, which is what the flag guards against.
-- Both must be revisited before the table is large enough for this statement to hold a lock for
-- noticeable time.
