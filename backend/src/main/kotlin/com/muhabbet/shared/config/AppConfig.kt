package com.muhabbet.shared.config

import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.LoginApprovalRepository
import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.TwoStepAttemptRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserDataQueryPort
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.auth.domain.service.AuthService
import com.muhabbet.auth.domain.service.ContactSyncService
import com.muhabbet.auth.domain.service.DeviceLinkingService
import com.muhabbet.auth.domain.service.LastSeenService
import com.muhabbet.auth.domain.service.LoginApprovalService
import com.muhabbet.auth.domain.service.TwoStepVerificationService
import com.muhabbet.auth.domain.service.UserDataService
import com.muhabbet.media.domain.port.out.MediaFileRepository
import com.muhabbet.media.domain.port.out.MediaStoragePort
import com.muhabbet.media.domain.port.out.ThumbnailPort
import com.muhabbet.media.domain.service.MediaObjectService
import com.muhabbet.media.domain.service.MediaService
import com.muhabbet.messaging.domain.port.out.BroadcastListRepository
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import com.muhabbet.messaging.domain.port.out.ChatFolderRepository
import com.muhabbet.messaging.domain.port.out.ChatWallpaperRepository
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.EncryptionKeyRepository
import com.muhabbet.messaging.domain.port.out.GroupEventRepository
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
import com.muhabbet.messaging.domain.port.out.MediaObjectPort
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PinnedMessageRepository
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import com.muhabbet.messaging.domain.port.out.ReactionRepository
import com.muhabbet.messaging.domain.port.out.StatusRepository
import com.muhabbet.messaging.domain.port.out.NotificationTextPort
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicyPort
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.service.PushNotificationComposer
import com.muhabbet.messaging.domain.service.ViewOnceService
import com.muhabbet.messaging.domain.service.BroadcastListService
import com.muhabbet.messaging.domain.service.CallHistoryService
import com.muhabbet.messaging.domain.service.ChatFolderService
import com.muhabbet.messaging.domain.service.PinnedMessageService
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
import com.muhabbet.messaging.domain.service.SearchService
import com.muhabbet.messaging.domain.service.StatusService
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EnableConfigurationProperties(
    JwtProperties::class,
    OtpProperties::class,
    SmsProperties::class,
    MediaProperties::class,
    MultiDeviceProperties::class,
    InviteLinkProperties::class,
    TwoStepProperties::class
)
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
        otpSender: OtpSender?,
        jwtProvider: JwtProvider,
        passwordEncoder: PasswordEncoder,
        otpProperties: OtpProperties,
        jwtProperties: JwtProperties,
        otpVerifier: OtpVerifier?,
        otpQuotaPort: OtpQuotaPort,
        firebaseTokenVerifier: FirebaseTokenVerifier,
        twoStepAttemptRepository: TwoStepAttemptRepository,
        twoStepProperties: TwoStepProperties
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
        otpVerifier = otpVerifier,
        otpMaxAttempts = otpProperties.maxAttempts,
        refreshTokenExpirySeconds = jwtProperties.refreshTokenExpiry,
        mockEnabled = otpProperties.mockEnabled,
        testNumbers = otpProperties.testNumbers.toSet(),
        otpQuotaPort = otpQuotaPort,
        firebaseTokenVerifier = firebaseTokenVerifier,
        twoStepAttemptRepository = twoStepAttemptRepository,
        twoStepMaxAttempts = twoStepProperties.maxAttempts,
        twoStepLockSeconds = twoStepProperties.lockSeconds
    )

    /**
     * The transaction boundary for `last_seen_at` (#402). Declared as a bean like every other
     * domain service so Spring proxies it — that proxy is the entire fix, because the WebSocket
     * adapter that calls it has no transaction of its own and the write is a `@Modifying` query.
     */
    @Bean
    fun lastSeenService(
        userRepository: UserRepository
    ): LastSeenService = LastSeenService(
        userRepository = userRepository
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
        userDataQueryPort: UserDataQueryPort,
        deviceRepository: DeviceRepository,
        loginApprovalRepository: LoginApprovalRepository,
        deviceLinkSessionRepository: DeviceLinkSessionRepository,
        phoneHashRepository: PhoneHashRepository
    ): UserDataService = UserDataService(
        userRepository = userRepository,
        refreshTokenRepository = refreshTokenRepository,
        userDataQueryPort = userDataQueryPort,
        deviceRepository = deviceRepository,
        loginApprovalRepository = loginApprovalRepository,
        deviceLinkSessionRepository = deviceLinkSessionRepository,
        phoneHashRepository = phoneHashRepository
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
        messageBroadcaster: MessageBroadcaster,
        ports: MessageServicePorts,
        transactions: TransactionRunner
    ): MessageService = MessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        userDirectory = ports.userDirectory,
        readReceiptPolicy = ports.readReceiptPolicy,
        blockPolicy = ports.blockPolicy,
        mediaAttachmentPolicy = ports.mediaAttachmentPolicy,
        transactions = transactions
    )

    /**
     * Opening a view-once message, kept out of [messageService] (#541). It is the one message
     * operation that reaches into object storage and the one that destroys something, and giving
     * it its own service means the class that handles every text message sent does not carry a
     * media dependency it never uses.
     */
    @Bean
    fun viewOnceService(
        messageRepository: MessageRepository,
        conversationRepository: ConversationRepository,
        mediaObjects: MediaObjectPort
    ): ViewOnceService = ViewOnceService(
        messageRepository = messageRepository,
        conversationRepository = conversationRepository,
        mediaObjects = mediaObjects
    )

    @Bean
    fun searchService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository
    ): SearchService = SearchService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository
    )

    @Bean
    fun groupService(
        conversationRepository: ConversationRepository,
        userRepository: UserRepository,
        messageBroadcaster: MessageBroadcaster,
        blockPolicy: BlockPolicyPort,
        transactions: TransactionRunner
    ): GroupService = GroupService(
        conversationRepository = conversationRepository,
        userRepository = userRepository,
        messageBroadcaster = messageBroadcaster,
        blockPolicy = blockPolicy,
        transactions = transactions
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

    /**
     * The destructive half of the media module, kept out of [mediaService] on purpose (#541) —
     * a class that both accepts uploads and deletes them invites an edit that does the second
     * while meaning the first, and `MediaService` already implements the three use cases it is
     * allowed.
     */
    @Bean
    fun mediaObjectService(
        mediaFileRepository: MediaFileRepository,
        mediaStoragePort: MediaStoragePort
    ): MediaObjectService = MediaObjectService(
        mediaFileRepository = mediaFileRepository,
        mediaStoragePort = mediaStoragePort
    )

    @Bean
    fun statusService(
        statusRepository: StatusRepository,
        conversationRepository: ConversationRepository,
        userDirectory: UserDirectoryPort,
        blockPolicy: BlockPolicyPort,
        mediaAttachmentPolicy: MediaAttachmentPolicyPort
    ): StatusService = StatusService(
        statusRepository = statusRepository,
        conversationRepository = conversationRepository,
        userDirectory = userDirectory,
        blockPolicy = blockPolicy,
        mediaAttachmentPolicy = mediaAttachmentPolicy
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
        pollVoteRepository: PollVoteRepository,
        conversationRepository: ConversationRepository
    ): PollService = PollService(
        messageRepository = messageRepository,
        pollVoteRepository = pollVoteRepository,
        conversationRepository = conversationRepository
    )

    @Bean
    fun reactionService(
        reactionRepository: ReactionRepository,
        messageRepository: MessageRepository,
        conversationRepository: ConversationRepository
    ): ReactionService = ReactionService(
        reactionRepository = reactionRepository,
        messageRepository = messageRepository,
        conversationRepository = conversationRepository
    )

    @Bean
    fun disappearingMessageService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        messageBroadcaster: MessageBroadcaster,
        transactions: TransactionRunner
    ): DisappearingMessageService = DisappearingMessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        transactions = transactions
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
        backupRepository: com.muhabbet.messaging.domain.port.out.BackupRepository,
        conversationRepository: ConversationRepository,
        messageRepository: com.muhabbet.messaging.domain.port.out.MessageRepository,
        backupArchivePort: com.muhabbet.messaging.domain.port.out.BackupArchivePort
    ): com.muhabbet.messaging.domain.service.BackupService =
        com.muhabbet.messaging.domain.service.BackupService(
            backupRepository = backupRepository,
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            backupArchivePort = backupArchivePort
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
        passwordEncoder: PasswordEncoder,
        twoStepAttemptRepository: TwoStepAttemptRepository,
        twoStepProperties: TwoStepProperties
    ): TwoStepVerificationService = TwoStepVerificationService(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        twoStepAttemptRepository = twoStepAttemptRepository,
        maxAttempts = twoStepProperties.maxAttempts,
        lockSeconds = twoStepProperties.lockSeconds
    )

    @Bean
    fun loginApprovalService(
        loginApprovalRepository: LoginApprovalRepository
    ): LoginApprovalService = LoginApprovalService(
        loginApprovalRepository = loginApprovalRepository
    )

    @Bean
    fun deviceLinkingService(
        deviceLinkSessionRepository: DeviceLinkSessionRepository,
        deviceRepository: DeviceRepository
    ): DeviceLinkingService = DeviceLinkingService(
        deviceLinkSessionRepository = deviceLinkSessionRepository,
        deviceRepository = deviceRepository
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
        communityRepository: CommunityRepository,
        conversationRepository: ConversationRepository,
        userDirectoryPort: UserDirectoryPort,
        blockPolicy: BlockPolicyPort
    ): CommunityService = CommunityService(
        communityRepository = communityRepository,
        conversationRepository = conversationRepository,
        userDirectoryPort = userDirectoryPort,
        blockPolicy = blockPolicy
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
        broadcastListRepository: BroadcastListRepository,
        userDirectoryPort: UserDirectoryPort
    ): BroadcastListService = BroadcastListService(
        broadcastListRepository = broadcastListRepository,
        userDirectoryPort = userDirectoryPort
    )

    @Bean
    fun chatFolderService(
        chatFolderRepository: ChatFolderRepository
    ): ChatFolderService = ChatFolderService(
        chatFolderRepository = chatFolderRepository
    )

    @Bean
    fun pinnedMessageService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        pinnedMessageRepository: PinnedMessageRepository
    ): PinnedMessageService = PinnedMessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        pinnedMessageRepository = pinnedMessageRepository
    )

    @Bean
    fun pushNotificationComposer(
        notificationTextPort: NotificationTextPort
    ): PushNotificationComposer = PushNotificationComposer(
        texts = notificationTextPort
    )
}
