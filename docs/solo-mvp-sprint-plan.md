# Muhabbet — Solo MVP Sprint Plan
### Kotlin Everywhere: Spring Boot + KMP/CMP

---

## Overview

| Item | Detail |
|------|--------|
| **Engineer** | 1 (you) |
| **Timeline** | 6-8 weeks to first working message |
| **Stack** | Kotlin → Spring Boot 3 (backend), KMP+CMP (mobile), shared module |
| **Architecture** | Modular monolith, hexagonal architecture per module |
| **MVP Goal** | Two phones can register, find each other, and exchange real-time text + image messages |

---

## Week 1: Skeleton + Shared Module + Auth

### Day 1-2: Project Setup
```
□ Clone/init monorepo: muhabbet/
□ Verify Gradle builds all subprojects: ./gradlew build
□ Start infra: cd infra && docker compose up -d
□ Verify PostgreSQL: psql -h localhost -U muhabbet -d muhabbet
□ Verify Redis: redis-cli ping
□ Verify MinIO: open http://localhost:9001 (minioadmin/minioadmin)
□ Run backend: cd backend && ../gradlew bootRun
□ Verify: GET http://localhost:8080/actuator/health → 200
□ Verify Flyway migration applied (check tables exist)
□ Push to GitHub, CI green
```

### Day 3: Shared Module Foundation
```
□ Domain models in shared/: Message, Conversation, UserProfile, Contact
□ WsMessage sealed class (WebSocket protocol)
□ DTOs: Auth requests/responses, API envelope
□ ValidationRules object (phone, OTP, message length)
□ EncryptionPort interface + NoOpEncryption
□ Verify: shared/ compiles for JVM + Android + iOS targets
□ Verify: backend/ can import and use shared/ types
```

### Day 4-5: Auth Module (Backend)
```
□ Domain: User aggregate, Device entity, OtpRequest value object
□ Ports (in): RequestOtpUseCase, VerifyOtpUseCase, RefreshTokenUseCase
□ Ports (out): UserRepository, OtpRepository, OtpSender, TokenStore
□ Service: AuthService (implements in-ports)
□ Adapters:
  □ AuthController (REST: /api/v1/auth/*)
  □ UserPersistenceAdapter (JPA → PostgreSQL)
  □ MockOtpSender (logs OTP to console for dev)
  □ JwtProvider (RS256 token generation/validation)
  □ JwtAuthFilter (Spring Security filter)
□ SecurityConfig (permit auth endpoints, protect everything else)
□ GlobalExceptionHandler + ErrorCode enum
□ Tests: AuthService unit test (MockK), AuthController integration test

✅ EXIT CRITERIA:
  POST /api/v1/auth/otp/request → 200 (OTP in console log)
  POST /api/v1/auth/otp/verify → 200 (returns JWT)
  GET /api/v1/users/me (with JWT) → 200 (returns user)
  GET /api/v1/users/me (without JWT) → 401
```

---

## Week 2: Messaging Core (Backend)

### Day 1-3: Messaging Module
```
□ Domain: Message aggregate root, Conversation aggregate root
□ Domain events: MessageSentEvent, MessageDeliveredEvent, MessageReadEvent
□ Ports (in): SendMessageUseCase, GetConversationHistoryUseCase,
              UpdateDeliveryStatusUseCase, CreateConversationUseCase
□ Ports (out): MessageRepository, ConversationRepository,
               MessageBroadcaster, EventPublisher
□ Service: MessagingService
□ Adapters:
  □ MessageController (REST: history, conversations list)
  □ ConversationController (REST: create, list)
  □ MessagePersistenceAdapter (JPA → PostgreSQL)
  □ ConversationPersistenceAdapter (JPA → PostgreSQL)
□ Tests: MessagingService unit test, conversation creation integration test

✅ EXIT CRITERIA:
  POST /api/v1/conversations (create direct) → 201
  GET /api/v1/conversations → 200 (list inbox)
  GET /api/v1/conversations/{id}/messages → 200 (paginated history)
```

### Day 4-5: WebSocket + Real-Time
```
□ WebSocketConfig: register WS endpoint at /ws
□ ChatWebSocketHandler: authenticate via JWT query param,
  parse WsMessage frames, route to MessagingService
□ WebSocketSessionManager: track userId → WebSocket sessions (in-memory + Redis)
□ WebSocketBroadcaster implements MessageBroadcaster:
  route message to recipient's WS session
□ Presence module:
  □ RedisPresenceStore: online/offline tracking
  □ Typing indicator forwarding via WS
□ Offline queue:
  □ If recipient not connected → message waits in DB
  □ On reconnect: drain unseen messages as NewMessage frames
□ Heartbeat: Ping/Pong every 30s, disconnect on 90s timeout
□ Tests: WebSocket connection test, message delivery test

✅ EXIT CRITERIA:
  Two WebSocket clients can connect (wscat or Postman)
  Client A sends SendMessage → Client B receives NewMessage
  Server sends ServerAck to Client A
  Client B sends AckMessage(DELIVERED) → Client A gets StatusUpdate
  Disconnect Client B → send message → reconnect → message delivered
```

---

## Week 3: Flutter Mobile Skeleton → Wait, KMP/CMP!

### Day 1-2: CMP Project Setup
```
□ Initialize mobile/composeApp with CMP template
□ Verify: ./gradlew :mobile:composeApp:assembleDebug (Android)
□ Verify: iOS build via Xcode (if Mac available)
□ Setup Koin DI container with modules
□ Setup Decompose: RootComponent with child stack
□ Setup Ktor HTTP client in shared/ or mobile:
  □ Base URL configuration
  □ JWT interceptor (attach token, refresh on 401)
  □ Error handling (map API errors to domain errors)
□ Setup Ktor WebSocket client in shared/ or mobile
□ Setup SQLDelight:
  □ Schema definition (local messages, conversations, user cache)
  □ Generated queries
□ Basic Material3 theme (Turkish-friendly colors)
□ Navigation skeleton: Splash → Auth → Home (conversations list) → Chat

✅ EXIT CRITERIA:
  App launches on Android emulator
  Navigation between screens works
  Koin DI resolves all dependencies
  Ktor client can reach backend: GET /actuator/health → 200
```

### Day 3-5: Auth Flow (Mobile)
```
□ Auth screens:
  □ PhoneInputScreen: Turkish phone input with +90 prefix
  □ OtpVerificationScreen: 6-digit OTP entry, countdown timer
  □ ProfileSetupScreen: display name + avatar (for new users)
□ AuthRepository: Ktor HTTP calls to /api/v1/auth/*
□ Token storage: secure local storage (encrypted shared prefs / keychain)
□ Auth state management: ViewModel with StateFlow
□ Auto-token-refresh: Ktor interceptor refreshes on 401
□ Deep integration with Decompose navigation (auth guard)

✅ EXIT CRITERIA:
  Phone input → OTP screen → enter code from backend console → logged in
  Token persisted → app restart → still logged in
  Token expired → auto-refresh → seamless
```

---

## Week 4: Chat Feature (Mobile)

### Day 1-3: Conversation List + Chat Screen
```
□ ConversationListScreen:
  □ Shows all conversations with last message preview
  □ Unread count badges
  □ Pull to refresh
  □ Tap → navigate to ChatScreen
  □ FAB → new chat (contact selection)
□ ChatScreen:
  □ Message list (LazyColumn, newest at bottom)
  □ Message bubbles (sent = right/blue, received = left/gray)
  □ Text input bar with send button
  □ Scroll to bottom on new message
  □ Delivery status indicators (✓ sent, ✓✓ delivered, blue ✓✓ read)
  □ Typing indicator ("Mehmet yazıyor...")
  □ Date separator headers
□ WebSocket integration:
  □ Connect on app start, reconnect on failure
  □ Send messages via WsMessage.SendMessage
  □ Receive via WsMessage.NewMessage → update local DB → update UI
  □ Send delivery ACKs when message rendered
  □ Send read ACKs when conversation opened
□ Local storage (SQLDelight):
  □ Cache messages locally
  □ Offline queue: save unsent messages, send on reconnect
  □ Conversations cached for instant inbox load

✅ EXIT CRITERIA:
  Open app → see conversation list
  Tap conversation → see message history
  Type message → send → appears on other device
  Receive message → bubble appears in real-time
  Delivery status: ✓ → ✓✓ → blue ✓✓ works
```

### Day 4-5: Contacts + New Chat
```
□ ContactsScreen: list of registered contacts
□ Contact sync:
  □ Request phone book permission
  □ Hash phone numbers (SHA-256)
  □ POST /api/v1/users/contacts/sync
  □ Show matched contacts
□ New chat flow:
  □ Select contact → create/get conversation → navigate to chat
□ User module (backend):
  □ UserController: GET /me, PATCH /me
  □ ContactSyncController: POST /contacts/sync
  □ Phone hash matching logic
  □ UserPersistenceAdapter

✅ EXIT CRITERIA:
  App syncs contacts → shows registered users
  Tap contact → creates conversation → opens chat
  Send first message to new contact → works
```

---

## Week 5: Media + Push Notifications

### Day 1-2: Media Sharing
```
□ Media module (backend):
  □ MediaService: upload, download, thumbnail generation
  □ MinioFileStorageAdapter (S3 API)
  □ MediaController: POST /media/upload, GET /media/{id}
  □ Thumbnail generation (Java ImageIO / Thumbnailator)
  □ Media expiry job (30-day cleanup, @Scheduled)
□ Media in mobile:
  □ Image picker integration
  □ Upload flow: pick image → upload to /media/upload → send message with mediaUrl
  □ Image message bubble: show thumbnail, tap to full-size
  □ Image loading + caching (Coil for CMP)

✅ EXIT CRITERIA:
  Pick photo → upload → send → recipient sees image
  Thumbnails display in chat
  Full-size image loads on tap
```

### Day 3-4: Push Notifications
```
□ Notification module (backend):
  □ NotificationService: decide when to push
  □ FcmPushAdapter: send via Firebase Admin SDK
  □ Trigger: MessageSentEvent → if recipient offline → push
  □ Push payload: { conversationId, senderName, preview }
□ Device token management:
  □ PUT /api/v1/devices/push-token (register FCM token)
  □ Store in devices table
□ Mobile:
  □ Firebase setup (google-services.json / GoogleService-Info.plist)
  □ Token registration on app start
  □ Foreground notification handling
  □ Background: tap notification → open conversation
  □ Badge count

✅ EXIT CRITERIA:
  Send message to offline user → push notification appears
  Tap notification → app opens correct conversation
  Foreground: no system notification, message appears in chat
```

### Day 5: Presence Polish
```
□ Online/offline indicator on conversation list (green dot)
□ Last seen: "son görülme 14:30"
□ Typing indicator smooth animations
□ Presence updates via WebSocket PresenceUpdate frames
```

---

## Week 6: Deployment + Hardening

### Day 1-2: Production Hardening
```
□ Rate limiting (per-user, per-endpoint):
  □ OTP: 5/hour per phone
  □ Messages: 100/minute per user
  □ Media upload: 20/hour per user
□ Input validation: Jakarta Bean Validation on all DTOs
□ SQL injection prevention (parameterized queries — JPA handles this)
□ CORS configuration (allow mobile origins)
□ Request logging (structured JSON, no PII)
□ Connection recovery testing:
  □ Kill backend → verify mobile reconnects
  □ Kill PostgreSQL → verify backend recovers
  □ Network throttle (3G) → verify messages still deliver
□ Database index optimization (EXPLAIN ANALYZE critical queries)
□ Sentry integration (backend + mobile)
```

### Day 3-4: Deployment to GCP
```
□ Dockerfile (multi-stage build):
  □ Stage 1: Gradle build
  □ Stage 2: Eclipse Temurin 21 JRE (distroless)
□ docker-compose.prod.yml:
  □ Backend + PostgreSQL + Redis + MinIO + Nginx
□ GCP VM setup:
  □ e2-medium (2 vCPU, 4 GB) — free trial
  □ Ubuntu 24.04
  □ Docker + Docker Compose installed
  □ Firewall: 80, 443 only
□ DNS: muhabbet.rollingcatsoftware.com → GCP VM IP (A record in Hostinger)
□ Nginx reverse proxy:
  □ TLS via Let's Encrypt (certbot)
  □ WebSocket upgrade support
  □ Proxy to backend:8080
□ GitHub Actions deploy workflow:
  □ Build → Push Docker image → SSH → docker compose pull + up
□ Smoke test: full flow on real server

✅ EXIT CRITERIA:
  https://muhabbet.rollingcatsoftware.com/actuator/health → 200
  WebSocket connects via wss://
  Full flow works on real Android device over mobile network
```

### Day 5: Beta Launch
```
□ Install APK on 5-10 real devices (friends/family)
□ Full flow: register → sync contacts → chat → send image → push notification
□ Test on slow network (3G throttle)
□ Test offline → online transition
□ Collect crash reports via Sentry
□ Fix critical bugs
□ iOS TestFlight build (if Apple Developer account ready)
```

---

## Week 7-8: Buffer + Polish

```
□ Bug fixes from beta feedback
□ UI polish: animations, empty states, error states
□ Performance optimization: message list scrolling, image loading
□ Edge cases: very long messages, rapid sending, concurrent edits
□ README.md documentation
□ Architecture documentation update
□ Plan Phase 2 (group chat, E2E encryption, voice messages)
```

---

## Phase 2 Preview (after MVP)

| Feature | Estimated Time | Priority |
|---------|---------------|----------|
| Signal Protocol E2E encryption | 4-6 weeks | High |
| Group messaging (Sender Keys) | 2-3 weeks | High |
| Voice messages | 1-2 weeks | High |
| Voice/video calls (WebRTC) | 4-6 weeks | Medium |
| Message search | 1 week | Medium |
| Disappearing messages | 1 week | Medium |
| Turkish provider migration | 1 week | High |
| App Store / Google Play release | 1-2 weeks | High |
