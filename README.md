# Muhabbet

**A messaging app for Turkey, built so that what it tells you is true.**

[![Backend CI](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/backend-ci.yml)
[![Mobile CI](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/mobile-ci.yml/badge.svg)](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/mobile-ci.yml)
[![Security & Quality](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/security.yml/badge.svg)](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/security.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

> 🇹🇷 **Türkçe:** [`README.tr.md`](README.tr.md)

---

## Why this exists

Turkey has around 85 million people and, in practice, one messaging app. It is owned outside the
country, governed by someone else's privacy law, and nobody here can change how it treats their
data. Muhabbet is the domestic alternative to that: the data sits on infrastructure its operator
controls, KVKK is a design input rather than a legal appendix bolted on at the end, and Turkish is
the default language rather than a translation layer.

Being an alternative is only worth something if the claims survive contact with the code. So the
project has one rule that outranks the feature list:

> **The app must not tell the user something that is not true.**

A padlock means encryption. A tick means delivered. A switch means the setting is applied
server-side. Where the code cannot yet honour a claim, the claim is **removed** — not restyled, not
greyed out with a "coming soon". This is not a slogan; it is enforced by the release process. The
version number obeys it too: **1.0.0 is reserved for the first release that ships end-to-end
encryption switched on**, and no amount of other progress buys it early.

It is also why the section below is unflattering. A README that lists everything the repository
contains as though it worked would break the one rule the project has.

## Where it actually is

Version **0.3.10**, distributed through the Play internal track and
[GitHub releases](https://github.com/ahmetabdullahgultekin/Muhabbet/releases). Solo engineer, fast
iteration, real users in the single digits.

| Area | State |
|---|---|
| Auth — phone OTP, JWT, device management | Working in production |
| 1:1 and group messaging over WebSocket | Working in production |
| Media — images, documents, voice, thumbnails | Working in production |
| Presence, typing, delivery ticks, push | Working in production |
| Chat features — reply, forward, edit, react, star, search, polls, status | Working in production |
| **End-to-end encryption** | **Off, and off twice over.** Messages are TLS-encrypted in transit and readable by the server. The flags are `false`, *and* a no-op cipher is what dependency injection binds on both platforms, so flipping them would encrypt nothing. The Signal implementation sits in `.disabled` files and libsignal is not even a dependency. This is the 1.0.0 gate. |
| **Voice / video calls** | **Not working.** Signalling types, a service and a LiveKit adapter exist; the client never initiates a call and LiveKit is unconfigured in production. |
| **Communities** | Partial. They can be created and read, and each now has an announcement channel; there is still no invite a recipient can accept. |
| **iOS** | Partial. The Compose Multiplatform target builds, several platform modules are stubs, and it has never been through TestFlight. |

Known defects are listed openly in each [`CHANGELOG.md`](CHANGELOG.md) release section, including in
the release that shipped them. The inventory is the
[issue tracker](https://github.com/ahmetabdullahgultekin/Muhabbet/issues).

> A red CI badge above is usually the environment, not the branch — the runner is the production
> host and has been rate-limited out of downloading its own actions ([#419](https://github.com/ahmetabdullahgultekin/Muhabbet/issues/419)).
> Compare against `main` before blaming a change.

## How it is built

Kotlin everywhere — one language across the server, the shared contracts and both mobile platforms.

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.1, Kotlin 2.4, PostgreSQL 16, Redis 7, MinIO |
| Shared | Kotlin Multiplatform + `kotlinx.serialization` |
| Mobile | Compose Multiplatform, Ktor, Koin, Decompose, SQLDelight |
| Infra | Docker Compose, Traefik, GitHub Actions on a self-hosted runner |

```text
muhabbet/
├── backend/   # Spring Boot modular monolith, hexagonal per module
├── shared/    # KMP module: domain models, WS protocol, DTOs, validation
├── mobile/    # Compose Multiplatform client (composeApp + designsystem)
├── infra/     # Docker Compose, scripts, load tests
└── docs/      # Architecture, API contract, ADRs, QA, legal
```

The backend is a **modular monolith** with Ports & Adapters inside each module, deliberately chosen
over microservices for a single engineer. The `shared/` module is compiled into both the server and
the app, so the wire protocol cannot drift between them.

**[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) is the explanation** — module boundaries, how the
shared module feeds both sides, the end-to-end path of a message, and what has deliberately *not*
been built.

## Getting it running

**Prerequisites:** JDK 21, Docker + Docker Compose, and the Android SDK if you want to build the app.

```bash
# 1) Local infrastructure — PostgreSQL, Redis, MinIO
cd infra && docker compose up -d

# 2) Backend, with OTP codes logged to the console instead of sent by SMS
OTP_MOCK_ENABLED=true ./gradlew :backend:bootRun     # Windows: $env:OTP_MOCK_ENABLED="true"

# 3) Health check
curl http://localhost:8080/actuator/health

# 4) Gates
./gradlew :backend:test :shared:jvmTest
./gradlew :mobile:composeApp:compileCommonMainKotlinMetadata   # cheap mobile compile check
```

Two things that will catch you out. `:backend:test` needs **Docker and Redis running** — without
them ten Testcontainers classes fail at start-up and are silently not executed, so a green-looking
run proves less than it appears to. And the mobile app's base URL is **hardcoded to production**;
pointing it at a local backend needs a temporary edit to `ApiClient.BASE_URL`. Both, and every other
sharp edge, are written down in [`CLAUDE.md`](CLAUDE.md).

## Documentation

| Document | What it answers |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | What shape is this system, and why |
| [`docs/api-contract.md`](docs/api-contract.md) | REST endpoints and the WebSocket protocol |
| [`ROADMAP.md`](ROADMAP.md) | What ships in which version, and how that is decided |
| [`CHANGELOG.md`](CHANGELOG.md) | What changed, and what is still broken |
| [`docs/adr/`](docs/adr/) · [`docs/decisions.md`](docs/decisions.md) | Why things were done one way and not another |
| [`docs/design/muhabbet-design-system.md`](docs/design/muhabbet-design-system.md) | The visual language and its guardrails |
| [`docs/legal/`](docs/legal/) | KVKK documents — privacy policy, consent, terms (Turkish) |
| [`docs/qa/`](docs/qa/) | ISO/IEC 25010 quality documentation |
| [`CLAUDE.md`](CLAUDE.md) | Working instructions for agents — long, and not a system description |

## Contributing, security, licence

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — setup, standards, branching
- [`SECURITY.md`](SECURITY.md) — report vulnerabilities to security@rollingcatsoftware.com, not via
  a public issue
- MIT — see [`LICENSE`](LICENSE)

---

## More from Ahmet Abdullah Gültekin

Personal portfolio + writing: **[ahmetabdullah.gultek.in](https://ahmetabdullah.gultek.in)**
LinkedIn: **[ahmet-abdullah-gultekin](https://www.linkedin.com/in/ahmet-abdullah-gultekin)**
