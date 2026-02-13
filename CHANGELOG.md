# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added — Phase 5: iOS Platform Foundation
- **AudioPlayer.ios.kt**: Real AVAudioPlayer implementation with play/pause/stop/seekTo, progress tracking via coroutine
- **AudioRecorder.ios.kt**: AVAudioRecorder implementation with M4A output, AVAudioSession permission checking
- **ContactsProvider.ios.kt**: CNContactStore implementation with `enumerateContactsWithFetchRequest`, permission request
- **PushTokenProvider.ios.kt**: UNUserNotificationCenter permission request, `registerForRemoteNotifications()`, cached token pattern

### Added — Phase 4: E2E Encryption Architecture
- **Encryption key exchange**: `POST /api/v1/encryption/keys` (upload key bundle), `GET /api/v1/encryption/keys/{userId}` (fetch pre-key bundle)
- **Domain models**: `EncryptionKeyBundle`, `OneTimePreKey` — ready for Signal Protocol (X3DH, Double Ratchet)
- **EncryptionService**: Implements `ManageEncryptionUseCase` — key bundle CRUD, one-time pre-key consumption
- **Persistence**: `EncryptionKeyJpaEntity`, `OneTimePreKeyJpaEntity`, Spring Data repos, persistence adapter
- **Migration**: `V11__add_encryption_keys.sql` — `encryption_keys` + `one_time_pre_keys` tables

### Added — Phase 4: KVKK Compliance
- **Data export**: `GET /api/v1/users/data/export` — returns all user data (profile, messages, media, conversations)
- **Account deletion**: `DELETE /api/v1/users/data/account` — soft-deletes account with `deleted_at` timestamp
- **UserDataService**: Implements `ManageUserDataUseCase` — aggregates data from multiple repositories
- **UserDataQueryPort**: Out-port interface for cross-module data aggregation

### Added — Phase 3: Call Signaling Infrastructure
- **WebSocket call messages**: `CallInitiate`, `CallAnswer`, `CallIceCandidate`, `CallEnd` in WsMessage sealed class
- **CallSignalingService**: In-memory call state management, routes signaling messages between participants
- **Call history**: `GET /api/v1/calls/history` — persisted call records (caller, callee, status, duration)
- **CallHistoryService**: Implements `GetCallHistoryUseCase`
- **Migration**: `V12__add_call_history.sql` — `call_history` table
- **ChatWebSocketHandler**: Extended to handle call signaling frame types

### Added — Phase 3: Notification Improvements
- **Notification grouping**: Messages from same conversation grouped under one notification
- **Inline reply**: Reply directly from notification without opening app (Android `NotificationReplyReceiver`)
- **Notification channels**: Separate channels for messages, calls, system notifications

### Added — Phase 3: Crash Reporting (Sentry)
- **CrashReporter expect/actual**: Common interface, Android Sentry implementation, iOS stub
- **Sentry Android SDK**: `io.sentry:sentry-android:7.14.0`, auto-init via AndroidManifest meta-data
- **CrashReporter.init()**: Called in App.kt on launch, sets user ID from token storage
- **SENTRY_DSN**: Configurable via environment variable → manifest placeholder

### Changed — Phase 2: Architecture Refactoring
- **ChatScreen.kt refactored**: 1,771 → 405 lines — extracted `MessageBubble.kt`, `MessageInputPane.kt`, `ChatDialogs.kt`
- **MessagingService split**: Deleted monolithic `MessagingService`, replaced with `ConversationService` + `MessageService` + `GroupService`
- **5 controllers refactored to use case pattern**: `StatusController`, `ChannelController`, `PollController`, `ReactionController`, `DisappearingMessageController` — all now depend on use case interfaces instead of Spring Data repositories
- **AppConfig expanded**: Wires 11+ services as `@Bean` (ConversationService, MessageService, GroupService, StatusService, ChannelService, PollService, ReactionService, DisappearingMessageService, EncryptionService, CallHistoryService, UserDataService)
- **New use case interfaces**: `ManageStatusUseCase`, `ManageChannelUseCase`, `ManagePollUseCase`, `ManageReactionUseCase`, `ManageDisappearingMessageUseCase`, `ManageEncryptionUseCase`, `GetCallHistoryUseCase`, `ManageUserDataUseCase`
- **New out-port interfaces**: `StatusRepository`, `ReactionRepository`, `PollVoteRepository`, `EncryptionKeyRepository`, `CallHistoryRepository`, `UserDataQueryPort`

### Added — Phase 2: Stickers & GIFs
- **GiphyClient**: GIPHY API client — search/trending for GIFs and stickers, public beta key
- **GifStickerPicker**: Modal bottom sheet with tab row (GIF | Stickers), debounced search, 3-column grid, GIPHY attribution
- **ContentType expansion**: Added `STICKER` and `GIF` to shared + backend ContentType enums
- **MessageBubble**: GIF renders as full-width async image (max 200dp), sticker renders at 150dp without bubble background
- **MessageInputPane**: GIF menu item in attachment dropdown

### Added — Phase 2: Backend Tests (~125 total)
- **MediaServiceTest**: Upload, download URL generation, thumbnail, validation, error cases
- **ConversationServiceTest**: Create DM (dedup), create group, list conversations, pagination
- **GroupServiceTest**: Add/remove members, role management, leave group, owner transfer
- **ChatWebSocketHandlerTest**: Message send/ack, typing indicators, invalid frames, auth
- **RateLimitFilterTest**: Rate limiting on auth endpoints, sliding window, IP-based

### Added — Media Sharing (Week 5)
- **Backend media module**: Hexagonal architecture — `MediaService`, `MinioMediaStorageAdapter`, `JavaImageThumbnailAdapter`, `MediaController`
- **Image upload**: `POST /api/v1/media/upload` (multipart) — validates type/size, generates thumbnail (320x320), uploads to MinIO, returns pre-signed URLs
- **Pre-signed URL refresh**: `GET /api/v1/media/{mediaId}/url` — returns fresh URLs when old ones expire
- **Nginx MinIO proxy**: `/muhabbet-media/` proxied to MinIO with `Host minio:9000` for pre-signed URL signature validation
- **Mobile image picker**: Platform `expect/actual` — Android uses `PickVisualMedia`, iOS stubbed
- **Image compression**: Client-side JPEG compression (max 1280px, quality 80) before upload
- **Image bubbles**: AsyncImage (Coil 3) with thumbnail in chat, tap for full-size dialog viewer
- **Optimistic UI**: Shows local image immediately while uploading

### Added — Push Notifications (Week 5)
- **FCM integration**: `FcmPushNotificationAdapter` sends push to offline recipients on new message
- **Push token registration**: Mobile registers FCM token on app start via `PUT /api/v1/devices/push-token`
- **Android FCM service**: `MuhabbetFirebaseMessagingService` handles `onNewToken` and `onMessageReceived`

### Added — Presence Tracking (Week 5)
- **Redis presence**: `RedisPresenceAdapter` — `presence:{userId}` keys with 60s TTL, refreshed by heartbeat/ping
- **Online/offline broadcast**: `PresenceUpdate` WS messages sent to contacts on connect/disconnect
- **Last seen persistence**: `users.last_seen_at` column (V2 migration), updated on WS disconnect
- **Mobile online indicator**: Green dot on conversation list avatars for online users
- **Chat header subtitle**: "yazıyor..." (typing) > "çevrimiçi" (online) > "son görülme HH:mm" (last seen)
- **30s heartbeat**: WsClient sends `Ping` every 30s, backend refreshes Redis TTL on Ping/GoOnline

### Fixed — Week 5
- **Koin crash on Activity recreation**: Replaced `KoinApplication` with `GlobalContext` guard + `KoinContext`
- **WS send crash**: Wrapped all unguarded `wsClient.send()` calls in try-catch (typing indicators, READ acks)
- **Empty image bubbles**: Pre-signed URLs used internal `minio:9000` endpoint. Fixed with URL rewrite + nginx proxy
- **Contact sync not matching**: Phone numbers not normalized to E.164 before hashing. Added `normalizeToE164()` for Turkish formats (05XX, 5XX, 90XX)

### Added — Mobile App (Weeks 3-4)
- **Auth flow**: Phone input → OTP verify → auto-login with platform detection (expect/actual)
- **Conversation list**: Real-time WS updates, pull-to-refresh, unread badges, participant name resolution
- **Chat screen**: Real-time messaging via WebSocket, message status ticks (clock→single→double→blue), message pagination (cursor-based scroll-to-top), typing indicator send/receive
- **Settings screen**: Profile edit (displayName + about), app version, logout with confirmation dialog
- **Dark mode**: Auto-detects system theme, full light/dark color schemes
- **Contacts sync**: Android permission flow, device contact reading, SHA-256 phone hash, backend sync endpoint
- **Navigation**: Decompose-based (Root → Auth/Main → Conversations/Chat/Settings/NewConversation)
- **DI**: Koin modules for platform-specific implementations (TokenStorage, ContactsProvider, PlatformInfo)
- **Test Bot**: Python script (`infra/scripts/test_bot.py`) for automated WS message testing

### Fixed — Backend (Weeks 3-4)
- **Participant data**: ConversationController now populates displayName/phoneNumber from UserRepository (was hardcoded null)
- **Unread badge**: READ ack now bulk-updates ALL unread messages via `markConversationRead()` (was single message only)
- **Typing broadcast**: Backend now forwards typing indicators to conversation members via PresenceUpdate (was TODO/log-only)

### Added — Messaging Module (Week 2)
- **Domain models**: Conversation, ConversationMember, Message, MessageDeliveryStatus with enums (ConversationType, MemberRole, ContentType, DeliveryStatus)
- **Domain events**: MessageSentEvent, MessageDeliveredEvent
- **Use case ports**: CreateConversation, GetConversations, SendMessage, GetMessageHistory, UpdateDeliveryStatus
- **Repository ports**: ConversationRepository (with direct lookup dedup), MessageRepository (cursor pagination, delivery status)
- **MessagingService**: Full business logic — direct conversation dedup via sorted UUID lookup, group creation with owner role, idempotent message send, cursor-based pagination, delivery status broadcast
- **JPA persistence**: 5 JPA entities (ConversationJpaEntity, ConversationMemberJpaEntity, DirectConversationLookupJpaEntity, MessageJpaEntity, MessageDeliveryStatusJpaEntity), 5 Spring Data repos, 2 persistence adapters
- **WebSocket real-time**: ChatWebSocketHandler (JWT auth via query param, handles SendMessage/AckMessage/TypingIndicator/Ping), WebSocketSessionManager (ConcurrentHashMap-based), WebSocketConfig (/ws endpoint)
- **WebSocketMessageBroadcaster**: Delivers messages to online users via WebSocket, falls back to DB queueing for offline users
- **REST endpoints**: `POST /api/v1/conversations`, `GET /api/v1/conversations`, `GET /api/v1/conversations/{id}/messages`
- **Unit tests**: 16 MessagingService tests (MockK) covering conversation creation, message sending, pagination, delivery status, all error codes

### Added — Auth Module (Week 1)
- **Domain models**: User aggregate, Device entity, OtpRequest value object
- **Use case ports**: RequestOtp, VerifyOtp, RefreshToken, Logout
- **Repository ports**: UserRepository, OtpRepository, DeviceRepository, RefreshTokenRepository, PhoneHashRepository
- **AuthService**: Full business logic — OTP request with cooldown, OTP verify with BCrypt, token refresh with rotation, logout
- **JPA persistence**: 5 JPA entities with domain mappers, 5 Spring Data repos, 5 persistence adapters
- **MockOtpSender**: Logs OTP to console for development (conditional on `muhabbet.otp.mock-enabled`)
- **JWT security**: JwtProvider (HS256 via JJWT), JwtAuthFilter, SecurityConfig (stateless, CSRF disabled)
- **AuthenticatedUser**: Utility to extract userId/deviceId from SecurityContext
- **REST endpoints**: `POST /api/v1/auth/otp/request`, `otp/verify`, `token/refresh`, `logout`
- **User endpoints**: `GET /api/v1/users/me`, `PATCH /api/v1/users/me`
- **Phone hash**: SHA-256 hash stored on user creation for contact sync
- **Unit tests**: 9 AuthService tests (MockK) covering happy paths and all error codes
- **Integration test**: AuthControllerIntegrationTest with Testcontainers PostgreSQL

### Added — Shared KMP Module (Week 1)
- Domain models: Message, Conversation, UserProfile, Contact
- WebSocket protocol: WsMessage sealed class with all frame types
- DTOs: Auth, User, Conversation, Media request/response types
- ValidationRules: Turkish phone, OTP, display name, message length, media size
- EncryptionPort interface + NoOpEncryption (TLS-only MVP)

### Added — Project Setup (Week 1)
- Gradle multi-project build (backend, shared, mobile)
- Gradle wrapper 8.12
- Docker Compose: PostgreSQL 16 + Redis 7 + MinIO
- Flyway migration V1: users, devices, otp_requests, refresh_tokens, conversations, messages, media_files
- Application profiles: dev (mock OTP, debug SQL), prod (real SMS, JSON logs)
- ErrorCode enum with Turkish messages
- GlobalExceptionHandler + ApiResponseBuilder

### Fixed
- Android Gradle Plugin declared in root build.gradle.kts to fix shared module build
- JVM target 21 set for shared module's jvm() target
- Added kotlinx-datetime dependency to backend for shared model compatibility
