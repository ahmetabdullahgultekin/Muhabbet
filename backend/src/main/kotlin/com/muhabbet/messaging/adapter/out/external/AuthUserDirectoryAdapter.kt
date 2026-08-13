package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.port.out.UserDisplayInfo
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Bridges messaging's [UserDirectoryPort] to the auth module's user store. Being an adapter, this
 * is the one class in messaging allowed to know that users live in auth at all.
 */
@Component
class AuthUserDirectoryAdapter(
    private val userRepository: UserRepository
) : UserDirectoryPort {

    override fun findDisplayInfo(userIds: Collection<UUID>): Map<UUID, UserDisplayInfo> {
        if (userIds.isEmpty()) return emptyMap()
        return userRepository.findAllByIds(userIds.distinct())
            .associate { it.id to UserDisplayInfo(it.id, it.displayName, it.avatarUrl) }
    }
}
