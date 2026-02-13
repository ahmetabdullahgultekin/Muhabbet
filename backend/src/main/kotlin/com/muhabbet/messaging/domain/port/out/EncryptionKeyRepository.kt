package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import java.util.UUID

interface EncryptionKeyRepository {
    fun saveKeyBundle(userId: UUID, bundle: EncryptionKeyBundle): EncryptionKeyBundle
    fun getKeyBundle(userId: UUID): EncryptionKeyBundle?
    fun saveOneTimePreKeys(userId: UUID, keys: List<OneTimePreKey>)
    fun consumeOneTimePreKey(userId: UUID): OneTimePreKey?
    fun countUnusedPreKeys(userId: UUID): Int
}
