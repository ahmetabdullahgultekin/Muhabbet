package com.muhabbet.shared

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import java.time.Instant
import java.util.UUID

/**
 * Shared test data factory — provides consistent, realistic test objects
 * for all test classes. Avoids duplicating builder functions across tests.
 */
object TestData {

    // ─── Fixed UUIDs for Predictable Tests ───────────────

    val USER_ID_1: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val USER_ID_2: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val USER_ID_3: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    val DEVICE_ID_1: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011")
    val CONVERSATION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000100")
    val MESSAGE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000200")
    val GROUP_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000300")

    val PHONE_1 = "+905000000001"
    val PHONE_2 = "+905000000002"
    val PHONE_3 = "+905000000003"

    // ─── User Factory ────────────────────────────────────

    fun user(
        id: UUID = USER_ID_1,
        phoneNumber: String = PHONE_1,
        displayName: String? = "Test User",
        avatarUrl: String? = null,
        about: String? = null,
        status: UserStatus = UserStatus.ACTIVE,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now()
    ) = User(
        id = id,
        phoneNumber = phoneNumber,
        displayName = displayName,
        avatarUrl = avatarUrl,
        about = about,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // ─── Conversation Factory ────────────────────────────

    fun directConversation(
        id: UUID = CONVERSATION_ID,
        createdBy: UUID = USER_ID_1,
        createdAt: Instant = Instant.now()
    ) = Conversation(
        id = id,
        type = ConversationType.DIRECT,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    fun groupConversation(
        id: UUID = GROUP_ID,
        name: String = "Test Group",
        createdBy: UUID = USER_ID_1,
        createdAt: Instant = Instant.now()
    ) = Conversation(
        id = id,
        type = ConversationType.GROUP,
        name = name,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    fun channelConversation(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Channel",
        createdBy: UUID = USER_ID_1
    ) = Conversation(
        id = id,
        type = ConversationType.CHANNEL,
        name = name,
        createdBy = createdBy
    )

    // ─── Message Factory ─────────────────────────────────

    fun textMessage(
        id: UUID = MESSAGE_ID,
        conversationId: UUID = CONVERSATION_ID,
        senderId: UUID = USER_ID_1,
        content: String = "Hello, world!",
        clientTimestamp: Instant = Instant.now(),
        serverTimestamp: Instant = Instant.now()
    ) = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        contentType = ContentType.TEXT,
        content = content,
        clientTimestamp = clientTimestamp,
        serverTimestamp = serverTimestamp
    )

    fun imageMessage(
        id: UUID = UUID.randomUUID(),
        conversationId: UUID = CONVERSATION_ID,
        senderId: UUID = USER_ID_1,
        mediaUrl: String = "https://media.muhabbet.com/images/test.jpg",
        thumbnailUrl: String = "https://media.muhabbet.com/thumbnails/test.jpg"
    ) = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        contentType = ContentType.IMAGE,
        content = "",
        mediaUrl = mediaUrl,
        thumbnailUrl = thumbnailUrl,
        clientTimestamp = Instant.now(),
        serverTimestamp = Instant.now()
    )

    fun voiceMessage(
        id: UUID = UUID.randomUUID(),
        conversationId: UUID = CONVERSATION_ID,
        senderId: UUID = USER_ID_1,
        mediaUrl: String = "https://media.muhabbet.com/audio/test.ogg"
    ) = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        contentType = ContentType.VOICE,
        content = "",
        mediaUrl = mediaUrl,
        clientTimestamp = Instant.now(),
        serverTimestamp = Instant.now()
    )

    fun deletedMessage(
        id: UUID = UUID.randomUUID(),
        conversationId: UUID = CONVERSATION_ID,
        senderId: UUID = USER_ID_1
    ) = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        contentType = ContentType.TEXT,
        content = "This was deleted",
        isDeleted = true,
        deletedAt = Instant.now(),
        clientTimestamp = Instant.now(),
        serverTimestamp = Instant.now()
    )

    // ─── Member Factory ──────────────────────────────────

    fun member(
        conversationId: UUID = CONVERSATION_ID,
        userId: UUID = USER_ID_1,
        role: MemberRole = MemberRole.MEMBER,
        joinedAt: Instant = Instant.now()
    ) = ConversationMember(
        conversationId = conversationId,
        userId = userId,
        role = role,
        joinedAt = joinedAt
    )

    fun owner(
        conversationId: UUID = GROUP_ID,
        userId: UUID = USER_ID_1
    ) = member(conversationId = conversationId, userId = userId, role = MemberRole.OWNER)

    fun admin(
        conversationId: UUID = GROUP_ID,
        userId: UUID = USER_ID_2
    ) = member(conversationId = conversationId, userId = userId, role = MemberRole.ADMIN)

    // ─── Delivery Status Factory ─────────────────────────

    fun deliveryStatus(
        messageId: UUID = MESSAGE_ID,
        userId: UUID = USER_ID_2,
        status: DeliveryStatus = DeliveryStatus.DELIVERED,
        updatedAt: Instant = Instant.now()
    ) = MessageDeliveryStatus(
        messageId = messageId,
        userId = userId,
        status = status,
        updatedAt = updatedAt
    )
}
