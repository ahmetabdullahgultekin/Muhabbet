package com.muhabbet.auth.domain.port.`in`

import com.muhabbet.shared.dto.MatchedContact
import java.util.UUID

interface ContactSyncUseCase {
    fun syncContacts(userId: UUID, phoneHashes: List<String>): List<MatchedContact>
}
