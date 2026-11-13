package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DevicePlatform
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.`in`.AuthResult
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtProvider
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Which device a sign-in is for, as the client described it (#882). Validated by [parse].
 *
 * @property installationId the client-generated id of this app installation, or null from a client
 *   too old to send one.
 */
data class DeviceSignIn(
    val deviceName: String,
    val platform: DevicePlatform,
    val installationId: UUID?
) {
    companion object {
        /** Rejects an unknown platform or a malformed installation id with `VALIDATION_ERROR`. */
        fun parse(deviceName: String, platform: String, installationId: String?): DeviceSignIn =
            DeviceSignIn(
                deviceName = deviceName,
                platform = DevicePlatform.parse(platform)
                    ?: throw BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown platform"),
                installationId = installationId?.let {
                    runCatching { UUID.fromString(it) }.getOrNull()
                        ?: throw BusinessException(ErrorCode.VALIDATION_ERROR, "Malformed installationId")
                }
            )
    }
}

/**
 * The common tail of both sign-in paths: find or create the device this sign-in is for, then
 * start a new refresh-token family on it and mint the first token pair.
 *
 * Runs inside the caller's transaction (see `AuthService`), after every external check — SMS
 * provider, Firebase — has already finished outside it.
 */
class SignInSessionIssuer(
    private val deviceRepository: DeviceRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val sessionRevocation: SessionRevocation,
    private val jwtProvider: JwtProvider,
    private val refreshTokenExpirySeconds: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun issue(user: User, signIn: DeviceSignIn, isNewUser: Boolean): AuthResult {
        val device = resolveDevice(user.id, signIn)
        val refreshToken = jwtProvider.generateRefreshToken()
        val familyRoot = RefreshTokenRecord(
            userId = user.id,
            deviceId = device.id,
            tokenHash = AuthService.sha256(refreshToken),
            expiresAt = Instant.now().plusSeconds(refreshTokenExpirySeconds)
        )
        refreshTokenRepository.save(familyRoot)

        return AuthResult(
            accessToken = jwtProvider.generateAccessToken(user.id, device.id, device.tokenVersion),
            refreshToken = refreshToken,
            expiresIn = jwtProvider.accessTokenExpirySeconds,
            userId = user.id.toString(),
            deviceId = device.id.toString(),
            isNewUser = isNewUser
        )
    }

    /**
     * The same installation signs in to the same device row; anything else is a new device (#882).
     * A revoked row is never matched, so signing in again after a revoke cannot resurrect it.
     *
     * The first sign-in of an upgraded app adopts the account's legacy row of the same platform
     * (one written before installation ids existed) instead of creating a second row beside it.
     * Without that, the phone would end up with two live rows carrying the same push token and
     * every notification would arrive twice. Adoption stamps the installation id on the row, so it
     * happens at most once per legacy row and the lookup is exact from then on.
     */
    private fun resolveDevice(userId: UUID, signIn: DeviceSignIn): Device {
        val installationId = signIn.installationId
        val existing = installationId?.let { deviceRepository.findActiveByInstallation(userId, it) }
            ?: deviceRepository.findLegacyPrimaryCandidate(userId, signIn.platform)

        if (existing != null) {
            return deviceRepository.save(
                existing.copy(
                    deviceName = signIn.deviceName,
                    lastActiveAt = Instant.now(),
                    installationId = existing.installationId ?: installationId
                )
            )
        }

        val active = deviceRepository.findActiveByUserId(userId)
        evictBeyondCap(userId, active)
        return deviceRepository.save(
            Device(
                userId = userId,
                platform = signIn.platform,
                deviceName = signIn.deviceName,
                lastActiveAt = Instant.now(),
                isPrimary = active.none { it.isPrimary && !it.isCompanion },
                installationId = signIn.installationId
            )
        )
    }

    /**
     * Keeps the number of signed-in (non-companion) devices per account bounded, as a domain
     * invariant rather than an accident of the lookup. Over the cap, the least recently active one
     * is signed out — not the new sign-in refused, because the usual way to reach the cap is to
     * reinstall the app a few times, and refusing would lock the owner out of their own account.
     */
    private fun evictBeyondCap(userId: UUID, active: List<Device>) {
        val signedIn = active.filter { !it.isCompanion }
        if (signedIn.size < MAX_SIGNED_IN_DEVICES) return
        signedIn
            .sortedBy { it.lastActiveAt ?: it.createdAt }
            .take(signedIn.size - MAX_SIGNED_IN_DEVICES + 1)
            .forEach { stale ->
                deviceRepository.save(stale.copy(revokedAt = Instant.now(), pushToken = null))
                sessionRevocation.endDeviceSessions(userId, stale.id)
                log.info("Device evicted at sign-in cap: userId={}, deviceId={}", userId, stale.id)
            }
    }

    companion object {
        /** Phones/tablets signed in by OTP or Firebase at once. Companions have their own cap. */
        const val MAX_SIGNED_IN_DEVICES = 5
    }
}
