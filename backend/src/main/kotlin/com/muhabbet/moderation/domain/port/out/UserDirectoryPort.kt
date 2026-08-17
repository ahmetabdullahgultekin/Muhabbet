package com.muhabbet.moderation.domain.port.out

import java.util.UUID

/**
 * The slice of a user record moderation needs to put a name and a face on a block.
 *
 * Users are owned by the auth module. Moderation declares what it needs as its own out-port so that
 * neither its domain nor its controllers import auth types directly; the adapter behind this port is
 * the single, explicit place where the two modules meet. Near-twin of messaging's `UserDirectoryPort`
 * — the duplication is deliberate, the same trade already accepted for `BlockDirectoryPort` /
 * `BlockPolicyPort` and the backend/shared enum pairs.
 */
interface UserDirectoryPort {
    /** Batched by contract — a block list resolves every row in one query, never one by one. */
    fun findDisplayInfo(userIds: Collection<UUID>): Map<UUID, UserDisplayInfo>
}

data class UserDisplayInfo(
    val userId: UUID,
    val displayName: String?,
    val avatarUrl: String?
)
