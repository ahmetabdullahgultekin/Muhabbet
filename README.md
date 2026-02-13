# Muhabbet

**Turkey's domestic messaging platform.** Privacy-first, KVKK-compliant, built with Kotlin everywhere.

> *Muhabbet* — Turkish for "conversation, chat" — from Arabic *mahabbah* meaning "love."

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Spring Boot 4.0.2 + Kotlin 2.3.10 (Java 25), PostgreSQL 16, Redis 7, MinIO |
| **Mobile** | Compose Multiplatform (Android + iOS), Ktor 3.1.3, Koin, Decompose |
| **Shared** | Kotlin Multiplatform (domain models, protocol, validation, DTOs) |
| **Infra** | Docker Compose, Nginx, GitHub Actions CI/CD |
| **Security** | HSTS, CSP, X-Frame-Options, InputSanitizer, Trivy + CodeQL scanning |
| **Monitoring** | Sentry (mobile), SLF4J + Logback (backend), Spring Actuator |

## Project Structure

```
muhabbet/
├── backend/     → Spring Boot modular monolith (hexagonal architecture)
├── shared/      → KMP shared module (domain models, WS protocol, DTOs)
├── mobile/      → Compose Multiplatform app (Android + iOS)
├── infra/       → Docker Compose, Nginx, deployment scripts
├── docs/        → Architecture docs, ADRs, API contract
├── CLAUDE.md    → Project context for Claude Code CLI
├── ROADMAP.md   → Product roadmap & phase tracking
└── CHANGELOG.md → Release history
```

## Features (24 MVP + Post-MVP)

### Core Messaging
- 1:1 and group messaging via WebSocket (real-time)
- Message delete/edit (soft delete, `editedAt`, context menu)
- Reply/quote + message forwarding
- Disappearing messages (24h, 7d, 90d)
- Starred messages (list view with scroll-to-message), message search (full-text)
- Typing indicators, read receipts, delivery status (batch-resolved)
- Message info (per-recipient delivery status with timestamps, media preview, avatars)
- Shared media viewer (images grid + documents list, video/voice playback, forward/delete)
- Pinch-to-zoom image viewer (1x-5x, double-tap toggle)

### Media & Content
- Image sharing (upload, thumbnails, full-size viewer)
- Voice messages (record, playback, audio bubbles)
- File/document sharing (PDF, DOC, etc.)
- Stickers & GIFs (GIPHY integration)
- Location sharing (static pin, map preview)
- Link previews (Open Graph metadata)
- Polls (create, vote, real-time results)

### Social
- Status/Stories (24h ephemeral, contacts visibility)
- Channels/Broadcasts (one-to-many, subscriber model)
- Contacts sync (phone hash matching)
- Presence (online/offline via Redis, last seen)

### Platform
- Auth: OTP via SMS (Netgsm) + JWT + device management
- Push notifications: FCM with grouping, inline reply, channels
- Localization: Turkish + English with runtime switch
- Dark mode + OLED theme
- Crash reporting: Sentry SDK (Android + iOS hooks)
- Storage usage stats (per-user breakdown by type)
- KVKK compliance: data export, account deletion

### Calling & Encryption (Infrastructure Ready)
- Call UI screens: IncomingCallScreen, ActiveCallScreen, CallHistoryScreen
- Call signaling infrastructure (WebSocket-based, LiveKit adapter ready)
- E2E encryption key exchange endpoints + client infrastructure (ready for Signal Protocol)

### Growth & Moderation
- Content moderation: Report/block system (BTK Law 5651 compliance)
- Channel analytics: Daily stats, subscriber tracking, REST API
- Bot platform: API token auth, webhook support, permissions system
- Message backup: Initiate, track status, download, delete

### Security & Quality
- Security headers: HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy
- Input sanitization: HTML escaping, control char stripping, URL validation
- WebSocket rate limiting: 50 msg/10s per-connection sliding window
- CI/CD: GitHub Actions (backend CI, mobile CI, security scanning, deployment)
- 251 tests (201 backend + 23 mobile + 27 shared)
- Security scanning: Trivy vulnerability scanning, Gitleaks secret detection, CodeQL static analysis
- Redis Pub/Sub for horizontal WebSocket scaling

## Module Status

| Module | Status | Key Endpoints |
|--------|--------|---------------|
| Auth | Done | `POST /api/v1/auth/otp/request`, `otp/verify`, `token/refresh`, `logout` |
| Users | Done | `GET /api/v1/users/me`, `PATCH /api/v1/users/me` |
| Messaging | Done | `POST /api/v1/conversations`, `GET /api/v1/conversations`, `GET /api/v1/conversations/{id}/messages`, `GET /api/v1/conversations/{id}/media`, `GET /api/v1/messages/{id}/info` |
| Groups | Done | `POST /api/v1/groups`, member management, role assignment |
| WebSocket | Done | `wss://host/ws?token={jwt}` — Send, Ack, Typing, Presence, Call signaling |
| Contacts | Done | `POST /api/v1/contacts/sync` — phone hash matching |
| Media | Done | `POST /api/v1/media/upload`, `GET /api/v1/media/{id}/url`, `GET /api/v1/media/storage` — MinIO + thumbnails + storage stats |
| Presence | Done | Redis TTL-based online tracking, typing indicators, last seen |
| Notification | Done | FCM push with grouping, `PUT /api/v1/devices/push-token` |
| Status | Done | `POST /api/v1/statuses`, `GET /api/v1/statuses` — 24h ephemeral stories |
| Channels | Done | `POST /api/v1/channels`, subscribe/unsubscribe, broadcast |
| Polls | Done | `POST /api/v1/polls`, `POST /api/v1/polls/{id}/vote` |
| Encryption | Done | `POST /api/v1/encryption/keys`, `GET /api/v1/encryption/keys/{userId}` |
| Call History | Done | `GET /api/v1/calls/history` |
| User Data | Done | `GET /api/v1/users/data/export`, `DELETE /api/v1/users/data/account` (KVKK) |
| Moderation | Done | `POST /api/v1/moderation/reports`, `POST /api/v1/moderation/blocks` |
| Backups | Done | `POST /api/v1/backups`, `GET /api/v1/backups`, `GET /api/v1/backups/{id}` |
| Bots | Done | `POST /api/v1/bots`, `PATCH /api/v1/bots/{id}`, token regeneration |
| Analytics | Done | `GET /api/v1/channels/{id}/analytics` — daily stats, subscriber tracking |
| Mobile | Done | CMP Android app — all features above + iOS foundation |

## Quick Start

### Prerequisites
- JDK 25+
- Docker & Docker Compose
- Android SDK (API 35) for mobile development
- Kotlin 2.3.10+ (managed by Gradle wrapper)

### 1. Start Infrastructure
```bash
cd infra && docker compose up -d
```
This starts PostgreSQL (5432), Redis (6379), and MinIO (9000/9001).

### 2. Run Backend
```bash
# Linux/macOS
OTP_MOCK_ENABLED=true ./gradlew :backend:bootRun

# Windows (PowerShell)
$env:OTP_MOCK_ENABLED="true"; ./gradlew :backend:bootRun
```
Backend starts at `http://localhost:8080`. Flyway auto-creates database schema.

### 3. Verify
```bash
# Health check
curl http://localhost:8080/actuator/health

# Request OTP (check console log for code)
curl -X POST http://localhost:8080/api/v1/auth/otp/request \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+905321234567"}'

# Verify OTP
curl -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+905321234567", "otp": "CODE_FROM_LOG", "deviceName": "Test", "platform": "android"}'

# Get profile (use accessToken from verify response)
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

### 4. Run Tests
```bash
./gradlew :backend:test
```

## Architecture

Each backend module follows **Hexagonal Architecture (Ports & Adapters)**:

```
module/
├── domain/          → Pure business logic (no framework dependencies)
│   ├── model/       → Aggregate roots, entities, value objects
│   ├── port/in/     → Use case interfaces
│   ├── port/out/    → Repository interfaces
│   └── service/     → Business logic implementation
└── adapter/         → Framework-specific code
    ├── in/web/      → REST controllers
    ├── in/websocket/→ WebSocket handlers
    └── out/         → JPA repositories, external services
```

### Key Principles
- **Domain is Spring-free** — no framework annotations in domain layer
- **Controllers depend on use case interfaces (in-ports)** — never on services directly
- **Services depend on repository interfaces (out-ports)** — never on JPA implementations
- **Services wired via `AppConfig` `@Bean`** — not `@Service` annotation (keeps domain clean)
- **Domain models ≠ JPA entities** — always mapped between layers

## Documentation

- [API Contract](docs/api-contract.md) — REST + WebSocket specification
- [Roadmap](ROADMAP.md) — Product roadmap & phase tracking
- [Changelog](CHANGELOG.md) — Release history
- [Decisions](docs/decisions.md) — Technical decisions
- [Privacy Policy](docs/privacy-policy.md) — KVKK compliant
- [QA Engineering](docs/qa/) — Quality assurance plans and test strategies

## License

Private — All rights reserved.
