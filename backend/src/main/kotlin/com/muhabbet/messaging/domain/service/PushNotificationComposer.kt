package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PushNotification
import com.muhabbet.messaging.domain.port.out.NotificationTextPort
import java.util.Locale

/**
 * Turns a stored [Message] into the two lines a phone shows in its tray.
 *
 * This is pure decision-making — who wrote it, what it says, which tray entry it belongs to — so it
 * lives in the domain and both broadcasters call it. #469 was really one bug repeated: the same
 * four lines were written twice, in [com.muhabbet.messaging.adapter.out.external.RedisMessageBroadcaster]
 * and in `WebSocketMessageBroadcaster`, and they had already drifted apart (one had per-type bodies,
 * the live one did not). One composer is the only way they stay in agreement.
 */
class PushNotificationComposer(
    private val texts: NotificationTextPort
) {

    companion object {
        /**
         * Used when the recipient's locale is unknown — a device that has not registered one since
         * V22, which is every device that has not yet updated. Turkish because Turkey is the launch
         * market and the app's own default locale is Turkish.
         */
        val FALLBACK_LOCALE: Locale = Locale.forLanguageTag("tr")

        /**
         * Turns the tag stored on a device row into the [Locale] [compose] wants.
         *
         * Null for null or blank, so the caller does not have to decide what "unknown" means twice —
         * [compose] already answers that with [FALLBACK_LOCALE]. Lives here rather than in each
         * broadcaster because both of them make this same translation on the push path, and one of
         * them drifting is exactly how #469 happened the first time.
         */
        fun localeOf(tag: String?): Locale? =
            tag?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) }

        /** A tray line is truncated by the OS anyway; this only bounds what leaves the server. */
        const val MAX_BODY_LENGTH = 100

        const val KEY_CONVERSATION_ID = "conversationId"
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_SENDER_ID = "senderId"
        const val KEY_SENDER_NAME = "senderName"
        const val KEY_CONVERSATION_TYPE = "conversationType"

        /**
         * Epoch millis of [Message.serverTimestamp]. The client needs this for `setWhen` on a
         * `MessagingStyle` notification — without it, the tray can only show *delivery* time, which
         * is close but wrong for a message that queued while the recipient was offline (#595).
         */
        const val KEY_SENT_AT = "sentAt"

        /**
         * The raw group name, separate from the already-composed "Sender · Group" that [compose]
         * puts in [PushNotification.title]. A `MessagingStyle` conversation title wants the group
         * name alone, not the combined string — and that combined string is locale-formatted
         * (`push.title.group`), so the client cannot safely split it back apart. Empty, never null:
         * FCM data values must be non-null strings, and empty already means "no group name" the same
         * way it does for the title's own blank check.
         */
        const val KEY_GROUP_NAME = "groupName"
    }

    /**
     * @param senderName the sender's profile name, or null when it could not be resolved
     * @param conversation the conversation the message belongs to, or null when it could not be
     *   loaded — the title then degrades to the bare sender name rather than failing the push
     * @param recipientLocale the *recipient's* locale; null falls back to [FALLBACK_LOCALE]
     */
    fun compose(
        message: Message,
        senderName: String?,
        conversation: Conversation?,
        recipientLocale: Locale? = null
    ): PushNotification {
        val locale = recipientLocale ?: FALLBACK_LOCALE
        val sender = senderName?.takeIf { it.isNotBlank() } ?: texts.unknownSender(locale)
        val groupName = conversation?.name?.takeIf { it.isNotBlank() }

        // A group with no name is still a group, but "Sender · " reads as a bug, so it degrades to
        // the sender alone — the same shape a 1:1 gets.
        val title = if (conversation?.type != ConversationType.DIRECT && groupName != null) {
            texts.groupTitle(sender, groupName, locale)
        } else {
            sender
        }

        return PushNotification(
            title = title,
            body = body(message, locale),
            collapseKey = message.conversationId.toString(),
            data = mapOf(
                KEY_CONVERSATION_ID to message.conversationId.toString(),
                KEY_MESSAGE_ID to message.id.toString(),
                KEY_SENDER_ID to message.senderId.toString(),
                KEY_SENDER_NAME to sender,
                KEY_CONVERSATION_TYPE to (conversation?.type ?: ConversationType.DIRECT).name,
                KEY_SENT_AT to message.serverTimestamp.toEpochMilli().toString(),
                KEY_GROUP_NAME to (groupName ?: "")
            )
        )
    }

    /**
     * Text falls through to what was written; everything else gets a summary, because `content` on
     * a photo, a voice note or a poll is a caption, a blob key or nothing at all. The tray line for
     * a photo used to be empty for exactly this reason.
     */
    private fun body(message: Message, locale: Locale): String =
        if (message.contentType == ContentType.TEXT) {
            message.content.take(MAX_BODY_LENGTH)
        } else {
            texts.contentSummary(message.contentType, locale)
        }
}
