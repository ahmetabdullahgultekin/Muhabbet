# 02 — Security

> Quality attribute: Protection of data, authentication integrity, and regulatory compliance.

---

## 1. Security Architecture

### 1.1 Current Security Controls

| Layer | Control | Status |
|-------|---------|--------|
| **Transport** | TLS 1.3 (nginx termination) | Deployed |
| **Authentication** | JWT HS256 (access + refresh tokens) | Deployed |
| **Authorization** | Spring Security filter chain, per-endpoint | Deployed |
| **Input validation** | InputSanitizer (HTML escape, control chars, URL validation) | Deployed |
| **Rate limiting** | Auth endpoints: 10 req/min/IP, WS: 50 msg/10s/connection | Deployed |
| **Security headers** | HSTS, CSP, X-Frame-Options DENY, X-Content-Type-Options, Referrer-Policy, Permissions-Policy | Deployed |
| **Secret scanning** | Gitleaks in CI | Deployed |
| **Vulnerability scanning** | Trivy (filesystem + Docker images) | Deployed |
| **Static analysis** | CodeQL (java-kotlin) | Deployed |
| **E2E encryption** | Key exchange endpoints, NoOp implementation (Signal Protocol pending) | Infrastructure ready |
| **Data protection** | KVKK data export + account deletion | Deployed |
| **Content moderation** | Report/block system (BTK Law 5651) | Deployed |

### 1.2 Security Architecture Diagram

```
Client ──TLS 1.3──▶ Nginx ──HTTP──▶ Spring Boot
                      │                  │
                      │            ┌─────▼──────┐
                      │            │ JwtAuthFilter│
                      │            │ (every req) │
                      │            └─────┬──────┘
                      │                  │
                      │            ┌─────▼──────────┐
                      │            │ RateLimitFilter │
                      │            │ (auth endpoints)│
                      │            └─────┬──────────┘
                      │                  │
                      │            ┌─────▼──────────┐
                      │            │ InputSanitizer  │
                      │            │ (all user input)│
                      │            └─────┬──────────┘
                      │                  │
                      │            ┌─────▼──────┐
                      │            │ Controllers │
                      │            └─────┬──────┘
                      │                  │
                      │            ┌─────▼──────┐
                      │            │  Services   │
                      │            └──┬──────┬──┘
                      │               │      │
                   ┌──▼───┐    ┌──────▼┐  ┌──▼────┐
                   │MinIO │    │  PG   │  │ Redis │
                   │(media)│   │(data) │  │(cache)│
                   └──────┘    └───────┘  └───────┘
```

### 1.3 CORS Configuration

| Property | Value |
|----------|-------|
| Allowed Origins | `https://muhabbet.rollingcatsoftware.com`, `https://*.rollingcatsoftware.com` |
| Allowed Methods | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| Allowed Headers | Authorization, Content-Type, X-Requested-With |
| Allow Credentials | true |

### 1.4 Public Endpoints (No Authentication Required)

| Path | Purpose |
|------|---------|
| `/api/v1/auth/**` | OTP request, verify, token refresh |
| `/ws/**` | WebSocket (JWT in query param) |
| `/actuator/health` | Health check |
| `/actuator/info` | App info |
| `/actuator/metrics` | Metrics |
| `/actuator/prometheus` | Prometheus scrape |

All other endpoints require a valid JWT Bearer token.

### 1.5 InputSanitizer Functions

| Function | Behavior | Max Length |
|----------|----------|-----------|
| `sanitizeHtml(input)` | Escapes `&`, `<`, `>`, `"`, `'` to HTML entities | N/A |
| `stripControlChars(input)` | Removes control chars; preserves `\n`, `\t`, `\r` | N/A |
| `sanitizeDisplayName(input)` | Trims + strips control chars + truncates | 64 chars |
| `sanitizeMessageContent(input)` | Strips control chars + truncates | 10,000 chars |
| `sanitizeUrl(input)` | Allows HTTPS only; rejects `http:`, `javascript:`, `data:` | N/A |

### 1.6 Error Code Inventory (43 codes)

| Category | Error Codes |
|----------|------------|
| **Auth (11)** | AUTH_INVALID_PHONE, AUTH_OTP_COOLDOWN, AUTH_OTP_RATE_LIMIT, AUTH_OTP_INVALID, AUTH_OTP_EXPIRED, AUTH_OTP_MAX_ATTEMPTS, AUTH_TOKEN_INVALID, AUTH_TOKEN_EXPIRED, AUTH_TOKEN_REVOKED, AUTH_UNAUTHORIZED |
| **Messaging (5)** | MSG_CONVERSATION_NOT_FOUND, MSG_NOT_MEMBER, MSG_CONTENT_TOO_LONG, MSG_EMPTY_CONTENT, MSG_DUPLICATE |
| **Conversation (4)** | CONV_ALREADY_EXISTS, CONV_INVALID_PARTICIPANTS, CONV_NOT_FOUND, CONV_MAX_MEMBERS |
| **Media (4)** | MEDIA_TOO_LARGE, MEDIA_UNSUPPORTED_TYPE, MEDIA_NOT_FOUND, MEDIA_UPLOAD_FAILED |
| **Group (7)** | GROUP_NOT_FOUND, GROUP_NOT_MEMBER, GROUP_PERMISSION_DENIED, GROUP_ALREADY_MEMBER, GROUP_CANNOT_REMOVE_OWNER, GROUP_OWNER_CANNOT_LEAVE, GROUP_CANNOT_MODIFY_DIRECT |
| **Message Mgmt (4)** | MSG_NOT_SENDER, MSG_NOT_FOUND, MSG_ALREADY_DELETED, MSG_EDIT_WINDOW_EXPIRED |
| **Status (1)** | STATUS_NOT_FOUND |
| **Channel (2)** | CHANNEL_NOT_FOUND, CHANNEL_NOT_A_CHANNEL |
| **Poll (2)** | POLL_MESSAGE_NOT_FOUND, POLL_INVALID_OPTION |
| **Encryption (2)** | ENCRYPTION_KEY_BUNDLE_NOT_FOUND, ENCRYPTION_INVALID_KEY_DATA |
| **Call (3)** | CALL_NOT_FOUND, CALL_USER_BUSY, CALL_INVALID_TARGET |
| **User/KVKK (2)** | USER_NOT_FOUND, USER_ALREADY_DELETED |
| **Moderation (2)** | REPORT_NOT_FOUND, BLOCK_SELF |
| **Bot (2)** | BOT_NOT_FOUND, BOT_INACTIVE |
| **Backup (2)** | BACKUP_NOT_FOUND, BACKUP_IN_PROGRESS |
| **General (3)** | VALIDATION_ERROR, INTERNAL_ERROR, RATE_LIMITED |
| **Contacts (1)** | CONTACT_SYNC_LIMIT_EXCEEDED |

---

## 2. OWASP Top 10 Assessment

### 2.1 Coverage Matrix

| # | Vulnerability | Status | Controls |
|---|---------------|--------|----------|
| A01 | Broken Access Control | Mitigated | JWT + per-endpoint auth checks, conversation membership validation |
| A02 | Cryptographic Failures | Partial | TLS 1.3, BCrypt for OTP, JWT HS256 (consider RS256 for multi-service) |
| A03 | Injection | Mitigated | JPA parameterized queries, InputSanitizer HTML escaping |
| A04 | Insecure Design | Mitigated | Hexagonal architecture, use case interfaces, no business logic in controllers |
| A05 | Security Misconfiguration | Partial | Security headers set, default Spring Security config reviewed |
| A06 | Vulnerable Components | Mitigated | Trivy scanning in CI, dependency updates to latest (Feb 2026) |
| A07 | Auth Failures | Mitigated | OTP with cooldown/rate limit/max attempts, token refresh rotation |
| A08 | Software/Data Integrity | Partial | CI/CD pipeline, no dependency pinning yet |
| A09 | Logging & Monitoring | Partial | Structured logging, Sentry, no SIEM integration |
| A10 | SSRF | Low risk | No user-controlled URL fetching except link preview (validate scheme) |

### 2.2 Detailed Findings & Recommendations

#### A01 — Broken Access Control
**Current controls:**
- JWT-based authentication on all endpoints
- `AuthenticatedUser.userId()` extracted from SecurityContext
- Conversation membership validated in `MessageService.sendMessage()`
- Group role checks in `GroupService` (OWNER, ADMIN, MEMBER)
- Bot ownership validation in `BotService`

**Gaps to test:**
- [ ] IDOR: Can user A access user B's conversations?
- [ ] IDOR: Can user A read messages from a conversation they're not a member of?
- [ ] IDOR: Can non-owner regenerate bot API token?
- [ ] IDOR: Can non-admin review moderation reports?
- [ ] Horizontal privilege escalation: MEMBER → ADMIN operations
- [ ] Vertical privilege escalation: Regular user → admin endpoints

#### A02 — Cryptographic Failures
**Current state:**
- JWT signed with HS256 (shared secret)
- OTP hashed with BCrypt before storage
- Phone numbers hashed with SHA-256 for contact sync
- Media URLs are pre-signed (time-limited)

**Recommendations:**
- [ ] Consider RS256 for JWT if microservice split is planned
- [ ] Verify JWT_SECRET is at least 256 bits
- [ ] Add HKDF or Argon2 for phone hash (SHA-256 is fast to brute-force)
- [ ] Ensure MinIO pre-signed URL expiry is appropriate (currently 7 days)
- [ ] E2E encryption: Implement Signal Protocol for message content

#### A03 — Injection
**SQL injection:** Protected by JPA parameterized queries. No raw SQL in application code.

**XSS:** `InputSanitizer` escapes HTML entities (`&`, `<`, `>`, `"`, `'`). 15 unit tests verify coverage.

**Test cases:**
```
Input: <script>alert('xss')</script>
Expected: &lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;

Input: '; DROP TABLE users; --
Expected: Passed through (JPA parameterized, not HTML context)

Input: javascript:alert(1)
Expected: URL validation rejects non-HTTPS schemes
```

---

## 3. Authentication Security

### 3.1 OTP Flow Security

| Control | Implementation | Test |
|---------|---------------|------|
| OTP cooldown | 60s between requests | `AuthServiceTest.should fail when cooldown active` |
| OTP rate limit | 10 requests per IP per 60s | `RateLimitFilterTest` (16 tests) |
| OTP max attempts | 5 per code | `AuthServiceTest.should fail on max attempts` |
| OTP expiry | 300s (5 min) | `AuthServiceTest.should fail on expired OTP` |
| OTP storage | BCrypt hashed | Verified in code review |
| Phone validation | Turkish format E.164 | `PhoneNormalizationTest` (13 tests) |

### 3.2 JWT Security

| Property | Value | Risk |
|----------|-------|------|
| Algorithm | HS256 | Adequate for monolith; RS256 for multi-service |
| Access token expiry | 900s (15 min) | Low risk (short-lived) |
| Refresh token expiry | 2,592,000s (30 days) | Medium risk — ensure rotation |
| Refresh token rotation | Yes (new token on each refresh) | Mitigates token theft |
| Token revocation | Logout invalidates refresh token | Verified |
| Claims | `sub` (userId), `deviceId`, `iss` ("muhabbet") | Minimal claims |
| Secret length | Environment variable | Verify ≥256 bits |

### 3.3 Test Matrix

| Test Case | Category | Priority |
|-----------|----------|----------|
| Expired access token returns 401 | Auth | P0 |
| Expired refresh token returns 401 | Auth | P0 |
| Invalid JWT signature returns 401 | Auth | P0 |
| Revoked refresh token returns 401 | Auth | P0 |
| Token from different issuer rejected | Auth | P0 |
| OTP brute force (6th attempt) returns 401 | Auth | P0 |
| OTP cooldown (request within 60s) returns 429 | Auth | P0 |
| Rate limit (11th request/min) returns 429 | Auth | P1 |
| Cross-device token isolation | Auth | P1 |
| Concurrent refresh token race condition | Auth | P1 |

---

## 4. Data Protection

### 4.1 KVKK Compliance

| Requirement | Implementation | Status |
|-------------|---------------|--------|
| Right to access | `GET /api/v1/users/data/export` | Deployed |
| Right to erasure | `DELETE /api/v1/users/data/account` (soft delete) | Deployed |
| Data minimization | Only phone hash stored for contacts, not full number | Deployed |
| Consent | Privacy policy acceptance on registration | Mobile UI done |
| Data portability | Export includes messages, media, conversations | Deployed |
| Breach notification | Sentry alerting (partial) | Needs SIEM |

### 4.2 Data Classification

| Data | Classification | Storage | Protection |
|------|---------------|---------|-----------|
| Phone numbers | PII / Sensitive | PostgreSQL (hashed for contacts) | SHA-256 hash |
| Messages | PII | PostgreSQL | TLS in transit, E2E pending |
| Media files | PII | MinIO | Pre-signed URLs (7d expiry) |
| JWT tokens | Secret | Client-side (secure storage) | HTTPS only |
| OTP codes | Secret | Redis (BCrypt hashed) | 5 min TTL |
| User profiles | PII | PostgreSQL | Access control |
| Call history | PII | PostgreSQL | User-scoped queries |
| Bot API tokens | Secret | PostgreSQL | Unique, regeneratable |

---

## 5. Penetration Testing Plan

### 5.1 Scope

| Target | Type | Tools |
|--------|------|-------|
| REST API (`/api/v1/*`) | DAST | OWASP ZAP, Burp Suite |
| WebSocket (`/ws`) | Custom | Custom Python/k6 scripts |
| Authentication flow | Manual | Burp Suite + scripts |
| Authorization (IDOR) | Manual | Burp Suite |
| Input validation | Automated | OWASP ZAP fuzzer |
| Dependencies | SCA | Trivy, Snyk |
| Infrastructure | Network | nmap, SSL Labs |

### 5.2 Test Scenarios

**Authentication bypass attempts:**
1. Token tampering (modify claims, re-sign with common secrets)
2. Algorithm confusion (HS256 → none)
3. Token replay after logout
4. OTP bypass (predictable OTP, timing attacks)
5. Rate limit bypass (IP rotation, header spoofing)

**Authorization bypass attempts:**
1. Access other user's conversations via ID enumeration
2. Send messages to conversations user is not a member of
3. Modify group settings without ADMIN/OWNER role
4. Access moderation admin endpoints as regular user
5. Use deactivated bot tokens

**Injection attempts:**
1. SQL injection via search endpoints
2. XSS via message content, display names, group names
3. Path traversal via media upload filenames
4. Command injection via webhook URLs (bot platform)
5. SSRF via link preview URL fetching

### 5.3 Schedule

| Phase | Activity | Duration | When |
|-------|----------|----------|------|
| 1 | Automated scan (OWASP ZAP baseline) | 1 day | Pre-beta |
| 2 | Manual auth/authz testing | 2 days | Pre-beta |
| 3 | Full pen test (external firm) | 5 days | Pre-GA |
| 4 | Remediation | 5 days | Post-findings |
| 5 | Retest | 2 days | Post-fix |

---

## 6. Security Testing in CI

### 6.1 Current Pipeline

```yaml
# .github/workflows/security.yml
- Trivy filesystem scan (HIGH, CRITICAL)
- Trivy Docker image scan
- Gitleaks secret detection
- CodeQL static analysis (java-kotlin)
```

### 6.2 Planned Additions

- [ ] OWASP Dependency-Check in CI (SCA)
- [ ] OWASP ZAP baseline scan against staging environment
- [ ] Secret rotation verification (ensure no hardcoded secrets)
- [ ] Container security benchmarks (CIS Docker)
- [ ] License compliance scanning

---

## 7. Action Items

### P0 (Pre-Beta)
- [ ] Run OWASP ZAP baseline scan against staging API
- [ ] Verify JWT secret is ≥256 bits in production
- [ ] Test all IDOR scenarios manually
- [ ] Review all `@RequestMapping` for proper authentication
- [ ] Verify media upload validates file content (not just extension)

### P1 (Pre-GA)
- [ ] Commission external penetration test
- [ ] Implement rate limiting on all public endpoints (not just auth)
- [ ] Add CSRF protection for any future web client
- [ ] Implement API key rotation schedule for bot platform
- [ ] Set up WAF (Web Application Firewall) in front of nginx
- [ ] Consider phone hash upgrade from SHA-256 to Argon2

### P2 (Continuous)
- [ ] Implement Signal Protocol E2E encryption
- [ ] Set up SIEM for security event aggregation
- [ ] Bug bounty program
- [ ] Annual third-party security audit
- [ ] Red team exercise (social engineering + technical)
