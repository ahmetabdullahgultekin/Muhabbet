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

        // Soft-delete user
        userRepository.save(
            user.copy(
                status = UserStatus.DELETED,
                deletedAt = Instant.now()
            )
        )

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllForUser(userId)

        // Remove user from all conversations
        userDataQueryPort.removeUserFromAllConversations(userId)

        log.info("Account deletion requested: userId={}", userId)
    }
}
