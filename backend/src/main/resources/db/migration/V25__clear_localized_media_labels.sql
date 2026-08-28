-- #534 — remove the localized media labels the app stored as message bodies.
--
-- Until this release the client resolved "Photo" / "Fotoğraf" as a UI string and then sent it as
-- the message *content* for photos, voice notes, GIFs and stickers. The list preview is built from
-- that body, so every such conversation shows a label frozen in whichever language the sender's
-- phone happened to be set to at the moment of sending — wrong for the recipient from the start,
-- and unfixable from the reading end because it was never the reader's string.
--
-- The client now renders the label from `content_type` instead, which fixes the preview for these
-- historical rows without touching them. This migration exists for the other half: the chat bubble.
-- Its caption test is now "is there a caption", so a row that still holds the word "Photo" would
-- draw "Photo" as a caption under every old picture. Blanking the body is what makes history read
-- the way new messages do.
--
-- Why this is safe to blank rather than translate or keep:
--   * These four content types have no caption UI, in this version or any earlier one. The body was
--     written by the app, never by a person, so nothing here is user-authored text.
--   * The values below are the complete set the app has ever written. `git log -S` on
--     `values/strings.xml` shows one commit introducing these string resources and no edit since,
--     and the pre-i18n literals in ChatScreen were the same two Turkish words. Anything outside the
--     list is therefore not one of ours and is left alone.
--   * Nothing else reads these bodies for meaning. Push notification text already ignores `content`
--     for non-TEXT messages and composes a summary in the *recipient's* locale
--     (PushNotificationComposer.body) — the behaviour this change brings to the rest of the app.
--     Starred messages already fall back on a blank body. Full-text search stops matching photos on
--     the word "photo", which is a correction rather than a loss.
--
-- Deliberately scoped by content_type as well as by value: a text message whose entire body is the
-- word "GIF" is a real thing somebody typed, and it keeps it.
UPDATE messages
SET content = ''
WHERE content_type IN ('IMAGE', 'VOICE', 'GIF', 'STICKER')
  AND content IN (
      'Fotoğraf', 'Photo',
      'Sesli mesaj', 'Voice message',
      'GIF',
      'Çıkartma', 'Sticker'
  );
