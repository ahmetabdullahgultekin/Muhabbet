package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Bridges messaging's [ReadReceiptPolicyPort] to the auth module's user store, mirroring
 * [AuthUserDirectoryAdapter]. Being an adapter, this is one of the two classes in messaging allowed
 * to know that users live in auth at all.
 */
@Component
class AuthReadReceiptPolicyAdapter(
    private val userRepository: UserRepository
) : ReadReceiptPolicyPort {

    override fun findReadReceiptsDisabled(userIds: Collection<UUID>): Set<UUID> {
        if (userIds.isEmpty()) return emptySet()
        return userRepository.findAllByIds(userIds.distinct())
            .filterNot { it.readReceiptsEnabled }
            .mapTo(mutableSetOf()) { it.id }
    }
}
