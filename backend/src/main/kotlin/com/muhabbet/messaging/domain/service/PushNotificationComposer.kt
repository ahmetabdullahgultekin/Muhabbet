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
         * Used when the recipient's locale is unknown, which today is always: no device row carries
         * one. Turkish because Turkey is the launch market and the app's own default locale is
         * Turkish. Adding the column is the follow-up on #469 — this constant is the seam it plugs
         * into, and it exists so the next change is one parameter rather than a string hunt.
         */
        val FALLBACK_LOCALE: Locale = Locale.forLanguageTag("tr")

        /** A tray line is truncated by the OS anyway; this only bounds what leaves the server. */
        const val MAX_BODY_LENGTH = 100

        const val KEY_CONVERSATION_ID = "conversationId"
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_SENDER_ID = "senderId"
        const val KEY_SENDER_NAME = "senderName"
        const val KEY_CONVERSATION_TYPE = "conversationType"
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
                KEY_CONVERSATION_TYPE to (conversation?.type ?: ConversationType.DIRECT).name
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
