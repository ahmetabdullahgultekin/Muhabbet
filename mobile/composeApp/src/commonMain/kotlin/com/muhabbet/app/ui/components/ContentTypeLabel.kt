package com.muhabbet.app.ui.components

import androidx.compose.runtime.Composable
import com.muhabbet.shared.model.ContentType
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.attach_gif
import com.muhabbet.composeapp.generated.resources.attach_location
import com.muhabbet.composeapp.generated.resources.attach_poll
import com.muhabbet.composeapp.generated.resources.attach_sticker
import com.muhabbet.composeapp.generated.resources.chat_photo
import com.muhabbet.composeapp.generated.resources.chat_video
import com.muhabbet.composeapp.generated.resources.chat_voice_message
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Which string names a message that has no words of its own — or null when the message speaks for
 * itself.
 *
 * The one place this decision is made. It used to be made in three: `StarredMessagesScreen` had its
 * own `when`, and the conversation list and the home search made it by *not* making it — they
 * printed the message body, which the sender's app had helpfully filled in with the word "Photo" in
 * the sender's language at send time (#534). A label baked in at write time is wrong for the
 * recipient the moment it is sent and cannot be corrected from the reading end, because it was never
 * the reader's string.
 *
 * Returns the resource rather than the resolved text so the mapping itself is an ordinary function:
 * `stringResource` is `@Composable`, and a rule that can only be exercised inside a composition is a
 * rule with no test. `ContentTypeLabelTest` pins it.
 *
 * Null for the types that carry their own readable body: TEXT is the message, and DOCUMENT is the
 * filename, which is more use than the word "Document". CONTACT has no label resource and falls
 * through the same way.
 */
internal fun contentTypeLabelResource(contentType: ContentType?): StringResource? = when (contentType) {
    ContentType.IMAGE -> Res.string.chat_photo
    ContentType.VIDEO -> Res.string.chat_video
    ContentType.VOICE -> Res.string.chat_voice_message
    ContentType.GIF -> Res.string.attach_gif
    ContentType.STICKER -> Res.string.attach_sticker
    // Both of these put JSON in the body, so a label is not merely nicer here — without one the
    // conversation list rendered a raw serialized object as the preview.
    ContentType.LOCATION -> Res.string.attach_location
    ContentType.POLL -> Res.string.attach_poll
    ContentType.TEXT, ContentType.DOCUMENT, ContentType.CONTACT, null -> null
}

/** [contentTypeLabelResource], resolved in the language of whoever is looking at the screen. */
@Composable
fun contentTypeLabel(contentType: ContentType?): String? =
    contentTypeLabelResource(contentType)?.let { stringResource(it) }

/**
 * The one line a conversation row shows under the name.
 *
 * The label wins over the stored body for media, rather than being a fallback for when the body is
 * empty. That ordering is the fix: history is full of rows whose body is the word "Fotoğraf", and
 * treating the body as the better answer would go on showing it. Never reading the body for media
 * types is also what makes those old conversations correct without rewriting them.
 *
 * [contentType] is null for a conversation read back from the on-device cache, which has no column
 * for it — that row falls back to the stored preview and is replaced by the server's answer as soon
 * as the list refreshes.
 */
@Composable
fun conversationPreviewText(contentType: ContentType?, storedPreview: String?): String? =
    contentTypeLabel(contentType) ?: storedPreview?.takeIf { it.isNotBlank() }
