package com.muhabbet.auth.domain.port.`in`

import java.util.UUID

interface LogoutUseCase {
    suspend fun logout(userId: UUID, deviceId: UUID)
}
