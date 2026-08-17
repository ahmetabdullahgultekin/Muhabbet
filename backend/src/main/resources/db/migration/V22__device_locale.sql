-- =====================================================
-- Muhabbet — V22: the language a device wants its push notifications in
-- =====================================================
--
-- Push text is the one thing this service writes for a human to read, and it is written on the
-- server, where nothing knows what language the reader speaks. #469 shipped "Yeni mesaj" to every
-- device on earth; #476 moved the wording into resource bundles and gave PushNotificationComposer
-- a recipientLocale parameter — and then every caller passed null, because there was nowhere to
-- read an answer from. notification-messages_en.properties has been in the tree since, unreachable.
-- This column is the answer.
--
-- On the device row rather than on the user row on purpose: the language belongs to the phone that
-- will draw the notification, not to the account. A user with a Turkish phone and an English tablet
-- is two rows here and gets two languages, which is right; on the user row they would be one row
-- and one language, which is not.
--
-- NULL means "never told us", and is what every existing row and every older client stays. The
-- composer falls back to Turkish for null, which is byte-identical to the behaviour before this
-- migration — no row changes meaning, nothing is backfilled, and a client that never sends a locale
-- is not made worse off.
--
-- VARCHAR(16) holds any BCP-47 tag this could reasonably carry ("tr", "en", "en-GB",
-- "sr-Latn-RS" is 10). The value is client-supplied, so AuthService normalises it through
-- java.util.Locale before it reaches this column and rejects anything that is not a language tag;
-- the width is the second fence, not the first.
--
-- No index: this column is only ever read alongside push_token on a row already fetched by
-- user_id, which idx_devices_user already serves.

ALTER TABLE devices ADD COLUMN locale VARCHAR(16);

COMMENT ON COLUMN devices.locale IS
    'BCP-47 language tag this device wants server-generated push text in. NULL = unknown, falls back to Turkish.';
