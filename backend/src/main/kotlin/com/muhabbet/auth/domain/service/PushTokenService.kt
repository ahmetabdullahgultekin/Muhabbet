package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.normalizeDeviceLocale
import com.muhabbet.auth.domain.model.normalizePushToken
import com.muhabbet.auth.domain.port.`in`.InvalidatePushTokenUseCase
import com.muhabbet.auth.domain.port.`in`.RegisterPushTokenUseCase
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Owns the rule that **a push token belongs to exactly one device** (#908).
 *
 * A token addresses one app install on one phone. Before this, registering it only wrote it to the
 * caller's row, so when user B signed in on a phone user A had used, both rows held the same token
 * and every message to A was pushed — sender and preview — to the phone B was now holding.
 * `devices.push_token` is now unique (V29); this service is what keeps registrations within it:
 * registration *moves* the token to the caller's device rather than copying it.
 *
 * The other ways a token leaves a device live where their transaction already is: logout in
 * [AuthService.logout], companion revocation in [DeviceLinkingService.revokeDevice], and account
 * erasure deletes the rows outright.
 *
 * Its own service rather than more methods on [AuthService], which already implements more use
 * cases than CLAUDE.md allows; push registration is not authentication.
 *
 * `open` for the same reason as [LastSeenService]: Spring proxies this class for `@Transactional`.
 */
open class PushTokenService(
    private val deviceRepository: DeviceRepository
) : RegisterPushTokenUseCase, InvalidatePushTokenUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun registerPushToken(userId: UUID, deviceId: UUID, pushToken: String, locale: String?) {
        val token = normalizePushToken(pushToken)
            ?: throw BusinessException(ErrorCode.VALIDATION_ERROR)
        val device = deviceRepository.findById(deviceId)
            ?.takeIf { it.userId == userId && it.isActive }
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED)

        // Before the write, so the unique index never sees two holders; the repository serialises
        // concurrent registrations of the same token until this transaction ends.
        val detached = deviceRepository.detachPushTokenFromOtherDevices(token, keepDeviceId = device.id)

        // An unusable or absent tag keeps what the device registered last rather than clearing it:
        // the token rotation callback on the client has no language to report, and losing the
        // language every time Firebase rotates a token would put the device back on the fallback.
        val resolvedLocale = normalizeDeviceLocale(locale) ?: device.locale
        deviceRepository.save(device.copy(pushToken = token, locale = resolvedLocale))
        log.info(
            "Push token registered: userId={}, deviceId={}, locale={}, detachedFromOtherDevices={}",
            userId, deviceId, resolvedLocale, detached
        )
    }

    @Transactional
    override fun invalidatePushToken(pushToken: String) {
        val cleared = deviceRepository.clearPushToken(pushToken)
        log.info("Push token invalidated by provider: token={}..., devicesCleared={}", pushToken.take(TOKEN_LOG_PREFIX), cleared)
    }

    companion object {
        /** Enough to correlate with the provider's log line, too little to address the device. */
        private const val TOKEN_LOG_PREFIX = 10
    }
}
