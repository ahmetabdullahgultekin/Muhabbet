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

## Project Structure

```
muhabbet/
├── backend/     → Spring Boot modular monolith (hexagonal architecture)
├── shared/      → KMP shared module (domain models, WS protocol, DTOs)
├── mobile/      → Compose Multiplatform app
├── infra/       → Docker Compose, Nginx, deployment scripts
├── docs/        → Architecture docs, ADRs, API contract
└── CLAUDE.md    → Project context for Claude Code CLI
```

## Module Status

| Module | Status | Endpoints |
|--------|--------|-----------|
| Auth | Done | `POST /api/v1/auth/otp/request`, `otp/verify`, `token/refresh`, `logout` |
| Users | Done | `GET /api/v1/users/me`, `PATCH /api/v1/users/me` |
| Messaging | In Progress | Conversations CRUD, message history |
| WebSocket | In Progress | Real-time messaging |
| Media | Planned | Upload/download via MinIO |
| Presence | Planned | Online/typing indicators |
| Notification | Planned | Push via FCM |

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
    └── out/         → JPA repositories, external services
```

## Documentation

- [API Contract](docs/api-contract.md) — REST + WebSocket specification
- [Sprint Plan](docs/solo-mvp-sprint-plan.md) — Week-by-week development plan
- [Decisions](docs/decisions.md) — Technical decisions
- [Changelog](CHANGELOG.md) — Release history

## License

Private — All rights reserved.
