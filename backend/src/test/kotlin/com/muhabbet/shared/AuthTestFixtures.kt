package com.muhabbet.shared

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier
import com.muhabbet.auth.domain.port.out.OtpChannel
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.TwoStepAttemptRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.auth.domain.service.AuthService
import com.muhabbet.auth.domain.service.SessionRevocation
import com.muhabbet.auth.domain.service.SignInSessionIssuer
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.transaction.TransactionRunner
import io.mockk.every
import io.mockk.mockk
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/** One place that knows how an [AuthService] is assembled, so its tests state only what differs. */
object AuthTestFixtures {

    const val SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256"

    fun jwtProperties() = JwtProperties(
        secret = SECRET,
        accessTokenExpiry = 900,
        refreshTokenExpiry = 2592000,
        issuer = "muhabbet-test"
    )

    fun jwtProvider(properties: JwtProperties = jwtProperties()) = JwtProvider(properties, MockEnvironment())

    /**
     * A budget that always allows. Relaxed mocks answer false for Boolean, which would make every
     * test fail on the quota rather than on what it tests; the quota itself is covered by
     * RedisOtpQuotaAdapterTest against a real Redis.
     */
    fun allowingQuota(): OtpQuotaPort = mockk<OtpQuotaPort>(relaxed = true).also {
        every { it.tryConsume(any()) } returns true
    }

    @Suppress("LongParameterList")
    fun authService(
        otpChannel: OtpChannel,
        userRepository: UserRepository = mockk(relaxed = true),
        otpRepository: OtpRepository = mockk(relaxed = true),
        deviceRepository: DeviceRepository = mockk(relaxed = true),
        refreshTokenRepository: RefreshTokenRepository = mockk(relaxed = true),
        phoneHashRepository: PhoneHashRepository = mockk(relaxed = true),
        passwordEncoder: PasswordEncoder = BCryptPasswordEncoder(),
        jwtProvider: JwtProvider = jwtProvider(),
        transactions: TransactionRunner = InlineTransactionRunner(),
        otpQuotaPort: OtpQuotaPort = allowingQuota(),
        firebaseTokenVerifier: FirebaseTokenVerifier = NoFirebaseTokenVerifier,
        twoStepAttemptRepository: TwoStepAttemptRepository = InMemoryTwoStepAttemptRepository(),
        testNumbers: Set<String> = emptySet(),
        mockEnabled: Boolean = false
    ): AuthService = AuthService(
        userRepository = userRepository,
        otpRepository = otpRepository,
        deviceRepository = deviceRepository,
        phoneHashRepository = phoneHashRepository,
        otpChannel = otpChannel,
        passwordEncoder = passwordEncoder,
        sessionIssuer = SignInSessionIssuer(
            deviceRepository = deviceRepository,
            refreshTokenRepository = refreshTokenRepository,
            sessionRevocation = SessionRevocation(refreshTokenRepository, deviceRepository),
            jwtProvider = jwtProvider,
            refreshTokenExpirySeconds = 2592000
        ),
        transactions = transactions,
        otpLength = 6,
        otpExpirySeconds = 300,
        otpCooldownSeconds = 60,
        otpMaxAttempts = 5,
        mockEnabled = mockEnabled,
        testNumbers = testNumbers,
        otpQuotaPort = otpQuotaPort,
        firebaseTokenVerifier = firebaseTokenVerifier,
        twoStepAttemptRepository = twoStepAttemptRepository,
        twoStepMaxAttempts = 5,
        twoStepLockSeconds = 900
    )
}
