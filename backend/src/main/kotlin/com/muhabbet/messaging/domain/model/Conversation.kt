package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

enum class ConversationType {
    DIRECT, GROUP, CHANNEL
}

enum class MemberRole {
    OWNER, ADMIN, MEMBER
}

data class Conversation(
    val id: UUID = UUID.randomUUID(),
    val type: ConversationType,
    val name: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val createdBy: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val disappearAfterSeconds: Int? = null,
    val announcementOnly: Boolean = false
)

data class ConversationMember(
    val conversationId: UUID,
    val userId: UUID,
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Instant = Instant.now(),
    val mutedUntil: Instant? = null,
    val lastReadAt: Instant? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val archivedAt: Instant? = null,
    val locked: Boolean = false,
    val lockedAt: Instant? = null
) {
    /**
     * This member's own mute of this conversation, right now — per-user, per-conversation, and
     * time-bounded, never per-conversation. "Mute for 8 hours" is a `mutedUntil` timestamp
     * ([ConversationController.muteConversation] sets it 8h/1w/forever in the future); once that
     * timestamp is in the past the mute has lapsed and this returns false again without anyone
     * having to unmute explicitly. `null` mutedUntil is "never muted" — also false.
     *
     * The one place this should be asked: the push fan-out (#571). A muted conversation still gets
     * the message — delivered over the socket if online, still counted unread, still in the chat —
     * only the notification is withheld, and only for the member whose row this is.
     */
    fun isMuted(now: Instant = Instant.now()): Boolean = mutedUntil?.isAfter(now) == true
}
