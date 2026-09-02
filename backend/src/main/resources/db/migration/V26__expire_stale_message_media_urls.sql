-- #679 — retire the media URLs that were written before anyone checked where they pointed.
--
-- Until this release `messages.media_url` was whatever string the sending client put in the
-- `message.send` frame. The recipient's app fetches it to draw the bubble, with no tap and no
-- confirmation, so a sender could aim every recipient's phone at an address of their choosing:
-- their IP in someone else's log, and the exact moment they opened the chat, which is a read
-- receipt taken around the back of the setting that governs read receipts.
--
-- The send path now refuses a URL that is not ours. That fixes every message written from here on
-- and does nothing at all for the rows already in this table — validation at write time cannot
-- reach back through a column, and a planted URL keeps working for as long as the attacker's
-- server answers, which is forever.
--
-- The cut is by age, not by host, and that is the point:
--
--   * Every URL this application has ever written to this column is a presigned MinIO URL. They are
--     signed with an expiry, and the expiry is seven days — `MediaStoragePort.getPresignedUrl`
--     takes `expirySeconds` with a default of 604800 and every caller uses the default. Seven days
--     after it was written, one of our URLs is a 403 from MinIO. It draws a broken image and
--     nothing else.
--   * A URL still alive after seven days is therefore, by construction, not one of ours. It is
--     either a foreign address someone planted or a string that never worked at all.
--
-- So blanking `media_url` and `thumbnail_url` on messages older than that window removes exactly the
-- rows that can still cause a fetch and removes nothing that was still rendering. It needs no
-- knowledge of which host this deployment publishes on, which matters because that host differs per
-- environment and is not available to a migration — and a migration that guessed it wrong would
-- either blank every photo or none.
--
-- What survives: the message, its sender, its timestamp, its `content_type` and (since V24) its
-- `media_id`. The bubble still says a photo was sent, in the recipient's own language since V25,
-- and a row that carries a `media_id` can be re-signed on demand the day the refresh path in #719
-- exists. Nothing here is user-authored text.
--
-- Statuses are deliberately not touched. `statuses.media_url` has the same shape, but a status is
-- served only while `expires_at` is in the future — twenty-four hours — so every status old enough
-- to matter here is already unreachable through the API.
UPDATE messages
SET media_url = NULL,
    thumbnail_url = NULL
WHERE server_timestamp < NOW() - INTERVAL '7 days'
  AND (media_url IS NOT NULL OR thumbnail_url IS NOT NULL);
