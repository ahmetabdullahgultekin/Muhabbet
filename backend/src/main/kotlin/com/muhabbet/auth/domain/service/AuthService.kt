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
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.OtpVerifier
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
    /** Absent when an [otpVerifier] owns delivery; see the init block. */
    private val otpSender: OtpSender? = null,
    private val jwtProvider: JwtProvider,
    private val passwordEncoder: PasswordEncoder,
    private val otpLength: Int = 6,
    private val otpExpirySeconds: Int = 300,
    private val otpCooldownSeconds: Int = 60,
    private val otpMaxAttempts: Int = 5,
    private val refreshTokenExpirySeconds: Long = 2592000,
    private val mockEnabled: Boolean = false,
    /**
     * Present only when an external verification provider is configured. When set, it owns code
     * generation and checking; [otpSender] and the local hash comparison are bypassed.
     */
    private val otpVerifier: OtpVerifier? = null,
    /**
     * Numbers that bypass the SMS provider: the code is generated here and written to the log.
     * See [com.muhabbet.shared.config.OtpProperties.testNumbers] for why this exists and why it is
     * not a fixed code. Empty by default.
     */
    private val testNumbers: Set<String> = emptySet(),
    /**
     * Budget for verifications *started*, because each one is billed. Required rather than
     * defaulted: a no-op default would silently remove the only ceiling on the provider bill, which
     * is the failure #440 is about.
     */
    private val otpQuotaPort: OtpQuotaPort
) : RequestOtpUseCase, VerifyOtpUseCase, RefreshTokenUseCase, LogoutUseCase, RegisterPushTokenUseCase, FirebaseVerifyUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    init {
        // Exactly one delivery path must exist. Neither means every login silently fails at the
        // first OTP; both means the configuration is ambiguous about who owns the code. Failing
        // here stops the context with a message naming the property, instead of a bare
        // NoSuchBeanDefinitionException on OtpSender.
        require(otpSender != null || otpVerifier != null) {
            "No OTP delivery configured: muhabbet.sms.provider matched neither an OtpSender " +
                "(mock/netgsm/twilio) nor an OtpVerifier (twilio-verify)"
        }
        require(otpSender == null || otpVerifier == null) {
            "Both an OtpSender and an OtpVerifier are configured; muhabbet.sms.provider must " +
                "select exactly one"
        }

        // A test number skips the SMS provider entirely, so anyone able to add one to the config
        // could turn a real person's number into an account they can sign into by reading the log.
        // Restricting the list to +90500 — a range BTK has not allocated, so no handset can ever
        // hold one — makes that impossible rather than merely discouraged. Failing at startup is
        // the point: a typo must stop the deployment, not quietly widen the door.
        testNumbers.forEach { number ->
            require(number.startsWith(TEST_NUMBER_PREFIX)) {
                "muhabbet.otp.test-numbers may only contain unallocated $TEST_NUMBER_PREFIX numbers, " +
                    "so a real subscriber can never be listed; got ${number.takeLast(4)}"
            }
            require(ValidationRules.isValidTurkishPhone(number)) {
                "muhabbet.otp.test-numbers contains a malformed number ending ${number.takeLast(4)}"
            }
        }
        require(testNumbers.size <= MAX_TEST_NUMBERS) {
            "muhabbet.otp.test-numbers holds ${testNumbers.size} entries; at most $MAX_TEST_NUMBERS " +
                "is a test fleet, more is a bypass"
        }
        if (testNumbers.isNotEmpty()) {
            log.warn(
                "OTP bypass active for {} test number(s); their codes are written to this log and " +
                    "never sent by SMS",
                testNumbers.size
            )
        }
    }

    private fun isTestNumber(phoneNumber: String) = phoneNumber in testNumbers

    @Transactional
    override fun requestOtp(phoneNumber: String): OtpResult {
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

        // Checked after the cooldown so a client hammering one number spends its own cooldown
        // rather than the deployment's hourly budget, and before anything is generated or stored so
        // a refusal costs nothing. Test numbers are exempt: they never reach the provider, so they
        // cost nothing and must not be able to exhaust the budget real users share.
        if (!isTestNumber(phoneNumber) && !otpQuotaPort.tryConsume(phoneNumber)) {
            throw BusinessException(ErrorCode.AUTH_OTP_RATE_LIMIT)
        }

        // With an external verifier the code never exists on this side, so there is nothing to
        // generate or hash. The record is still written — cooldown, expiry and attempt limits are
        // ours either way. The stored hash is a sentinel that no input can match, so a mis-wired
        // branch fails closed rather than accepting an unchecked code.
        // A test number always takes the local path, whichever provider is configured: the code is
        // generated here, hashed here, and checked here.
        val local = otpVerifier == null || isTestNumber(phoneNumber)
        val otp = if (local) generateOtp() else null
        val otpHash = otp?.let { passwordEncoder.encode(it) ?: "" } ?: EXTERNALLY_VERIFIED

        val otpRequest = OtpRequest(
            phoneNumber = phoneNumber,
            otpHash = otpHash,
            expiresAt = Instant.now().plusSeconds(otpExpirySeconds.toLong())
        )
        otpRepository.save(otpRequest)

        when {
            // Nothing is sent anywhere. The code reaches the log and nowhere else, so signing in as
            // a test account requires server access rather than a handset.
            isTestNumber(phoneNumber) ->
                log.warn("TEST NUMBER {} — OTP not sent, code is {}", phoneNumber, otp)
            otpVerifier != null -> otpVerifier.start(phoneNumber)
            else -> otpSender!!.send(phoneNumber, otp!!)
        }
        log.info("OTP requested for phone={}", phoneNumber.takeLast(4))

        return OtpResult(
            ttlSeconds = otpExpirySeconds,
            retryAfterSeconds = otpCooldownSeconds,
            mockCode = if (mockEnabled) otp else null
        )
    }

    @Transactional
    override fun verifyOtp(
        phoneNumber: String,
        otp: String,
        deviceName: String,
        platform: String
    ): AuthResult {
        val activeOtp = otpRepository.findActiveByPhoneNumber(phoneNumber)
            ?: throw BusinessException(ErrorCode.AUTH_OTP_EXPIRED)

        // Claiming an attempt and enforcing the limit are the same statement, so concurrent verifies
        // cannot each read an under-limit count and all be granted a guess.
        if (!otpRepository.claimAttempt(activeOtp, otpMaxAttempts)) {
            throw BusinessException(ErrorCode.AUTH_OTP_MAX_ATTEMPTS)
        }

        // Must mirror requestOtp's branch exactly. Asking the verifier about a test number would
        // always fail — it was never told to start one — and comparing a hash for a normal number
        // would always fail too, since the stored value is the EXTERNALLY_VERIFIED sentinel.
        val accepted = if (otpVerifier != null && !isTestNumber(phoneNumber)) {
            otpVerifier.check(phoneNumber, otp)
        } else {
            passwordEncoder.matches(otp, activeOtp.otpHash)
        }
        if (!accepted) {
            throw BusinessException(ErrorCode.AUTH_OTP_INVALID)
        }

        otpRepository.markVerified(activeOtp)

        return authenticatePhone(phoneNumber, deviceName, platform, "OTP verified")
    }

    @Transactional
    override fun verifyFirebaseToken(
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
    override fun refresh(refreshToken: String): TokenResult {
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
            expiresIn = jwtProvider.accessTokenExpirySeconds,
            userId = record.userId.toString(),
            deviceId = record.deviceId.toString()
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
    override fun logout(userId: UUID, deviceId: UUID) {
        refreshTokenRepository.revokeAllForDevice(userId, deviceId)
        log.info("Logout: userId={}, deviceId={}", userId, deviceId)
    }

    private fun generateOtp(): String {
        val bound = Math.pow(10.0, otpLength.toDouble()).toInt()
        val code = secureRandom.nextInt(bound)
        return code.toString().padStart(otpLength, '0')
    }

    companion object {
        /**
         * Stored in place of a hash when an external verifier owns the code. BCrypt never produces
         * this, so [PasswordEncoder.matches] can only ever return false against it.
         */
        internal const val EXTERNALLY_VERIFIED = "externally-verified"

        /**
         * BTK has not allocated `+90 500`, so no subscriber can hold one. Confining test numbers to
         * this range is what makes the SMS bypass safe: the worst a mistake can do is create an
         * account nobody could have owned anyway.
         */
        internal const val TEST_NUMBER_PREFIX = "+90500"

        /** A handful is a test fleet. A long list is a bypass wearing a test fleet's name. */
        internal const val MAX_TEST_NUMBERS = 10

        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}
