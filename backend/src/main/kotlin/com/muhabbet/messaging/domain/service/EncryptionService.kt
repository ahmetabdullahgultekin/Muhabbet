package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import com.muhabbet.messaging.domain.port.`in`.ManageEncryptionUseCase
import com.muhabbet.messaging.domain.port.`in`.PreKeyBundle
import com.muhabbet.messaging.domain.port.out.EncryptionKeyRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class EncryptionService(
    private val encryptionKeyRepository: EncryptionKeyRepository
) : ManageEncryptionUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun registerKeyBundle(userId: UUID, bundle: EncryptionKeyBundle) {
        val bundleWithUser = bundle.copy(userId = userId)
        encryptionKeyRepository.saveKeyBundle(userId, bundleWithUser)
        log.info("Key bundle registered for user={}", userId)
    }

    @Transactional(readOnly = true)
    override fun getKeyBundle(userId: UUID): EncryptionKeyBundle? {
        return encryptionKeyRepository.getKeyBundle(userId)
    }

    @Transactional
    override fun uploadPreKeys(userId: UUID, keys: List<OneTimePreKey>) {
        if (keys.isEmpty()) {
            throw BusinessException(ErrorCode.ENCRYPTION_INVALID_KEY_DATA)
        }
        val keysWithUser = keys.map { it.copy(userId = userId) }
        encryptionKeyRepository.saveOneTimePreKeys(userId, keysWithUser)
        log.info("Uploaded {} one-time pre-keys for user={}", keys.size, userId)
    }

    @Transactional
    override fun fetchPreKeyBundle(targetUserId: UUID): PreKeyBundle? {
        val keyBundle = encryptionKeyRepository.getKeyBundle(targetUserId) ?: return null

        val oneTimePreKey = encryptionKeyRepository.consumeOneTimePreKey(targetUserId)
        if (oneTimePreKey == null) {
            log.warn("No one-time pre-keys available for user={}", targetUserId)
        }

        return PreKeyBundle(
            identityKey = keyBundle.identityKey,
            signedPreKey = keyBundle.signedPreKey,
            signedPreKeyId = keyBundle.signedPreKeyId,
            registrationId = keyBundle.registrationId,
            oneTimePreKey = oneTimePreKey?.publicKey,
            oneTimePreKeyId = oneTimePreKey?.keyId
        )
    }
}
