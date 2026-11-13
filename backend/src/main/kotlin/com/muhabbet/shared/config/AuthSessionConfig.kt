package com.muhabbet.shared.config

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.OtpChannel
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.auth.domain.service.SessionRevocation
import com.muhabbet.auth.domain.service.SessionService
import com.muhabbet.auth.domain.service.SignInSessionIssuer
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.transaction.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wiring for the auth session lifecycle (#880, #923, #886): how codes are delivered, how a sign-in
 * becomes a session, and how sessions rotate and end. Kept apart from [AppConfig] so the session
 * model reads as one unit.
 */
@Configuration
class AuthSessionConfig {

    /**
     * Exactly one delivery path must exist. Neither means every login silently fails at the first
     * OTP; both means the configuration is ambiguous about who owns the code. Failing here stops the
     * context with a message naming the property, instead of a bare NoSuchBeanDefinitionException.
     */
    @Bean
    fun otpChannel(otpSender: OtpSender?, otpVerifier: OtpVerifier?): OtpChannel = when {
        otpSender != null && otpVerifier != null -> error(
            "Both an OtpSender and an OtpVerifier are configured; muhabbet.sms.provider must select exactly one"
        )
        otpSender != null -> OtpChannel.LocalCode(otpSender)
        otpVerifier != null -> OtpChannel.ExternalVerifier(otpVerifier)
        else -> error(
            "No OTP delivery configured: muhabbet.sms.provider matched neither an OtpSender " +
                "(mock/netgsm/twilio) nor an OtpVerifier (twilio-verify)"
        )
    }

    @Bean
    fun sessionRevocation(
        refreshTokenRepository: RefreshTokenRepository,
        deviceRepository: DeviceRepository
    ): SessionRevocation = SessionRevocation(refreshTokenRepository, deviceRepository)

    @Bean
    fun signInSessionIssuer(
        deviceRepository: DeviceRepository,
        refreshTokenRepository: RefreshTokenRepository,
        sessionRevocation: SessionRevocation,
        jwtProvider: JwtProvider,
        jwtProperties: JwtProperties
    ): SignInSessionIssuer = SignInSessionIssuer(
        deviceRepository = deviceRepository,
        refreshTokenRepository = refreshTokenRepository,
        sessionRevocation = sessionRevocation,
        jwtProvider = jwtProvider,
        refreshTokenExpirySeconds = jwtProperties.refreshTokenExpiry
    )

    @Bean
    fun sessionService(
        refreshTokenRepository: RefreshTokenRepository,
        deviceRepository: DeviceRepository,
        userRepository: UserRepository,
        sessionRevocation: SessionRevocation,
        jwtProvider: JwtProvider,
        transactions: TransactionRunner,
        jwtProperties: JwtProperties
    ): SessionService = SessionService(
        refreshTokenRepository = refreshTokenRepository,
        deviceRepository = deviceRepository,
        userRepository = userRepository,
        sessionRevocation = sessionRevocation,
        jwtProvider = jwtProvider,
        transactions = transactions,
        refreshTokenExpirySeconds = jwtProperties.refreshTokenExpiry
    )
}
