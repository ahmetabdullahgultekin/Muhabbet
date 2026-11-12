package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import java.util.UUID

interface ManageEncryptionUseCase {
    fun registerKeyBundle(userId: UUID, bundle: EncryptionKeyBundle)
    fun getKeyBundle(userId: UUID): EncryptionKeyBundle?
    fun uploadPreKeys(userId: UUID, keys: List<OneTimePreKey>)

    /**
     * Serves [targetUserId]'s prekey bundle to [requesterId], claiming one of the target's one-time
     * prekeys. Refused unless the two share a conversation (or are the same account — sender
     * fan-out to one's own devices), neither has blocked the other, and the requester is within
     * its fetch budget (#749, ADR 0018 §3). Null when the target has published no bundle.
     */
    fun fetchPreKeyBundle(requesterId: UUID, targetUserId: UUID): PreKeyBundle?
}

data class PreKeyBundle(
    val identityKey: String,
    val signedPreKey: String,
    val signedPreKeySignature: String?,
    val signedPreKeyId: Int,
    val registrationId: Int,
    val oneTimePreKey: String?,
    val oneTimePreKeyId: Int?
)
