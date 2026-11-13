package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.normalizeDeviceLocale
import com.muhabbet.auth.domain.port.`in`.RegisterPushTokenUseCase
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Registering where a device can be reached for push, and in which language (#894).
 */
class DeviceRegistrationService(
    private val deviceRepository: DeviceRepository,
    private val transactions: TransactionRunner
) : RegisterPushTokenUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun registerPushToken(userId: UUID, deviceId: UUID, pushToken: String, locale: String?) {
        val resolvedLocale = transactions.inTransaction {
            // Looked up among the caller's own devices, so a device id from someone else's account
            // is indistinguishable from one that does not exist.
            val device = deviceRepository.findByUserId(userId).find { it.id == deviceId }
                ?: throw BusinessException(ErrorCode.DEVICE_NOT_FOUND)
            // An unusable or absent tag keeps what the device registered last rather than clearing
            // it: the token rotation callback on the client has no language to report, and losing
            // the language every time Firebase rotates a token would put the device back on the
            // fallback.
            val resolved = normalizeDeviceLocale(locale) ?: device.locale
            deviceRepository.save(device.copy(pushToken = pushToken, locale = resolved))
            resolved
        }
        log.info("Push token registered: userId={}, deviceId={}, locale={}", userId, deviceId, resolvedLocale)
    }
}
