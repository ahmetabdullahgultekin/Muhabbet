package com.muhabbet.shared

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DevicePlatform
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.shared.security.JwtProvider
import java.util.UUID

/**
 * Bearer tokens for integration tests, backed by a real device row.
 *
 * Since #923 a signature-valid token is not enough: the REST filter checks the token's device
 * against the database, so a token for a random device id — which is what these tests used to
 * mint — is correctly refused. The user must already exist (devices.user_id is a foreign key).
 */
object TestSessions {

    fun bearer(jwtProvider: JwtProvider, deviceRepository: DeviceRepository, userId: UUID): String {
        val device = deviceRepository.save(Device(userId = userId, platform = DevicePlatform.ANDROID))
        return "Bearer " + jwtProvider.generateAccessToken(userId, device.id, device.tokenVersion)
    }
}
