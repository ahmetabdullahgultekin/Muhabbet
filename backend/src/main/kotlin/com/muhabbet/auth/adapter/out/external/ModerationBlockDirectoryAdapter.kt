package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.BlockDirectoryPort
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Bridges auth's [BlockDirectoryPort] to the moderation module. Depends on moderation's in-port,
 * not on its `BlockRepository` — a use-case interface is the published face of a module, a
 * repository is its private plumbing.
 */
@Component
class ModerationBlockDirectoryAdapter(
    private val blockUserUseCase: BlockUserUseCase
) : BlockDirectoryPort {

    override fun hasBlocked(blockerId: UUID, blockedId: UUID): Boolean =
        blockUserUseCase.isBlocked(blockerId, blockedId)
}
