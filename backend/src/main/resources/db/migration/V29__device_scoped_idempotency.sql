-- #750 (BE-07, ADR 0013 §3) — a retried send is answered with the original ack, not an error.
--
-- A client that lost its ack and resent the same `clientMessageId` used to be told MSG_DUPLICATE,
-- so it could never learn whether the message had gone through. The server now replays the
-- original ServerAck (same seq, same server timestamp, `replay = true`). The key for "the same
-- send" is the sending device plus the id it chose.
--
-- `messages.id` stays the client message id (it is what edits, deletes, replies and reactions
-- refer to), so `client_message_id` equals `id` for every row. Legacy rows keep
-- `sender_device_id` NULL (the device is not recoverable) and are matched by sender instead.
SET lock_timeout = '5s';

ALTER TABLE messages ADD COLUMN sender_device_id UUID;
ALTER TABLE messages ADD COLUMN client_message_id UUID;

-- Identity rule (data-model.md §3.2): `messages.id = client_message_id` for every row, legacy ones
-- included. §3.2 puts this backfill in a batched job outside the migration; at the current, small
-- row count one statement is the same work with less machinery, and it must
-- be moved out before the table is large enough for the rewrite to matter.
UPDATE messages SET client_message_id = id WHERE client_message_id IS NULL;

-- The unique index is V29.1 (CREATE INDEX CONCURRENTLY, which cannot run in this transaction).
