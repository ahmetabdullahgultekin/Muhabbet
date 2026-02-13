# 05 — Maintainability & Code Quality

> Quality attribute: Ease of understanding, modifying, testing, and extending the codebase.

---

## 1. Architecture Compliance

### 1.1 Hexagonal Architecture Rules

| Rule | Description | Verification Method |
|------|-------------|-------------------|
| Domain is Spring-free | No `@Component`, `@Service`, `@Repository` in domain layer | Grep scan |
| Controllers use in-ports only | No service class imports in controllers | ArchUnit test |
| Services use out-ports only | No JPA entity imports in services | ArchUnit test |
| Domain model ≠ JPA entity | Separate classes with mappers | Code review |
| Cross-module via events | No direct imports across module boundaries | Grep scan |
| Services wired via AppConfig | `@Bean` definitions, not `@Service` | Code review |

### 1.2 Architecture Compliance Scan

```kotlin
// Proposed ArchUnit test
@AnalyzeClasses(packages = ["com.muhabbet"])
class ArchitectureTest {

    @ArchTest
    val domainShouldNotDependOnAdapters = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..adapter..")

    @ArchTest
    val domainShouldNotDependOnSpring = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework..")

    @ArchTest
    val controllersShouldNotDependOnRepositories = noClasses()
        .that().resideInAPackage("..adapter.in.web..")
        .should().dependOnClassesThat().resideInAPackage("..adapter.out.persistence..")

    @ArchTest
    val servicesShouldNotDependOnJpa = noClasses()
        .that().resideInAPackage("..domain.service..")
        .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
}
```

### 1.3 Module Dependency Map

```
auth ──────────────────┐
messaging ─────────────┤
media ─────────────────┤──▶ shared (cross-cutting)
presence ──────────────┤
notification ──────────┤
moderation ────────────┘

No direct dependencies between modules.
Communication: Spring ApplicationEvent (when needed)
```

---

## 2. Code Metrics

### 2.1 Codebase Size

| Component | Kotlin Files | Estimated Lines |
|-----------|-------------|-----------------|
| Backend (main) | 210 | ~15,000 |
| Backend (test) | 14 | ~2,500 |
| Mobile (main) | 98 | ~10,000 |
| Mobile (test) | 3 | ~300 |
| Shared (main) | 7 | ~1,500 |
| Shared (test) | 1 | ~400 |
| **Total** | **333** | **~29,700** |

### 2.2 Service Complexity

| Service | Lines | Use Cases | Dependencies | Complexity |
|---------|-------|-----------|-------------|-----------|
| AuthService | 280 | 4 | 8 | Medium |
| GroupService | 251 | 6 | 3 | Medium |
| MessageService | 250 | 5 | 3 | Medium |
| MediaService | 219 | 3 | 4 | Low |
| CallSignalingService | 157 | 3 | 1 | Low |
| ConversationService | 156 | 3 | 3 | Low |
| BotService | 100 | 7 | 2 | Low |
| ModerationService | 88 | 5 | 2 | Low |
| All others (11) | <80 each | 1-3 each | 1-2 each | Low |

**No God classes** — all services are under 300 lines (SRP compliant).

### 2.3 Cyclomatic Complexity Targets

| Threshold | Action |
|-----------|--------|
| Method >10 | Refactor — extract private methods or new classes |
| Class >20 (sum of methods) | Split into smaller classes |
| File >500 lines | Must refactor before merge |

---

## 3. Test Coverage

### 3.1 Current Coverage

| Module | Test Files | Actual Tests | Est. Coverage |
|--------|-----------|-------------|--------------|
| auth | 2 | 13 (9 unit + 4 integration) | ~40% |
| messaging | 6 | 117 (ConversationSvc:28, GroupSvc:41, MsgSvc:16, WsHandler:19, CallSig:7, DeliveryStatus:6) | ~40% |
| media | 1 | 21 | ~35% |
| encryption | 1 | 7 | ~50% |
| moderation | 1 | 8 | ~40% |
| shared/security | 3 | 35 (InputSanitizer:15, RateLimitFilter:16, WsRateLimiter:4) | ~60% |
| mobile | 3 | 23 (TokenStorage:5, AuthRepo:5, PhoneNorm:13) | ~10% |
| shared module | 1 | 27 (WsMessageSerialization) | ~50% |
| **Total** | **18** | **251** | **~40% est.** |

### 3.2 Coverage Targets

| Milestone | Backend | Mobile | Shared |
|-----------|---------|--------|--------|
| Current | ~40% (201 tests) | ~10% (23 tests) | ~50% (27 tests) |
| Beta | 60% | 40% | 80% |
| GA | 80% | 60% | 90% |

### 3.3 Coverage Gaps (Priority Order)

| Area | Current Tests | Gap | Priority |
|------|--------------|-----|----------|
| REST controllers (integration) | 1 (AuthController) | 22 controllers untested | P0 |
| Persistence adapters | 0 | All adapters need Testcontainers tests | P0 |
| WebSocket handler | 1 (19 tests) | Edge cases, call signaling flows | P1 |
| Mobile ViewModels | 0 | State management, error handling | P1 |
| Mobile navigation | 0 | Decompose config transitions | P2 |
| Shared validation | 0 | ValidationRules functions | P1 |
| Mapper functions | 0 | Domain ↔ JPA, Domain ↔ DTO | P1 |

### 3.4 Test Quality Rules

| Rule | Rationale |
|------|-----------|
| No test depends on execution order | Tests run in parallel |
| No shared mutable state between tests | Use `@BeforeEach` setup |
| No `Thread.sleep()` in tests | Use coroutines-test `advanceTimeBy()` |
| Each test has single assertion focus | "should X when Y" naming |
| Integration tests use Testcontainers | No shared/external DB |
| Mock only out-ports, never domain | Test real business logic |

---

## 4. Technical Debt Tracker

### 4.1 Active Debt

| ID | Description | Severity | Effort | Impact |
|----|------------|----------|--------|--------|
| TD-01 | Backend enum duplication (ContentType, ConversationType, MemberRole in both domain and shared) | Low | Medium | Mapper maintenance |
| TD-02 | No ArchUnit tests for architecture enforcement | Medium | Low | Architecture drift risk |
| TD-03 | 22 controllers without integration tests | High | High | Regression risk |
| TD-04 | No API contract tests (consumer-driven) | Medium | Medium | Breaking change risk |
| TD-05 | Mobile has only 4 test files | High | High | Regression risk |
| TD-06 | No database migration rollback scripts | Low | Low | Rollback requires manual SQL |
| TD-07 | `AppConfig.kt` growing (250 lines, 15+ beans) | Low | Low | Split into per-module configs |
| TD-08 | No structured logging correlation IDs | Medium | Low | Debugging difficulty |

### 4.2 Resolved Debt

| ID | Description | Resolution |
|----|------------|-----------|
| ~~TD-R01~~ | ChatScreen.kt 1,700 lines | Split to 405 lines (Phase 2) |
| ~~TD-R02~~ | MessagingService 7 use cases | Split into 3 services |
| ~~TD-R03~~ | 5 controllers bypass use cases | Refactored (Phase 2) |
| ~~TD-R04~~ | No CI/CD pipeline | GitHub Actions (4 workflows) |
| ~~TD-R05~~ | No mobile tests | 50 tests added (23 mobile + 27 shared) |
| ~~TD-R06~~ | No performance indexes | 12 indexes (V14) |
| ~~TD-R07~~ | Single-server WS limitation | Redis Pub/Sub broadcaster |
| ~~TD-R08~~ | Push notifications disabled | FCM_ENABLED=true |
| ~~TD-R09~~ | Delivery ticks stuck | Global DELIVERED ack |

---

## 5. Code Style & Conventions

### 5.1 Kotlin Style

| Convention | Rule |
|-----------|------|
| No `!!` | Handle nulls with `?.`, `?:`, `let` |
| Data classes | For DTOs, value objects, events |
| Sealed classes | For type-safe variants (WsMessage, ErrorCode) |
| Extension functions | For mapping, formatting, conversion |
| Coroutines | For async operations (`suspend fun`) |
| Named parameters | For constructors with 3+ parameters |
| Trailing lambda | For builder-style APIs |

### 5.2 Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Package | Lowercase, dot-separated | `com.muhabbet.messaging.domain.service` |
| Class | PascalCase | `MessageService`, `ConversationController` |
| Interface | PascalCase (no `I` prefix) | `MessageRepository`, `SendMessageUseCase` |
| Function | camelCase | `sendMessage()`, `getConversations()` |
| Constant | SCREAMING_SNAKE | `MAX_MESSAGE_LENGTH`, `EDIT_WINDOW_MINUTES` |
| Test | `should [expected] when [condition]` | `should throw when otp expired` |
| Migration | `V{n}__{description}` | `V15__add_moderation_analytics_backup.sql` |
| Error code | DOMAIN_ACTION_RESULT | `AUTH_OTP_EXPIRED`, `MSG_NOT_FOUND` |

### 5.3 Package Structure Conventions

```
com.muhabbet.{module}/
├── domain/
│   ├── model/           → 1 file per aggregate root
│   ├── port/in/         → 1 file per use case interface
│   ├── port/out/        → 1 file per repository interface
│   ├── service/         → 1 file per service (implements ≤3 use cases)
│   └── event/           → 1 file per domain event
└── adapter/
    ├── in/web/          → 1 controller per REST resource
    ├── in/websocket/    → WS handlers
    └── out/
        ├── persistence/ → 1 adapter per repository port
        └── external/    → 1 adapter per external service
```

---

## 6. Dependency Management

### 6.1 Current Dependency Versions

| Category | Dependency | Version | Last Updated |
|----------|-----------|---------|-------------|
| Language | Kotlin | 2.3.10 | Feb 2026 |
| Framework | Spring Boot | 4.0.2 | Feb 2026 |
| Runtime | Java (JVM target) | 21 | Feb 2026 |
| Build | Gradle | 8.14.4 | Feb 2026 |
| Serialization | kotlinx.serialization | 1.10.0 (mobile), 1.8.1 (backend) | Feb 2026 |
| HTTP client | Ktor | 3.4.0 (mobile) | Feb 2026 |
| DI (mobile) | Koin | 4.1.1 | Feb 2026 |
| Navigation | Decompose | 3.4.0 | Feb 2026 |
| Image loading | Coil | 3.3.0 | Feb 2026 |
| JWT | JJWT | 0.12.6 | Feb 2026 |
| S3/MinIO | MinIO SDK | 8.5.14 | Feb 2026 |
| Coroutines | kotlinx-coroutines | 1.10.2 (mobile) | Feb 2026 |
| Date/time | kotlinx-datetime | 0.7.1 (mobile) | Feb 2026 |
| Testing | MockK | 1.13.13 | Feb 2026 |
| Testing | Testcontainers | 1.20.4 | Feb 2026 |
| Firebase | BOM | 33.7.0 (mobile) | Feb 2026 |
| Crash reporting | Sentry | 7.19.1 | Feb 2026 |

### 6.2 Dependency Update Policy

| Priority | Frequency | Scope |
|----------|-----------|-------|
| Security patches | Within 48h | All dependencies |
| Minor versions | Monthly | Review changelogs |
| Major versions | Quarterly | Plan migration, test thoroughly |
| Kotlin/Spring | Per release | Dedicated upgrade PR |

---

## 7. Documentation Standards

### 7.1 Required Documentation

| Artifact | Location | Owner |
|----------|----------|-------|
| CLAUDE.md | Root | Engineer (auto-updated) |
| ROADMAP.md | Root | Product |
| CHANGELOG.md | Root | Engineer (per release) |
| README.md | Root | Engineer |
| API Contract | `docs/api-contract.md` | Engineer |
| ADRs | `docs/adr/` | Engineer (per decision) |
| QA docs | `docs/qa/` | QA/Engineer |
| Inline comments | Code | Engineer (only when non-obvious) |

### 7.2 Comment Policy
- **DO** comment non-obvious business rules
- **DO** comment workarounds with links to issues
- **DON'T** comment obvious code (`// get user` before `getUser()`)
- **DON'T** add TODO comments without tracking in todo.md

---

## 8. Action Items

### P0
- [ ] Add ArchUnit dependency and create architecture compliance tests
- [ ] Set up JaCoCo code coverage reporting in CI
- [ ] Create integration tests for top 5 most-used controllers
- [ ] Add detekt (Kotlin static analysis) to CI pipeline

### P1
- [ ] Reduce `AppConfig.kt` — split into per-module config classes
- [ ] Add correlation IDs to request logging (MDC)
- [ ] Create test fixtures/factories for common test data
- [ ] Add mutation testing (PITest) to measure test quality

### P2
- [ ] Automated dependency update PRs (Renovate or Dependabot)
- [ ] SonarQube integration for continuous code quality monitoring
- [ ] Technical debt burndown tracking in JIRA/Linear
- [ ] Architecture fitness functions (automated compliance checks)
