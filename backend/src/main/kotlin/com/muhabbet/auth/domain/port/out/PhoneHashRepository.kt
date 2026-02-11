package com.muhabbet.auth.domain.port.out

import java.util.UUID

interface PhoneHashRepository {
    fun save(userId: UUID, phoneHash: String)
}
