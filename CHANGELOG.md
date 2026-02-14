# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Implemented — UI/UX Remediation (Feb 2026)
- **Design system tokens**: `MuhabbetSemanticColors` (statusOnline, statusRead, callDecline, callAccept, callMissed), `MuhabbetSpacing` (XSmall→XXLarge), `MuhabbetSizes` (touch targets, icons) — all via `CompositionLocalProvider`
- **Accessibility (P0 fixes)**: 28+ contentDescription fixes across MessageInputPane, ChatScreen, CallScreens, ConversationList, GroupInfo, SharedMedia, Settings, StarredMessages, NewConversation; 12 new localized string resources (TR+EN)
- **Touch targets**: VoiceBubble play button 36→48dp, ReactionBar emoji buttons 36→48dp
- **IME actions**: Phone input (Done), OTP input (Done), search fields (Search), message input (Send), GIF search (Search)
- **Hardcoded colors eliminated**: 8 `Color(0xFF...)` replaced with `LocalSemanticColors.current.*` in IncomingCallScreen, ActiveCallScreen, CallHistoryScreen, ConversationListScreen, UserProfileScreen, MessageInfoScreen
- **Skeleton loading**: ConversationListScreen shimmer placeholders replacing spinner
- **Edit mode banner**: Visually distinct `tertiaryContainer` background with larger icons and labels
- **TestTags**: `message_input`, `send_button`, `phone_input`, `phone_continue`, `otp_input`, `otp_verify`, `new_chat_fab`, `search_input`
- **KeyboardOptions**: Added to all text input fields across the app

### Added — Lead UI/UX Engineer Analysis (Feb 2026)
- **Comprehensive UI audit**: 34 files / 8,407 lines reviewed across 14 navigation destinations
- **Accessibility audit**: 28+ missing contentDescription violations, touch target sizing analysis, semantic annotation gaps
- **Design system assessment**: Hardcoded color inventory (8 violations), typography inconsistency catalog, spacing token recommendations
- **Interaction design review**: Strengths (swipe-to-reply, pinch-to-zoom, pull-to-refresh) and gaps (no skeleton loaders, search state reset, filter chip logic)
- **Localization verification**: 238/238 strings fully translated TR/EN, 2 minor hardcoded string violations
- **Performance analysis**: LazyColumn pagination patterns, image loading concerns, animation inventory
- **Testability audit**: Zero testTags, zero semantic annotations — critical gap for UI testing
- **6-phase remediation roadmap**: P0 accessibility (1d) → P1 design system (1d) → P1 interaction polish (1d) → P2 components (1d) → P2 testability (1.5d) → P3 tokens (1d)
- **Document**: `docs/qa/09-ui-ux-engineer-analysis.md` — 9th QA document in ISO/IEC 25010 series

### Added — WebRTC Voice Calls via LiveKit (Feb 2026)
- **CallRoomInfo WsMessage**: New `call.room` WS message type carrying `serverUrl`, `token`, `roomName` for LiveKit room connection
- **Backend room management**: `ChatWebSocketHandler` now creates LiveKit room + generates participant tokens on `CallAnswer(accepted=true)`, closes room on `CallEnd`
- **CallEngine expect/actual**: Platform abstraction for WebRTC — `connect(serverUrl, token)`, `disconnect()`, `setMuted()`, `setSpeaker()`
- **Android CallEngine**: `io.livekit:livekit-android:2.5.0` — connects to LiveKit room, manages audio tracks, handles mute/speaker
- **iOS CallEngine**: Stub (awaits LiveKit Swift SDK bridge)
- **ActiveCallScreen**: Wired to `CallEngine` — auto-connects on `CallRoomInfo`, disconnects on `CallEnd`/dispose, mute/speaker controls delegate to engine

### Added — Signal Protocol E2E Encryption (Feb 2026)
- **SignalKeyManager**: Implements `E2EKeyManager` using `org.signal:libsignal-android:0.64.1` — Curve25519 identity keys, signed pre-keys, one-time pre-keys, X3DH session initialization, Double Ratchet encrypt/decrypt
- **InMemorySignalProtocolStore**: Full `SignalProtocolStore` + `SenderKeyStore` implementation — identity, pre-key, signed pre-key, session, and sender key stores (in-memory for MVP)
- **SignalEncryption**: Implements `EncryptionPort` — delegates to `SignalKeyManager` with plaintext fallback when no session exists
- **E2ESetupService**: Post-login key registration — generates identity key pair, signed pre-key, 100 OTPKs, registers with backend via `EncryptionRepository`
- **Platform DI split**: Android provides `SignalKeyManager` + `SignalEncryption`, iOS provides `NoOpKeyManager` + `NoOpEncryption` (moved from `AppModule` to `PlatformModule`)
- **App.kt integration**: E2E key registration runs on app startup for logged-in users

### Added — QA Engineering & Tooling (Feb 2026)
- **JaCoCo code coverage**: Added to `backend/build.gradle.kts` with HTML/XML reports, coverage verification (30% min project, 60% min for domain services)
- **detekt static analysis**: Kotlin linter with project-specific rules (`backend/detekt.yml`), SARIF/HTML reports
- **ArchUnit architecture tests**: 13 tests in `HexagonalArchitectureTest` — domain independence, module boundaries, naming conventions, no Spring in domain
- **TestData factory**: Shared test data factory object (`com.muhabbet.shared.TestData`) with builders for User, Message, Conversation, Member, DeliveryStatus
- **Controller tests** (18 test files, 100+ tests): MessageController, ModerationController, UserDataController, ConversationController, GroupController, StatusController, ChannelController, PollController, EncryptionController, BackupController, BotController, ReactionController, DeviceController, ContactController, CallHistoryController, DisappearingMessageController, StarredMessageController, LinkPreviewController
- **CI pipeline**: JaCoCo coverage report + verification, detekt static analysis, artifact uploads (test results, coverage, detekt), PR coverage comments via jacoco-report action
- **k6 performance scripts**: `infra/k6/auth-load-test.js` (OTP flow), `infra/k6/api-load-test.js` (REST endpoints), `infra/k6/websocket-load-test.js` (WS connections) with P50/P95/P99 thresholds
- **QA documentation**: 8 ISO/IEC 25010 quality attribute documents in `docs/qa/` — updated with verified codebase metrics

### Added — Phase 6: Growth Features (Feb 2026)
- **Channel analytics**: `ChannelAnalyticsService` with daily stats (messages, views, reactions, shares), subscriber tracking, REST API at `GET /api/v1/channels/{channelId}/analytics` with date-range queries
- **Bot platform**: `Bot` domain model with `BotPermission` enum, `BotService` with secure API token generation (`mhb_` prefix + Base64), webhook support, permissions system, REST API at `/api/v1/bots` (CRUD, token regeneration, webhook management)
- **Bot JPA persistence**: `BotJpaEntity`, `BotPersistenceAdapter`, `SpringDataBotRepository` — full hexagonal chain
- **Channel analytics persistence**: `ChannelAnalyticsJpaEntity`, `ChannelAnalyticsPersistenceAdapter`, `SpringDataChannelAnalyticsRepository`

### Added — Phase 5: Horizontal Scaling (Feb 2026)
- **Redis Pub/Sub message broadcaster**: `RedisMessageBroadcaster` replaces in-memory broadcaster — publishes WS messages to `ws:broadcast:{userId}` Redis channels for cross-instance routing
- **Redis broadcast listener**: `RedisBroadcastListener` subscribes to `ws:broadcast:*` pattern, delivers messages to local WebSocket sessions, enabling multi-instance deployment

### Added — Phase 4: Message Backup (Feb 2026)
- **BackupService**: Implements `ManageBackupUseCase` — initiate backup, check status, list backups, delete backup
- **BackupController**: REST API at `/api/v1/backups` — `POST` (initiate), `GET` (list), `GET /{id}` (status), `DELETE /{id}`
- **Backup persistence**: `MessageBackupJpaEntity`, `BackupPersistenceAdapter`, `SpringDataBackupRepository`
- **BackupRepository out-port**: `MessageBackup` data class with status tracking (PENDING, IN_PROGRESS, COMPLETED, FAILED)

### Added — Phase 3: LiveKit Integration (Feb 2026)
- **CallRoomProvider out-port**: Interface for call room creation/token generation/room termination
- **LiveKitRoomAdapter**: LiveKit server SDK integration with `@ConditionalOnProperty(muhabbet.livekit.enabled)` — creates rooms, generates participant tokens, terminates rooms
- **NoOpCallRoomProvider**: Fallback when LiveKit is disabled — returns stub tokens and room IDs
- **Outgoing call initiation**: `MainComponent` now generates callId and opens ActiveCall screen on call button press
- **LiveKit configuration**: `muhabbet.livekit.*` properties in `application.yml` (enabled, api-key, api-secret, server-url)

### Added — Phase 2: Content Moderation (Feb 2026)
- **Moderation module**: Full hexagonal architecture — `UserReport` + `UserBlock` domain models, `ReportReason` enum (SPAM, HARASSMENT, INAPPROPRIATE_CONTENT, IMPERSONATION, OTHER), `ReportStatus` enum (PENDING, REVIEWED, RESOLVED, DISMISSED)
- **ModerationService**: Implements `ReportUserUseCase`, `BlockUserUseCase`, `ReviewReportsUseCase` — report users, block/unblock, admin review with status updates
- **ModerationController**: REST API at `/api/v1/moderation/reports` (CRUD), `/api/v1/moderation/blocks` (block/unblock/list), admin endpoints for report review
- **Moderation persistence**: `ReportJpaEntity`, `BlockJpaEntity`, `ModerationPersistenceAdapter`, Spring Data repositories
- **Error codes**: `REPORT_NOT_FOUND`, `BLOCK_SELF`, `BOT_NOT_FOUND`, `BOT_INACTIVE`, `BACKUP_NOT_FOUND`, `BACKUP_IN_PROGRESS` added to `ErrorCode` enum

### Added — Phase 1: Stabilization (Feb 2026)
- **WebSocket rate limiting**: `WebSocketRateLimiter` — per-connection sliding window (50 messages per 10-second window), integrated into `ChatWebSocketHandler`, auto-cleanup on disconnect
- **Deep linking**: `muhabbet://` custom scheme + `https://muhabbet.app` universal links for `/invite` and `/chat` paths in AndroidManifest.xml
- **Structured analytics**: `AnalyticsEvent` utility with SLF4J logger named "analytics" for structured event tracking with context maps

### Added — Backend Test Expansion (Feb 2026)
- **DeliveryStatusTest**: 6 tests — message delivery lifecycle (SENT → DELIVERED → READ), multi-recipient aggregation, status transitions
- **CallSignalingServiceTest**: 7 tests — call initiation, answer, end, busy detection, call history recording, concurrent call handling
- **EncryptionServiceTest**: 7 tests — key bundle registration, pre-key consumption, key bundle retrieval, one-time pre-key rotation
- **ModerationServiceTest**: 8 tests — report creation, duplicate reporting, block/unblock, self-block prevention, admin report review
- **WebSocketRateLimiterTest**: 4 tests — message allowance within limits, rate limiting enforcement, window expiry, user cleanup

### Added — V15 Database Migration (Feb 2026)
- **user_reports**: id, reporter_id, reported_user_id, reason, description, status, created_at, reviewed_at, reviewed_by
- **user_blocks**: id, blocker_id, blocked_id, created_at (unique constraint on blocker+blocked)
- **channel_analytics**: id, channel_id, date, message_count, view_count, reaction_count, share_count, new_subscribers, unsubscribes
- **channel_subscriptions**: id, channel_id, user_id, subscribed_at, notification_enabled
- **bots**: id, owner_id, user_id, name, description, api_token, webhook_url, is_active, permissions (JSONB), created_at, updated_at
- **message_backups**: id, user_id, status, file_url, file_size_bytes, message_count, created_at, completed_at, expires_at
- Indexes on all foreign keys and frequently queried columns

### Added — Security Hardening (Feb 2026)
- **Security headers**: HSTS (max-age 31536000), X-Frame-Options DENY, X-Content-Type-Options nosniff, CSP (`default-src 'self'; frame-ancestors 'none'; form-action 'self'`), XSS protection, Referrer-Policy strict-origin-when-cross-origin, Permissions-Policy (geolocation/camera/mic denied)
- **InputSanitizer**: Server-side input sanitization utility — HTML entity escaping (`&`, `<`, `>`, `"`, `'`), control character stripping (preserves `\n`, `\t`, `\r`), display name trimming/length limiting, message content length limiting, HTTPS-only URL validation (rejects `javascript:` and `data:` schemes)
- **InputSanitizer tests**: 15 unit tests covering XSS prevention, HTML entities, control chars, URL validation, null handling

### Added — Call UI Screens (Feb 2026)
- **IncomingCallScreen**: Full-screen incoming call overlay with avatar, caller name, accept (green) / decline (red) buttons, WebSocket signaling integration
- **ActiveCallScreen**: In-call UI with duration timer (coroutine-based), mute/speaker toggles, end call button, real-time CallEnd listener
- **CallHistoryScreen**: Paginated call history list with direction icons (incoming/outgoing/missed), duration display, call-back button
- **Decompose navigation**: 3 new Config entries (IncomingCall, ActiveCall, CallHistory) with navigation methods wired in MainComponent
- **CallRepository**: REST client for `GET /api/v1/calls/history` endpoint
- **21 call strings**: Turkish + English localization for all call UI elements

### Added — E2E Encryption Infrastructure (Feb 2026)
- **E2EKeyManager interface**: X3DH key lifecycle — generateIdentityKeyPair, generateSignedPreKey, generateOneTimePreKeys, initializeSession, hasSession, encryptMessage, decryptMessage
- **NoOpKeyManager**: MVP pass-through implementation (all encrypt/decrypt returns plaintext unchanged)
- **EncryptionRepository**: Mobile client for key bundle registration (`PUT /api/v1/encryption/keys`), pre-key upload (`POST /api/v1/encryption/prekeys`), bundle fetch (`GET /api/v1/encryption/bundle/{userId}`)
- **Koin DI**: Registered EncryptionPort, E2EKeyManager, EncryptionRepository in AppModule

### Added — iOS Platform Completion (Feb 2026)
- **ImagePicker.ios.kt**: PHPickerViewController with delegate retention pattern, image data loading via NSItemProvider
- **FilePicker.ios.kt**: UIDocumentPickerViewController with security-scoped resource access, MIME type detection
- **ImageCompressor.ios.kt**: CoreGraphics CGBitmapContext resize + UIImageJPEGRepresentation compression (matches Android logic)
- **CrashReporter.ios.kt**: NSLog-based crash logging with Sentry CocoaPod integration hooks
- **PushTokenProvider.ios.kt**: Token caching via NSUserDefaults, `onTokenReceived()` companion method for AppDelegate, polling-based token wait (5s timeout)
- **LocaleHelper.ios.kt**: AppleLanguages UserDefaults for locale switching with `exit(0)` restart
- **FirebasePhoneAuth.ios.kt**: `isAvailable()=false` stub for graceful backend OTP fallback

### Added — Mobile & Shared Test Infrastructure (Feb 2026)
- **Test dependencies**: kotlin-test, kotlinx-coroutines-test, ktor-client-mock, koin-test added to commonTest
- **FakeTokenStorageTest**: 5 tests — initial state, save/persist, clear, language, theme
- **AuthRepositoryTest**: Tests for isLoggedIn, logout, token persistence, error handling
- **PhoneNormalizationTest**: 14 tests covering E.164, Turkish phone formats, separators, edge cases
- **WsMessageSerializationTest**: 25+ tests covering all WsMessage types — SendMessage, AckMessage, TypingIndicator, CallInitiate/Answer/End/Incoming, NewMessage, StatusUpdate, ServerAck, PresenceUpdate, GroupMemberAdded/Removed, MessageDeleted/Edited/Reaction, Ping/Pong, Error, round-trip fidelity

### Added — CI/CD Pipeline (Feb 2026)
- **backend-ci.yml**: On push to `backend/` or `shared/` — Gradle test + bootJar with caching
- **mobile-ci.yml**: On push to `mobile/` or `shared/` — Android assembleDebug (with dummy google-services.json) + iOS framework build on macOS
- **security.yml**: Trivy filesystem + Docker image scanning, Gitleaks secret detection, CodeQL static analysis for java-kotlin (weekly + on push)
- **deploy.yml**: On merge to `main` — SSH to GCP, docker compose pull/up with health check and rollback

### Changed — Dependency Upgrades (Feb 2026)
- **Kotlin**: 2.1.10 → 2.3.10
- **Spring Boot**: 3.4.1 → 4.0.2
- **Java**: 21 → 25
- **Gradle**: 8.12 → 8.14.4
- **Ktor**: 3.0.3 → 3.1.3
- **Compose BOM**: 2024.12.01 → 2025.04.01
- **Koin**: 4.0.2 → 4.1.0-Beta1
- **Decompose**: 3.2.3 → 3.3.0
- **Coil**: 3.0.4 → 3.1.0
- **SQLDelight**: 2.0.2 → 2.1.0
- **kotlinx.serialization**: 1.7.3 → 1.8.1
- **kotlinx.datetime**: 0.6.1 → 0.7.0

### Changed — System Optimization (Feb 2026)
- **Database indexes**: 12 performance indexes — messages (conversation_id+created_at), delivery_status (message_id), conversations (updated_at), phone_hashes (hash), media_files (uploader+type), statuses (user_id+expires_at), etc.
- **N+1 query fixes**: `@BatchSize(size=50)` on ConversationJpaEntity.members and MessageJpaEntity.deliveryStatuses
- **Redis connection pooling**: Lettuce pool enabled (min-idle=2, max-active=8)
- **Ktor client connection pooling**: maxConnectionsCount=100, connectTimeout=10s, requestTimeout=30s
- **Nginx optimization**: gzip on (text, JSON, JS, CSS), static file caching (30d for images, 7d for JS/CSS), proxy buffering enabled
- **PostgreSQL tuning**: shared_buffers=256MB, effective_cache_size=1GB, work_mem=16MB, random_page_cost=1.1 (SSD)

### Added — Round 6: Media UX & Storage
- **Chat scroll fix**: Chat now starts at the bottom instantly on first load, subsequent messages animate smoothly (no more exhaustive top-to-bottom scroll)
- **Pinch-to-zoom**: MediaViewer supports pinch-to-zoom (1x–5x), double-tap to toggle zoom (1x ↔ 3x), pan while zoomed via `rememberTransformableState` + `graphicsLayer`
- **SharedMedia video/voice/doc playback**: Videos open in external player, voices play inline with play/pause toggle + AudioPlayer, documents open externally via `LocalUriHandler`
- **Forward button fix**: Forward now opens `ForwardPickerDialog` with conversation list (was incorrectly just viewing the image)
- **MessageInfo media preview**: Message info screen now shows image/video thumbnail preview in the message card (added `mediaUrl`/`thumbnailUrl` to `MessageInfoResponse` DTO)
- **MessageInfo avatars**: Recipient rows now display user avatars (added `avatarUrl` to `RecipientDeliveryInfo` DTO, populated from backend user record)
- **Storage usage stats**: `GET /api/v1/media/storage` — returns per-user storage breakdown by type (images, audio, documents) with byte counts and item counts. Mobile Settings screen shows storage section with colored breakdown rows
- **Hexagonal storage chain**: `GetStorageUsageUseCase` in-port → `MediaService` implementation → `MediaFileRepository` out-port → `MediaFilePersistenceAdapter` → Spring Data JPA queries with LIKE prefix matching
- **8 new string resources**: Storage UI strings in Turkish and English (storage_title/total/images/audio/documents/loading/error/items)

### Added — Round 5: UI/UX Polish
- **MediaViewer**: Full-screen image viewer with semi-transparent top bar (close), bottom action bar (forward, delete), tap-to-toggle UI overlay (WhatsApp-style). Replaces bare `FullImageViewer` dialog
- **SharedMediaScreen enhancements**: Tap grid item opens full-screen MediaViewer, long-press opens context menu (forward, delete own), Crossfade tab transitions, AnimatedVisibility for loading states
- **MessageInfoScreen polish**: Message preview in Card with elevation and rounded corners, content type icon for media, separate "Read By" (blue) / "Delivered To" (grey) / "Waiting" sections with colored dots and headers, UserAvatar in recipient rows, empty state with schedule icon
- **New string resources**: 8 new strings in Turkish and English (media_viewer_forward/share/delete/info, message_info_read_by/delivered_to/waiting/not_sent)

### Fixed — Round 4 Bug Fixes
- **Shared media JPQL query**: Changed inline enum references to `@Param` list approach — JPQL `IN` clause with fully-qualified enum constants may not resolve in Hibernate 6
- **Message info endpoint**: Added defensive error handling, filtered sender from recipients list
- **Status text position**: Text content now appears at bottom of status viewer (was centered)
- **Starred message scroll**: Clicking a starred message now navigates to chat AND scrolls to the specific message (added `scrollToMessageId` param to ChatScreen + Config.Chat)
- **Starred back navigation**: Removed `goBack()` before `openChat()` — back button now correctly returns to StarredMessages instead of skipping to Settings

### Added — Round 3 Bug Fixes & Features
- **Delivery status resolution (critical)**: Backend now batch-queries `message_delivery_status` table and resolves per-message status — sender sees aggregate min across recipients, recipient sees their own status row. Fixed `MessageMapper.toSharedMessage()` hardcoding `status = SENT`
- **Shared media screen**: `GET /api/v1/conversations/{id}/media` endpoint + `SharedMediaScreen` with grid (images/videos) and list (documents) tabs. Accessible from GroupInfoScreen and UserProfileScreen
- **Message info screen**: `GET /api/v1/messages/{id}/info` endpoint + `MessageInfoScreen` with sent time, per-recipient delivery status with icons (single tick, double grey, double blue)
- **Starred messages redesign**: Replaced chat bubble layout with list items showing sender label, content preview with type icons, timestamp. Click navigates to conversation
- **Profile contact name**: Shows `~contactName` below display name when different
- **Status image upload**: "Add Photo" button in status dialog, uploads via MediaRepository
- **Forwarded message visual improvements**: Forward icon, 12sp italic, alpha 0.8
- **Video thumbnails**: Play overlay on video messages in chat
- **Call button snackbar**: Shows "Coming soon" instead of empty click handler
- **New string resources**: 16 new strings in Turkish and English for all new features

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

### Added — Phase 2: Backend Tests (201 backend, 251 total)
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
