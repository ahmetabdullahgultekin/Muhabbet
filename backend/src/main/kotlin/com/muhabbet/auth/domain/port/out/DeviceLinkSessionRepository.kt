package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.DeviceLinkSession
import java.util.UUID

interface DeviceLinkSessionRepository {
    fun save(session: DeviceLinkSession): DeviceLinkSession
    fun findById(id: UUID): DeviceLinkSession?
    fun findByLinkToken(token: String): DeviceLinkSession?
}
