package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.ExportedDevice
import com.muhabbet.auth.domain.model.ExportedDeviceLinkSession
import com.muhabbet.auth.domain.model.ExportedLoginApproval
import com.muhabbet.auth.domain.model.ExportedPage
import com.muhabbet.auth.domain.model.ExportedPrivacySettings
import com.muhabbet.auth.domain.model.ExportedProfile
import com.muhabbet.auth.domain.model.ExportedSession
import com.muhabbet.auth.domain.model.UserDataExport
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.`in`.ManageUserDataUseCase
import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.LoginApprovalRepository
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserDataQueryPort
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * [exportUserData] depends on every repository that has a slice of a user's data because that is,
 * by nature of the KVKK/GDPR access right, exactly what an honest export requires (#341) — see
 * [UserDataExport]'s doc for the privacy rule applied when assembling it and the symmetry with
 * [UserDataQueryPort.erasePersonalData].
 */
open class UserDataService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userDataQueryPort: UserDataQueryPort,
    private val deviceRepository: DeviceRepository,
    private val loginApprovalRepository: LoginApprovalRepository,
    private val deviceLinkSessionRepository: DeviceLinkSessionRepository,
    private val phoneHashRepository: PhoneHashRepository
) : ManageUserDataUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun exportUserData(
        userId: UUID,
        messagesCursor: String?,
        mediaCursor: String?,
        pageSize: Int
    ): UserDataExport {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val limit = pageSize.coerceIn(1, MAX_EXPORT_PAGE_SIZE)
        val messagesSince = messagesCursor?.let { parseCursorOrNull(it) } ?: Instant.EPOCH
        val mediaSince = mediaCursor?.let { parseCursorOrNull(it) } ?: Instant.EPOCH

        val messagesPage = userDataQueryPort.findMessagesPage(userId, messagesSince, limit)
        val messagesTotal = userDataQueryPort.countMessagesByUserId(userId)
        val mediaPage = userDataQueryPort.findMediaFilesPage(userId, mediaSince, limit)
        val mediaTotal = userDataQueryPort.countMediaFilesByUserId(userId)

        val export = UserDataExport(
            exportedAt = Instant.now(),
            profile = ExportedProfile(
                userId = user.id,
                phoneNumber = user.phoneNumber,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                about = user.about,
                joinedAt = user.createdAt,
                twoStepVerificationEnabled = user.twoStepPinHash != null
            ),
            privacySettings = ExportedPrivacySettings(
                readReceiptsEnabled = user.readReceiptsEnabled,
                onlineStatusVisibility = user.onlineStatusVisibility,
                aboutVisibility = user.aboutVisibility
            ),
            devices = deviceRepository.findByUserId(userId).map {
                ExportedDevice(
                    id = it.id,
                    platform = it.platform,
                    deviceName = it.deviceName,
                    displayName = it.displayName,
                    isPrimary = it.isPrimary,
                    createdAt = it.createdAt,
                    lastActiveAt = it.lastActiveAt,
                    revokedAt = it.revokedAt
                )
            },
            sessions = refreshTokenRepository.findByUserId(userId).map {
                ExportedSession(
                    deviceId = it.deviceId,
                    createdAt = it.createdAt,
                    expiresAt = it.expiresAt,
                    revokedAt = it.revokedAt
                )
            },
            loginApprovals = loginApprovalRepository.findByUserId(userId).map {
                ExportedLoginApproval(
                    deviceName = it.deviceName,
                    platform = it.platform,
                    ipAddress = it.ipAddress,
                    status = it.status.name,
                    createdAt = it.createdAt,
                    resolvedAt = it.resolvedAt,
                    expiresAt = it.expiresAt
                )
            },
            linkedDeviceSessions = deviceLinkSessionRepository.findByUserId(userId).map {
                ExportedDeviceLinkSession(
                    status = it.status.name,
                    companionPlatform = it.companionPlatform,
                    companionDeviceName = it.companionDeviceName,
                    createdAt = it.createdAt,
                    expiresAt = it.expiresAt,
                    completedAt = it.completedAt
                )
            },
            contacts = userDataQueryPort.findContacts(userId),
            discoverableByPhoneHash = phoneHashRepository.existsByUserId(userId),
            conversations = userDataQueryPort.findConversationMemberships(userId),
            messages = ExportedPage.of(messagesPage, limit, messagesTotal) { it.serverTimestamp.toString() },
            mediaFiles = ExportedPage.of(mediaPage, limit, mediaTotal) { it.createdAt.toString() },
            chatWallpapers = userDataQueryPort.findChatWallpapers(userId),
            chatFolders = userDataQueryPort.findChatFolders(userId),
            messageBackups = userDataQueryPort.findMessageBackups(userId),
            ownedBroadcastLists = userDataQueryPort.findOwnedBroadcastLists(userId),
            broadcastListMemberships = userDataQueryPort.findBroadcastListMemberships(userId),
            encryptionKeys = userDataQueryPort.findEncryptionKeySummary(userId)
        )

        log.info("User data exported: userId={}, messages={}/{}, media={}/{}", userId, messagesPage.size, messagesTotal, mediaPage.size, mediaTotal)
        return export
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

    private fun parseCursorOrNull(cursor: String): Instant? =
        try {
            Instant.parse(cursor)
        } catch (e: Exception) {
            null
        }

    companion object {
        const val MAX_EXPORT_PAGE_SIZE = 500
        private const val RADIX_36 = 36
    }
}
