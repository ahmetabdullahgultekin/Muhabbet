package com.muhabbet.shared.config

import com.muhabbet.auth.domain.model.OtpSettings
import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.LoginApprovalRepository
import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.TwoStepAttemptRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.auth.domain.port.out.PasswordHasher
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserDataQueryPort
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.auth.domain.service.DeviceRegistrationService
import com.muhabbet.auth.domain.service.FirebaseSignInService
import com.muhabbet.auth.domain.service.OtpAuthService
import com.muhabbet.auth.domain.service.PhoneSignIn
import com.muhabbet.auth.domain.service.SessionTokens
import com.muhabbet.auth.domain.service.TokenService
import com.muhabbet.auth.domain.service.TwoStepSignInGate
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
import com.muhabbet.shared.transaction.TransactionRunner
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
import com.muhabbet.messaging.domain.service.CommunityGroupService
import com.muhabbet.messaging.domain.service.CommunityMembershipService
import com.muhabbet.messaging.domain.service.CommunityService
import com.muhabbet.messaging.domain.service.ConversationService
import com.muhabbet.messaging.domain.service.DisappearingMessageService
import com.muhabbet.messaging.domain.service.EncryptionService
import com.muhabbet.messaging.domain.service.GroupEventService
import com.muhabbet.messaging.domain.service.GroupService
import com.muhabbet.messaging.domain.service.InviteLinkService
import com.muhabbet.messaging.domain.service.JoinRequestService
import com.muhabbet.messaging.domain.service.DeliveryReceiptService
import com.muhabbet.messaging.domain.service.MessageEditService
import com.muhabbet.messaging.domain.service.MessageHistoryService
import com.muhabbet.messaging.domain.service.ScheduledDeliveryService
import com.muhabbet.messaging.domain.service.SendMessageService
import com.muhabbet.messaging.domain.service.PollService
import com.muhabbet.messaging.domain.service.ReactionService
import com.muhabbet.messaging.domain.service.SearchService
import com.muhabbet.messaging.domain.service.StatusService
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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

    // ─── Sign-in and sessions (#894: one service per concern) ─────────

    @Bean
    fun sessionTokens(
        jwtProvider: JwtProvider,
        refreshTokenRepository: RefreshTokenRepository,
        jwtProperties: JwtProperties
    ): SessionTokens = SessionTokens(
        jwtProvider = jwtProvider,
        refreshTokenRepository = refreshTokenRepository,
        refreshTokenExpirySeconds = jwtProperties.refreshTokenExpiry
    )

    @Bean
    fun twoStepSignInGate(
        passwordHasher: PasswordHasher,
        twoStepAttemptRepository: TwoStepAttemptRepository,
        twoStepProperties: TwoStepProperties
    ): TwoStepSignInGate = TwoStepSignInGate(
        passwordHasher = passwordHasher,
        twoStepAttemptRepository = twoStepAttemptRepository,
        maxAttempts = twoStepProperties.maxAttempts,
        lockSeconds = twoStepProperties.lockSeconds
    )

    @Bean
    fun phoneSignIn(
        userRepository: UserRepository,
        phoneHashRepository: PhoneHashRepository,
        deviceRepository: DeviceRepository,
        sessionTokens: SessionTokens
    ): PhoneSignIn = PhoneSignIn(
        userRepository = userRepository,
        phoneHashRepository = phoneHashRepository,
        deviceRepository = deviceRepository,
        sessionTokens = sessionTokens
    )

    @Bean
    fun otpAuthService(
        userRepository: UserRepository,
        otpRepository: OtpRepository,
        otpSender: OtpSender?,
        otpVerifier: OtpVerifier?,
        passwordHasher: PasswordHasher,
        otpQuotaPort: OtpQuotaPort,
        twoStepSignInGate: TwoStepSignInGate,
        phoneSignIn: PhoneSignIn,
        otpProperties: OtpProperties,
        transactions: TransactionRunner
    ): OtpAuthService = OtpAuthService(
        userRepository = userRepository,
        otpRepository = otpRepository,
        otpSender = otpSender,
        otpVerifier = otpVerifier,
        passwordHasher = passwordHasher,
        otpQuotaPort = otpQuotaPort,
        twoStepGate = twoStepSignInGate,
        phoneSignIn = phoneSignIn,
        settings = OtpSettings(
            length = otpProperties.length,
            expirySeconds = otpProperties.expirySeconds,
            cooldownSeconds = otpProperties.cooldownSeconds,
            maxAttempts = otpProperties.maxAttempts,
            mockEnabled = otpProperties.mockEnabled,
            testNumbers = otpProperties.testNumbers.toSet()
        ),
        transactions = transactions
    )

    @Bean
    fun firebaseSignInService(
        firebaseTokenVerifier: FirebaseTokenVerifier,
        userRepository: UserRepository,
        twoStepSignInGate: TwoStepSignInGate,
        phoneSignIn: PhoneSignIn,
        transactions: TransactionRunner
    ): FirebaseSignInService = FirebaseSignInService(
        firebaseTokenVerifier = firebaseTokenVerifier,
        userRepository = userRepository,
        twoStepGate = twoStepSignInGate,
        phoneSignIn = phoneSignIn,
        transactions = transactions
    )

    @Bean
    fun tokenService(
        refreshTokenRepository: RefreshTokenRepository,
        userRepository: UserRepository,
        sessionTokens: SessionTokens,
        transactions: TransactionRunner
    ): TokenService = TokenService(
        refreshTokenRepository = refreshTokenRepository,
        userRepository = userRepository,
        sessionTokens = sessionTokens,
        transactions = transactions
    )

    @Bean
    fun deviceRegistrationService(
        deviceRepository: DeviceRepository,
        transactions: TransactionRunner
    ): DeviceRegistrationService = DeviceRegistrationService(
        deviceRepository = deviceRepository,
        transactions = transactions
    )

    /**
     * The transaction boundary for `last_seen_at` (#402). The WebSocket adapter that calls it has
     * no transaction of its own and the write is a `@Modifying` query, so the service opens one
     * through [TransactionRunner] (ADR 0014).
     */
    @Bean
    fun lastSeenService(
        userRepository: UserRepository,
        transactions: TransactionRunner
    ): LastSeenService = LastSeenService(
        userRepository = userRepository,
        transactions = transactions
    )

    @Bean
    fun contactSyncService(
        phoneHashRepository: PhoneHashRepository,
        userRepository: UserRepository,
        transactions: TransactionRunner
    ): ContactSyncService = ContactSyncService(
        phoneHashRepository = phoneHashRepository,
        userRepository = userRepository,
        transactions = transactions
    )

    @Bean
    fun userDataService(
        userRepository: UserRepository,
        refreshTokenRepository: RefreshTokenRepository,
        userDataQueryPort: UserDataQueryPort,
        deviceRepository: DeviceRepository,
        loginApprovalRepository: LoginApprovalRepository,
        deviceLinkSessionRepository: DeviceLinkSessionRepository,
        phoneHashRepository: PhoneHashRepository,
        transactions: TransactionRunner
    ): UserDataService = UserDataService(
        userRepository = userRepository,
        refreshTokenRepository = refreshTokenRepository,
        userDataQueryPort = userDataQueryPort,
        deviceRepository = deviceRepository,
        loginApprovalRepository = loginApprovalRepository,
        deviceLinkSessionRepository = deviceLinkSessionRepository,
        phoneHashRepository = phoneHashRepository,
        transactions = transactions
    )

    @Bean
    fun conversationService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        userRepository: UserRepository,
        transactions: TransactionRunner
    ): ConversationService = ConversationService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        userRepository = userRepository,
        transactions = transactions
    )

    /**
     * The message services, one per concern (#894). They share their out-ports through
     * [MessageServicePorts] and nothing else; the rules they have in common are small domain
     * policies each one composes, not a base class.
     */
    @Bean
    fun sendMessageService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        messageBroadcaster: MessageBroadcaster,
        ports: MessageServicePorts,
        transactions: TransactionRunner
    ): SendMessageService = SendMessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        blockPolicy = ports.blockPolicy,
        mediaAttachmentPolicy = ports.mediaAttachmentPolicy,
        transactions = transactions
    )

    @Bean
    fun messageHistoryService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        transactions: TransactionRunner
    ): MessageHistoryService = MessageHistoryService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        transactions = transactions
    )

    @Bean
    fun deliveryReceiptService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        messageBroadcaster: MessageBroadcaster,
        ports: MessageServicePorts,
        transactions: TransactionRunner
    ): DeliveryReceiptService = DeliveryReceiptService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        userDirectory = ports.userDirectory,
        readReceiptPolicy = ports.readReceiptPolicy,
        transactions = transactions
    )

    @Bean
    fun messageEditService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        messageBroadcaster: MessageBroadcaster,
        ports: MessageServicePorts,
        transactions: TransactionRunner
    ): MessageEditService = MessageEditService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        blockPolicy = ports.blockPolicy,
        transactions = transactions
    )

    @Bean
    fun scheduledDeliveryService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        messageBroadcaster: MessageBroadcaster,
        ports: MessageServicePorts,
        transactions: TransactionRunner
    ): ScheduledDeliveryService = ScheduledDeliveryService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        blockPolicy = ports.blockPolicy,
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
        mediaObjects: MediaObjectPort,
        transactions: TransactionRunner
    ): ViewOnceService = ViewOnceService(
        messageRepository = messageRepository,
        conversationRepository = conversationRepository,
        mediaObjects = mediaObjects,
        transactions = transactions
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
        mediaAttachmentPolicy: MediaAttachmentPolicyPort,
        transactions: TransactionRunner
    ): StatusService = StatusService(
        statusRepository = statusRepository,
        conversationRepository = conversationRepository,
        userDirectory = userDirectory,
        blockPolicy = blockPolicy,
        mediaAttachmentPolicy = mediaAttachmentPolicy,
        transactions = transactions
    )

    @Bean
    fun channelService(
        conversationRepository: ConversationRepository,
        transactions: TransactionRunner
    ): ChannelService = ChannelService(
        conversationRepository = conversationRepository,
        transactions = transactions
    )

    @Bean
    fun pollService(
        messageRepository: MessageRepository,
        pollVoteRepository: PollVoteRepository,
        conversationRepository: ConversationRepository,
        transactions: TransactionRunner
    ): PollService = PollService(
        messageRepository = messageRepository,
        pollVoteRepository = pollVoteRepository,
        conversationRepository = conversationRepository,
        transactions = transactions
    )

    @Bean
    fun reactionService(
        reactionRepository: ReactionRepository,
        messageRepository: MessageRepository,
        conversationRepository: ConversationRepository,
        transactions: TransactionRunner
    ): ReactionService = ReactionService(
        reactionRepository = reactionRepository,
        messageRepository = messageRepository,
        conversationRepository = conversationRepository,
        transactions = transactions
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
        encryptionKeyRepository: EncryptionKeyRepository,
        transactions: TransactionRunner
    ): EncryptionService = EncryptionService(
        encryptionKeyRepository = encryptionKeyRepository,
        transactions = transactions
    )

    @Bean
    fun callSignalingService(
        callHistoryRepository: CallHistoryRepository
    ): CallSignalingService = CallSignalingService(
        callHistoryRepository = callHistoryRepository
    )

    @Bean
    fun callHistoryService(
        callHistoryRepository: CallHistoryRepository,
        transactions: TransactionRunner
    ): CallHistoryService = CallHistoryService(
        callHistoryRepository = callHistoryRepository,
        transactions = transactions
    )

    // ─── Phase 2: Content Moderation ──────────────────

    @Bean
    fun moderationService(
        reportRepository: com.muhabbet.moderation.domain.port.out.ReportRepository,
        blockRepository: com.muhabbet.moderation.domain.port.out.BlockRepository,
        transactions: TransactionRunner
    ): com.muhabbet.moderation.domain.service.ModerationService =
        com.muhabbet.moderation.domain.service.ModerationService(
            reportRepository = reportRepository,
            blockRepository = blockRepository,
            transactions = transactions
        )

    // ─── Phase 4: Message Backup ──────────────────────

    @Bean
    fun backupService(
        backupRepository: com.muhabbet.messaging.domain.port.out.BackupRepository,
        conversationRepository: ConversationRepository,
        messageRepository: com.muhabbet.messaging.domain.port.out.MessageRepository,
        backupArchivePort: com.muhabbet.messaging.domain.port.out.BackupArchivePort,
        transactions: TransactionRunner
    ): com.muhabbet.messaging.domain.service.BackupService =
        com.muhabbet.messaging.domain.service.BackupService(
            backupRepository = backupRepository,
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            backupArchivePort = backupArchivePort,
            transactions = transactions
        )

    // ─── Phase 6: Channel Analytics ───────────────────

    @Bean
    fun channelAnalyticsService(
        channelAnalyticsRepository: com.muhabbet.messaging.domain.port.out.ChannelAnalyticsRepository,
        conversationRepository: ConversationRepository,
        transactions: TransactionRunner
    ): com.muhabbet.messaging.domain.service.ChannelAnalyticsService =
        com.muhabbet.messaging.domain.service.ChannelAnalyticsService(
            analyticsRepository = channelAnalyticsRepository,
            conversationRepository = conversationRepository,
            transactions = transactions
        )

    // ─── Phase 6: Bot Platform ────────────────────────

    @Bean
    fun botService(
        botRepository: com.muhabbet.messaging.domain.port.out.BotRepository,
        userRepository: UserRepository,
        transactions: TransactionRunner
    ): com.muhabbet.messaging.domain.service.BotService =
        com.muhabbet.messaging.domain.service.BotService(
            botRepository = botRepository,
            userRepository = userRepository,
            transactions = transactions
        )

    // ─── WhatsApp Feature Parity ──────────────────────

    @Bean
    fun twoStepVerificationService(
        userRepository: UserRepository,
        passwordHasher: PasswordHasher,
        twoStepAttemptRepository: TwoStepAttemptRepository,
        twoStepProperties: TwoStepProperties,
        transactions: TransactionRunner
    ): TwoStepVerificationService = TwoStepVerificationService(
        userRepository = userRepository,
        passwordHasher = passwordHasher,
        twoStepAttemptRepository = twoStepAttemptRepository,
        maxAttempts = twoStepProperties.maxAttempts,
        lockSeconds = twoStepProperties.lockSeconds,
        transactions = transactions
    )

    @Bean
    fun loginApprovalService(
        loginApprovalRepository: LoginApprovalRepository,
        transactions: TransactionRunner
    ): LoginApprovalService = LoginApprovalService(
        loginApprovalRepository = loginApprovalRepository,
        transactions = transactions
    )

    @Bean
    fun deviceLinkingService(
        deviceLinkSessionRepository: DeviceLinkSessionRepository,
        deviceRepository: DeviceRepository,
        transactions: TransactionRunner
    ): DeviceLinkingService = DeviceLinkingService(
        deviceLinkSessionRepository = deviceLinkSessionRepository,
        deviceRepository = deviceRepository,
        transactions = transactions
    )

    @Bean
    fun inviteLinkService(
        inviteLinkRepository: GroupInviteLinkRepository,
        joinRequestRepository: GroupJoinRequestRepository,
        conversationRepository: ConversationRepository,
        transactions: TransactionRunner
    ): InviteLinkService = InviteLinkService(
        inviteLinkRepository = inviteLinkRepository,
        joinRequestRepository = joinRequestRepository,
        conversationRepository = conversationRepository,
        transactions = transactions
    )

    @Bean
    fun joinRequestService(
        joinRequestRepository: GroupJoinRequestRepository,
        conversationRepository: ConversationRepository,
        inviteLinkRepository: GroupInviteLinkRepository,
        transactions: TransactionRunner
    ): JoinRequestService = JoinRequestService(
        joinRequestRepository = joinRequestRepository,
        conversationRepository = conversationRepository,
        inviteLinkRepository = inviteLinkRepository,
        transactions = transactions
    )

    @Bean
    fun communityService(
        communityRepository: CommunityRepository,
        conversationRepository: ConversationRepository,
        transactions: TransactionRunner
    ): CommunityService = CommunityService(
        communityRepository = communityRepository,
        conversationRepository = conversationRepository,
        transactions = transactions
    )

    @Bean
    fun communityGroupService(
        communityRepository: CommunityRepository,
        conversationRepository: ConversationRepository,
        transactions: TransactionRunner
    ): CommunityGroupService = CommunityGroupService(
        communityRepository = communityRepository,
        conversationRepository = conversationRepository,
        transactions = transactions
    )

    @Bean
    fun communityMembershipService(
        communityRepository: CommunityRepository,
        conversationRepository: ConversationRepository,
        userDirectoryPort: UserDirectoryPort,
        blockPolicy: BlockPolicyPort,
        transactions: TransactionRunner
    ): CommunityMembershipService = CommunityMembershipService(
        communityRepository = communityRepository,
        conversationRepository = conversationRepository,
        userDirectoryPort = userDirectoryPort,
        blockPolicy = blockPolicy,
        transactions = transactions
    )

    @Bean
    fun groupEventService(
        groupEventRepository: GroupEventRepository,
        conversationRepository: ConversationRepository,
        transactions: TransactionRunner
    ): GroupEventService = GroupEventService(
        groupEventRepository = groupEventRepository,
        conversationRepository = conversationRepository,
        transactions = transactions
    )

    @Bean
    fun chatWallpaperService(
        chatWallpaperRepository: ChatWallpaperRepository,
        transactions: TransactionRunner
    ): ChatWallpaperService = ChatWallpaperService(
        chatWallpaperRepository = chatWallpaperRepository,
        transactions = transactions
    )

    @Bean
    fun broadcastListService(
        broadcastListRepository: BroadcastListRepository,
        userDirectoryPort: UserDirectoryPort,
        transactions: TransactionRunner
    ): BroadcastListService = BroadcastListService(
        broadcastListRepository = broadcastListRepository,
        userDirectoryPort = userDirectoryPort,
        transactions = transactions
    )

    @Bean
    fun chatFolderService(
        chatFolderRepository: ChatFolderRepository,
        transactions: TransactionRunner
    ): ChatFolderService = ChatFolderService(
        chatFolderRepository = chatFolderRepository,
        transactions = transactions
    )

    @Bean
    fun pinnedMessageService(
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        pinnedMessageRepository: PinnedMessageRepository,
        transactions: TransactionRunner
    ): PinnedMessageService = PinnedMessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        pinnedMessageRepository = pinnedMessageRepository,
        transactions = transactions
    )

    @Bean
    fun pushNotificationComposer(
        notificationTextPort: NotificationTextPort
    ): PushNotificationComposer = PushNotificationComposer(
        texts = notificationTextPort
    )
}
