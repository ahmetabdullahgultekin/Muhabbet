package com.muhabbet.auth.domain.port.out

import java.util.UUID

interface PhoneHashRepository {
    fun save(userId: UUID, phoneHash: String)
    fun findUserIdsByPhoneHashes(phoneHashes: List<String>): Map<String, UUID>

    /** Whether the user is discoverable via phone-hash contact matching — used by the KVKK data export. */
    fun existsByUserId(userId: UUID): Boolean
}
