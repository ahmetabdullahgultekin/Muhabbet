# Muhabbet — Final Decision Summary
### All Architectural & Technical Decisions

---

## Project Identity

| Item | Decision |
|------|----------|
| **Project Name** | Muhabbet (cultural, warm — "conversation/chat" in Turkish) |
| **Domain (MVP)** | muhabbet.rollingcatsoftware.com (subdomain) |
| **Domain (Launch)** | TBD — muhabbet.app / muhabbet.com.tr / other (decide before launch) |

---

## Architecture Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Architecture Pattern | **Modular Monolith** | Solo engineer — 3-5x faster than microservices. Hexagonal boundaries per module enable future extraction. YAGNI principle. |
| 2 | Backend Language | **Kotlin** | Kotlin everywhere strategy. Null safety, coroutines, 40% less boilerplate than Java. Shared domain models with mobile. |
| 3 | Backend Framework | **Spring Boot 3** | Mature ecosystem, Spring Security, Spring WebSocket, Spring Data JPA. Virtual threads + coroutines support. |
| 4 | Mobile Framework | **KMP + CMP** (Kotlin Multiplatform + Compose Multiplatform) | Single language (Kotlin) across backend + mobile. Shared domain models, validation, networking. JetBrains-backed. |
| 5 | Shared Module | **KMP shared module** | Domain models, enums, validation rules, WebSocket protocol, DTOs — written once, used on backend AND mobile. |
| 6 | Database | **PostgreSQL for everything** | One DB, zero operational overhead. Handles 50-100M messages. Repository pattern isolates future migration to ScyllaDB. |
| 7 | Cache / Presence | **Redis** | Presence tracking, session cache, rate limiting. Single instance for MVP. |
| 8 | Real-Time Protocol | **Raw WebSocket + custom Kotlin protocol** | Full control, shared `WsMessage` sealed class in KMP module. No library overhead. Spring WebSocket + Ktor client. |
| 9 | Media Storage (MVP) | **MinIO in Docker Compose** | S3-compatible API. Portable — same adapter code works with Turkish providers later. |
| 10 | Media Storage (Launch) | **TBD — Turkish S3-compatible provider** | Research needed: TTBulut, Huawei Cloud Turkey, others. Decision deferred. |
| 11 | Media Retention | **30 days server-side** | WhatsApp standard. Caps storage costs. Clients cache locally. |
| 12 | E2E Encryption | **TLS-only MVP → Signal Protocol Phase 2** | Ship fast, add crypto properly later. EncryptionPort interface ready for swap. |
| 13 | SMS Gateway | **Netgsm** | Cheapest Turkish SMS provider. OTP-optimized routes. All 3 operators supported. |
| 14 | Push Notifications | **FCM** (Firebase Cloud Messaging) | Free, handles both Android and iOS via APNs bridge. |
| 15 | Repository Structure | **Monorepo** | Shared KMP module is a Gradle subproject. Atomic changes. One IDE, one build. |
| 16 | Build System | **Gradle (Kotlin DSL)** | Required by KMP. Type-safe build scripts. Single build for all subprojects. |
| 17 | Hosting (MVP) | **GCP free trial** (2 months) | Zero cost development. Docker Compose — fully portable. |
| 18 | Hosting (Launch) | **Turkish provider** | TBD — KVKK compliant. Docker Compose migrates in one afternoon. |
| 19 | CI/CD | **GitHub Actions** | Free for private repos. Path-based triggers for monorepo. |
| 20 | IDE | **IntelliJ IDEA Ultimate** | Spring Boot + KMP + CMP + Gradle + Docker in one window. |
| 21 | Monitoring | **Structured Logging + Actuator + Sentry** | SLF4J/Logback JSON logs. Spring Actuator endpoints. Sentry free tier for crash alerting. |
| 22 | Mobile Architecture | **Decompose + Simple ViewModels** | Decompose for navigation/lifecycle. MVI-style for chat screen. Simple MVVM elsewhere. |
| 23 | Mobile DI | **Koin** | KMP-native. Simple DSL. De-facto standard for Kotlin Multiplatform. |
| 24 | Mobile Local DB | **SQLDelight** | KMP-native. SQL-first. Compile-time validation. Lives in shared module. |

---

## Tech Stack Summary

```
┌─────────────────────────────────────────────────────────────┐
│                    MUHABBET TECH STACK                        │
│                                                             │
│  LANGUAGE:     Kotlin (everywhere)                          │
│                                                             │
│  BACKEND:      Spring Boot 3 + Kotlin                       │
│                Spring WebSocket (raw)                       │
│                Spring Security (JWT)                         │
│                Spring Data JPA + Flyway                      │
│                                                             │
│  MOBILE:       KMP + CMP (Compose Multiplatform)            │
│                Decompose (navigation/lifecycle)              │
│                Koin (DI)                                     │
│                Ktor Client (HTTP + WebSocket)                │
│                SQLDelight (local DB)                         │
│                                                             │
│  SHARED:       KMP shared module                            │
│  (backend +    ├── Domain models (Message, Conversation)    │
│   mobile)      ├── WsMessage protocol (sealed class)        │
│                ├── Validation rules                          │
│                ├── DTOs + serialization                      │
│                └── EncryptionPort (future Signal Protocol)   │
│                                                             │
│  DATA:         PostgreSQL 16 (all data)                     │
│                Redis 7 (cache, presence, sessions)           │
│                MinIO (media storage, S3-compatible)          │
│                SQLDelight/SQLite (mobile local)              │
│                                                             │
│  INFRA:        Docker Compose                               │
│                GCP VM (MVP) → Turkish provider (launch)      │
│                GitHub Actions (CI/CD)                        │
│                Sentry (error tracking)                       │
│                                                             │
│  EXTERNAL:     Netgsm (SMS/OTP)                             │
│                FCM (push notifications)                      │
│                Let's Encrypt (SSL)                           │
└─────────────────────────────────────────────────────────────┘
```

---

## Monorepo Structure

```
muhabbet/
├── backend/                          ← Spring Boot 3 + Kotlin
│   ├── src/main/kotlin/com/muhabbet/
│   │   ├── MuhabbetApplication.kt
│   │   ├── shared/                   ← Cross-cutting: config, security, exceptions
│   │   ├── auth/                     ← Module: OTP, JWT, device management
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   ├── port/in/          ← Use case interfaces
│   │   │   │   ├── port/out/         ← Repository interfaces
│   │   │   │   └── service/          ← Business logic
│   │   │   └── adapter/
│   │   │       ├── in/web/           ← Controllers
│   │   │       └── out/              ← JPA repos, Netgsm adapter
│   │   ├── messaging/                ← Module: send, receive, delivery status
│   │   ├── media/                    ← Module: upload, download, thumbnails
│   │   ├── presence/                 ← Module: online/offline, typing
│   │   ├── notification/             ← Module: FCM push
│   │   └── user/                     ← Module: profile, contacts
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/            ← Flyway SQL migrations
│   ├── src/test/
│   ├── Dockerfile
│   └── build.gradle.kts
│
├── shared/                           ← KMP Shared Module
│   ├── src/commonMain/kotlin/
│   │   ├── model/                    ← Message, Conversation, User (domain)
│   │   ├── protocol/                 ← WsMessage sealed class
│   │   ├── validation/               ← Input validation rules
│   │   ├── dto/                      ← API request/response DTOs
│   │   └── port/                     ← EncryptionPort interface
│   ├── src/androidMain/
│   ├── src/iosMain/
│   └── build.gradle.kts              ← KMP configuration
│
├── mobile/                           ← CMP App
│   ├── composeApp/
│   │   ├── src/commonMain/kotlin/
│   │   │   ├── App.kt
│   │   │   ├── di/                   ← Koin modules
│   │   │   ├── navigation/           ← Decompose root component
│   │   │   ├── feature/
│   │   │   │   ├── auth/
│   │   │   │   │   ├── AuthComponent.kt (Decompose)
│   │   │   │   │   ├── AuthViewModel.kt
│   │   │   │   │   └── ui/           ← Compose screens
│   │   │   │   ├── chat/
│   │   │   │   │   ├── ChatComponent.kt
│   │   │   │   │   ├── ChatViewModel.kt (MVI-style)
│   │   │   │   │   └── ui/
│   │   │   │   ├── contacts/
│   │   │   │   └── profile/
│   │   │   └── core/
│   │   │       ├── network/          ← Ktor HTTP + WebSocket client
│   │   │       ├── storage/          ← SQLDelight setup
│   │   │       └── theme/            ← Material3 theming
│   │   ├── src/androidMain/
│   │   └── src/iosMain/
│   ├── build.gradle.kts
│   └── iosApp/                       ← Xcode project wrapper
│
├── infra/
│   ├── docker-compose.yml            ← Local dev: PG + Redis + MinIO
│   ├── docker-compose.prod.yml       ← Production: + nginx + certbot
│   ├── nginx/conf.d/
│   └── scripts/
│
├── docs/
│   ├── system-design.md              ← North star architecture
│   ├── solo-mvp-plan.md              ← This plan
│   ├── decisions.md                  ← This file
│   └── adr/                          ← Architecture Decision Records
│
├── .github/
│   └── workflows/
│       ├── backend-ci.yml            ← Build + test backend (on backend/ or shared/ change)
│       ├── mobile-ci.yml             ← Build mobile (on mobile/ or shared/ change)
│       └── deploy.yml                ← Deploy to GCP/production
│
├── build.gradle.kts                  ← Root Gradle build
├── settings.gradle.kts               ← Includes: backend, shared, mobile
├── gradle.properties
├── .editorconfig
├── .gitignore
├── CLAUDE.md                         ← Claude Code project context
└── README.md
```

---

## Architecture Patterns Applied

| Pattern | Where | Why |
|---------|-------|-----|
| **Hexagonal Architecture** | Each backend module | Decouple domain from infra. Swap adapters (DB, SMS, storage) without touching business logic. |
| **Modular Monolith** | Overall backend structure | Solo dev speed. Module boundaries = future microservice boundaries. |
| **Repository Pattern** | All data access | Abstract persistence. Test with in-memory. Swap PostgreSQL → ScyllaDB later. |
| **Strategy Pattern** | Message routing, notification delivery | 1:1 vs group routing is interchangeable. FCM vs APNs via same interface. |
| **Observer Pattern** | Spring ApplicationEvent | Loose coupling between modules. Messaging publishes event → Notification reacts. |
| **Port/Adapter Pattern** | All external integrations | OtpSender, FileStorage, PushProvider — swap implementations freely. |
| **Sealed Class Protocol** | WebSocket messages | Type-safe, exhaustive when() handling. Shared between backend and mobile. |
| **Offline-First** | Mobile client | SQLDelight local DB. Queue messages when offline. Sync on reconnect. |

---

## SOLID Compliance

| Principle | Implementation |
|-----------|---------------|
| **SRP** | Each module owns one bounded context. Each class has one reason to change. Controllers don't contain business logic. |
| **OCP** | New message types = new sealed class variant. New SMS provider = new adapter. Zero modification to existing code. |
| **LSP** | All repository implementations are interchangeable. PostgresMessageRepo ↔ InMemoryMessageRepo for tests. |
| **ISP** | Fine-grained port interfaces: `SendMessageUseCase` ≠ `GetHistoryUseCase`. Clients depend only on what they use. |
| **DIP** | Domain core depends on port interfaces. Never on concrete adapters. Spring IoC wires implementations at runtime. |

---

## What's Next: Day 1 Checklist

```
□ Create GitHub repo: github.com/[org]/muhabbet (private)
□ Initialize Gradle project with settings.gradle.kts (3 subprojects)
□ Setup shared/ KMP module with basic domain models
□ Setup backend/ Spring Boot project with empty module packages
□ Setup mobile/ CMP project with Decompose skeleton
□ Create docker-compose.yml (PostgreSQL + Redis + MinIO)
□ First Flyway migration: V1__create_users_and_devices.sql
□ Health endpoint: GET /actuator/health → 200
□ Push to GitHub, CI pipeline green
□ Open Claude Code CLI: claude
□ Start building auth module
```

---

*This document is the single source of truth for all Muhabbet technical decisions.*
*Last updated: 2026-02-11*
