package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import java.util.UUID

interface ManageEncryptionUseCase {
    fun registerKeyBundle(userId: UUID, bundle: EncryptionKeyBundle)
    fun getKeyBundle(userId: UUID): EncryptionKeyBundle?
    fun uploadPreKeys(userId: UUID, keys: List<OneTimePreKey>)
    fun fetchPreKeyBundle(targetUserId: UUID): PreKeyBundle?
}

data class PreKeyBundle(
    val identityKey: String,
    val signedPreKey: String,
    val signedPreKeyId: Int,
    val registrationId: Int,
    val oneTimePreKey: String?,
    val oneTimePreKeyId: Int?
)
