package com.muhabbet.shared.config

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.auth.domain.service.AuthService
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EnableConfigurationProperties(JwtProperties::class, OtpProperties::class)
class AppConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authService(
        userRepository: UserRepository,
        otpRepository: OtpRepository,
        deviceRepository: DeviceRepository,
        refreshTokenRepository: RefreshTokenRepository,
        phoneHashRepository: PhoneHashRepository,
        otpSender: OtpSender,
        jwtProvider: JwtProvider,
        passwordEncoder: PasswordEncoder,
        otpProperties: OtpProperties,
        jwtProperties: JwtProperties
    ): AuthService = AuthService(
        userRepository = userRepository,
        otpRepository = otpRepository,
        deviceRepository = deviceRepository,
        refreshTokenRepository = refreshTokenRepository,
        phoneHashRepository = phoneHashRepository,
        otpSender = otpSender,
        jwtProvider = jwtProvider,
        passwordEncoder = passwordEncoder,
        otpLength = otpProperties.length,
        otpExpirySeconds = otpProperties.expirySeconds,
        otpCooldownSeconds = otpProperties.cooldownSeconds,
        otpMaxAttempts = otpProperties.maxAttempts,
        refreshTokenExpirySeconds = jwtProperties.refreshTokenExpiry
    )
}
