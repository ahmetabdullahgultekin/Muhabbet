package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.model.PrivacyVisibility
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.port.out.PresencePrivacyPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Bridges messaging's [PresencePrivacyPort] to the auth module's user store, mirroring
 * [AuthReadReceiptPolicyAdapter].
 */
@Component
class AuthPresencePrivacyAdapter(
    private val userRepository: UserRepository
) : PresencePrivacyPort {

    override fun hidesPresenceFromEveryone(userId: UUID): Boolean =
        userRepository.findById(userId)?.onlineStatusVisibility?.lowercase() == PrivacyVisibility.NOBODY
}
