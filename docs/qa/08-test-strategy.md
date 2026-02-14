# 08 — Test Strategy

> Comprehensive testing approach: test pyramid, environments, CI/CD integration, and test data management.

**Current inventory: 251 tests (201 backend + 23 mobile + 27 shared) across 18 test files.**

---

## 1. Test Pyramid

```
                 ┌───────────┐
                 │   E2E     │  5%  — Critical user flows only
                 │  (Manual  │       Smoke test on real devices
                 │   + Auto) │
                ┌┴───────────┴┐
                │ Integration  │  25% — API tests, DB tests
                │  (Container) │       Testcontainers (PG, Redis)
               ┌┴─────────────┴┐
               │   Component    │  10% — Controller tests with
               │   (MockMvc)    │       mocked services
              ┌┴───────────────┴┐
              │    Unit Tests    │  60% — Domain services, validators
              │  (MockK / Mock)  │       mappers, utilities
              └─────────────────┘
```

---

## 2. Test Categories

### 2.1 Unit Tests

**Scope:** Single class/function, no I/O, no framework.

**Backend:**
| Target | Framework | Mocking | Current | Target |
|--------|-----------|---------|---------|--------|
| AuthService | JUnit 5 | MockK | 9 tests | 20+ |
| MessageService | JUnit 5 | MockK | 16 tests | 30+ |
| ConversationService | JUnit 5 | MockK | 28 tests | 35+ |
| GroupService | JUnit 5 | MockK | 41 tests | 50+ |
| MediaService | JUnit 5 | MockK | 21 tests | 30+ |
| ModerationService | JUnit 5 | MockK | 8 tests | 15+ |
| BotService | JUnit 5 | MockK | 0 tests | 15+ |
| BackupService | JUnit 5 | MockK | 0 tests | 10+ |
| ChannelAnalyticsService | JUnit 5 | MockK | 0 tests | 10+ |
| CallSignalingService | JUnit 5 | MockK | 7 tests | 15+ |
| EncryptionService | JUnit 5 | MockK | 7 tests | 10+ |
| InputSanitizer | JUnit 5 | None | 15 tests | 20+ |
| WebSocketRateLimiter | JUnit 5 | None | 4 tests | 8+ |
| DeliveryStatus logic | JUnit 5 | MockK | 6 tests | 10+ |
| Mapper functions | JUnit 5 | None | 0 tests | 20+ |

**Mobile/Shared:**
| Target | Framework | Current | Target |
|--------|-----------|---------|--------|
| FakeTokenStorage | kotlin-test | 5 tests | 5 |
| AuthRepository | kotlin-test + ktor-mock | 5 tests | 10+ |
| PhoneNormalization | kotlin-test | 13 tests | 15+ |
| WsMessageSerialization | kotlin-test | 27 tests | 30+ |
| Validation rules | kotlin-test | 0 tests | 20+ |
| ViewModel state | kotlin-test + coroutines-test | 0 tests | 30+ |

### 2.2 Component Tests (Controller Layer)

**Scope:** HTTP endpoint testing with mocked services.

```kotlin
@WebMvcTest(ConversationController::class)
class ConversationControllerTest {

    @MockkBean
    private lateinit var conversationService: ConversationService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should return 200 with conversations when authenticated`() {
        every { conversationService.getConversations(any(), any(), any()) } returns
            listOf(testConversation())

        mockMvc.get("/api/v1/conversations") {
            header("Authorization", "Bearer $validToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data") { isArray() }
        }
    }

    @Test
    fun `should return 401 when not authenticated`() {
        mockMvc.get("/api/v1/conversations")
            .andExpect { status { isUnauthorized() } }
    }
}
```

**Coverage plan:**
| Controller | Endpoints | Test Count | Priority |
|-----------|-----------|-----------|----------|
| AuthController | 4 | 4 exists (integration) + 16 rate limit | P0 |
| ConversationController | 5 | 0 | P0 |
| MessageController | 4 | 0 | P0 |
| GroupController | 6 | 0 | P0 |
| MediaController | 3 | 0 | P0 |
| ModerationController | 5 | 0 | P1 |
| BotController | 6 | 0 | P1 |
| BackupController | 4 | 0 | P1 |
| StatusController | 3 | 0 | P1 |
| ChannelController | 4 | 0 | P1 |
| PollController | 3 | 0 | P1 |
| All others (12) | ~30 | 0 | P2 |

### 2.3 Integration Tests

**Scope:** Full stack (controller → service → repository → DB) with Testcontainers.

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("muhabbet_test")

        @Container
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.redis.host", redis::getHost)
            registry.add("spring.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `should complete full auth flow - request OTP, verify, get profile`() {
        // 1. Request OTP
        val otpResponse = restTemplate.postForEntity(
            "/api/v1/auth/otp/request",
            OtpRequestPayload(phoneNumber = "+905001234567"),
            ApiResponse::class.java
        )
        assertThat(otpResponse.statusCode).isEqualTo(HttpStatus.OK)

        // 2. Verify OTP (mock OTP enabled)
        val verifyResponse = restTemplate.postForEntity(
            "/api/v1/auth/otp/verify",
            OtpVerifyPayload(phoneNumber = "+905001234567", otp = "123456", ...),
            ApiResponse::class.java
        )
        assertThat(verifyResponse.statusCode).isEqualTo(HttpStatus.OK)

        // 3. Get profile with token
        val token = extractToken(verifyResponse)
        val profileResponse = restTemplate.exchange(
            "/api/v1/users/me",
            HttpMethod.GET,
            HttpEntity(null, HttpHeaders().apply { setBearerAuth(token) }),
            ApiResponse::class.java
        )
        assertThat(profileResponse.statusCode).isEqualTo(HttpStatus.OK)
    }
}
```

**Integration test plan:**
| Flow | Tests | Priority |
|------|-------|----------|
| Auth flow (OTP → JWT → profile) | 5 | P0 (4 exist) |
| Message flow (create conv → send → receive) | 5 | P0 |
| Group flow (create → add members → send) | 5 | P0 |
| Media flow (upload → thumbnail → download URL) | 3 | P1 |
| Moderation flow (report → review → resolve) | 3 | P1 |
| Bot flow (create → generate token → list) | 3 | P1 |
| KVKK flow (export data → delete account) | 3 | P0 |

### 2.4 E2E Tests (Manual → Automated)

**Critical user flows for smoke testing:**

| # | Flow | Steps | Priority |
|---|------|-------|----------|
| 1 | New user registration | Install → phone → OTP → conversations | P0 |
| 2 | Send first message | New conversation → type → send → delivered | P0 |
| 3 | Group creation | Create group → name → add members → send | P0 |
| 4 | Image sharing | Attach image → compress → upload → display | P0 |
| 5 | Voice message | Hold record → release → send → playback | P1 |
| 6 | Profile management | Settings → edit name → upload avatar → save | P1 |
| 7 | Contact sync | Permissions → sync → see matches | P1 |
| 8 | Delete message | Long-press → delete → "deleted" shown | P1 |
| 9 | Block user | Profile → block → verify no messages | P1 |
| 10 | Account deletion | Settings → delete → confirm → verify | P0 |

**Automation plan (Maestro):**
```yaml
# maestro/flows/send-message.yaml
appId: com.muhabbet.app
---
- launchApp
- assertVisible: "Konuşmalar"  # Conversations screen
- tapOn: "New conversation"     # FAB
- tapOn:
    text: "Test User"           # Select contact
- tapOn:
    id: "message_input"
- inputText: "Hello from Maestro!"
- tapOn:
    id: "send_button"
- assertVisible: "Hello from Maestro!"
- assertVisible:                 # Delivery tick
    id: "message_status_sent"
```

---

## 3. Test Environments

### 3.1 Environment Matrix

| Environment | Purpose | Infrastructure | Data |
|-------------|---------|---------------|------|
| Local dev | Developer testing | Docker Compose (local) | Mock data |
| CI | Automated tests | GitHub Actions + Testcontainers | Generated |
| Staging | Pre-production testing | GCP VM (separate) | Anonymized prod data |
| Production | Live users | GCP VM (prod) | Real data |

### 3.2 Test Data Management

**Test users:**
| Phone | Purpose | Notes |
|-------|---------|-------|
| +905000000001 | Test Bot | Auto-reply bot (Python script) |
| +905000000002 | Test User 2 | Second test account |
| +905000000003-009 | Load test users | Batch created for load tests |

**Test data factories:**
```kotlin
object TestData {
    fun user(
        id: UUID = UUID.randomUUID(),
        phone: String = "+90500${(1000000..9999999).random()}",
        displayName: String = "Test User ${(1..999).random()}"
    ) = User(id = id, phoneNumber = phone, displayName = displayName)

    fun conversation(
        type: ConversationType = ConversationType.DIRECT,
        members: List<UUID> = listOf(UUID.randomUUID(), UUID.randomUUID())
    ) = Conversation(id = UUID.randomUUID(), type = type, members = members)

    fun message(
        conversationId: UUID = UUID.randomUUID(),
        senderId: UUID = UUID.randomUUID(),
        content: String = "Test message ${System.nanoTime()}"
    ) = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = senderId,
        content = content,
        contentType = ContentType.TEXT,
        createdAt = Instant.now()
    )
}
```

### 3.3 Test Database Setup

```kotlin
// Shared Testcontainers setup for integration tests
abstract class IntegrationTestBase {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("muhabbet_test")
            .withUsername("test")
            .withPassword("test")

        @Container
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
    }
}
```

---

## 4. CI/CD Test Integration

### 4.1 Current Pipeline

```
Push to backend/ or shared/
  └─▶ backend-ci.yml
       ├─ Checkout
       ├─ Setup Java 21
       ├─ Gradle cache restore
       ├─ ./gradlew :backend:test
       ├─ ./gradlew :backend:bootJar
       └─ Upload test results

Push to mobile/ or shared/
  └─▶ mobile-ci.yml
       ├─ Android: assembleDebug (with test google-services.json)
       └─ iOS: build framework (macOS runner)

Weekly + Push
  └─▶ security.yml
       ├─ Trivy filesystem scan
       ├─ Trivy Docker image scan
       ├─ Gitleaks secret detection
       └─ CodeQL analysis
```

### 4.2 Planned Pipeline Additions

```
Push to any branch
  └─▶ test-suite.yml (NEW)
       ├─ Unit tests (parallel)
       │   ├─ Backend unit tests
       │   └─ Shared module tests
       ├─ Integration tests (sequential)
       │   ├─ Start Testcontainers
       │   ├─ Run integration test suite
       │   └─ Generate coverage report
       ├─ Code quality
       │   ├─ detekt (Kotlin static analysis)
       │   ├─ ArchUnit (architecture compliance)
       │   └─ JaCoCo coverage gate (fail if <threshold)
       └─ Upload artifacts
            ├─ Test results (JUnit XML)
            ├─ Coverage report (HTML + Cobertura)
            └─ detekt report

Merge to main
  └─▶ release-test.yml (NEW)
       ├─ Full integration test suite
       ├─ OWASP ZAP baseline scan
       ├─ Performance regression test (k6, 2 min)
       └─ Deploy to staging
```

### 4.3 Quality Gates

| Gate | Threshold | Action on Failure |
|------|-----------|-------------------|
| Unit test pass rate | 100% | Block merge |
| Integration test pass rate | 100% | Block merge |
| Code coverage (new code) | >80% | Warning |
| Code coverage (overall) | >60% (beta), >80% (GA) | Warning → Block |
| detekt violations | 0 critical | Block merge |
| ArchUnit violations | 0 | Block merge |
| Security scan (HIGH+) | 0 | Block merge |
| Performance regression | P95 < 2x baseline | Warning |

---

## 5. Test Reporting

### 5.1 Reports Generated

| Report | Tool | Location | Frequency |
|--------|------|----------|-----------|
| Unit test results | JUnit 5 | `build/reports/tests/` | Every CI run |
| Coverage report | JaCoCo | `build/reports/jacoco/` | Every CI run |
| Static analysis | detekt | `build/reports/detekt/` | Every CI run |
| Security scan | Trivy/CodeQL | GitHub Security tab | Weekly + push |
| Performance baseline | k6 | `docs/qa/benchmarks/` | On demand |

### 5.2 Test Metrics Tracking

| Metric | How | Target Trend |
|--------|-----|--------------|
| Test count over time | CI artifact history | Increasing |
| Coverage % over time | JaCoCo trend | Increasing |
| Test execution time | CI timing | Stable or decreasing |
| Flaky test count | CI failure analysis | Decreasing → zero |
| New code coverage | JaCoCo diff | >80% |

---

## 6. Specialized Testing

### 6.1 WebSocket Testing

```kotlin
// WebSocket integration test with StompClient or raw WebSocket
@Test
fun `should receive message broadcast when sent to conversation`() {
    // Connect User A
    val wsA = connectWebSocket(tokenA)
    // Connect User B
    val wsB = connectWebSocket(tokenB)

    // User A sends message
    wsA.send(SendMessage(conversationId, "Hello", ContentType.TEXT, clientMsgId))

    // User A receives ack
    val ack = wsA.receive<ServerAck>()
    assertThat(ack.status).isEqualTo("OK")

    // User B receives broadcast
    val newMsg = wsB.receive<NewMessage>()
    assertThat(newMsg.content).isEqualTo("Hello")
    assertThat(newMsg.conversationId).isEqualTo(conversationId)
}
```

### 6.2 Concurrency Testing

| Test | Description | Tool |
|------|-------------|------|
| Concurrent message sends | 100 users send to same group simultaneously | k6 |
| Concurrent conversation creation | 50 users create DM with same target | Custom |
| Token refresh race | Multiple devices refresh same token | Custom |
| Read/write consistency | Send + read in quick succession | Custom |

### 6.3 Compatibility Testing

| Test | Method | Priority |
|------|--------|----------|
| API backward compatibility | Compare OpenAPI spec versions | P1 |
| WS protocol compatibility | Serialize/deserialize all message types | P0 (exists) |
| Database migration safety | Flyway validate on clean + existing DB | P0 |
| Shared module cross-platform | Run shared tests on JVM + Android + iOS | P1 |

---

## 7. Action Items

### P0 (Immediate)
- [ ] Add JaCoCo to backend Gradle for coverage reports
- [ ] Create TestData factory object for standardized test data
- [ ] Write integration tests for auth flow (expand existing test)
- [ ] Write integration tests for message send/receive flow
- [ ] Add detekt to CI pipeline with sensible defaults

### P1 (Pre-Beta)
- [ ] Write component tests for top 10 controllers
- [ ] Create Testcontainers base class for reuse
- [ ] Set up staging environment for pre-production testing
- [ ] Create Maestro E2E test for critical flows (5 flows)
- [ ] Add ArchUnit tests for architecture compliance
- [ ] Set up coverage trend tracking

### P2 (Continuous)
- [ ] WebSocket integration test suite
- [ ] Concurrency test suite
- [ ] Performance regression detection in CI
- [ ] Automated API contract testing
- [ ] Fuzz testing for InputSanitizer
- [ ] Memory leak detection in mobile CI
