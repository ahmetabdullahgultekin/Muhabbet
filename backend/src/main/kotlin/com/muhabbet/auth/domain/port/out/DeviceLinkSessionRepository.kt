package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.DeviceLinkSession
import java.util.UUID

interface DeviceLinkSessionRepository {
    fun save(session: DeviceLinkSession): DeviceLinkSession
    fun findById(id: UUID): DeviceLinkSession?
    fun findByLinkToken(token: String): DeviceLinkSession?

    /** Every link handshake the user has started, regardless of status — used by the KVKK data export. */
    fun findByUserId(userId: UUID): List<DeviceLinkSession>
}
