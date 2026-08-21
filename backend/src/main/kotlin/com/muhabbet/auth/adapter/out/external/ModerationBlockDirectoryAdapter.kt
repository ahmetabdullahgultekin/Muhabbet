package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.BlockDirectoryPort
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Bridges auth's [BlockDirectoryPort] to the moderation module. Depends on moderation's in-port,
 * not on its `BlockRepository` — a use-case interface is the published face of a module, a
 * repository is its private plumbing.
 *
 * Two directional questions collapse to one symmetric answer here, and only here, so no caller in
 * auth has to know that a block has a direction. Short-circuits: a profile lookup between two users
 * neither of whom has blocked the other — very nearly all of them — costs two indexed reads, and
 * one when the first is a hit.
 */
@Component
class ModerationBlockDirectoryAdapter(
    private val blockUserUseCase: BlockUserUseCase
) : BlockDirectoryPort {

    override fun blockExistsBetween(userId: UUID, otherUserId: UUID): Boolean =
        blockUserUseCase.isBlocked(userId, otherUserId) ||
            blockUserUseCase.isBlocked(otherUserId, userId)
}
