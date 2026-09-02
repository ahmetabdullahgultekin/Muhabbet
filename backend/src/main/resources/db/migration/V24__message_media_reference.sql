-- #541 — a burned view-once photo was still fetchable for seven days.
--
-- `messages.media_url` holds a *presigned* URL: no credential is needed to fetch it, the URL is the
-- credential, and it was minted with a seven-day expiry. So burning a view-once message hid the
-- photo from every screen and left the bytes in the bucket, reachable by anyone who had kept the
-- string, for the rest of that week. "View once" was a convention of the UI rather than a property
-- of the data.
--
-- Deleting the object on burn needs an object to delete, and the URL cannot supply one: parsing a
-- key out of `media_url` would derive a deletion decision from a client-supplied string, which is
-- the class of mistake #267 exists for and which, done wrong here, deletes someone else's file. So
-- the message now carries a reference to the `media_files` row — a server-owned record — instead.
--
-- The value in this column is what the sending client asserted, and it is worth nothing on its own.
-- The burn path confirms the object really was that message's sender's upload immediately before it
-- deletes anything; the check lives next to the consequence, where it cannot be bypassed by a
-- message written some other way. Any future reader of this column owes the same check.
--
-- Nullable and additive on purpose. Every message written before this migration has no reference —
-- there is nothing to derive one from — so those rows keep the old behaviour and say so, rather
-- than being guessed at. No foreign key to `media_files`: purging a blob deletes that row while the
-- message stays, and a constraint would force a cascade that deleted the conversation history along
-- with the photo.
ALTER TABLE messages ADD COLUMN media_id UUID;

-- Partial: only view-once and other media messages ever carry one, and the burn path is the only
-- reader. Indexing the nulls would be most of the table for no query.
CREATE INDEX IF NOT EXISTS idx_messages_media_id ON messages (media_id) WHERE media_id IS NOT NULL;
