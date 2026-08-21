package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Bridges messaging's [BlockPolicyPort] to the moderation module, mirroring
 * [AuthUserDirectoryAdapter] and [AuthReadReceiptPolicyAdapter].
 *
 * Depends on moderation's **in-port**, not its `BlockRepository`: a use-case interface is the
 * published face of a module, a repository is its private plumbing. Reaching for the repository
 * would let messaging read blocks in ways moderation never sanctioned.
 */
@Component
class ModerationBlockPolicyAdapter(
    private val blockUserUseCase: BlockUserUseCase
) : BlockPolicyPort {

    override fun hasBlocked(blockerId: UUID, blockedId: UUID): Boolean =
        blockUserUseCase.isBlocked(blockerId, blockedId)

    override fun findBlockedBy(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> =
        blockUserUseCase.findBlockersAmong(userId, candidateIds)

    override fun findBlockedAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> =
        blockUserUseCase.findBlockedAmong(userId, candidateIds)
}
