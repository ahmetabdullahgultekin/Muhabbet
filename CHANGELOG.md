# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

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
