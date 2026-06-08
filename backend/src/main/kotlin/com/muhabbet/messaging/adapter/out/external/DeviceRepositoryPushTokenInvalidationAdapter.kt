package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.messaging.domain.port.out.PushTokenInvalidationPort
import org.springframework.stereotype.Component

/**
 * Implements the messaging-side [PushTokenInvalidationPort] over the `auth` module's
 * [DeviceRepository]. Cross-module access goes through `auth`'s public out-port (the same
 * port the broadcasters already depend on) — never a direct JPA import.
 */
@Component
class DeviceRepositoryPushTokenInvalidationAdapter(
    private val deviceRepository: DeviceRepository
) : PushTokenInvalidationPort {

    override fun invalidate(pushToken: String) {
        deviceRepository.clearPushToken(pushToken)
    }
}
