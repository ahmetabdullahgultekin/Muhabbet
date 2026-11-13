package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import java.util.UUID

interface ConversationRepository {
    fun save(conversation: Conversation): Conversation
    fun findById(id: UUID): Conversation?
    fun saveMember(member: ConversationMember): ConversationMember
    fun findMembersByConversationId(conversationId: UUID): List<ConversationMember>
    fun findMembersByConversationIds(conversationIds: List<UUID>): Map<UUID, List<ConversationMember>>
    fun findMember(conversationId: UUID, userId: UUID): ConversationMember?
    fun findConversationsByUserId(userId: UUID): List<Conversation>
    fun findConversationsByIds(ids: List<UUID>): List<Conversation>

    /**
     * True when [userId] is a member of at least one of [conversationIds]. One query, rather than
     * loading every member of every conversation to answer a question about a single user. An
     * empty [conversationIds] is false: no conversations means no membership to find.
     */
    fun isMemberOfAny(conversationIds: List<UUID>, userId: UUID): Boolean

    /**
     * Member counts for many conversations in one query. Conversations with no members are
     * absent from the map rather than mapped to zero.
     */
    fun countMembersByConversationIds(conversationIds: List<UUID>): Map<UUID, Int>
    /**
     * The people [userId] treats as contacts: everyone they share a DIRECT or GROUP conversation
     * with **that [userId] has taken part in** — created, or sent a message in. This is what a
     * "contacts" privacy setting of [userId]'s is checked against, and who receives their presence.
     *
     * Merely sharing a conversation is not enough, because anyone can arrange that: subscribe to a
     * channel, or create a group or a 1:1 chat that includes someone. Channels never count, and a
     * conversation someone else put [userId] in counts only once [userId] speaks in it.
     */
    fun findAllContactUserIds(userId: UUID): Set<UUID>

    /**
     * The mirror image of [findAllContactUserIds]: everyone who shares a DIRECT or GROUP
     * conversation with [userId] and has taken part in it. Answers "whose contacts-only things may
     * [userId] see", and whose phone number [userId] is shown, in one query for a whole screen.
     */
    fun findUsersWhoEngagedWith(userId: UUID): Set<UUID>

    /**
     * True when [userId] and [otherUserId] are both members of at least one DIRECT or GROUP
     * conversation — a relationship, for the prekey gate (#749, ADR 0018 §3 rule 3). An existence
     * check, so it costs one indexed query however many conversations either user is in.
     *
     * CHANNEL membership deliberately does not count: anyone can subscribe to any channel with no
     * invite or approval, so co-subscription would let a stranger "share a conversation" with the
     * owner and every subscriber at will. Groups do count, including ones joined via an invite
     * link — that link is issued by a group admin, who can require approval, so entry is granted
     * rather than self-served.
     */
    fun sharesConversation(userId: UUID, otherUserId: UUID): Boolean
    fun findDirectConversation(userIdLow: UUID, userIdHigh: UUID): UUID?
    fun saveDirectLookup(userIdLow: UUID, userIdHigh: UUID, conversationId: UUID)
    fun updateLastReadAt(conversationId: UUID, userId: UUID, timestamp: java.time.Instant)

    // Group management
    fun removeMember(conversationId: UUID, userId: UUID)
    fun updateConversation(conversation: Conversation): Conversation
    fun updateMemberRole(conversationId: UUID, userId: UUID, role: com.muhabbet.messaging.domain.model.MemberRole)

    // Pin management
    fun pinConversation(conversationId: UUID, userId: UUID)
    fun unpinConversation(conversationId: UUID, userId: UUID)

    // Channel
    fun findByType(type: com.muhabbet.messaging.domain.model.ConversationType): List<Conversation>

    // Archive
    fun archiveConversation(conversationId: UUID, userId: UUID)
    fun unarchiveConversation(conversationId: UUID, userId: UUID)

    // Mute
    fun muteConversation(conversationId: UUID, userId: UUID, mutedUntil: java.time.Instant?)

    // Lock
    fun lockConversation(conversationId: UUID, userId: UUID)
    fun unlockConversation(conversationId: UUID, userId: UUID)
}
