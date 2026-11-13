package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.`in`.AuthResult
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * The shared tail of every sign-in: once a phone number has been proved (by OTP or by Firebase) and
 * any second factor passed, find or create the account, find or register the device, and open a
 * session on it.
 *
 * Not a use case. It is only ever reached from one of the sign-in services, inside their
 * transaction, after their own checks — it trusts the number it is handed, so exposing it as a
 * port would be exposing a sign-in with no proof.
 */
class PhoneSignIn(
    private val userRepository: UserRepository,
    private val phoneHashRepository: PhoneHashRepository,
    private val deviceRepository: DeviceRepository,
    private val sessionTokens: SessionTokens
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @param existingUser the account the entry point has already read to decide the two-step gate.
     *   Passed in rather than read again: two reads of the same row would be one query more than the
     *   sign-in needs, and would let the gate and the session be decided about *different* states
     *   of the account.
     */
    fun complete(
        phoneNumber: String,
        deviceName: String,
        platform: String,
        logPrefix: String,
        existingUser: User?
    ): AuthResult {
        val isNewUser = existingUser == null
        val user = existingUser ?: createUser(phoneNumber)
        val device = registerDevice(user.id, platform, deviceName)
        val tokens = sessionTokens.issue(user.id, device.id, user.isAdmin)

        log.info("{}: userId={}, deviceId={}, isNewUser={}", logPrefix, user.id, device.id, isNewUser)

        return AuthResult(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresIn = tokens.expiresIn,
            userId = user.id.toString(),
            deviceId = device.id.toString(),
            isNewUser = isNewUser
        )
    }

    private fun createUser(phoneNumber: String): User {
        val user = userRepository.save(
            User(id = UUID.randomUUID(), phoneNumber = phoneNumber, status = UserStatus.ACTIVE)
        )
        // Stored for contact sync: other users find this account by the hash of its number.
        phoneHashRepository.save(user.id, sha256Hex(phoneNumber))
        log.info("New user created: userId={}", user.id)
        return user
    }

    private fun registerDevice(userId: UUID, platform: String, deviceName: String): Device {
        val existing = deviceRepository.findByUserIdAndPlatform(userId, platform)
        return if (existing != null) {
            deviceRepository.save(existing.copy(deviceName = deviceName, lastActiveAt = Instant.now()))
        } else {
            deviceRepository.save(
                Device(
                    userId = userId,
                    platform = platform,
                    deviceName = deviceName,
                    lastActiveAt = Instant.now(),
                    isPrimary = deviceRepository.findByUserId(userId).isEmpty()
                )
            )
        }
    }
}
