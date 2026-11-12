-- #750 (BE-07, ADR 0013 §3) — a retried send is answered with the original ack, not an error.
--
-- A client that lost its ack and resent the same `clientMessageId` used to be told MSG_DUPLICATE,
-- so it could never learn whether the message had gone through. The server now replays the
-- original ServerAck (same seq, same server timestamp, `replay = true`). The key for "the same
-- send" is the sending device plus the id it chose.
--
-- `messages.id` stays the client message id (it is what edits, deletes, replies and reactions
-- refer to), so `client_message_id` equals `id` for every row written from here on. Legacy rows
-- keep `sender_device_id` NULL and are matched by sender instead.
SET lock_timeout = '5s';

ALTER TABLE messages ADD COLUMN sender_device_id UUID;
ALTER TABLE messages ADD COLUMN client_message_id UUID;

CREATE UNIQUE INDEX ux_messages_sender_device_client_id
    ON messages (sender_device_id, client_message_id)
    WHERE sender_device_id IS NOT NULL;
