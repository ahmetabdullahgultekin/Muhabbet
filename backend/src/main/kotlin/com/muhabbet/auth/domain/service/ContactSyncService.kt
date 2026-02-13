package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.`in`.ContactSyncUseCase
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.dto.MatchedContact
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class ContactSyncService(
    private val phoneHashRepository: PhoneHashRepository,
    private val userRepository: UserRepository
) : ContactSyncUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_HASHES_PER_REQUEST = 1000
    }

    @Transactional(readOnly = true)
    override fun syncContacts(userId: UUID, phoneHashes: List<String>): List<MatchedContact> {
        if (phoneHashes.size > MAX_HASHES_PER_REQUEST) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR, "Tek seferde en fazla $MAX_HASHES_PER_REQUEST kişi senkronize edilebilir")
        }

        val hashToUserId = phoneHashRepository.findUserIdsByPhoneHashes(phoneHashes)

        // Exclude the requesting user's own hash
        val filtered = hashToUserId.filter { (_, matchedUserId) -> matchedUserId != userId }

        // Batch load all matched users in one query (was N individual queries)
        val matchedUserIds = filtered.map { it.value }
        val usersMap = userRepository.findAllByIds(matchedUserIds).associateBy { it.id }

        val matchedContacts = filtered.mapNotNull { (phoneHash, matchedUserId) ->
            val user = usersMap[matchedUserId] ?: return@mapNotNull null
            MatchedContact(
                userId = matchedUserId.toString(),
                phoneHash = phoneHash,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl
            )
        }

        log.info("Contact sync: userId={}, requested={}, matched={}", userId, phoneHashes.size, matchedContacts.size)
        return matchedContacts
    }
}
