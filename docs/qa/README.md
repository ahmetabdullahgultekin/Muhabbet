# Muhabbet — Quality Assurance Engineering

> **Last Updated**: February 14, 2026
> **Project Phase**: Post-MVP, Pre-Beta
> **QA Owner**: Engineering Team

---

## Overview

This directory contains the comprehensive Quality Assurance Engineering documentation for Muhabbet. Each document covers a distinct quality attribute as defined by ISO/IEC 25010 (Software Product Quality Model), adapted to Muhabbet's architecture and Turkish market requirements.

## Document Index

| Document | Quality Attribute | Scope |
|----------|------------------|-------|
| [01-performance-efficiency.md](01-performance-efficiency.md) | Performance & Efficiency | Response times, throughput, resource usage, scalability targets |
| [02-security.md](02-security.md) | Security | Authentication, authorization, data protection, KVKK, OWASP, pen testing |
| [03-robustness-reliability.md](03-robustness-reliability.md) | Robustness & Reliability | Fault tolerance, recovery, data integrity, graceful degradation |
| [04-interoperability-compatibility.md](04-interoperability-compatibility.md) | Interoperability & Compatibility | Platform support, API compatibility, protocol compliance, migration |
| [05-maintenance-code-quality.md](05-maintenance-code-quality.md) | Maintainability & Code Quality | Architecture compliance, test coverage, tech debt, code standards |
| [06-usability-responsiveness.md](06-usability-responsiveness.md) | Usability & Responsiveness | UI/UX quality, accessibility, localization, responsiveness |
| [07-safety-compliance.md](07-safety-compliance.md) | Safety & Compliance | KVKK, BTK regulations, data retention, legal obligations |
| [08-test-strategy.md](08-test-strategy.md) | Test Strategy | Test pyramid, environments, CI/CD integration, test data management |

## Quality Metrics Dashboard

### Current State (Feb 2026)

| Metric | Current | Target (Beta) | Target (GA) |
|--------|---------|---------------|-------------|
| Backend tests | 201 + ~50 new | 350+ | 500+ |
| Mobile/shared tests | 50 (23 + 27) | 100+ | 200+ |
| Test coverage (backend) | ~45% est. (JaCoCo enabled) | 60% | 80% |
| Test coverage (mobile) | ~10% est. | 40% | 60% |
| P95 API latency | Unmeasured | <200ms | <100ms |
| WebSocket message latency | Unmeasured | <50ms | <30ms |
| Crash-free rate (mobile) | Unmeasured | 99.5% | 99.9% |
| Security scan findings | 0 critical | 0 critical | 0 high+ |
| Accessibility (WCAG) | Not audited | AA partial | AA full |
| Uptime SLA | N/A | 99.5% | 99.9% |

### Test Pyramid

```
         ┌─────────┐
         │  E2E /  │  < 10% — Smoke tests (critical user flows)
         │  Manual │
        ┌┴─────────┴┐
        │Integration │  ~30% — API tests, DB tests (Testcontainers)
       ┌┴────────────┴┐
       │  Unit Tests   │  ~60% — Domain services, validators, mappers
       └──────────────┘
```

### Architecture Under Test

```
┌─────────────────────────────────────────────────┐
│                   Mobile App                     │
│  (Compose Multiplatform — Android + iOS)         │
│  Tests: FakeTokenStorage, AuthRepo, PhoneNorm,   │
│         WsMessageSerialization                   │
└──────────────┬──────────────────┬───────────────┘
               │ REST (HTTPS)     │ WebSocket (WSS)
┌──────────────▼──────────────────▼───────────────┐
│              Backend (Spring Boot 4.0.2)         │
│                                                  │
│  ┌──────────┐ ┌───────────┐ ┌────────────┐      │
│  │   Auth   │ │ Messaging │ │ Media      │      │
│  │ 13 tests │ │ 117 tests │ │ 21 tests   │      │
│  └────┬─────┘ └─────┬─────┘ └─────┬──────┘      │
│       │              │             │              │
│  ┌────┴──────────────┤  ┌──────────┴───────────┐ │
│  │ Moderation: 8     │  │ Shared (cross-cut)   │ │
│  └───────────────────┘  │ InputSanitizer: 15   │ │
│                         │ RateLimitFilter: 16   │ │
│                         │ WsRateLimiter: 4      │ │
│                         └──────────────────────┘ │
└──────────┬──────────────┬───────────┬───────────┘
           │              │           │
    ┌──────▼──────┐ ┌─────▼─────┐ ┌──▼────┐
    │ PostgreSQL  │ │   Redis   │ │ MinIO │
    │    16       │ │     7     │ │       │
    └─────────────┘ └───────────┘ └───────┘
```

## Priority Matrix

### P0 — Must Fix Before Beta
1. Increase backend test coverage to 60% — **JaCoCo added** (`./gradlew :backend:jacocoTestReport`)
2. Add integration tests for all REST controllers — **Started** (MessageController, ModerationController, UserDataController tests added)
3. Run OWASP ZAP baseline scan
4. Measure and establish P95 latency baselines — **k6 scripts created** (`infra/k6/`)
5. Load test WebSocket at 1K concurrent connections — **k6 WS script created** (`infra/k6/websocket-load-test.js`)

### P1 — Must Fix Before GA
1. Mobile test coverage to 40%
2. Full OWASP penetration test
3. WCAG AA accessibility audit
4. Chaos engineering (Redis/PG failover)
5. E2E test suite for critical flows
6. Performance regression detection in CI

### P2 — Continuous Improvement
1. Automated visual regression testing
2. API contract testing (consumer-driven)
3. Fuzz testing for input validation
4. Memory leak detection (mobile)
5. Battery/network usage profiling

## How to Run Tests

```bash
# Backend unit tests
./gradlew :backend:test

# Backend with coverage report (JaCoCo)
./gradlew :backend:test :backend:jacocoTestReport
# Report: backend/build/reports/jacoco/test/html/index.html

# Coverage verification (fails if below threshold)
./gradlew :backend:jacocoTestCoverageVerification

# Static analysis (detekt)
./gradlew :backend:detekt
# Report: backend/build/reports/detekt/detekt.html

# Specific test class
./gradlew :backend:test --tests "com.muhabbet.auth.domain.service.AuthServiceTest"

# Architecture compliance tests only
./gradlew :backend:test --tests "com.muhabbet.architecture.*"

# Mobile/shared tests
./gradlew :shared:allTests
./gradlew :mobile:composeApp:testDebugUnitTest

# k6 performance tests
k6 run infra/k6/auth-load-test.js
k6 run --env TOKEN=eyJ... infra/k6/api-load-test.js
k6 run --env TOKEN=eyJ... infra/k6/websocket-load-test.js
```

## CI/CD Integration

Tests run automatically via GitHub Actions:
- **backend-ci.yml**: On push to `backend/` or `shared/` — runs `./gradlew :backend:test`
- **mobile-ci.yml**: On push to `mobile/` or `shared/` — Android debug build + iOS framework
- **security.yml**: Weekly + on push — Trivy, Gitleaks, CodeQL

## Conventions

- Test naming: `should [expected behavior] when [condition]`
- Test framework: JUnit 5 + MockK (backend), kotlin-test + coroutines-test (mobile/shared)
- Integration tests: Testcontainers (PostgreSQL, Redis)
- Test data: `TestData` factory object (`com.muhabbet.shared.TestData`) — not inline builders
- Architecture: ArchUnit tests enforce hexagonal boundaries (domain independence, no Spring in domain, module isolation)
- Coverage: JaCoCo with 30% min project-wide, 60% min for domain services
- Static analysis: detekt with project-specific rules (`backend/detekt.yml`)
- Flaky test policy: Quarantine → fix within 48h → delete if unfixable

## Tooling

| Tool | Purpose | How to Run | Status |
|------|---------|-----------|--------|
| **JaCoCo** | Code coverage reports | `./gradlew :backend:jacocoTestReport` | Implemented |
| **detekt** | Kotlin static analysis | `./gradlew :backend:detekt` | Implemented |
| **ArchUnit** | Architecture compliance | `./gradlew :backend:test --tests "com.muhabbet.architecture.*"` | Implemented |
| **k6** | Load/performance testing | `k6 run infra/k6/*.js` | Scripts created |
| **TestData** | Shared test data factory | Imported via `com.muhabbet.shared.TestData` | Implemented |
| **Testcontainers** | Integration test DB | Automatic in `@Testcontainers` tests | Existing |

## Test Inventory (~290 total)

### Backend (~240 tests across 18 files)

| Test File | Tests | Module |
|-----------|-------|--------|
| AuthControllerIntegrationTest | 4 | auth (integration) |
| AuthServiceTest | 9 | auth |
| MediaServiceTest | 21 | media |
| ChatWebSocketHandlerTest | 19 | messaging (WebSocket) |
| CallSignalingServiceTest | 7 | messaging (calls) |
| ConversationServiceTest | 28 | messaging |
| DeliveryStatusTest | 6 | messaging |
| EncryptionServiceTest | 7 | messaging (encryption) |
| GroupServiceTest | 41 | messaging (groups) |
| MessagingServiceTest | 16 | messaging |
| ModerationServiceTest | 8 | moderation |
| InputSanitizerTest | 15 | shared/security |
| RateLimitFilterTest | 16 | shared/security |
| WebSocketRateLimiterTest | 4 | shared/security |
| **HexagonalArchitectureTest** | **13** | **architecture** |
| **MessageControllerTest** | **10** | **messaging (controller)** |
| **ModerationControllerTest** | **13** | **moderation (controller)** |
| **UserDataControllerTest** | **5** | **auth (controller)** |

### Mobile (23 tests across 3 files)

| Test File | Tests |
|-----------|-------|
| FakeTokenStorageTest | 5 |
| AuthRepositoryTest | 5 |
| PhoneNormalizationTest | 13 |

### Shared Module (27 tests, 1 file)

| Test File | Tests |
|-----------|-------|
| WsMessageSerializationTest | 27 |
