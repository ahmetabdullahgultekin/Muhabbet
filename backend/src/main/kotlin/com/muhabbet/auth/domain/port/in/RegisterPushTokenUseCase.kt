package com.muhabbet.auth.domain.port.`in`

import java.util.UUID

interface RegisterPushTokenUseCase {
    fun registerPushToken(userId: UUID, deviceId: UUID, pushToken: String)
}
