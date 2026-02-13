# Muhabbet

**Turkey's domestic messaging platform.** Privacy-first, KVKK-compliant, built with Kotlin everywhere.

> *Muhabbet* — Turkish for "conversation, chat" — from Arabic *mahabbah* meaning "love."

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Spring Boot 3.4 + Kotlin 2.1, PostgreSQL 16, Redis 7, MinIO |
| **Mobile** | Compose Multiplatform (Android + iOS) |
| **Shared** | Kotlin Multiplatform (domain models, protocol, validation) |
| **Infra** | Docker Compose, Nginx, GitHub Actions |
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
- Starred messages, message search (full-text)
- Typing indicators, read receipts, delivery status

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
- Crash reporting: Sentry SDK (Android; iOS stubbed)
- KVKK compliance: data export, account deletion

### Architecture (Post-MVP)
- Call signaling infrastructure (WebSocket-based, ready for WebRTC/LiveKit)
- E2E encryption key exchange endpoints (ready for Signal Protocol client)
- ~125 backend unit tests (MediaService, ConversationService, GroupService, WebSocket, RateLimit)

## Module Status

| Module | Status | Key Endpoints |
|--------|--------|---------------|
| Auth | Done | `POST /api/v1/auth/otp/request`, `otp/verify`, `token/refresh`, `logout` |
| Users | Done | `GET /api/v1/users/me`, `PATCH /api/v1/users/me` |
| Messaging | Done | `POST /api/v1/conversations`, `GET /api/v1/conversations`, `GET /api/v1/conversations/{id}/messages` |
| Groups | Done | `POST /api/v1/groups`, member management, role assignment |
| WebSocket | Done | `wss://host/ws?token={jwt}` — Send, Ack, Typing, Presence, Call signaling |
| Contacts | Done | `POST /api/v1/contacts/sync` — phone hash matching |
| Media | Done | `POST /api/v1/media/upload`, `GET /api/v1/media/{id}/url` — MinIO + thumbnails |
| Presence | Done | Redis TTL-based online tracking, typing indicators, last seen |
| Notification | Done | FCM push with grouping, `PUT /api/v1/devices/push-token` |
| Status | Done | `POST /api/v1/statuses`, `GET /api/v1/statuses` — 24h ephemeral stories |
| Channels | Done | `POST /api/v1/channels`, subscribe/unsubscribe, broadcast |
| Polls | Done | `POST /api/v1/polls`, `POST /api/v1/polls/{id}/vote` |
| Encryption | Done | `POST /api/v1/encryption/keys`, `GET /api/v1/encryption/keys/{userId}` |
| Call History | Done | `GET /api/v1/calls/history` |
| User Data | Done | `GET /api/v1/users/data/export`, `DELETE /api/v1/users/data/account` (KVKK) |
| Mobile | Done | CMP Android app — all features above + iOS foundation |

## Quick Start

### Prerequisites
- JDK 21+
- Docker & Docker Compose
- Android SDK (for mobile development)

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

## License

Private — All rights reserved.
