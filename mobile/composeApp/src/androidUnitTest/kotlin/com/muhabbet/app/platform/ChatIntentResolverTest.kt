package com.muhabbet.app.platform

import com.muhabbet.app.navigation.ChatOpenRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `MainActivity` is exported — it has to be, it is the launcher — so any installed app can start it
 * with whatever extras it likes. Only an intent this app built itself (a notification or a
 * conversation shortcut) carries the per-install launch token, and only such an intent may decide
 * the chat's title or whether it is a group.
 */
class ChatIntentResolverTest {

    private val conversationId = "3f1c2b9e-7a4d-4e8f-9b61-2d5a0c7e1f34"
    private val launchToken = "a".repeat(64)

    @Test
    fun `should open the chat with name and group flag when the launch token matches`() {
        val request = ChatIntentResolver.resolve(
            notificationConversationId = conversationId,
            notificationSenderName = "Elif",
            notificationConversationType = MuhabbetNotifications.CONVERSATION_TYPE_GROUP,
            presentedLaunchToken = launchToken,
            expectedLaunchToken = launchToken,
            deepLinkConversationId = null
        )

        assertEquals(ChatOpenRequest(conversationId, displayName = "Elif", isGroup = true), request)
    }

    @Test
    fun `should ignore notification extras when the launch token is missing`() {
        val request = ChatIntentResolver.resolve(
            notificationConversationId = conversationId,
            notificationSenderName = "Spoofed",
            notificationConversationType = MuhabbetNotifications.CONVERSATION_TYPE_GROUP,
            presentedLaunchToken = null,
            expectedLaunchToken = launchToken,
            deepLinkConversationId = null
        )

        assertNull(request)
    }

    @Test
    fun `should ignore notification extras when the launch token is wrong`() {
        val request = ChatIntentResolver.resolve(
            notificationConversationId = conversationId,
            notificationSenderName = "Spoofed",
            notificationConversationType = null,
            presentedLaunchToken = "b".repeat(64),
            expectedLaunchToken = launchToken,
            deepLinkConversationId = null
        )

        assertNull(request)
    }

    @Test
    fun `should not trust an empty launch token when the expected one is empty`() {
        val request = ChatIntentResolver.resolve(
            notificationConversationId = conversationId,
            notificationSenderName = "Spoofed",
            notificationConversationType = null,
            presentedLaunchToken = "",
            expectedLaunchToken = "",
            deepLinkConversationId = null
        )

        assertNull(request)
    }

    @Test
    fun `should open a deep link by id alone when it carries no launch token`() {
        val request = ChatIntentResolver.resolve(
            notificationConversationId = null,
            notificationSenderName = "Spoofed",
            notificationConversationType = MuhabbetNotifications.CONVERSATION_TYPE_GROUP,
            presentedLaunchToken = null,
            expectedLaunchToken = launchToken,
            deepLinkConversationId = conversationId
        )

        assertEquals(ChatOpenRequest(conversationId, displayName = null, isGroup = false), request)
    }

    @Test
    fun `should not let untrusted extras name a deep-linked chat when both are present`() {
        val request = ChatIntentResolver.resolve(
            notificationConversationId = "00000000-0000-0000-0000-000000000000",
            notificationSenderName = "Spoofed",
            notificationConversationType = MuhabbetNotifications.CONVERSATION_TYPE_GROUP,
            presentedLaunchToken = "wrong",
            expectedLaunchToken = launchToken,
            deepLinkConversationId = conversationId
        )

        assertEquals(ChatOpenRequest(conversationId, displayName = null, isGroup = false), request)
    }

    @Test
    fun `should refuse a conversation id that is not a uuid when it comes from a deep link`() {
        val request = ChatIntentResolver.resolve(
            notificationConversationId = null,
            notificationSenderName = null,
            notificationConversationType = null,
            presentedLaunchToken = null,
            expectedLaunchToken = launchToken,
            deepLinkConversationId = "../../users/me"
        )

        assertNull(request)
    }

    @Test
    fun `should refuse a conversation id that is not a uuid when the launch token matches`() {
        val request = ChatIntentResolver.resolve(
            notificationConversationId = "not-a-uuid",
            notificationSenderName = "Elif",
            notificationConversationType = null,
            presentedLaunchToken = launchToken,
            expectedLaunchToken = launchToken,
            deepLinkConversationId = null
        )

        assertNull(request)
    }
}
