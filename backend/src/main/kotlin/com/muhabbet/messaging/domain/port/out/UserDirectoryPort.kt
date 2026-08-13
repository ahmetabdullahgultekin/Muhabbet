package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * The slice of a user record that messaging needs in order to put a name and a face on a message.
 *
 * Users are owned by the auth module. Messaging declares what it needs as its own out-port so that
 * neither its domain nor its controllers import auth types directly; the adapter behind this port
 * is the single, explicit place where the two modules meet.
 */
interface UserDirectoryPort {
    /** Batched by contract — callers resolve a whole recipient list in one query, never one by one. */
    fun findDisplayInfo(userIds: Collection<UUID>): Map<UUID, UserDisplayInfo>
}

data class UserDisplayInfo(
    val userId: UUID,
    val displayName: String?,
    val avatarUrl: String?
)
