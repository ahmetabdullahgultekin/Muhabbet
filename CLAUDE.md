# Muhabbet — Project Context

## What Is This?
Muhabbet is a domestic Turkish messaging platform (WhatsApp alternative). Privacy-first, KVKK-compliant, designed for Turkey's 85M population. Currently in MVP phase — solo engineer, fast iteration.

## Architecture
**Modular Monolith** with Hexagonal Architecture (Ports & Adapters) per module.

```
muhabbet/
├── backend/    → Spring Boot 3 + Kotlin (modular monolith)
├── shared/     → KMP shared module (domain models, protocol, validation)
├── mobile/     → Compose Multiplatform app (Android + iOS)
├── infra/      → Docker Compose, nginx, scripts
└── docs/       → Architecture docs, ADRs, API contract
```

**Kotlin Everywhere** — backend, shared module, and mobile all use Kotlin. The `shared/` module is a Kotlin Multiplatform library used by both backend and mobile.

## Backend Architecture (Spring Boot 3 + Kotlin)

### Module Structure
Each module follows Hexagonal Architecture:
```
module/
├── domain/
│   ├── model/       → Aggregate roots, entities, value objects
│   ├── port/
│   │   ├── in/      → Use case interfaces (what the module CAN do)
│   │   └── out/     → Repository/external service interfaces (what the module NEEDS)
│   ├── service/     → Business logic (implements in-ports, uses out-ports)
│   └── event/       → Domain events
└── adapter/
    ├── in/
    │   ├── web/     → REST controllers + DTOs
    │   └── websocket/ → WebSocket handlers
    └── out/
        ├── persistence/ → JPA repositories + entities (JPA entity ≠ domain model)
        └── external/    → Third-party integrations (Netgsm, FCM, MinIO)
```

### Modules
- `auth` — **DONE** — OTP via MockOtpSender (Netgsm later), JWT HS256, device management, phone hash for contact sync
- `messaging` — **DONE** — Send/receive messages, delivery status, conversation management, WebSocket real-time, cursor pagination, backup, bots, channel analytics
- `media` — **DONE** — Upload/download via MinIO (S3 API), thumbnail generation, pre-signed URLs via nginx proxy
- `presence` — **DONE** — Online/offline tracking via Redis (TTL-based keys), typing indicators, last seen persistence
- `notification` — **DONE** — Push notifications via FCM (FcmPushNotificationAdapter), push token registration
- `moderation` — **DONE** — Report/block system (BTK Law 5651), admin review workflows
- `user` — Profile endpoints in auth module for now (`GET/PATCH /users/me`)

### Cross-Cutting (`shared/` package in backend)
- `config/` — SecurityConfig, WebSocketConfig, RedisConfig, AsyncConfig
- `exception/` — GlobalExceptionHandler, BusinessException, ErrorCode enum
- `security/` — JwtProvider, JwtAuthFilter
- `event/` — DomainEvent marker interface

## Software Engineering Principles — ALWAYS Follow These

Every piece of code in this project MUST adhere to these principles. These are not guidelines — they are hard rules.

### SOLID Principles

#### Single Responsibility Principle (SRP)
- **Each class/file has ONE reason to change.** Controllers handle HTTP, services handle logic, repositories handle persistence.
- **Composable functions ≤ 300 lines.** If a composable grows larger, extract sub-composables into separate files.
- **Services implement ≤ 3 use case interfaces.** If a service implements more, split into focused services (e.g., `ConversationService`, `MessageService`, `GroupService`).
- **No God classes.** If a file exceeds ~500 lines, it likely violates SRP — refactor.

#### Open/Closed Principle (OCP)
- **Use interfaces (ports) for extension.** New behavior should be added via new implementations, not by modifying existing code.
- **Use sealed classes for type-safe variants.** Prefer sealed hierarchies over `when` on strings or enums for business logic branching.
- **Mapper extension functions for DTO conversions.** Example: `Message.toSharedMessage()` in a dedicated mapper file — one place to change if mapping evolves.

#### Liskov Substitution Principle (LSP)
- **All implementations of an interface must be interchangeable.** If a service implements `ManageGroupUseCase`, every method must behave consistently with the contract.
- **No method should throw `UnsupportedOperationException`.** If a subtype can't fulfill the contract, the interface is too broad — split it.

#### Interface Segregation Principle (ISP)
- **Clients should not depend on interfaces they don't use.** Split fat interfaces into focused ones.
- **Use case interfaces are fine-grained:** `SendMessageUseCase`, `GetMessageHistoryUseCase` — not one `MessageUseCase` with 10 methods.

#### Dependency Inversion Principle (DIP)
- **Domain depends on NOTHING.** Domain model and service layer never import adapter/infrastructure classes.
- **Controllers depend on use case interfaces (in-ports)**, never on concrete service implementations.
- **Services depend on repository interfaces (out-ports)**, never on JPA repository implementations.

### Architecture Rules (Hexagonal)

1. **NO business logic in controllers or adapters.** Controllers only: validate input → call use case → map response.
   - **NO `@Transactional` on controllers.** Transactions belong on the service layer.
   - **NO JPA entity instantiation in controllers.** Controllers must not import `*JpaEntity` classes.
   - **NO `SpringData*Repository` in controllers.** Controllers call use case interfaces only.
2. **Domain models ≠ JPA entities.** Always map between them. Domain model lives in `domain/model/`, JPA entity lives in `adapter/out/persistence/`.
3. **Modules communicate via Spring ApplicationEvent**, never by direct imports across module boundaries.
4. **All use cases are interfaces** (in-ports). Services implement them.
5. **All repositories are interfaces** (out-ports). JPA adapters implement them.
6. **No Spring annotations in domain layer.** Domain is framework-agnostic.
7. **New features require:** migration → domain model → out-port → JPA adapter → in-port (use case) → service → controller. Never skip layers.

### DRY (Don't Repeat Yourself)

- **Shared utility functions go in `util/` packages.** Example: `normalizeToE164()` lives in `mobile/.../util/PhoneNormalization.kt`, imported by all screens.
- **DTO mapping logic extracted to mapper files.** Example: `MessageMapper.kt` with `Message.toSharedMessage()` extension function — used by all controllers that return messages.
- **If you copy-paste >3 lines, extract.** Create a function, extension, or composable instead.
- **Backend and shared module enum definitions:** Backend domain enums (`ContentType`, `ConversationType`, `MemberRole`) are intentionally separate from shared module enums — backend domain must be framework-agnostic. Use mapper extensions for conversion between them.

### KISS (Keep It Simple, Stupid)

- **Prefer simple, linear code over clever abstractions.** Three similar lines > premature abstraction.
- **No unnecessary generics.** Use concrete types unless abstraction is needed by 3+ callers.
- **Avoid deep nesting.** Use early returns and guard clauses instead of nested `if-else`.
- **Composables: Hoist state simply.** Use `mutableStateOf()` at the composable level, not custom ViewModel classes for simple screens.

### YAGNI (You Aren't Gonna Need It)

- **Don't build for hypothetical future.** Only implement what's needed now.
- **No feature flags, config options, or extension points** unless there are concrete current requirements.
- **No abstract base classes with one subclass.** Wait until you need the second implementation.

### No Hardcoded Strings

#### UI Layer (Mobile)
- **ALL user-visible text** uses `stringResource(Res.string.*)` — no Turkish or English text in Kotlin files.
- Default locale: Turkish (`composeResources/values/strings.xml`).
- English translations: `composeResources/values-en/strings.xml`.
- For strings in `scope.launch {}` (non-composable), resolve at the top of the composable function as `val`.

#### Data Layer (Repositories)
- **No hardcoded error messages** in exceptions. Use error codes: `throw Exception(response.error?.message ?: "ERROR_CODE")`.
- Error codes follow pattern: `DOMAIN_ACTION_RESULT` (e.g., `OTP_REQUEST_FAILED`, `GROUP_CREATE_FAILED`).

#### Backend
- **Use `ErrorCode` enum** for all business errors. Never throw with inline Turkish/English message strings.
- **Magic numbers go in companion objects or `application.yml`.** Example: `EDIT_WINDOW_MINUTES`, `MAX_HASHES_PER_REQUEST`.

### Code Style
- Kotlin idioms: data classes, sealed classes, extension functions, null safety
- No `!!` (non-null assertion) — handle nulls properly with `?.`, `?:`, `let`
- Use `Result<T>` or sealed class for operation results, not exceptions for business logic
- Coroutines for async operations (`suspend fun`)
- All DTOs use `kotlinx.serialization` annotations (shared with mobile)

### API Convention
- All REST responses wrapped in envelope: `{ "data": ..., "timestamp": "..." }` or `{ "error": { "code": "...", "message": "..." }, "timestamp": "..." }`
- Error codes are enum values from `ErrorCode` (e.g., `AUTH_OTP_EXPIRED`, `MSG_CONVERSATION_NOT_FOUND`)
- API versioning: `/api/v1/...`
- WebSocket: `wss://host/ws?token={jwt}`

### Testing
- JUnit 5 + MockK for unit tests
- Testcontainers for integration tests (PostgreSQL, Redis)
- Test domain logic with in-memory adapters (no DB needed)
- Name pattern: `should [expected] when [condition]`

### Database
- PostgreSQL 16 — all data in one DB for MVP
- Flyway for migrations: `V{number}__{description}.sql`
- Never manually alter DB — always via migration files
- Use UUIDs for primary keys (gen_random_uuid())
- Soft delete with `deleted_at` column where needed (KVKK right to erasure)

## Shared KMP Module
The `shared/` module contains code used by BOTH backend and mobile:
- `model/` — Domain models (Message, Conversation, MessageStatus, ContentType)
- `protocol/` — WebSocket message types (WsMessage sealed class)
- `validation/` — Input validation rules
- `dto/` — API request/response DTOs
- `port/` — EncryptionPort interface (NoOp for MVP, Signal Protocol later)

Uses `kotlinx.serialization` for JSON — same serialization on both sides.

## Mobile (Compose Multiplatform)
- Navigation/lifecycle: Decompose
- DI: Koin
- Local DB: SQLDelight
- HTTP client: Ktor
- WebSocket: Ktor WebSocket client
- State management: Simple ViewModels with StateFlow (MVI-style for chat screen)
- Architecture: Clean Architecture (data/domain/presentation per feature)

## Tech Stack Quick Reference
| Component | Technology |
|-----------|-----------|
| Language | Kotlin (everywhere) |
| Backend framework | Spring Boot 4.0.2 |
| Mobile framework | CMP (Compose Multiplatform) |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Media storage | MinIO (S3-compatible) |
| Build system | Gradle (Kotlin DSL) |
| SMS gateway | Netgsm |
| Push | FCM (Firebase Cloud Messaging) |
| Monitoring | SLF4J + Logback (JSON) + Spring Actuator + Sentry |
| Testing | JUnit 5 + MockK + Testcontainers + ArchUnit |
| Code quality | JaCoCo (coverage) + detekt (static analysis) |
| Load testing | k6 |
| CI/CD | GitHub Actions |

## Key Files
- `settings.gradle.kts` — Root, includes all subprojects
- `backend/build.gradle.kts` — Spring Boot dependencies
- `shared/build.gradle.kts` — KMP configuration
- `mobile/composeApp/build.gradle.kts` — CMP configuration
- `backend/src/main/resources/application.yml` — Backend config
- `backend/src/main/resources/db/migration/` — Flyway SQL migrations
- `infra/docker-compose.yml` — Local dev (PG + Redis + MinIO)
- `docs/api-contract.md` — REST + WebSocket API specification
- `docs/qa/` — QA engineering documentation (9 ISO/IEC 25010 documents + UI/UX analysis)
- `backend/detekt.yml` — detekt static analysis configuration
- `infra/k6/` — k6 load test scripts (auth, API, WebSocket)
- `mobile/.../ui/theme/MuhabbetTheme.kt` — Design tokens (semantic colors, spacing, sizes, elevation)
- `mobile/.../util/DateTimeFormatter.kt` — Centralized date/time formatting (DRY utility)
- `mobile/.../ui/components/SectionHeader.kt` — Reusable section header component
- `mobile/.../ui/components/ConfirmDialog.kt` — Reusable confirm/dismiss dialog
- `mobile/.../util/TextUtils.kt` — firstGrapheme() + parseFormattedText() utilities
- `mobile/.../BuildInfo.kt` — Centralized version constant
- `docs/qa/mobile-ui-audit.md` — 87-issue mobile UI audit report
- `mobile/.../data/repository/MediaUploadHelper.kt` — Centralized media upload with guaranteed compression
- `mobile/.../data/local/MuhabbetDatabase.sq` — SQLDelight schema (CachedConversation, CachedMessage, PendingMessage)
- `mobile/.../crypto/PersistentSignalProtocolStore.kt` — Android EncryptedSharedPreferences Signal store
- `mobile/.../crypto/KeychainHelper.kt` — iOS Keychain CRUD for secure token/key storage
- `mobile/.../platform/BackgroundSyncManager.kt` — expect/actual periodic message sync (WorkManager/BGTask)
- `mobile/.../platform/CameraPicker.kt` — expect/actual camera capture (TakePicture/UIImagePickerController)
- `mobile/.../platform/SpeechTranscriber.kt` — expect/actual on-device ASR (SpeechRecognizer/SFSpeechRecognizer)
- `mobile/.../ui/settings/PrivacyDashboardScreen.kt` — KVKK privacy controls UI

## Current Phase
MVP — solo engineer. Core 1:1 messaging complete, moving to polish and group chat:
1. ~~Auth (OTP + JWT)~~ — **DONE**
2. ~~1:1 messaging (WebSocket)~~ — **DONE**
3. ~~Mobile app (CMP Android)~~ — **DONE** (auth, chat, settings, dark mode, pull-to-refresh, pagination)
4. ~~Contacts sync~~ — **DONE** (Android; iOS stubbed)
5. ~~Typing indicators~~ — **DONE** (send, receive, backend broadcast)
6. ~~Media sharing (images)~~ — **DONE** (upload, thumbnail, image bubbles, full-size viewer)
7. ~~Push notifications~~ — **DONE** (FCM, push token registration, offline delivery)
8. ~~Presence (online/last seen)~~ — **DONE** (Redis TTL, green dot, header subtitle)

9. ~~Group messaging~~ — **DONE** (backend endpoints, mobile UI: create, info, roles, add/remove, leave)
10. ~~Voice messages~~ — **DONE** (backend audio upload, mobile record/playback, VoiceBubble)
11. ~~Production SMS (Netgsm)~~ — **DONE** (NetgsmOtpSender, @ConditionalOnProperty)
12. ~~Message delete/edit~~ — **DONE** (soft delete, edit with editedAt, WS broadcast, context menu)
13. ~~Localization (i18n)~~ — **DONE** (all strings in composeResources, values/ = Turkish, values-en/ = English, language switch in Settings)

14. ~~Reply/quote + forwarding~~ — **DONE**
15. ~~Starred messages~~ — **DONE**
16. ~~Link previews~~ — **DONE**
17. ~~Message search~~ — **DONE**
18. ~~File/document sharing~~ — **DONE**
19. ~~Disappearing messages~~ — **DONE**
20. ~~Polls~~ — **DONE**
21. ~~Location sharing~~ — **DONE**
22. ~~Status/Stories~~ — **DONE**
23. ~~Channels/Broadcasts~~ — **DONE**
24. ~~UI/UX polish~~ — **DONE** (reactions, swipe-to-reply, typing animation, FAB, filter chips, pinned chats, OLED theme, bubble tails, date pills, empty states, unread styling, a11y fixes, design tokens, skeleton loaders, testTags)

### Completed Phases
- **Phase 2 (Beta Quality)**: ChatScreen refactored (1,771→405 lines), MessagingService split into 3, 5 controllers use use cases, 201 backend tests (251 total incl. mobile/shared), Stickers & GIFs, Profile viewing (mutual groups, shared media, action buttons)
- **Phase 3 (Voice Calls)**: Call signaling infrastructure (WS messages, CallSignalingService, call history DB), notification improvements, Sentry SDK, LiveKit room adapter (`@ConditionalOnProperty`), NoOp fallback, outgoing call initiation in MainComponent
- **Phase 4 (Trust & Security)**: E2E encryption key exchange endpoints + DB migrations, KVKK data export + account deletion, message backup system (BackupService, BackupController, BackupPersistenceAdapter)
- **Phase 5 (iOS + Scale)**: All iOS platform modules implemented — AudioPlayer, AudioRecorder, ContactsProvider, PushTokenProvider, ImagePicker, FilePicker, ImageCompressor, CrashReporter, LocaleHelper, FirebasePhoneAuth. Redis Pub/Sub message broadcaster for horizontal WS scaling
- **Phase 6 (Growth)**: Channel analytics (daily stats, subscriber tracking, REST API), Bot platform (create/manage bots, API token auth, webhook support, permissions system)
- **Round 3 Bug Fixes**: Delivery status resolution (batch query + aggregation), shared media screen, message info screen, starred messages redesign, profile contact name, status image upload, forwarded message visuals, video thumbnails
- **Round 4 Bug Fixes**: Shared media JPQL query, message info endpoint, status text position, starred message scroll-to-message, starred back navigation
- **Round 5 UI/UX Polish**: MediaViewer with action bars (forward/delete), SharedMediaScreen long-press context menu + Crossfade tab transitions, MessageInfoScreen with cards/avatars/timeline sections
- **Round 6 Media UX & Storage**: Chat scroll fix (start at bottom), pinch-to-zoom (1x–5x + double-tap), SharedMedia video/voice/doc playback, forward fix, MessageInfo media preview + avatars, storage usage stats (`GET /api/v1/media/storage` with full hexagonal chain)
- **System Optimization**: Database indexes (12 performance indexes), N+1 query fixes (batch fetching), Redis connection pooling, Ktor connection pooling, nginx gzip/caching, PostgreSQL tuning
- **Dependency Upgrades (Feb 2026)**: Kotlin 2.3.10, Spring Boot 4.0.2, Java 25, Gradle 8.14.4, Ktor 3.1.3, Compose BOM 2025.04.01
- **CI/CD Pipeline**: GitHub Actions — backend CI (test + build), mobile CI (Android + iOS), security scanning (Trivy, Gitleaks, CodeQL), deployment automation
- **Call UI**: IncomingCallScreen, ActiveCallScreen, CallHistoryScreen with Decompose navigation
- **E2E Encryption Infrastructure**: E2EKeyManager interface + NoOpKeyManager (MVP), EncryptionRepository (mobile client for key exchange API)
- **LiveKit WebRTC Client**: CallEngine expect/actual (Android: LiveKit SDK, iOS: stub), CallRoomInfo WsMessage, backend room creation + token generation on call answer, ActiveCallScreen wired to LiveKit, DisposableEffect cleanup
- **Signal Protocol E2E Encryption**: SignalKeyManager (libsignal-android: X3DH + Double Ratchet), InMemorySignalProtocolStore, SignalEncryption implementing EncryptionPort, E2ESetupService for key registration on login, platform DI (Android: Signal, iOS: NoOp)
- **Security Hardening**: HSTS, X-Frame-Options DENY, CSP, XSS protection, Referrer-Policy, Permissions-Policy headers; InputSanitizer (HTML escaping, control char stripping, URL validation)
- **Mobile Test Infrastructure**: kotlin-test + coroutines-test + ktor-mock + koin-test; FakeTokenStorageTest, AuthRepositoryTest, PhoneNormalizationTest, WsMessageSerializationTest (25+ tests)
- **Stabilization (Phase 1)**: WebSocket rate limiting (50 msg/10s sliding window), deep linking (`muhabbet://` scheme + universal links), structured analytics event tracking, LiveKit config in application.yml
- **Content Moderation (Phase 2)**: Report/block system (BTK Law 5651 compliance), ModerationService + ModerationController, ReportRepository + BlockRepository, V15 migration for moderation/analytics/backup/bot tables, ~32 new backend tests (DeliveryStatus, CallSignaling, Encryption, Moderation, RateLimiter)
- **QA Engineering**: JaCoCo code coverage + detekt static analysis + ArchUnit architecture tests (14 rules), TestData factory, 18 controller test files (100+ tests covering all REST controllers), k6 load test scripts, 9 ISO/IEC 25010 QA documents in `docs/qa/` (including Lead UI/UX Engineer analysis), CI pipeline with JaCoCo/detekt/coverage-comments. Total: 364 tests (314 backend + 23 mobile + 27 shared)
- **UI/UX Remediation (Phase 1)**: Semantic color tokens (`LocalSemanticColors`), spacing/size tokens (`MuhabbetSpacing`, `MuhabbetSizes`), 28+ a11y contentDescription fixes, touch target fixes (36→48dp), IME actions on all inputs, skeleton loading states, edit mode visual banner, testTags on critical elements, 12 new localized strings (TR+EN)
- **UI/UX Remediation (Phase 2)**: Reusable components (`DateTimeFormatter` utility consolidating 6 duplicate formatters, `SectionHeader` component, `ConfirmDialog` wrapper), elevation tokens (`MuhabbetElevation`), full spacing token migration (`MuhabbetSpacing`) across 30+ UI files, elevation token migration across 7 files
- **Mobile UI Audit + 87 Fixes**: Lead Mobile Engineer audit (87 issues across 6 severity levels). Fixed: 5 critical bugs (dead condition, hardcoded colors, infinite timer loop, stringly-typed state, hardcoded version), 6 ship-blocking feature gaps (copy to clipboard, group sender names, emoji button, block/report dialogs, privacy settings, channels filter), expanded design system (7 semantic colors, avatar tokens, duration/gesture tokens), 62 new localized strings (TR+EN), 15+ `!!` assertion removals, 4 WCAG touch target fixes. Files: 15 modified, 784 insertions, 173 deletions
- **Production Hardening (Feb 2026)**: SQLDelight offline cache (cache-first repositories, PendingMessage queue), WebSocket resilience (offline queue drain, dedup, jitter backoff), KVKK Privacy Dashboard (data export, account deletion, visibility toggles), MediaUploadHelper (centralized compression pipeline), PersistentSignalProtocolStore (EncryptedSharedPreferences replacing InMemoryStore), background message sync (backend endpoint + WorkManager + BGTask), iOS platform completion (CameraPicker, AudioRecorder fix, KeychainHelper), voice transcription (SpeechRecognizer + SFSpeechRecognizer, Turkish ASR)

### Current Phase: Production Hardening (Feb 2026) — COMPLETE
All 9 production hardening features completed:

25. ~~Mobile UI audit + 87 issue fixes~~ — **DONE** (critical bugs, feature gaps, design system, a11y)
26. ~~SQLDelight offline caching~~ — **DONE** (conversations + messages cached in local DB, cache-first repository pattern)
27. ~~WebSocket connection resilience~~ — **DONE** (offline message queue, dedup via LinkedHashSet, exponential backoff with jitter)
28. ~~KVKK Privacy Dashboard~~ — **DONE** (data export, account deletion, visibility controls, KVKK rights info)
29. ~~Media compression pipeline~~ — **DONE** (MediaUploadHelper: images 1280px/80%, profiles 512px/75%, thumbnails 320px/60%)
30. ~~Persistent E2E key storage~~ — **DONE** (Android: EncryptedSharedPreferences, iOS: Keychain for tokens)
31. ~~Background message sync~~ — **DONE** (backend GET /api/v1/messages/since, Android WorkManager 15min, iOS BGTask)
32. ~~iOS platform modules~~ — **DONE** (CameraPicker, AudioRecorder fix, KeychainHelper for secure storage)
33. ~~Voice message transcription~~ — **DONE** (Android SpeechRecognizer, iOS SFSpeechRecognizer, Turkish tr-TR)

### Implementation Architecture

#### SQLDelight Offline Cache
- **Plugin**: `app.cash.sqldelight:2.2.1` (already declared in root build.gradle.kts)
- **Schema**: `MuhabbetDatabase.sq` with tables: `CachedConversation`, `CachedMessage`, `PendingMessage`
- **Pattern**: Repository-level cache — repositories check local DB first, fetch from API on miss, write-through on success
- **Offline queue**: `PendingMessage` table stores unsent messages, drained on WS reconnect
- **Platform drivers**: Android `AndroidSqliteDriver`, iOS `NativeSqliteDriver`

#### WebSocket Resilience
- **Offline queue**: Messages sent while disconnected stored in SQLDelight `PendingMessage` table
- **Drain on reconnect**: `WsClient.connect()` drains pending queue after successful auth
- **Deduplication**: `clientMessageId` (UUID generated client-side) used as idempotency key
- **Exponential backoff**: Already implemented (1s→30s), enhanced with jitter

#### Persistent E2E Key Storage
- **Android**: `PersistentSignalProtocolStore` using `EncryptedSharedPreferences` for identity key pair, sessions, pre-keys, signed pre-keys, sender keys
- **iOS**: `KeychainHelper` for secure token storage (access/refresh tokens, user/device IDs); E2E keys use NoOp until Signal Protocol is bridged
- **Migration**: `InMemorySignalProtocolStore` → `PersistentSignalProtocolStore` — `SignalKeyManager` now takes store via constructor injection

#### Media Compression Pipeline
- **Images**: Already implemented via `ImageCompressor` (1280px max, JPEG 80%). Extended to all upload paths
- **Audio**: OGG/OPUS 32kbps (already set in AudioRecorder). Ensure consistent across platforms
- **Video**: Extract thumbnail, compress to H.264 720p before upload (platform expect/actual)

#### Background Message Sync
- **Android**: `WorkManager` periodic sync (15min) + FCM data message trigger
- **iOS**: `BGAppRefreshTask` + APNs silent push trigger
- **Sync endpoint**: `GET /api/v1/messages/since?timestamp={lastSync}` — returns messages since last sync

#### Privacy Dashboard (KVKK)
- **Screen**: `PrivacyDashboardScreen` in settings navigation
- **Features**: Data export request, account deletion, read receipts toggle, last seen visibility, profile photo visibility
- **Backend**: Already has `GET /api/v1/users/data/export` and `DELETE /api/v1/users/data/account`

#### Voice Message Transcription
- **Android**: `SpeechRecognizer` API (on-device, Turkish tr-TR supported)
- **iOS**: `SFSpeechRecognizer` (Apple Speech framework, on-device when available)
- **UI**: "Transcribe" button on VoiceBubble, downloads audio + runs on-device ASR, shows inline transcript
- **Language**: Turkish (tr) primary, auto-detect for multilingual messages

### Remaining Work (Post-Production Hardening)
- ~~WebRTC client integration (LiveKit)~~ — **DONE** (LiveKit Android SDK + CallEngine + backend room management)
- ~~E2E encryption client (Signal Protocol)~~ — **DONE** (libsignal-android + SignalKeyManager + E2ESetupService)
- iOS APNs delivery, TestFlight, App Store
- iOS LiveKit integration (bridge LiveKit Swift SDK via Kotlin/Native)
- iOS Signal Protocol integration (bridge libsignal-client via Kotlin/Native)
- Security penetration testing (OWASP ZAP/Burp Suite)
- Web/Desktop client
- Load testing at scale — k6 scripts created, need to run against production-like environment

### Known Technical Debt
- **Backend enum duplication**: `ContentType`, `ConversationType`, `MemberRole` exist in both backend domain and shared module — intentional for hexagonal purity, but requires mapper conversions. Consider type aliases if maintenance burden grows.
- **~~Single-server architecture~~**: Resolved — Redis Pub/Sub broadcaster (`RedisMessageBroadcaster`) enables horizontal WS scaling across multiple backend instances.
- **~~2 active bugs~~**: Fixed — Push notifications enabled via FCM_ENABLED=true in docker-compose.prod.yml; delivery ticks fixed via global DELIVERED ack in App.kt.
- **~~In-memory E2E key store~~**: Resolved — `PersistentSignalProtocolStore` with EncryptedSharedPreferences (Android), `KeychainHelper` for iOS tokens.
- **~~No offline support~~**: Resolved — SQLDelight cache layer with cache-first repository pattern + PendingMessage offline queue.

### Localization Rules
- **No hardcoded strings in UI code.** All user-visible text must use `stringResource(Res.string.*)`.
- Default locale is Turkish (`composeResources/values/strings.xml`).
- English translations in `composeResources/values-en/strings.xml`.
- For strings used inside `scope.launch {}` (non-composable), resolve them as `val` at the top of the composable function.
- Language preference stored in `muhabbet_prefs` SharedPreferences (`app_language` key), applied in `MainActivity.onCreate()` via `Configuration.setLocale()`.

## Implementation Notes
- JWT uses HS256 (not RS256) — simpler for monolith, no asymmetric key management
- `AuthService` is wired via `AppConfig` bean (not `@Service`) to keep domain Spring-free
- `@Transactional` on AuthService requires `open class` (Kotlin plugin.spring handles this)
- Shared module `UserProfile` uses `kotlinx.datetime.Instant` — backend needs `kotlinx-datetime` dependency
- OTP mock: set `OTP_MOCK_ENABLED=true` to log OTP to console instead of SMS
- Phone hash: SHA-256 of phone number stored in `phone_hashes` table on user creation

## Deployment & Infrastructure
- **Cloud Provider**: GCP (europe-west1 region)
- **VM Spec**: e2-medium (2 vCPU, 4GB RAM + 4GB swap) — swap required for Docker Gradle builds
- **Domain**: Configured via environment — see `infra/docker-compose.prod.yml`
- **Docker containers**: `muhabbet-backend`, `muhabbet-postgres`, `muhabbet-redis`, `muhabbet-minio`, `muhabbet-nginx` (via `infra/docker-compose.prod.yml`)
- **Firebase**: Phone Auth enabled, Android app `com.muhabbet.app`, credentials path via `FIREBASE_CREDENTIALS_PATH` env var
- **Deploy**: `cd infra && docker compose -f docker-compose.prod.yml up -d --build backend` (add `&& ... restart nginx` when nginx config changes)
- **Test users**: `+905000000001` (Test Bot), `+905000000002` — prefix 500 is unallocated in Turkey

## Lessons Learned / Known Gotchas
- **Windows Gradle**: Use `cmd //c ".\\gradlew.bat <task>"` from bash shell (not `gradlew.bat` directly)
- **Spring Security 403 vs 401**: Default Spring Security returns 403 for unauthenticated requests. Must configure `authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))` so Ktor Auth plugin triggers token refresh on 401
- **kotlinx-datetime `toLocalDateTime`**: It's an extension function — needs explicit `import kotlinx.datetime.toLocalDateTime`, not available via fully-qualified `instant.toLocalDateTime()`
- **SharedFlow vs Channel**: `MutableSharedFlow` broadcasts to ALL collectors (no competition). Use for WS messages shared between multiple screens
- **ConversationResponse.name is null for DMs**: Must resolve display name from `participants` list using currentUserId to find the OTHER participant
- **Phone numbers for testing**: Never use real-looking Turkish numbers. Prefix `+90500` is safe (unallocated by BTK)
- **serializer<Any>()** doesn't work well in KMP for API responses — always use the concrete type (e.g., `patch<UserProfile>`)
- **ADB serial != device name**: ADB shows serial number (e.g., `MVFUC6GMNNINAU5D`), not model name
- **WsClient.send() was a silent no-op**: `session?.outgoing?.send()` does nothing when session is null — must throw instead so callers can handle the error. Fixed to `session ?: throw Exception("WebSocket not connected")`
- **Unread badge never cleared**: Backend `updateDeliveryStatus` only updated ONE message. READ ack should bulk-update ALL unread messages via `markConversationRead()` + update `last_read_at` on `conversation_members`
- **Backend ConversationController was returning null for participant data**: Must inject `UserRepository` and lookup user displayName/phoneNumber when building `ParticipantResponse`
- **JWT for scripts/bots**: JWT must include `iss: "muhabbet"` claim (validated by `requireIssuer`). Use HMAC-SHA with `JWT_SECRET` env var
- **Test Bot script**: `infra/scripts/test_bot.py` — Python script that connects as Test Bot via WS and auto-replies. Requires `pip install PyJWT websockets`
- **Python on Windows**: Console encoding breaks with emojis — use `sys.stdout.reconfigure(encoding="utf-8")` and `PYTHONUNBUFFERED=1` for background execution
- **WsMessage type discriminators** (critical — JSON `type` field values from `@SerialName`):
  - `message.send` = SendMessage (client→server)
  - `message.ack` = AckMessage (client→server)
  - `presence.typing` = TypingIndicator (client→server)
  - `presence.online` = GoOnline (client→server)
  - `message.new` = NewMessage (server→client, broadcast)
  - `message.status` = StatusUpdate (server→client)
  - `ack` = ServerAck (server→client, response to send)
  - `error` = Error (server→client)
  - `call.room` = CallRoomInfo (server→client, LiveKit room credentials)
  - These are NOT the Kotlin class names — they're the serialized JSON type strings. Any external client (bot, web) must use these exact strings.
- **Message delivery flow**: Client sends `message.send` → backend saves + returns `ack(OK)` to sender + broadcasts `message.new` to recipient → recipient sends `message.ack(DELIVERED)` then `message.ack(READ)` → backend broadcasts `message.status` to sender
- **Single tick = SENT (ServerAck OK)**: Mobile shows clock while sending, single tick after ServerAck OK. Double tick (DELIVERED/READ) requires the OTHER client to send `message.ack` back — if recipient app is closed or not processing acks, sender stays at single tick forever
- **MinIO pre-signed URLs in Docker**: MinIO runs inside Docker network (`http://minio:9000`). Pre-signed URLs contain the endpoint they were generated with. You CANNOT use a separate MinIO client with a public endpoint — the SDK makes internal API calls (GetBucketLocation) to the endpoint which fail through nginx. **Solution**: Generate URLs with internal client, then string-replace the endpoint: `url.replace(internalEndpoint, publicEndpoint)`. Nginx proxies `/muhabbet-media/` to `http://minio:9000/muhabbet-media/` with `Host minio:9000` header so signatures validate.
- **KoinApplication vs KoinContext**: `KoinApplication` composable starts a NEW Koin instance — crashes with `KoinApplicationAlreadyStartedException` when Android Activity recreates. **Fix**: Use `GlobalContext.getOrNull() ?: startKoin { ... }.koin` + `KoinContext(context = koin)`.
- **WsClient.send() callers must try-catch**: Even though `send()` correctly throws when disconnected, ALL callers in Composables must wrap in try-catch. Uncaught exceptions in `LaunchedEffect`/`scope.launch` kill the coroutine silently or crash the collector.
- **Phone number normalization for contact sync**: Android contacts store numbers in various formats (05XX, 5XX, 90XX, +90XX with spaces/dashes). Backend stores hash of E.164 format (`+90XXXXXXXXX`). Mobile must normalize to E.164 BEFORE hashing, otherwise hashes won't match. Use `normalizeToE164()` from `util/PhoneNormalization.kt` (shared utility — do NOT duplicate).
- **Nginx location trailing slash matters**: `location /muhabbet-media/` does NOT match `/muhabbet-media?query` (no trailing slash). MinIO SDK sends bucket location requests without trailing slash, causing 301 redirects then signature failures.
- **No hardcoded strings in UI**: All user-visible strings must go through `composeResources/values/strings.xml` (Turkish) and `values-en/strings.xml` (English) using `stringResource(Res.string.*)`. Never use Turkish text directly in Kotlin files.
- **stringResource in coroutine blocks**: `stringResource()` is a `@Composable` function — cannot be called inside `scope.launch {}`. Resolve it as a `val` at the top of the composable, then reference the val inside the coroutine.
- **RECORD_AUDIO permission**: Dangerous permission requiring both manifest declaration AND runtime request via `rememberAudioPermissionRequester`. Without it, `MediaRecorder.setAudioSource()` crashes.
- **GroupRepository.editMessage deserialization**: Don't try to deserialize the PATCH response as `Message` — use raw `httpClient.patch()` and check `response.status.isSuccess()` instead. The caller optimistically updates the local list anyway.
- **CMP language switch**: CMP compose resources follow Android's `Configuration.locale`. To switch language at runtime: save preference in `muhabbet_prefs` SharedPreferences → apply `Configuration.setLocale()` in `MainActivity.onCreate()` before `setContent` → restart activity.
- **JPQL enum IN clause**: Do NOT use fully-qualified enum references (`com.example.Enum.VALUE`) in JPQL `IN` clauses — may fail at runtime in Hibernate 6. Use `@Param` with `List<EnumType>` parameter instead: `WHERE m.field IN :paramList`.
- **Delivery status hardcoded SENT**: `MessageMapper.toSharedMessage()` originally hardcoded `status = MessageStatus.SENT`. Fixed by adding `resolvedStatus` parameter and batch-querying `message_delivery_status` table. Sender sees aggregate min (all READ → READ, any DELIVERED → DELIVERED), recipient sees their own row.
- **Navigation stack pop-then-push anti-pattern**: Calling `goBack()` then `openChat()` pops the current screen before pushing the new one, so back button skips the popped screen. Instead, just `push()` directly to keep the full stack intact.
