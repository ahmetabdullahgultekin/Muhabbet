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

-- A double allocation is a constraint violation, not silent corruption. A plain unique index
-- already admits any number of NULLs, so unreleased scheduled rows need no partial predicate.
CREATE UNIQUE INDEX ux_messages_conv_seq ON messages (conversation_id, seq);
CREATE UNIQUE INDEX ux_messages_conv_change_seq ON messages (conversation_id, change_seq);
