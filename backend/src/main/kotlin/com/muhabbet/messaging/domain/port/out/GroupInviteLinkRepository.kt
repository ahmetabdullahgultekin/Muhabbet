package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.GroupInviteLink
import java.util.UUID

interface GroupInviteLinkRepository {
    fun save(link: GroupInviteLink): GroupInviteLink
    fun findById(id: UUID): GroupInviteLink?
    fun findByToken(token: String): GroupInviteLink?
    fun findActiveByConversationId(conversationId: UUID): List<GroupInviteLink>
    fun deactivate(id: UUID)

    /**
     * Takes one use of the link, atomically: increments `use_count` only while the link is active
     * and below `max_uses`, and returns whether a use was taken.
     *
     * Replaces a read-modify-write `incrementUseCount` that, together with a `useCount >= maxUses`
     * check made earlier on a stale read, let concurrent joins through a single-use link (#888).
     * The returned flag *is* the check — callers must not consult `useCount` themselves.
     */
    fun claimUse(id: UUID): Boolean
}
