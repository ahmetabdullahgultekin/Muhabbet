package com.muhabbet.shared.config

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.LoginApprovalRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserDataQueryPort
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.auth.domain.service.AuthService
import com.muhabbet.auth.domain.service.ContactSyncService
import com.muhabbet.auth.domain.service.LoginApprovalService
import com.muhabbet.auth.domain.service.TwoStepVerificationService
import com.muhabbet.auth.domain.service.UserDataService
import com.muhabbet.media.domain.port.out.MediaFileRepository
import com.muhabbet.media.domain.port.out.MediaStoragePort
import com.muhabbet.media.domain.port.out.ThumbnailPort
import com.muhabbet.media.domain.service.MediaService
import com.muhabbet.messaging.domain.port.out.BroadcastListRepository
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import com.muhabbet.messaging.domain.port.out.ChatWallpaperRepository
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.EncryptionKeyRepository
import com.muhabbet.messaging.domain.port.out.GroupEventRepository
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import com.muhabbet.messaging.domain.port.out.ReactionRepository
import com.muhabbet.messaging.domain.port.out.StatusRepository
import com.muhabbet.messaging.domain.service.BroadcastListService
import com.muhabbet.messaging.domain.service.CallHistoryService
import com.muhabbet.messaging.domain.service.CallSignalingService
import com.muhabbet.messaging.domain.service.ChannelService
import com.muhabbet.messaging.domain.service.ChatWallpaperService
import com.muhabbet.messaging.domain.service.CommunityService
import com.muhabbet.messaging.domain.service.ConversationService
import com.muhabbet.messaging.domain.service.DisappearingMessageService
import com.muhabbet.messaging.domain.service.EncryptionService
import com.muhabbet.messaging.domain.service.GroupEventService
import com.muhabbet.messaging.domain.service.GroupService
import com.muhabbet.messaging.domain.service.InviteLinkService
import com.muhabbet.messaging.domain.service.JoinRequestService
import com.muhabbet.messaging.domain.service.MessageService
import com.muhabbet.messaging.domain.service.PollService
import com.muhabbet.messaging.domain.service.ReactionService
import com.muhabbet.messaging.domain.service.StatusService
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    fun userDataService(
        userRepository: UserRepository,
        refreshTokenRepository: RefreshTokenRepository,
        userDataQueryPort: UserDataQueryPort
    ): UserDataService = UserDataService(
        userRepository = userRepository,
        refreshTokenRepository = refreshTokenRepository,
        userDataQueryPort = userDataQueryPort
    )

    @Bean
    fun conversationService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        userRepository: UserRepository
    ): ConversationService = ConversationService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        userRepository = userRepository
    )

    @Bean
    fun messageService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        messageBroadcaster: MessageBroadcaster
    ): MessageService = MessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster
    )

    @Bean
    fun groupService(
        conversationRepository: ConversationRepository,
        userRepository: UserRepository,
        messageBroadcaster: MessageBroadcaster
    ): GroupService = GroupService(
        conversationRepository = conversationRepository,
        userRepository = userRepository,
        messageBroadcaster = messageBroadcaster
    )

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

    @Bean
    fun statusService(
        statusRepository: StatusRepository
    ): StatusService = StatusService(
        statusRepository = statusRepository
    )

    @Bean
    fun channelService(
        conversationRepository: ConversationRepository
    ): ChannelService = ChannelService(
        conversationRepository = conversationRepository
    )

    @Bean
    fun pollService(
        messageRepository: MessageRepository,
        pollVoteRepository: PollVoteRepository
    ): PollService = PollService(
        messageRepository = messageRepository,
        pollVoteRepository = pollVoteRepository
    )

    @Bean
    fun reactionService(
        reactionRepository: ReactionRepository
    ): ReactionService = ReactionService(
        reactionRepository = reactionRepository
    )

    @Bean
    fun disappearingMessageService(
        conversationRepository: ConversationRepository
    ): DisappearingMessageService = DisappearingMessageService(
        conversationRepository = conversationRepository
    )

    @Bean
    fun encryptionService(
        encryptionKeyRepository: EncryptionKeyRepository
    ): EncryptionService = EncryptionService(
        encryptionKeyRepository = encryptionKeyRepository
    )

    @Bean
    fun callSignalingService(
        callHistoryRepository: CallHistoryRepository
    ): CallSignalingService = CallSignalingService(
        callHistoryRepository = callHistoryRepository
    )

    @Bean
    fun callHistoryService(
        callHistoryRepository: CallHistoryRepository
    ): CallHistoryService = CallHistoryService(
        callHistoryRepository = callHistoryRepository
    )

    // ─── Phase 2: Content Moderation ──────────────────

    @Bean
    fun moderationService(
        reportRepository: com.muhabbet.moderation.domain.port.out.ReportRepository,
        blockRepository: com.muhabbet.moderation.domain.port.out.BlockRepository
    ): com.muhabbet.moderation.domain.service.ModerationService =
        com.muhabbet.moderation.domain.service.ModerationService(
            reportRepository = reportRepository,
            blockRepository = blockRepository
        )

    // ─── Phase 4: Message Backup ──────────────────────

    @Bean
    fun backupService(
        backupRepository: com.muhabbet.messaging.domain.port.out.BackupRepository
    ): com.muhabbet.messaging.domain.service.BackupService =
        com.muhabbet.messaging.domain.service.BackupService(
            backupRepository = backupRepository
        )

    // ─── Phase 6: Channel Analytics ───────────────────

    @Bean
    fun channelAnalyticsService(
        channelAnalyticsRepository: com.muhabbet.messaging.domain.port.out.ChannelAnalyticsRepository,
        conversationRepository: ConversationRepository
    ): com.muhabbet.messaging.domain.service.ChannelAnalyticsService =
        com.muhabbet.messaging.domain.service.ChannelAnalyticsService(
            analyticsRepository = channelAnalyticsRepository,
            conversationRepository = conversationRepository
        )

    // ─── Phase 6: Bot Platform ────────────────────────

    @Bean
    fun botService(
        botRepository: com.muhabbet.messaging.domain.port.out.BotRepository,
        userRepository: UserRepository
    ): com.muhabbet.messaging.domain.service.BotService =
        com.muhabbet.messaging.domain.service.BotService(
            botRepository = botRepository,
            userRepository = userRepository
        )

    // ─── WhatsApp Feature Parity ──────────────────────

    @Bean
    fun twoStepVerificationService(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder
    ): TwoStepVerificationService = TwoStepVerificationService(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder
    )

    @Bean
    fun loginApprovalService(
        loginApprovalRepository: LoginApprovalRepository
    ): LoginApprovalService = LoginApprovalService(
        loginApprovalRepository = loginApprovalRepository
    )

    @Bean
    fun inviteLinkService(
        inviteLinkRepository: GroupInviteLinkRepository,
        joinRequestRepository: GroupJoinRequestRepository,
        conversationRepository: ConversationRepository
    ): InviteLinkService = InviteLinkService(
        inviteLinkRepository = inviteLinkRepository,
        joinRequestRepository = joinRequestRepository,
        conversationRepository = conversationRepository
    )

    @Bean
    fun joinRequestService(
        joinRequestRepository: GroupJoinRequestRepository,
        conversationRepository: ConversationRepository,
        inviteLinkRepository: GroupInviteLinkRepository
    ): JoinRequestService = JoinRequestService(
        joinRequestRepository = joinRequestRepository,
        conversationRepository = conversationRepository,
        inviteLinkRepository = inviteLinkRepository
    )

    @Bean
    fun communityService(
        communityRepository: CommunityRepository
    ): CommunityService = CommunityService(
        communityRepository = communityRepository
    )

    @Bean
    fun groupEventService(
        groupEventRepository: GroupEventRepository,
        conversationRepository: ConversationRepository
    ): GroupEventService = GroupEventService(
        groupEventRepository = groupEventRepository,
        conversationRepository = conversationRepository
    )

    @Bean
    fun chatWallpaperService(
        chatWallpaperRepository: ChatWallpaperRepository
    ): ChatWallpaperService = ChatWallpaperService(
        chatWallpaperRepository = chatWallpaperRepository
    )

    @Bean
    fun broadcastListService(
        broadcastListRepository: BroadcastListRepository
    ): BroadcastListService = BroadcastListService(
        broadcastListRepository = broadcastListRepository
    )
}
