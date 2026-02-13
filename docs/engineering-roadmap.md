# Muhabbet — Engineering Roadmap

> **Author**: Lead Engineer
> **Date**: February 13, 2026
> **Status**: Living document — updated as priorities shift

---

## Executive Summary

Muhabbet is a privacy-first Turkish messaging platform targeting Turkey's 85M population as a domestic WhatsApp alternative. The MVP is **feature-complete**: 24 core features shipped, 6 rounds of bug fixes applied, hexagonal architecture enforced across 3 backend modules, and a fully functional Android app built with Compose Multiplatform.

**What exists today:**
- 19,500+ lines of Kotlin across backend, shared KMP module, and mobile
- 19 REST controllers, 15 domain services, 13 database migrations
- ~125 backend unit/integration tests + 25+ mobile/shared tests (JUnit 5, MockK, Testcontainers, kotlin-test)
- Real-time WebSocket messaging, media sharing (images/voice/files/video), push notifications, presence tracking
- Group messaging, polls, stories, channels, location sharing, reactions, stickers/GIFs
- Call UI screens (IncomingCallScreen, ActiveCallScreen, CallHistoryScreen) + backend signaling
- E2E encryption infrastructure (backend key exchange + client E2EKeyManager interface + NoOpKeyManager)
- iOS platform fully implemented (ImagePicker, FilePicker, ImageCompressor, CrashReporter, PushTokenProvider, LocaleHelper)
- CI/CD pipeline: GitHub Actions (backend CI, mobile CI, security scanning with Trivy/Gitleaks/CodeQL, deployment)
- Security hardening: HSTS, CSP, X-Frame-Options, InputSanitizer, Permissions-Policy
- Performance optimization: 12 DB indexes, N+1 fixes, connection pooling, nginx gzip/caching, PG tuning
- Production deployment on GCP (Docker Compose, nginx, Let's Encrypt)
- Tech stack: Kotlin 2.3.10, Spring Boot 4.0.2, Java 25, Gradle 8.14.4, Ktor 3.1.3

**What this roadmap covers:** The engineering path from working MVP to production-grade messaging platform — organized into 7 phases with clear dependencies, decision points, and risk mitigations.

---

## Current State Assessment

### Strengths
- **Architecture discipline**: Hexagonal architecture consistently applied; domain is framework-agnostic
- **Kotlin everywhere**: Single language across backend, shared module, and mobile — maximum code reuse
- **Feature parity with competitors**: Core messaging features match WhatsApp/Telegram for 1:1 and group chat
- **Backend signaling ready**: Call infrastructure and encryption key exchange are built — only client integration remains

### Gaps (Updated Feb 2026)
| Gap | Severity | Impact | Progress |
|-----|----------|--------|----------|
| No voice/video calls (WebRTC) | **Critical** | Non-negotiable for Turkish market (voice-heavy culture) | Call UI built, signaling ready — needs LiveKit SDK |
| No E2E encryption (client) | **High** | Core privacy differentiator missing | Backend + client infra ready — needs libsignal-client |
| ~~iOS incomplete~~ | ~~High~~ | ~~Blocks 30% of Turkish smartphone market~~ | **RESOLVED** — all platform modules implemented |
| ~~Zero mobile tests~~ | ~~Medium~~ | ~~10,670 lines untested~~ | **RESOLVED** — 25+ tests (FakeTokenStorage, AuthRepository, PhoneNormalization, WsMessage serialization) |
| ~~No CI/CD pipeline~~ | ~~Medium~~ | ~~Manual deploys, no automated quality gate~~ | **RESOLVED** — GitHub Actions (backend, mobile, security, deploy) |
| 2 active bugs (push notifications, delivery ticks) | **Medium** | User-facing issues in production | Env var fix + code fix needed |
| iOS APNs delivery missing | **Medium** | iOS push notifications won't work | Needs FCM→APNs bridge or direct adapter |
| Single-server architecture | **Low** (for now) | Adequate for beta; blocks beyond ~10K concurrent users | Performance optimized |

### Key Metrics
| Metric | Current | Target (Launch) |
|--------|---------|-----------------|
| Backend test coverage | ~125 tests (8 files) | 300+ tests (every service + controller) |
| Mobile test coverage | 25+ tests (4 files) | 50+ tests (ViewModels + repositories) |
| CI/CD | Active (GitHub Actions) | All PRs gated |
| Security scanning | Trivy + Gitleaks + CodeQL | Pen test passed |
| P95 message latency | Unmeasured | < 200ms |
| Concurrent WS connections | Untested at scale | 5,000+ per instance |
| Crash-free rate (mobile) | Unknown (Sentry active) | > 99.5% |

---

## Phase 1: Stabilization & Launch Prep

**Goal**: Fix active bugs, establish CI/CD, get on Play Store internal track.

### 1.1 Active Bug Fixes
| Bug | Root Cause | Fix |
|-----|-----------|-----|
| Push notifications not firing | `FCM_ENABLED=false` in production | Set `FCM_ENABLED=true` + `FIREBASE_CREDENTIALS_PATH` in `docker-compose.prod.yml` |
| Delivery ticks stuck at single | Mobile only sends READ ack, never DELIVERED | Add global DELIVERED ack dispatch in `App.kt` when `NewMessage` arrives; keep READ ack in `ChatScreen` only |

### 1.2 CI/CD Pipeline (GitHub Actions) — DONE
- **backend-ci.yml**: On push to `backend/` or `shared/` — Gradle test + bootJar with caching ✅
- **mobile-ci.yml**: On push to `mobile/` or `shared/` — Android assembleDebug + iOS framework build ✅
- **security.yml**: Trivy vulnerability scanning, Gitleaks secret detection, CodeQL static analysis ✅
- **deploy.yml**: On merge to `main` — SSH to GCP, docker compose pull/up with health check and rollback ✅
- Gate: All PRs require CI green before merge

### 1.3 Play Store Internal Testing
| Step | Type |
|------|------|
| Generate release keystore | Manual (local, never committed) |
| Build signed AAB | `./gradlew :mobile:composeApp:bundleRelease` |
| Capture 8 screenshots (Turkish) | Manual (emulator or device) |
| Feature graphic (1024x500) | Design |
| Store description (TR + EN) | Content |
| IARC content rating | Manual questionnaire |
| Upload to Play Console internal track | Manual |
| Smoke test on 3+ devices | QA |

### 1.4 Observability Baseline
- Verify Sentry captures crashes with stack traces and user context
- Enable Spring Actuator Prometheus endpoint (`/actuator/prometheus`)
- Add Grafana dashboard for: JVM heap, WS active connections, message throughput, HTTP error rates
- Structured log aggregation (JSON logs → file rotation or external collector)

---

## Phase 2: Quality & Testing

**Goal**: Build confidence for rapid iteration. Catch regressions before users do.

### 2.1 Backend Test Expansion
| Area | Current | Target | Approach |
|------|---------|--------|----------|
| Auth module | 2 test files | Full coverage | Add edge cases: expired OTP, max retries, device limits |
| Messaging module | 3 test files | Full coverage | Delivery status state machine, pagination edge cases, WebSocket reconnect |
| Media module | 1 test file | Full coverage | Upload validation, thumbnail generation, pre-signed URL expiry |
| Integration tests | 1 (AuthController) | All controllers | Testcontainers (PG + Redis), full request-response cycles |
| Call signaling | 0 | Unit tests | CallSignalingService state transitions, concurrent call handling |
| Encryption | 0 | Unit tests | Key bundle CRUD, one-time pre-key consumption |

**Test naming convention**: `should [expected behavior] when [condition]`

### 2.2 Mobile Test Foundation (Started — 25+ tests)
- **Unit tests**: FakeTokenStorageTest (5 tests), AuthRepositoryTest ✅
- **Utility tests**: PhoneNormalizationTest (14 tests — E.164, Turkish formats, separators) ✅
- **Shared module tests**: WsMessageSerializationTest (25+ tests — all frame types, round-trip fidelity) ✅
- **Remaining**: ViewModels (AuthViewModel, ChatViewModel), more repository tests
- **Target**: 50+ tests covering critical paths (auth flow, message send/receive, delivery status)

### 2.3 Load Testing
- **Tool**: k6 or Gatling against staging environment
- **Scenarios**:
  - 1,000 concurrent WebSocket connections, each sending 1 msg/sec
  - 100 simultaneous media uploads (10MB each)
  - Contact sync with 500 phone hashes per request
- **Goal**: Identify bottlenecks before real users hit them. Establish P95 baselines.

---

## Phase 3: Voice & Video Calls

**Goal**: 1:1 voice and video calls — the make-or-break feature for Turkish market adoption.

### 3.1 Architecture Decision: WebRTC Provider

| Option | Pros | Cons |
|--------|------|------|
| **LiveKit Cloud** (Recommended) | Managed SFU, free dev tier, Kotlin SDK, scales automatically | Vendor dependency, data may leave Turkey |
| **Self-hosted LiveKit** | Full control, data stays on Turkish infra | Ops burden, TURN server management, GPU for SFU |
| **Raw WebRTC (peer-to-peer)** | No SFU cost | NAT traversal issues, no group call path, TURN still needed |

**Recommendation**: Start with LiveKit Cloud for speed. Migrate to self-hosted on Turkish infra before public launch if KVKK requires data residency for call media.

### 3.2 Implementation Plan

**Backend** (signaling already built):
- Extend `CallSignalingService` to integrate with LiveKit room management API
- Add `LiveKitRoomAdapter` implementing a new `CallRoomProvider` out-port
- Call metadata persistence (duration, participants, quality metrics) via existing `call_history` table

**Mobile** (call UI DONE, WebRTC integration remaining):
- ✅ `IncomingCallScreen`: full-screen overlay with avatar, accept/decline buttons, WS signaling
- ✅ `ActiveCallScreen`: in-call UI with duration timer, mute/speaker toggles, end call, CallEnd listener
- ✅ `CallHistoryScreen`: paginated history list with direction icons, duration, call-back
- ✅ Decompose navigation: IncomingCall, ActiveCall, CallHistory configs wired in MainComponent
- ✅ CallRepository: REST client for call history endpoint
- Remaining: Add LiveKit Android SDK dependency for actual media streams
- Remaining: Audio routing (earpiece/speaker/Bluetooth via AudioManager)
- Remaining: Background call service, picture-in-picture for video calls (Android 12+)

**Shared module**:
- Add `CallState` sealed class to protocol (Ringing, Connected, OnHold, Ended)
- Call quality metrics DTO

### 3.3 Group Calls (After 1:1 Stable)
- LiveKit rooms support multi-party natively
- Participant grid UI (2x2, 3x3 layouts)
- Dominant speaker detection
- Screen sharing (desktop client prerequisite)

---

## Phase 4: End-to-End Encryption

**Goal**: Signal Protocol implementation — the core privacy differentiator.

### 4.1 Architecture (Backend + Client Infrastructure Built)
- `encryption_keys` table: identity key, signed pre-key, registration ID ✅
- `one_time_pre_keys` table: consumable pre-keys for X3DH ✅
- `POST/GET /api/v1/encryption/keys` endpoints: key bundle upload/fetch ✅
- `EncryptionPort` interface in shared module: ready for NoOp → Signal swap ✅
- `E2EKeyManager` interface: X3DH key lifecycle (generate, initialize session, encrypt/decrypt) ✅
- `NoOpKeyManager`: MVP pass-through implementation ✅
- `EncryptionRepository`: Mobile client for key exchange API ✅
- Security headers: HSTS, CSP, X-Frame-Options, InputSanitizer ✅

### 4.2 Client Implementation

**Library**: `libsignal-client` (Signal Foundation, battle-tested, Kotlin bindings available)

**Key exchange (X3DH)**:
1. On registration: generate identity key pair, signed pre-key, 100 one-time pre-keys
2. Upload key bundle to server via existing endpoint
3. On first message to new contact: fetch their pre-key bundle, perform X3DH, establish session

**Message encryption (Double Ratchet)**:
1. Each message encrypted with session-specific message key
2. Ratchet advances on every send/receive — forward secrecy per message
3. Encrypted payload replaces plaintext in `SendMessage` WsMessage

**Local key storage**:
- Android: EncryptedSharedPreferences (AndroidKeyStore-backed AES)
- iOS: Keychain Services
- SQLDelight table for session state (ratchet keys, chain keys, message keys)

**Key verification UI**:
- Safety number comparison (QR code + numeric fingerprint)
- Key change notification in chat header

### 4.3 Migration Strategy
- Existing messages remain unencrypted (server-readable)
- New messages after E2E activation are encrypted
- Server stores ciphertext only — cannot read new messages
- Graceful fallback: if recipient hasn't uploaded keys, send unencrypted with warning badge

### 4.4 Group E2E (Sender Keys)
- After 1:1 E2E is stable
- Signal's Sender Keys protocol for efficient group encryption
- Each member distributes a sender key to all other members
- One encrypt operation per message (vs N encrypts in naive approach)

---

## Phase 5: iOS & Cross-Platform

**Goal**: Ship iOS app to App Store. Complete the cross-platform promise.

### 5.1 iOS Completion Checklist
| Component | Status | Work Needed |
|-----------|--------|-------------|
| AudioPlayer | Done (AVAudioPlayer) | — |
| AudioRecorder | Done (AVAudioRecorder) | — |
| ContactsProvider | Done (CNContactStore) | — |
| PushTokenProvider | Done (UNUserNotificationCenter + NSUserDefaults) | — |
| ImagePicker | Done (PHPickerViewController) | — |
| FilePicker | Done (UIDocumentPickerViewController) | — |
| ImageCompressor | Done (CoreGraphics + UIImageJPEGRepresentation) | — |
| CrashReporter | Done (NSLog + Sentry CocoaPod hooks) | — |
| LocaleHelper | Done (AppleLanguages UserDefaults) | — |
| FirebasePhoneAuth | Done (fallback stub, isAvailable=false) | — |
| APNs delivery | **Missing** | FCM → APNs bridge OR direct APNs adapter |
| Push notification handling | **Missing** | Background fetch, notification service extension |
| Deep linking | **Missing** | Universal links for conversation/invite URLs |

### 5.2 iOS-Specific Testing
- TestFlight internal distribution
- iOS 16+ compatibility testing (minimum target)
- iPad layout considerations (adaptive UI)
- Background WebSocket behavior (iOS aggressively kills background connections)
  - Solution: APNs for offline delivery + WS reconnect on foreground

### 5.3 App Store Submission
- Apple Developer Program enrollment ($99/year)
- App Store screenshots (iPhone 15 Pro, iPad Pro)
- Privacy nutrition labels (App Privacy section)
- App Review guidelines compliance check

### 5.4 Platform Decision Point
> **Question**: Should iOS stay as CMP (current) or pivot to native SwiftUI?

| Factor | CMP (Keep) | SwiftUI (Pivot) |
|--------|-----------|-----------------|
| Code sharing | 80%+ shared with Android | Separate UI layer (~40% duplication) |
| Platform feel | Good but not native-perfect | Native gestures, animations, patterns |
| Development speed | Faster (single codebase) | Slower (two UI codebases) |
| Hiring | Kotlin devs (easier) | Need Swift developer |
| Ecosystem maturity | Growing but occasional rough edges | Mature, well-documented |

**Recommendation**: Stay with CMP for now. The shared code advantage is significant for a small team. Re-evaluate when the team grows beyond 3 engineers.

---

## Phase 6: Scale & Infrastructure

**Goal**: Prepare architecture for growth beyond single-server MVP.

### 6.1 Horizontal Scaling Path

```
Current (MVP):                    Target (10K+ users):
┌──────────────┐                  ┌──────────────┐  ┌──────────────┐
│  Single VM   │                  │  App Server 1 │  │  App Server 2 │
│  (all-in-one)│        →         │  (backend)    │  │  (backend)    │
│  backend     │                  └──────┬───────┘  └──────┬───────┘
│  postgres    │                         │                  │
│  redis       │                  ┌──────┴──────────────────┴──────┐
│  minio       │                  │         Load Balancer           │
│  nginx       │                  │      (nginx / Cloud LB)        │
└──────────────┘                  └──────┬──────────────────┬──────┘
                                         │                  │
                                  ┌──────┴──────┐   ┌──────┴──────┐
                                  │  PostgreSQL  │   │    Redis     │
                                  │  (managed)   │   │  (managed)   │
                                  └─────────────┘   └─────────────┘
```

### 6.2 WebSocket Scaling
- **Problem**: WebSocket connections are sticky to a single server instance
- **Solution**: Redis Pub/Sub for cross-instance message routing
  - When User A (on Server 1) sends to User B (on Server 2):
    1. Server 1 publishes to Redis channel `user:{B_id}`
    2. Server 2 subscribes, delivers to User B's WS session
  - Implementation: Replace in-memory `WebSocketSessionManager` with `RedisBackedSessionManager`

### 6.3 Database Scaling
- **Phase 1 (current)**: Single PostgreSQL instance — adequate for 100K users
- **Phase 2**: Read replicas for conversation list queries, message search
- **Phase 3**: Message table partitioning by `conversation_id` hash (when messages exceed 100M rows)
- **Phase 4 (if needed)**: Extract messages to ScyllaDB/Cassandra — repository pattern makes this a swap

### 6.4 Media CDN
- **When**: Media traffic exceeds 100GB/day or latency from single MinIO server is noticeable
- **How**: CloudFront or Bunny CDN in front of MinIO. Pre-signed URLs still work — CDN caches the response.
- **Turkish CDN option**: Medianova (Istanbul PoPs, KVKK-friendly)

### 6.5 Infrastructure Migration
- **Current**: GCP free trial (europe-west1)
- **Target**: Turkish cloud provider for KVKK data residency
- **Candidates**: TTBulut, Huawei Cloud Turkey, Turkcell Cloud
- **Migration**: Docker Compose is portable — same `docker-compose.prod.yml` works anywhere
- **Managed services**: Move PostgreSQL and Redis to managed offerings to reduce ops burden

---

## Phase 7: Platform Expansion & Growth

**Goal**: Web/desktop client, monetization strategy, network effects.

### 7.1 Web Client
- **Framework**: React + TypeScript (largest talent pool) OR Kotlin/JS (code sharing with backend)
- **Device linking**: QR code scan from mobile → establish secondary device session
- **Message sync**: Fetch history from server (messages stored server-side before E2E; after E2E, requires multi-device Signal Protocol)
- **WebSocket**: Same `WsMessage` protocol — web client connects to same `/ws` endpoint

### 7.2 Desktop Client
- **Option A**: Electron wrapper around web client (fastest path)
- **Option B**: Compose for Desktop (Kotlin, shares code with mobile)
- **Recommendation**: Web first, desktop later. Web covers 90% of desktop use cases.

### 7.3 Channel Monetization
- Channel analytics dashboard (views, reach, engagement)
- Subscriber management (premium channels)
- Tipping / Super Chats for channel posts
- Revenue model: 70/30 split (creator/platform) — standard in Turkey

### 7.4 Growth Features
- Invite links with referral tracking
- Public group/channel directory
- Bot platform (API for automated accounts — test bot script is the prototype)
- Mini-apps / integrations (payments, appointments, polls)

---

## Technical Debt Reduction Plan

| Debt Item | Severity | Phase | Resolution |
|-----------|----------|-------|------------|
| ~~Zero mobile unit tests~~ | ~~Medium~~ | ~~Phase 2~~ | **RESOLVED** — 25+ tests (FakeTokenStorage, AuthRepository, PhoneNormalization, WsMessage) |
| ~~No CI/CD pipeline~~ | ~~Medium~~ | ~~Phase 1~~ | **RESOLVED** — GitHub Actions (backend, mobile, security, deploy) |
| ~~iOS ImagePicker stub~~ | ~~Medium~~ | ~~Phase 5~~ | **RESOLVED** — PHPickerViewController implementation |
| ~~iOS CrashReporter stub~~ | ~~Low~~ | ~~Phase 5~~ | **RESOLVED** — NSLog + Sentry CocoaPod hooks |
| ~~No performance optimization~~ | ~~Medium~~ | — | **RESOLVED** — 12 DB indexes, N+1 fixes, connection pooling, nginx/PG tuning |
| Backend enum duplication | Low | Monitor | Type aliases if maintenance burden grows |
| Single-server architecture | Low | Phase 6 | Redis-backed WS sessions, managed DB |
| No load/stress tests | Medium | Phase 2 | k6 scripts for WS + HTTP endpoints |
| Push notifications disabled | Medium | Phase 1 | Environment variable fix in production |
| Delivery ack bug | Medium | Phase 1 | Global DELIVERED ack in App.kt |

---

## Risk Register

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| WhatsApp copies privacy features, neutralizing differentiator | Medium | High | Move faster on KVKK compliance + Turkish data residency — regulatory moat |
| LiveKit pricing increases after scale | Low | Medium | Architecture uses port/adapter — can swap to self-hosted LiveKit or raw WebRTC |
| Apple rejects CMP-based iOS app | Low | High | CMP apps are accepted today (JetBrains Toolbox, others). Fallback: SwiftUI rewrite |
| KVKK enforcement requires Turkish data residency | Medium | High | Docker Compose is portable. Migration to Turkish provider is a one-day operation |
| Signal Protocol integration is harder than expected | Medium | Medium | `libsignal-client` is battle-tested. Budget extra time for key management edge cases (new device, reinstall) |
| Solo engineer burnout / bus factor | High | Critical | Document everything (CLAUDE.md is excellent). Prioritize hiring a second engineer at Phase 3 |

---

## Key Decision Points

These decisions should be made at specific milestones — not prematurely (YAGNI).

| Decision | When to Decide | Options | Current Lean |
|----------|----------------|---------|--------------|
| WebRTC provider | Before Phase 3 starts | LiveKit Cloud / Self-hosted / Raw P2P | LiveKit Cloud |
| E2E encryption library | Before Phase 4 starts | libsignal-client / Custom | libsignal-client |
| iOS strategy (CMP vs native) | After Phase 5 TestFlight feedback | Keep CMP / Pivot to SwiftUI | Keep CMP |
| Turkish cloud provider | Before public launch | TTBulut / Huawei Cloud TR / Turkcell Cloud | Research needed |
| Revenue model | Before Phase 7 | Ad-free premium / Freemium / Channel monetization / B2B | Channel monetization |
| Web client framework | Before Phase 7 | React + TS / Kotlin/JS / Compose Web | React + TS |
| Domain name for launch | Before Play Store public release | muhabbet.app / muhabbet.com.tr / other | muhabbet.app |
| Second engineer hire | When Phase 3 begins | Full-stack Kotlin / Mobile specialist / Backend specialist | Full-stack Kotlin |

---

## Phase Dependencies

```
Phase 1 (Stabilization)
  │
  ├──→ Phase 2 (Quality & Testing)
  │       │
  │       ├──→ Phase 3 (Voice/Video Calls)
  │       │       │
  │       │       └──→ Phase 6.2 (WebSocket Scaling — needed for call signaling at scale)
  │       │
  │       ├──→ Phase 4 (E2E Encryption)
  │       │       │
  │       │       └──→ Phase 7.1 (Web Client — multi-device E2E adds complexity)
  │       │
  │       └──→ Phase 5 (iOS)
  │               │
  │               └──→ Phase 7 (Growth — need both platforms before growth push)
  │
  └──→ Phase 6.1 (Infrastructure) — can start in parallel after Phase 1
```

**Critical path**: Phase 1 → Phase 2 → Phase 3 (calls) → Public launch

Voice/video calls are the single most important feature for Turkish market adoption. Everything else is secondary until calls work.

---

## Team Scaling Recommendations

| Team Size | Phase | Roles |
|-----------|-------|-------|
| 1 (current) | Phases 1-2 | Solo full-stack (you) |
| 2 | Phase 3 | + Mobile engineer (calls UI is complex) |
| 3 | Phase 4-5 | + Security/crypto engineer (Signal Protocol) |
| 5 | Phase 6-7 | + DevOps/SRE + Backend engineer |

---

## Success Criteria Per Phase

| Phase | Ship Criteria |
|-------|--------------|
| **Phase 1** | CI green on every PR. App on Play Store internal track. Zero active bugs. |
| **Phase 2** | 300+ backend tests, 50+ mobile tests. Load test passes at 1K concurrent WS. |
| **Phase 3** | 1:1 voice call works reliably over 4G. Call drop rate < 5%. |
| **Phase 4** | Messages encrypted end-to-end. Safety number verification works. Cannot read messages server-side. |
| **Phase 5** | iOS app on TestFlight. Feature parity with Android for core messaging. |
| **Phase 6** | Handles 10K concurrent users. Database reads from replica. Media served via CDN. |
| **Phase 7** | Web client launched. Channel monetization generating revenue. 100K registered users. |

---

## Appendix: Architecture Reference

### Current Module Map (Backend)
```
backend/
├── auth/           → OTP, JWT, devices, contacts, user data (KVKK)
├── messaging/      → Messages, conversations, groups, calls, presence,
│                     status, channels, polls, reactions, disappearing
├── media/          → Upload, download, thumbnails, storage stats
└── shared/         → Security config, WS config, Redis config, exceptions,
                      JWT provider, domain events
```

### Shared KMP Module
```
shared/
├── model/          → Message, Conversation, UserProfile, Contact
├── protocol/       → WsMessage sealed class (10+ frame types)
├── dto/            → API request/response types (kotlinx.serialization)
├── validation/     → Phone, OTP, message length rules
└── port/           → EncryptionPort + E2EKeyManager (NoOp → Signal Protocol)
```

### Database Schema (13 Migrations)
```
V1:  users, devices, otp_requests, refresh_tokens, conversations,
     conversation_members, direct_conversation_lookups, messages,
     message_delivery_status, media_files, phone_hashes
V2:  last_seen_at (presence)
V3:  media_duration (voice/video)
V4:  edited_at (message edit)
V5:  starred_messages
V6:  disappearing_messages + timers
V7:  polls + poll_options + poll_votes
V8:  statuses + status_viewers
V9:  reactions
V10: pinned_conversations
V11: encryption_keys + one_time_pre_keys
V12: call_history
V13: forwarded_from
```

---

*This is a living document. Update it as priorities shift and decisions are made.*
