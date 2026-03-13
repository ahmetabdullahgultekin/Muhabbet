# Muhabbet (English)

Turkey-focused messaging platform. Privacy-first, KVKK-compliant, and implemented with Kotlin end-to-end.

## Highlights

- Real-time 1:1 and group messaging over WebSocket
- Rich chat: reply/forward, edit/delete, reactions, starred messages, search
- Media stack: image/document/voice sharing, previews, compression pipeline
- Status/Stories, channels, polls, location sharing
- OTP auth + JWT + device management
- Offline-first mobile behavior with SQLDelight cache and queued sends
- Security hardening: sanitization, rate limiting, security headers, CI scanning

## Tech Stack

| Layer | Tech |
|---|---|
| Backend | Spring Boot 4, Kotlin 2.3.10, PostgreSQL 16, Redis 7, MinIO |
| Mobile | Compose Multiplatform, Ktor, Koin, Decompose, SQLDelight |
| Shared | Kotlin Multiplatform + `kotlinx.serialization` |
| Infra | Docker Compose, nginx, GitHub Actions |

## Project Layout

```text
muhabbet/
├── backend/
├── shared/
├── mobile/
├── infra/
└── docs/
```

## Quick Start

### Prerequisites

- JDK 21+
- Docker + Docker Compose
- Android SDK (for mobile builds)

### 1) Start infrastructure

```bash
cd infra && docker compose up -d
```

### 2) Run backend

```bash
# Linux/macOS
OTP_MOCK_ENABLED=true ./gradlew :backend:bootRun

# Windows (PowerShell)
$env:OTP_MOCK_ENABLED="true"; .\gradlew.bat :backend:bootRun
```

### 3) Health check

```bash
curl http://localhost:8080/actuator/health
```

### 4) Run tests

```bash
./gradlew :backend:test
```

## Architecture Notes

- Backend uses a modular monolith with Hexagonal Architecture.
- Domain layer is framework-agnostic.
- Controllers depend on use-case interfaces (in-ports).
- Services depend on repository interfaces (out-ports).
- Domain models are separated from persistence entities.

## Documentation

- API Contract: [`docs/api-contract.md`](docs/api-contract.md)
- Roadmap: [`ROADMAP.md`](ROADMAP.md)
- Changelog: [`CHANGELOG.md`](CHANGELOG.md)
- Privacy policy: [`docs/privacy-policy.md`](docs/privacy-policy.md)
- QA docs: [`docs/qa/`](docs/qa/)

## Contributing & Security

- Contributing guide: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Security policy: [`SECURITY.md`](SECURITY.md)

## License

Private repository — all rights reserved.
