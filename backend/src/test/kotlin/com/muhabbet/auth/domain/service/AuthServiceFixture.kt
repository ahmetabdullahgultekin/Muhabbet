package com.muhabbet.auth.domain.service

import com.muhabbet.auth.adapter.out.external.BCryptPasswordHasher
import com.muhabbet.auth.domain.model.OtpSettings
import com.muhabbet.auth.domain.port.`in`.AuthResult
import com.muhabbet.auth.domain.port.`in`.OtpResult
import com.muhabbet.auth.domain.port.`in`.TokenResult
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.auth.domain.port.out.PasswordHasher
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.TwoStepAttemptRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.InlineTransactionRunner
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.transaction.TransactionRunner
import java.util.UUID

/**
 * The sign-in and session services wired together exactly as `AppConfig` wires them (#894), behind
 * the constructor the old `AuthService` had.
 *
 * The behavioural suites for sign-in were written against one `AuthService` and are kept as they
 * were: what they pin — cooldowns, quotas, the two-step gate, token rotation — did not change when
 * the class was split, and rewriting them per service would have changed the tests and the code in
 * the same commit. Building the real services here, from the same out-ports, is what keeps them
 * meaningful: every call below lands on the production class that now owns it.
 */
class AuthServiceFixture(
    userRepository: UserRepository,
    otpRepository: OtpRepository,
    deviceRepository: DeviceRepository,
    refreshTokenRepository: RefreshTokenRepository,
    phoneHashRepository: PhoneHashRepository,
    otpSender: OtpSender? = null,
    jwtProvider: JwtProvider,
    passwordHasher: PasswordHasher = BCryptPasswordHasher(),
    otpLength: Int = 6,
    otpExpirySeconds: Int = 300,
    otpCooldownSeconds: Int = 60,
    otpMaxAttempts: Int = 5,
    refreshTokenExpirySeconds: Long = 2592000,
    mockEnabled: Boolean = false,
    otpVerifier: OtpVerifier? = null,
    testNumbers: Set<String> = emptySet(),
    otpQuotaPort: OtpQuotaPort,
    firebaseTokenVerifier: FirebaseTokenVerifier,
    twoStepAttemptRepository: TwoStepAttemptRepository,
    twoStepMaxAttempts: Int = 5,
    twoStepLockSeconds: Long = 900,
    transactions: TransactionRunner = InlineTransactionRunner()
) {
    private val sessionTokens = SessionTokens(jwtProvider, refreshTokenRepository, refreshTokenExpirySeconds)
    private val gate = TwoStepSignInGate(passwordHasher, twoStepAttemptRepository, twoStepMaxAttempts, twoStepLockSeconds)
    private val signIn = PhoneSignIn(userRepository, phoneHashRepository, deviceRepository, sessionTokens)

    val otp = OtpAuthService(
        userRepository = userRepository,
        otpRepository = otpRepository,
        otpSender = otpSender,
        otpVerifier = otpVerifier,
        passwordHasher = passwordHasher,
        otpQuotaPort = otpQuotaPort,
        twoStepGate = gate,
        phoneSignIn = signIn,
        settings = OtpSettings(
            length = otpLength,
            expirySeconds = otpExpirySeconds,
            cooldownSeconds = otpCooldownSeconds,
            maxAttempts = otpMaxAttempts,
            mockEnabled = mockEnabled,
            testNumbers = testNumbers
        ),
        transactions = transactions
    )
    val firebase = FirebaseSignInService(firebaseTokenVerifier, userRepository, gate, signIn, transactions)
    val tokens = TokenService(refreshTokenRepository, userRepository, sessionTokens, transactions)
    val devices = DeviceRegistrationService(deviceRepository, transactions)

    fun requestOtp(phoneNumber: String): OtpResult = otp.requestOtp(phoneNumber)

    fun verifyOtp(
        phoneNumber: String,
        otp: String,
        deviceName: String,
        platform: String,
        twoStepPin: String? = null
    ): AuthResult = this.otp.verifyOtp(phoneNumber, otp, deviceName, platform, twoStepPin)

    fun verifyFirebaseToken(idToken: String, deviceName: String, platform: String, twoStepPin: String? = null): AuthResult =
        firebase.verifyFirebaseToken(idToken, deviceName, platform, twoStepPin)

    fun refresh(refreshToken: String): TokenResult = tokens.refresh(refreshToken)

    fun logout(userId: UUID, deviceId: UUID) = tokens.logout(userId, deviceId)

    fun registerPushToken(userId: UUID, deviceId: UUID, pushToken: String, locale: String? = null) =
        devices.registerPushToken(userId, deviceId, pushToken, locale)
}
