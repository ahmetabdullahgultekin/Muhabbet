package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import com.muhabbet.messaging.domain.port.`in`.ManageEncryptionUseCase
import com.muhabbet.messaging.domain.port.`in`.PreKeyBundle
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.EncryptionKeyRepository
import com.muhabbet.messaging.domain.port.out.PreKeyFetchQuotaPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class EncryptionService(
    private val encryptionKeyRepository: EncryptionKeyRepository,
    private val conversationRepository: ConversationRepository,
    private val blockPolicy: BlockPolicyPort,
    private val preKeyFetchQuota: PreKeyFetchQuotaPort,
    private val eventPublisher: ApplicationEventPublisher? = null
) : ManageEncryptionUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun registerKeyBundle(userId: UUID, bundle: EncryptionKeyBundle) {
        val existingBundle = encryptionKeyRepository.getKeyBundle(userId)

        val bundleToSave = if (existingBundle != null && existingBundle.identityKey != bundle.identityKey) {
            // Identity key changed — track for security notifications
            log.warn("Identity key changed for user={}, version {} -> {}", userId, existingBundle.keyVersion, existingBundle.keyVersion + 1)
            bundle.copy(
                userId = userId,
                keyVersion = existingBundle.keyVersion + 1,
                previousIdentityKey = existingBundle.identityKey,
                keyChangedAt = Instant.now()
            )
        } else {
            bundle.copy(userId = userId)
        }

        encryptionKeyRepository.saveKeyBundle(userId, bundleToSave)
        log.info("Key bundle registered for user={}, version={}", userId, bundleToSave.keyVersion)
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

    /**
     * The budget is spent first, so probing who one is related to costs the same as a real fetch.
     * The relationship and block checks share one error code, so a refusal does not reveal which
     * of the two it was. Only then is the target's bundle looked up — a refused caller learns
     * nothing about whether the target has published one.
     */
    @Transactional
    override fun fetchPreKeyBundle(requesterId: UUID, targetUserId: UUID): PreKeyBundle? {
        if (!preKeyFetchQuota.tryConsume(requesterId, targetUserId)) {
            throw BusinessException(ErrorCode.ENCRYPTION_KEY_BUNDLE_RATE_LIMITED)
        }
        if (requesterId != targetUserId && !mayFetch(requesterId, targetUserId)) {
            log.warn("Prekey bundle refused: requester={} has no relationship with target={}", requesterId, targetUserId)
            throw BusinessException(ErrorCode.ENCRYPTION_KEY_BUNDLE_FORBIDDEN)
        }

        val keyBundle = encryptionKeyRepository.getKeyBundle(targetUserId) ?: return null

        val oneTimePreKey = encryptionKeyRepository.consumeOneTimePreKey(targetUserId)
        if (oneTimePreKey == null) {
            log.warn("No one-time pre-keys available for user={}", targetUserId)
        }

        return PreKeyBundle(
            identityKey = keyBundle.identityKey,
            signedPreKey = keyBundle.signedPreKey,
            signedPreKeySignature = keyBundle.signedPreKeySignature,
            signedPreKeyId = keyBundle.signedPreKeyId,
            registrationId = keyBundle.registrationId,
            oneTimePreKey = oneTimePreKey?.publicKey,
            oneTimePreKeyId = oneTimePreKey?.keyId
        )
    }

    private fun mayFetch(requesterId: UUID, targetUserId: UUID): Boolean =
        conversationRepository.sharesConversation(requesterId, targetUserId) &&
            !blockPolicy.hasBlocked(targetUserId, requesterId) &&
            !blockPolicy.hasBlocked(requesterId, targetUserId)
}
