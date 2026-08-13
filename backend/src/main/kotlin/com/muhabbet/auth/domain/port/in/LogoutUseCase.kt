package com.muhabbet.auth.domain.port.`in`

import java.util.UUID

interface LogoutUseCase {
    fun logout(userId: UUID, deviceId: UUID)
}
