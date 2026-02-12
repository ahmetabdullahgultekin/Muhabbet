package com.muhabbet.shared.config

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.auth.domain.service.AuthService
import com.muhabbet.auth.domain.service.ContactSyncService
import com.muhabbet.media.domain.port.out.MediaFileRepository
import com.muhabbet.media.domain.port.out.MediaStoragePort
import com.muhabbet.media.domain.port.out.ThumbnailPort
import com.muhabbet.media.domain.service.MediaService
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.service.MessagingService
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EnableConfigurationProperties(JwtProperties::class, OtpProperties::class, SmsProperties::class, MediaProperties::class)
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
        refreshTokenExpirySeconds = jwtProperties.refreshTokenExpiry,
        mockEnabled = otpProperties.mockEnabled
    )

    @Bean
    fun contactSyncService(
        phoneHashRepository: PhoneHashRepository,
        userRepository: UserRepository
    ): ContactSyncService = ContactSyncService(
        phoneHashRepository = phoneHashRepository,
        userRepository = userRepository
    )

    @Bean
    @Primary
    fun messagingService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        userRepository: UserRepository,
        messageBroadcaster: MessageBroadcaster,
        eventPublisher: ApplicationEventPublisher
    ): MessagingService = MessagingService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        userRepository = userRepository,
        messageBroadcaster = messageBroadcaster,
        eventPublisher = eventPublisher
    )

    @Bean
    fun manageGroupUseCase(messagingService: MessagingService): ManageGroupUseCase = messagingService

    @Bean
    fun manageMessageUseCase(messagingService: MessagingService): ManageMessageUseCase = messagingService

    @Bean
    fun mediaService(
        mediaStoragePort: MediaStoragePort,
        mediaFileRepository: MediaFileRepository,
        thumbnailPort: ThumbnailPort,
        mediaProperties: MediaProperties
    ): MediaService = MediaService(
        mediaStoragePort = mediaStoragePort,
        mediaFileRepository = mediaFileRepository,
        thumbnailPort = thumbnailPort,
        thumbnailWidth = mediaProperties.thumbnailWidth,
        thumbnailHeight = mediaProperties.thumbnailHeight
    )
}
