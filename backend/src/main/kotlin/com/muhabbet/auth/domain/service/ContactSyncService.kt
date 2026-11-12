package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.`in`.ContactSyncUseCase
import com.muhabbet.auth.domain.port.out.BlockDirectoryPort
import com.muhabbet.auth.domain.port.out.ContactSyncQuotaPort
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.dto.MatchedContact
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Matches an address book's phone hashes to accounts.
 *
 * The hashes are unsalted SHA-256 of E.164 numbers and protect nothing on their own — the Turkish
 * mobile space is about 10^9 numbers (#748). What bounds enumeration is the per-account daily
 * budget in [ContactSyncQuotaPort], checked **before** the lookup so a refused request learns
 * nothing. The answer is then narrowed to people it is right to show: never the requester, never
 * anyone on either side of a block with them, and never an account that is not active (a deleted
 * account is anonymised, and a suspended one is not to be surfaced).
 */
open class ContactSyncService(
    private val phoneHashRepository: PhoneHashRepository,
    private val userRepository: UserRepository,
    private val quota: ContactSyncQuotaPort,
    private val blockDirectory: BlockDirectoryPort
) : ContactSyncUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_HASHES_PER_REQUEST = 1000
    }

    @Transactional(readOnly = true)
    override fun syncContacts(userId: UUID, phoneHashes: List<String>): List<MatchedContact> {
        if (phoneHashes.size > MAX_HASHES_PER_REQUEST) {
            throw BusinessException(ErrorCode.CONTACT_SYNC_LIMIT_EXCEEDED)
        }
        val distinctHashes = phoneHashes.toSet()
        if (!quota.tryConsume(userId, distinctHashes)) {
            throw BusinessException(ErrorCode.CONTACT_SYNC_DAILY_LIMIT_EXCEEDED)
        }

        val hashToUserId = phoneHashRepository.findUserIdsByPhoneHashes(distinctHashes.toList())
            .filterValues { it != userId }
        if (hashToUserId.isEmpty()) return emptyList()

        val candidateIds = hashToUserId.values.toSet()
        val blocked = blockDirectory.findBlockedEitherWay(userId, candidateIds)
        val activeUsers = userRepository.findAllByIds((candidateIds - blocked).toList())
            .filter { it.status == UserStatus.ACTIVE && it.deletedAt == null }
            .associateBy { it.id }

        val matchedContacts = hashToUserId.mapNotNull { (phoneHash, matchedUserId) ->
            val user = activeUsers[matchedUserId] ?: return@mapNotNull null
            MatchedContact(
                userId = matchedUserId.toString(),
                phoneHash = phoneHash,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl
            )
        }

        log.info("Contact sync: userId={}, requested={}, matched={}", userId, distinctHashes.size, matchedContacts.size)
        return matchedContacts
    }
}
