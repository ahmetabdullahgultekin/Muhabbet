package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.UserDataExport
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.`in`.ManageUserDataUseCase
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserDataQueryPort
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class UserDataService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userDataQueryPort: UserDataQueryPort
) : ManageUserDataUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun exportUserData(userId: UUID): UserDataExport {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val messageCount = userDataQueryPort.countMessagesByUserId(userId)
        val conversationCount = userDataQueryPort.countConversationsByUserId(userId)
        val mediaCount = userDataQueryPort.countMediaFilesByUserId(userId)

        log.info("User data exported: userId={}", userId)

        return UserDataExport(
            userId = user.id.toString(),
            phoneNumber = user.phoneNumber,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            about = user.about,
            messageCount = messageCount,
            conversationCount = conversationCount,
            mediaCount = mediaCount,
            joinedAt = user.createdAt
        )
    }

    @Transactional
    override fun requestAccountDeletion(userId: UUID) {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        if (user.status == UserStatus.DELETED) {
            throw BusinessException(ErrorCode.USER_ALREADY_DELETED)
        }

        refreshTokenRepository.revokeAllForUser(userId)
        userDataQueryPort.removeUserFromAllConversations(userId)

        // Everything that identifies the person or makes them findable — discovery hash, devices,
        // push tokens, keys, contacts, per-user settings.
        userDataQueryPort.erasePersonalData(userId)

        // The row itself has to survive: messages.sender_id references it, and those messages sit
        // in other people's conversations. So the identity is overwritten in place rather than the
        // row deleted. This used to only set status and deletedAt, which left the phone number in
        // plaintext and the account fully reconstructible — a status flag is not erasure under
        // KVKK m.7 or GDPR Art. 17 (#426).
        userRepository.save(
            user.copy(
                phoneNumber = anonymousPhonePlaceholder(userId),
                displayName = null,
                avatarUrl = null,
                about = null,
                lastSeenAt = null,
                twoStepPinHash = null,
                twoStepEmail = null,
                twoStepEnabledAt = null,
                status = UserStatus.DELETED,
                deletedAt = Instant.now()
            )
        )

        log.info("Account erased: userId={}", userId)
    }

    /**
     * `users.phone_number` is `VARCHAR(15) NOT NULL UNIQUE`, so the number cannot simply be nulled
     * out. Derived from the account id, which keeps it unique without a lookup, and prefixed so it
     * can never be mistaken for a real number — an E.164 number always starts `+`.
     */
    private fun anonymousPhonePlaceholder(userId: UUID): String =
        "d-" + java.lang.Long.toUnsignedString(userId.leastSignificantBits, RADIX_36)

    private companion object {
        const val RADIX_36 = 36
    }
}
