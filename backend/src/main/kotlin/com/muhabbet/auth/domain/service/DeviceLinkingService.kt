package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.model.DeviceLinkStatus
import com.muhabbet.auth.domain.port.`in`.LinkDeviceUseCase
import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
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
 * Reachability is gated upstream by the `muhabbet.multi-device.enabled` flag at the controller, so
 * with the flag OFF this service is never invoked and the single-device path is unchanged.
 */
open class DeviceLinkingService(
    private val deviceLinkSessionRepository: DeviceLinkSessionRepository,
    private val deviceRepository: DeviceRepository
) : LinkDeviceUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        private const val LINK_EXPIRY_SECONDS = 120L      // QR is short-lived
        private const val TOKEN_BYTES = 24                // 192-bit random token
        const val MAX_COMPANION_DEVICES = 4               // WhatsApp-style cap (excludes the primary)
    }

    @Transactional
    override fun beginLink(userId: UUID, primaryDeviceId: UUID): DeviceLinkSession {
        // The opener must be a device the user actually owns.
        val primary = deviceRepository.findById(primaryDeviceId)
            ?: throw BusinessException(ErrorCode.DEVICE_NOT_FOUND)
        if (primary.userId != userId) {
            throw BusinessException(ErrorCode.DEVICE_NOT_FOUND)
        }

        val activeCompanions = deviceRepository.findActiveByUserId(userId).count { it.isCompanion }
        if (activeCompanions >= MAX_COMPANION_DEVICES) {
            throw BusinessException(ErrorCode.DEVICE_LINK_LIMIT_REACHED)
        }

        val session = DeviceLinkSession(
            userId = userId,
            primaryDeviceId = primaryDeviceId,
            linkToken = generateToken(),
            expiresAt = Instant.now().plusSeconds(LINK_EXPIRY_SECONDS)
        )
        val saved = deviceLinkSessionRepository.save(session)
        log.info("Device link session opened: id={}, user={}, primary={}", saved.id, userId, primaryDeviceId)
        return saved
    }

    @Transactional
    override fun completeLink(
        linkToken: String,
        companionPlatform: String,
        companionDeviceName: String?,
        publicBundle: String?
    ): Device {
        val session = deviceLinkSessionRepository.findByLinkToken(linkToken)
            ?: throw BusinessException(ErrorCode.DEVICE_LINK_TOKEN_INVALID)

        if (session.status != DeviceLinkStatus.PENDING) {
            throw BusinessException(ErrorCode.DEVICE_LINK_ALREADY_USED)
        }
        if (session.isExpired()) {
            deviceLinkSessionRepository.save(session.copy(status = DeviceLinkStatus.EXPIRED))
            throw BusinessException(ErrorCode.DEVICE_LINK_EXPIRED)
        }

        // Re-check the cap at completion (a concurrent link could have filled the last slot).
        val activeCompanions = deviceRepository.findActiveByUserId(session.userId).count { it.isCompanion }
        if (activeCompanions >= MAX_COMPANION_DEVICES) {
            throw BusinessException(ErrorCode.DEVICE_LINK_LIMIT_REACHED)
        }

        // Register the companion in the device registry (owned by the same user).
        val companion = deviceRepository.save(
            Device(
                userId = session.userId,
                platform = companionPlatform,
                deviceName = companionDeviceName,
                displayName = companionDeviceName,
                linkedByDeviceId = session.primaryDeviceId,
                lastActiveAt = Instant.now(),
                isPrimary = false
            )
        )

        // ── Crypto boundary (NotYetImplemented) ──
        // Per-device Signal session transfer plugs in here via DeviceLinkCrypto.establishSession,
        // consuming `publicBundle`. It is intentionally NOT invoked: the registry + transport slice
        // ships without it (BLOCKED on the libsignal upgrade). We persist the public bundle opaquely
        // so the future crypto slice has what it needs, without faking any encryption now.
        deviceLinkSessionRepository.save(
            session.copy(
                status = DeviceLinkStatus.COMPLETED,
                companionPlatform = companionPlatform,
                companionDeviceName = companionDeviceName,
                publicBundle = publicBundle,
                linkedDeviceId = companion.id,
                completedAt = Instant.now()
            )
        )
        log.info(
            "Device linked: companion={}, user={}, platform={}, bundlePresent={}",
            companion.id, session.userId, companionPlatform, publicBundle != null
        )
        return companion
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

        val revoked = deviceRepository.save(device.copy(revokedAt = Instant.now()))
        // Crypto boundary: DeviceLinkCrypto.dropSession(deviceId) would run here to enforce forward
        // secrecy on the peers — NotYetImplemented (libsignal block). The tombstone alone already
        // excludes the device from findActiveByUserId / the (future) fan-out set.
        log.info("Device revoked: device={}, user={}", deviceId, userId)
        return revoked
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        // URL-safe base64 without padding (QR-friendly, no '+' '/' '=').
        return bytes.toBase64UrlNoPadding()
    }
}

private const val BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private fun ByteArray.toBase64UrlNoPadding(): String {
    val sb = StringBuilder((size + 2) / 3 * 4)
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        val b1 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else 0
        sb.append(BASE64_URL[b0 ushr 2])
        sb.append(BASE64_URL[(b0 and 0x03 shl 4) or (b1 ushr 4)])
        if (i + 1 < size) sb.append(BASE64_URL[(b1 and 0x0F shl 2) or (b2 ushr 6)])
        if (i + 2 < size) sb.append(BASE64_URL[b2 and 0x3F])
        i += 3
    }
    return sb.toString()
}
