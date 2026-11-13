package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DeviceLinkCompletion
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.port.`in`.CompletedDeviceLink
import com.muhabbet.auth.domain.port.`in`.DeviceLinkOffer
import com.muhabbet.auth.domain.port.`in`.LinkDeviceUseCase
import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID

/**
 * Companion-device linking — the NON-CRYPTO slice (Tier 2, S1).
 *
 * Owns the device registry writes + the QR link-session handshake. NO Signal session transfer
 * happens here: the per-device key migration is the blocked crypto boundary
 * ([com.muhabbet.shared.port.DeviceLinkCrypto], NotYetImplemented). The companion's PUBLIC prekey
 * bundle is accepted and stored opaquely for that future slice, but linking succeeds (registry +
 * UX) without it.
 *
 * The handshake is a state machine (#884): [DeviceLinkSession.complete] decides, and
 * [DeviceLinkSessionRepository.claimCompletion] commits only if the row is still PENDING and
 * unexpired, so one QR links exactly one device however many companions present it. Only the
 * token's hash is stored, and completing issues the companion its own credentials.
 *
 * Reachability is gated upstream by the `muhabbet.multi-device.enabled` flag at the controller, so
 * with the flag OFF this service is never invoked and the single-device path is unchanged.
 */
open class DeviceLinkingService(
    private val deviceLinkSessionRepository: DeviceLinkSessionRepository,
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository,
    private val tokenIssuer: DeviceTokenIssuer,
    private val clock: Clock = Clock.systemUTC()
) : LinkDeviceUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        private const val LINK_EXPIRY_SECONDS = 120L      // QR is short-lived
        private const val TOKEN_BYTES = 24                // 192-bit random token
        const val MAX_COMPANION_DEVICES = 4               // WhatsApp-style cap (excludes the primary)
    }

    @Transactional
    override fun beginLink(userId: UUID, primaryDeviceId: UUID): DeviceLinkOffer {
        // The opener must be a device the user actually owns.
        val primary = deviceRepository.findById(primaryDeviceId)
            ?: throw BusinessException(ErrorCode.DEVICE_NOT_FOUND)
        if (primary.userId != userId) {
            throw BusinessException(ErrorCode.DEVICE_NOT_FOUND)
        }

        requireCompanionSlot(userId)

        val token = generateToken()
        val session = DeviceLinkSession(
            userId = userId,
            primaryDeviceId = primaryDeviceId,
            tokenHash = AuthService.sha256(token),
            createdAt = clock.instant(),
            expiresAt = clock.instant().plusSeconds(LINK_EXPIRY_SECONDS)
        )
        val saved = deviceLinkSessionRepository.save(session)
        log.info("Device link session opened: id={}, user={}, primary={}", saved.id, userId, primaryDeviceId)
        return DeviceLinkOffer(saved, token)
    }

    /**
     * Everything here is one transaction, and a throw anywhere undoes all of it — the companion row
     * and its refresh token included. That is what makes the claim safe to take *after* creating
     * the device: the loser of a race rolls its device back.
     */
    @Transactional
    override fun completeLink(
        linkToken: String,
        companionPlatform: String,
        companionDeviceName: String?,
        publicBundle: String?
    ): CompletedDeviceLink {
        val now = clock.instant()
        val session = deviceLinkSessionRepository.findByTokenHash(AuthService.sha256(linkToken))
            ?: throw BusinessException(ErrorCode.DEVICE_LINK_TOKEN_INVALID)

        val completed = when (val decision = session.complete(now, companionPlatform, companionDeviceName, publicBundle)) {
            // Expiry is derived from expires_at, not written: there is nothing to persist, so
            // nothing to lose to the rollback this throw causes.
            DeviceLinkCompletion.Expired -> throw BusinessException(ErrorCode.DEVICE_LINK_EXPIRED)
            DeviceLinkCompletion.AlreadyUsed -> throw BusinessException(ErrorCode.DEVICE_LINK_ALREADY_USED)
            is DeviceLinkCompletion.Completed -> decision.session
        }

        // Re-check the cap at completion (a concurrent link could have filled the last slot).
        requireCompanionSlot(session.userId)

        val companion = deviceRepository.save(
            Device(
                userId = session.userId,
                platform = companionPlatform,
                deviceName = companionDeviceName,
                displayName = companionDeviceName,
                linkedByDeviceId = session.primaryDeviceId,
                lastActiveAt = now,
                isPrimary = false
            )
        )

        // ── Crypto boundary (NotYetImplemented) ──
        // Per-device Signal session transfer plugs in here via DeviceLinkCrypto.establishSession,
        // consuming `publicBundle`. It is intentionally NOT invoked: the registry + transport slice
        // ships without it (BLOCKED on the libsignal upgrade). We persist the public bundle opaquely
        // so the future crypto slice has what it needs, without faking any encryption now.
        if (!deviceLinkSessionRepository.claimCompletion(completed.copy(linkedDeviceId = companion.id), now)) {
            throw BusinessException(ErrorCode.DEVICE_LINK_ALREADY_USED)
        }

        val isAdmin = userRepository.findById(session.userId)?.isAdmin ?: false
        val tokens = tokenIssuer.issue(session.userId, companion.id, isAdmin)

        log.info(
            "Device linked: companion={}, user={}, platform={}, bundlePresent={}",
            companion.id, session.userId, companionPlatform, publicBundle != null
        )
        return CompletedDeviceLink(companion, tokens)
    }

    @Transactional(readOnly = true)
    override fun listDevices(userId: UUID): List<Device> =
        deviceRepository.findActiveByUserId(userId).sortedByDescending { it.createdAt }

    @Transactional
    override fun revokeDevice(userId: UUID, deviceId: UUID): Device {
        val device = deviceRepository.findById(deviceId)
            ?: throw BusinessException(ErrorCode.DEVICE_NOT_FOUND)
        if (device.userId != userId) {
            throw BusinessException(ErrorCode.DEVICE_NOT_FOUND)
        }
        if (device.isPrimary || !device.isCompanion) {
            throw BusinessException(ErrorCode.DEVICE_CANNOT_REVOKE_PRIMARY)
        }
        if (!device.isActive) {
            return device // already revoked — idempotent
        }

        val revoked = deviceRepository.save(device.copy(revokedAt = clock.instant()))
        // Crypto boundary: DeviceLinkCrypto.dropSession(deviceId) would run here to enforce forward
        // secrecy on the peers — NotYetImplemented (libsignal block). The tombstone alone already
        // excludes the device from findActiveByUserId / the (future) fan-out set.
        log.info("Device revoked: device={}, user={}", deviceId, userId)
        return revoked
    }

    private fun requireCompanionSlot(userId: UUID) {
        val activeCompanions = deviceRepository.findActiveByUserId(userId).count { it.isCompanion }
        if (activeCompanions >= MAX_COMPANION_DEVICES) {
            throw BusinessException(ErrorCode.DEVICE_LINK_LIMIT_REACHED)
        }
    }

    /** 192 random bits, URL-safe base64 without padding (QR-friendly: no '+', '/' or '='). */
    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
