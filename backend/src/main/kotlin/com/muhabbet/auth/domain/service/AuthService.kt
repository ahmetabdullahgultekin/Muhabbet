package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.`in`.AuthResult
import com.muhabbet.auth.domain.port.`in`.FirebaseVerifyUseCase
import com.muhabbet.auth.domain.port.`in`.LogoutUseCase
import com.muhabbet.auth.domain.port.`in`.OtpResult
import com.muhabbet.auth.domain.port.`in`.RefreshTokenUseCase
import com.muhabbet.auth.domain.port.`in`.RegisterPushTokenUseCase
import com.muhabbet.auth.domain.port.`in`.RequestOtpUseCase
import com.muhabbet.auth.domain.port.`in`.TokenResult
import com.muhabbet.auth.domain.port.`in`.VerifyOtpUseCase
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

open class AuthService(
    private val userRepository: UserRepository,
    private val otpRepository: OtpRepository,
    private val deviceRepository: DeviceRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val phoneHashRepository: PhoneHashRepository,
    private val otpSender: OtpSender,
    private val jwtProvider: JwtProvider,
    private val passwordEncoder: PasswordEncoder,
    private val otpLength: Int = 6,
    private val otpExpirySeconds: Int = 300,
    private val otpCooldownSeconds: Int = 60,
    private val otpMaxAttempts: Int = 5,
    private val refreshTokenExpirySeconds: Long = 2592000,
    private val mockEnabled: Boolean = false
) : RequestOtpUseCase, VerifyOtpUseCase, RefreshTokenUseCase, LogoutUseCase, RegisterPushTokenUseCase, FirebaseVerifyUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    @Transactional
    override suspend fun requestOtp(phoneNumber: String): OtpResult {
        if (!ValidationRules.isValidTurkishPhone(phoneNumber)) {
            throw BusinessException(ErrorCode.AUTH_INVALID_PHONE)
        }

        // Check cooldown
        val activeOtp = otpRepository.findActiveByPhoneNumber(phoneNumber)
        if (activeOtp != null) {
            val elapsed = Duration.between(activeOtp.createdAt, Instant.now())
            if (elapsed.seconds < otpCooldownSeconds) {
                throw BusinessException(ErrorCode.AUTH_OTP_COOLDOWN)
            }
        }

        // Generate OTP
        val otp = generateOtp()
        val otpHash = passwordEncoder.encode(otp) ?: ""

        val otpRequest = OtpRequest(
            phoneNumber = phoneNumber,
            otpHash = otpHash,
            expiresAt = Instant.now().plusSeconds(otpExpirySeconds.toLong())
        )
        otpRepository.save(otpRequest)

        // Send OTP
        otpSender.send(phoneNumber, otp)
        log.info("OTP requested for phone={}", phoneNumber.takeLast(4))

        return OtpResult(
            ttlSeconds = otpExpirySeconds,
            retryAfterSeconds = otpCooldownSeconds,
            mockCode = if (mockEnabled) otp else null
        )
    }

    @Transactional
    override suspend fun verifyOtp(
        phoneNumber: String,
        otp: String,
        deviceName: String,
        platform: String
    ): AuthResult {
        val activeOtp = otpRepository.findActiveByPhoneNumber(phoneNumber)
            ?: throw BusinessException(ErrorCode.AUTH_OTP_EXPIRED)

        if (activeOtp.attempts >= otpMaxAttempts) {
            throw BusinessException(ErrorCode.AUTH_OTP_MAX_ATTEMPTS)
        }

        otpRepository.incrementAttempts(activeOtp)

        if (!passwordEncoder.matches(otp, activeOtp.otpHash)) {
            throw BusinessException(ErrorCode.AUTH_OTP_INVALID)
        }

        otpRepository.markVerified(activeOtp)

        return authenticatePhone(phoneNumber, deviceName, platform, "OTP verified")
    }

    @Transactional
    override suspend fun verifyFirebaseToken(
        idToken: String,
        deviceName: String,
        platform: String
    ): AuthResult {
        val decodedToken = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().verifyIdToken(idToken)
        } catch (e: Exception) {
            log.warn("Firebase token verification failed: {}", e.message)
            throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "Firebase token geçersiz")
        }

        val phoneNumber = decodedToken.claims["phone_number"] as? String
            ?: throw BusinessException(ErrorCode.AUTH_INVALID_PHONE, "Telefon numarası bulunamadı")

        if (!ValidationRules.isValidTurkishPhone(phoneNumber)) {
            throw BusinessException(ErrorCode.AUTH_INVALID_PHONE)
        }

        return authenticatePhone(phoneNumber, deviceName, platform, "Firebase verified")
    }

    private fun authenticatePhone(
        phoneNumber: String,
        deviceName: String,
        platform: String,
        logPrefix: String
    ): AuthResult {
        // Find or create user
        val existingUser = userRepository.findByPhoneNumber(phoneNumber)
        val isNewUser = existingUser == null
        val user = existingUser ?: userRepository.save(
            User(
                id = UUID.randomUUID(),
                phoneNumber = phoneNumber,
                status = UserStatus.ACTIVE
            )
        )

        // Store phone hash for contact sync
        if (isNewUser) {
            val phoneHash = sha256(phoneNumber)
            phoneHashRepository.save(user.id, phoneHash)
            log.info("New user created: userId={}", user.id)
        }

        // Find or create device
        val existingDevice = deviceRepository.findByUserIdAndPlatform(user.id, platform)
        val device = if (existingDevice != null) {
            deviceRepository.save(
                existingDevice.copy(
                    deviceName = deviceName,
                    lastActiveAt = Instant.now()
                )
            )
        } else {
            deviceRepository.save(
                Device(
                    userId = user.id,
                    platform = platform,
                    deviceName = deviceName,
                    lastActiveAt = Instant.now(),
                    isPrimary = deviceRepository.findByUserId(user.id).isEmpty()
                )
            )
        }

        // Generate tokens
        val accessToken = jwtProvider.generateAccessToken(user.id, device.id)
        val refreshToken = jwtProvider.generateRefreshToken()
        val refreshTokenHash = sha256(refreshToken)

        refreshTokenRepository.save(
            RefreshTokenRecord(
                userId = user.id,
                deviceId = device.id,
                tokenHash = refreshTokenHash,
                expiresAt = Instant.now().plusSeconds(refreshTokenExpirySeconds)
            )
        )

        log.info("{}: userId={}, deviceId={}, isNewUser={}", logPrefix, user.id, device.id, isNewUser)

        return AuthResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtProvider.accessTokenExpirySeconds,
            userId = user.id.toString(),
            deviceId = device.id.toString(),
            isNewUser = isNewUser
        )
    }

    @Transactional
    override suspend fun refresh(refreshToken: String): TokenResult {
        val tokenHash = sha256(refreshToken)
        val record = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID)

        if (record.revokedAt != null) {
            throw BusinessException(ErrorCode.AUTH_TOKEN_REVOKED)
        }

        if (record.expiresAt.isBefore(Instant.now())) {
            throw BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED)
        }

        // Revoke old refresh token (rotation)
        refreshTokenRepository.revokeByTokenHash(tokenHash)

        // Generate new tokens
        val newAccessToken = jwtProvider.generateAccessToken(record.userId, record.deviceId)
        val newRefreshToken = jwtProvider.generateRefreshToken()
        val newRefreshTokenHash = sha256(newRefreshToken)

        refreshTokenRepository.save(
            RefreshTokenRecord(
                userId = record.userId,
                deviceId = record.deviceId,
                tokenHash = newRefreshTokenHash,
                expiresAt = Instant.now().plusSeconds(refreshTokenExpirySeconds)
            )
        )

        log.info("Token refreshed: userId={}, deviceId={}", record.userId, record.deviceId)

        return TokenResult(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            expiresIn = jwtProvider.accessTokenExpirySeconds
        )
    }

    @Transactional
    override fun registerPushToken(userId: UUID, deviceId: UUID, pushToken: String) {
        val devices = deviceRepository.findByUserId(userId)
        val device = devices.find { it.id == deviceId }
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Cihaz bulunamadı")
        deviceRepository.save(device.copy(pushToken = pushToken))
        log.info("Push token registered: userId={}, deviceId={}", userId, deviceId)
    }

    @Transactional
    override suspend fun logout(userId: UUID, deviceId: UUID) {
        refreshTokenRepository.revokeAllForDevice(userId, deviceId)
        log.info("Logout: userId={}, deviceId={}", userId, deviceId)
    }

    private fun generateOtp(): String {
        val bound = Math.pow(10.0, otpLength.toDouble()).toInt()
        val code = secureRandom.nextInt(bound)
        return code.toString().padStart(otpLength, '0')
    }

    companion object {
        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}
