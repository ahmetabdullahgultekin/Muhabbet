package com.muhabbet.app.platform

import com.muhabbet.app.navigation.ChatOpenRequest
import java.security.MessageDigest

/**
 * Decides which chat, if any, an intent delivered to `MainActivity` may open.
 *
 * `MainActivity` is exported because it is the launcher, so any installed app can start it with
 * any extras. Two sources are therefore told apart:
 *
 * - **An intent this app built** — a notification tap or a conversation shortcut — carries the
 *   per-install [NotificationLaunchToken]. Only then are the extras trusted, including the title
 *   and the group flag.
 * - **Anything else** contributes at most a conversation id, from a `muhabbet://chat/{id}` or app
 *   link URL. The title is then resolved locally from the conversation itself, so another app
 *   cannot choose what the chat is called. Server-side membership checks still decide whether the
 *   chat opens at all.
 *
 * Every id must be a UUID, because it is interpolated into API paths.
 *
 * Pure, so it is tested on the JVM without an Activity.
 */
internal object ChatIntentResolver {

    private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun resolve(
        notificationConversationId: String?,
        notificationSenderName: String?,
        notificationConversationType: String?,
        presentedLaunchToken: String?,
        expectedLaunchToken: String,
        deepLinkConversationId: String?
    ): ChatOpenRequest? {
        val trusted = presentedLaunchToken != null &&
            expectedLaunchToken.isNotBlank() &&
            MessageDigest.isEqual(presentedLaunchToken.toByteArray(), expectedLaunchToken.toByteArray())

        if (trusted && notificationConversationId.isUuid()) {
            return ChatOpenRequest(
                conversationId = notificationConversationId.orEmpty(),
                displayName = notificationSenderName,
                isGroup = notificationConversationType == MuhabbetNotifications.CONVERSATION_TYPE_GROUP
            )
        }

        return deepLinkConversationId
            ?.takeIf { it.isUuid() }
            ?.let { ChatOpenRequest(conversationId = it) }
    }

    private fun String?.isUuid(): Boolean = this != null && UUID_PATTERN.matches(this)
}
