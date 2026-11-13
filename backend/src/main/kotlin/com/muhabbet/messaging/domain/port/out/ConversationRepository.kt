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
    fun findAllContactUserIds(userId: UUID): Set<UUID>

    /**
     * Which of [candidateIds] have made themselves known to [viewerId] one-to-one: they share a
     * DIRECT conversation with the viewer **and** created it or have sent a message in it (#902).
     *
     * Membership alone would not do. Anyone can open a direct conversation with any user id, and
     * every group roster hands out user ids, so "shares a DM" on its own would let a stranger
     * harvest a group's phone numbers by opening a DM with each member. Requiring the *other* side
     * to have acted keeps the disclosure theirs to make: messaging someone, or answering them, is
     * the moment they chose to talk to this person. Group, channel and community co-membership
     * never counts.
     *
     * One query for the whole candidate set, none when it is empty.
     */
    fun findDirectCounterpartsWhoDisclosed(viewerId: UUID, candidateIds: Collection<UUID>): Set<UUID>
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
