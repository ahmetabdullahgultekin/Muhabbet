# 03 — Robustness & Reliability

> Quality attribute: System's ability to handle errors, recover from failures, and maintain data integrity.

---

## 1. Reliability Targets

| Metric | Beta Target | GA Target |
|--------|-------------|-----------|
| Uptime (monthly) | 99.5% (3.6h downtime) | 99.9% (43min downtime) |
| Mean Time to Recovery (MTTR) | <30 min | <10 min |
| Mean Time Between Failures (MTBF) | >168h (1 week) | >720h (1 month) |
| Data loss tolerance | Zero (for messages) | Zero |
| Message delivery guarantee | At-least-once | At-least-once |
| Crash-free rate (mobile) | 99.5% | 99.9% |
| WebSocket reconnect success | >99% | >99.9% |

---

## 2. Fault Tolerance Analysis

### 2.1 Component Failure Matrix

| Component | Failure Mode | Impact | Current Mitigation | Gap |
|-----------|-------------|--------|-------------------|-----|
| **PostgreSQL** | Crash/restart | All data operations fail | Docker restart policy | No replica |
| **PostgreSQL** | Disk full | Writes fail | Monitoring (manual) | No alerts |
| **PostgreSQL** | Slow queries | Request timeouts | 12 performance indexes | No query timeout |
| **Redis** | Crash/restart | Presence lost, Pub/Sub disrupted | Docker restart, TTL keys | No sentinel |
| **Redis** | Memory full | Eviction or OOM | maxmemory-policy allkeys-lru | Not configured |
| **MinIO** | Crash/restart | Media upload/download fails | Docker restart | No replication |
| **MinIO** | Disk full | Uploads fail | Storage stats endpoint | No alerts |
| **Nginx** | Crash/restart | All traffic blocked | Docker restart | No failover |
| **Backend** | OOM | Process killed | JVM heap settings | No auto-restart |
| **Backend** | Deadlock | Requests hang | Spring async | No detection |
| **FCM** | Service degradation | Push notifications delayed | Fire-and-forget | No fallback |
| **WebSocket** | Connection drop | Messages not delivered | Client reconnect | No offline queue |

### 2.2 Cascading Failure Scenarios

```
Scenario 1: PostgreSQL slow → Connection pool exhaustion → All requests timeout → Mobile shows errors
Mitigation: Connection timeout (10s), circuit breaker (future), health check endpoint

Scenario 2: Redis crash → Presence lost → Typing indicators fail → Pub/Sub fails → Cross-instance WS breaks
Mitigation: Graceful degradation (presence optional), reconnect on Redis listener

Scenario 3: MinIO crash → Media upload fails → Image messages fail → User sees error
Mitigation: Upload retry on mobile, error toast, queue failed uploads (future)

Scenario 4: Backend OOM → Process restart → All WS connections dropped → Mass reconnection storm
Mitigation: Client exponential backoff reconnect (1s→30s), staggered reconnection
```

---

## 3. Data Integrity

### 3.1 Message Delivery Guarantees

**Current flow:**
```
Client A                  Backend                  Client B
   │                        │                        │
   │── message.send ───────▶│                        │
   │                        │── save to DB ──────────│
   │◀── ack(OK) ───────────│                        │
   │                        │── message.new ────────▶│
   │                        │                        │── message.ack(DELIVERED) ──▶│
   │◀── message.status ────│◀───────────────────────│
   │                        │                        │── message.ack(READ) ──────▶│
   │◀── message.status ────│◀───────────────────────│
```

**Guarantees:**
- Messages are **persisted before acknowledgement** (ack(OK) is sent after DB save)
- Message delivery is **at-least-once** (recipient may receive duplicate `message.new` on reconnect)
- Idempotency via `clientMessageId` prevents duplicate message creation

**Gaps:**
- [ ] No offline message queue — if recipient is offline, message waits in DB until they reconnect and fetch
- [ ] No retry for failed WS delivery — if broadcast fails, no automatic retry
- [ ] No dead letter queue for persistently failing deliveries

### 3.2 Database Integrity

| Control | Implementation | Status |
|---------|---------------|--------|
| Foreign keys | All relationships have FK constraints | Deployed |
| Unique constraints | Direct conversation dedup, phone hash, bot token | Deployed |
| NOT NULL | Critical fields enforced | Deployed |
| UUID primary keys | `gen_random_uuid()` — no collisions | Deployed |
| Soft delete | `deleted_at` column (KVKK compliance) | Messages, Users |
| Optimistic locking | Not implemented | Gap (concurrent edits) |
| Transaction isolation | Spring `@Transactional` (READ_COMMITTED default) | Deployed |

### 3.3 Idempotency

| Operation | Idempotency Key | Status |
|-----------|----------------|--------|
| Send message | `clientMessageId` | Deployed |
| OTP request | Phone + cooldown window | Deployed |
| Contact sync | Hash comparison | Deployed |
| Media upload | Client-generated UUID | Deployed |
| Delivery ack | Message ID + user ID | Deployed |
| Block user | Unique(blocker_id, blocked_id) | Deployed |
| Report user | Non-idempotent (creates new report) | Acceptable |

---

## 4. Error Handling

### 4.1 Error Classification

| Category | HTTP Status | ErrorCode Pattern | Recovery |
|----------|------------|-------------------|----------|
| Client error | 400 | `*_INVALID_*`, `VALIDATION_ERROR` | Fix request |
| Authentication | 401 | `AUTH_TOKEN_*`, `AUTH_OTP_*` | Re-authenticate |
| Authorization | 403 | `*_NOT_MEMBER`, `*_PERMISSION_DENIED` | No retry |
| Not found | 404 | `*_NOT_FOUND` | No retry |
| Conflict | 409 | `*_ALREADY_*`, `*_DUPLICATE` | Refresh state |
| Rate limit | 429 | `AUTH_OTP_COOLDOWN`, `RATE_LIMITED` | Wait and retry |
| Server error | 500 | `INTERNAL_ERROR`, `MEDIA_UPLOAD_FAILED` | Retry with backoff |

### 4.2 Mobile Error Handling

| Scenario | Expected Behavior | Test |
|----------|-------------------|------|
| Network offline | Show "Bağlantı yok" banner, queue actions | Manual |
| WS disconnect | Auto-reconnect with exponential backoff | Manual |
| 401 on API call | Refresh token → retry → logout if refresh fails | `AuthRepositoryTest` |
| 500 on API call | Show error toast, allow retry | Manual |
| Image upload fails | Show retry button on failed image | Manual |
| OTP cooldown (429) | Show countdown timer | Manual |
| Message send fails | Keep message in input, show error | Manual |

### 4.3 Backend Error Handling

```kotlin
// GlobalExceptionHandler catches all BusinessException instances
// and returns structured error response:
{
  "error": {
    "code": "AUTH_OTP_EXPIRED",
    "message": "Doğrulama kodu süresi doldu"
  },
  "timestamp": "2026-02-13T10:00:00Z"
}
```

**Coverage:** All 40+ `ErrorCode` enum values have default Turkish messages.

---

## 5. Chaos Engineering Plan

### 5.1 Experiments

| Experiment | Method | Expected Result | Priority |
|------------|--------|-----------------|----------|
| Kill PostgreSQL | `docker stop muhabbet-postgres` | Backend returns 503, reconnects on restart | P0 |
| Kill Redis | `docker stop muhabbet-redis` | Presence degrades, Pub/Sub fails, core messaging works | P0 |
| Kill MinIO | `docker stop muhabbet-minio` | Media ops fail, text messaging works | P1 |
| Simulate network partition | `iptables` rules | WS clients reconnect, messages eventually delivered | P1 |
| Exhaust DB connections | Open 100 idle connections | Backend queues requests, no crash | P1 |
| Simulate slow DB | `pg_sleep()` wrapper | Requests timeout gracefully, no thread starvation | P1 |
| Fill Redis memory | Write large keys | Eviction triggers, no crash | P2 |
| Kill backend during WS broadcast | `kill -9` during message delivery | Clients reconnect, messages re-fetched from DB | P2 |
| Corrupt WS frame | Send invalid JSON to `/ws` | Backend logs error, connection closed gracefully | P0 |

### 5.2 Recovery Verification

For each chaos experiment, verify:
1. **Detection**: Is the failure logged/alerted?
2. **Impact**: What user-facing behavior changes?
3. **Recovery**: Does the system auto-recover?
4. **Data**: Is any data lost or corrupted?
5. **Duration**: How long until full recovery?

---

## 6. Graceful Degradation

### 6.1 Feature Degradation Table

| Primary Feature | Dependency | Degraded Behavior |
|----------------|-----------|-------------------|
| Send message | PostgreSQL | Cannot send (hard dependency) |
| Send message | Redis | Can send (Pub/Sub degraded, local delivery works) |
| Typing indicators | Redis | Silently disabled |
| Online presence | Redis | Shows all users as offline |
| Push notifications | FCM | Not delivered (message still in DB) |
| Media upload | MinIO | Upload fails, text messaging works |
| Link previews | External URLs | Preview not shown, link still clickable |
| Contact sync | PostgreSQL | Sync fails, cached contacts work |
| Bot webhooks | External URLs | Webhook call fails, logged, no retry |

### 6.2 Circuit Breaker Candidates

| Dependency | Failure Threshold | Reset Timeout | Fallback |
|-----------|-------------------|--------------|----------|
| FCM (push) | 5 failures/min | 60s | Skip push, log warning |
| GIPHY API | 3 failures/min | 120s | Show empty sticker grid |
| Link preview fetch | 3 failures/min | 60s | Skip preview generation |
| Bot webhook delivery | 3 failures/min per bot | 300s | Queue webhook, retry later |

---

## 7. Test Plan

### 7.1 Reliability Tests

| Test | Type | Priority |
|------|------|----------|
| Backend survives PostgreSQL restart | Chaos | P0 |
| Backend survives Redis restart | Chaos | P0 |
| Mobile reconnects after WS disconnect | Integration | P0 |
| Mobile handles 401 with token refresh | Unit | P0 (exists) |
| Duplicate message.send produces single message | Unit | P0 |
| Concurrent conversation creation doesn't duplicate | Integration | P1 |
| Message ordering preserved under load | Load | P1 |
| Backend recovers from OOM kill | Chaos | P1 |
| Media upload handles MinIO timeout | Integration | P1 |
| Long-running WS connection stable (1hr) | Soak | P1 |

### 7.2 Data Integrity Tests

| Test | Type | Priority |
|------|------|----------|
| Message persisted before ack | Unit | P0 (exists) |
| Foreign key violations produce clean errors | Integration | P1 |
| Soft delete preserves data for KVKK export | Integration | P1 |
| Concurrent message edits don't lose data | Integration | P1 |
| Backup export includes all user messages | Integration | P1 |

---

## 8. Action Items

### P0
- [ ] Run PostgreSQL/Redis chaos experiments on staging
- [ ] Verify mobile reconnect behavior after WS disconnect
- [ ] Add connection pool exhaustion handling (timeouts, queue limits)
- [ ] Test invalid WS frame handling (malformed JSON, oversized, binary)

### P1
- [ ] Implement circuit breakers for external dependencies (FCM, GIPHY)
- [ ] Add health check that verifies PG + Redis connectivity
- [ ] Implement offline message queue on mobile (SQLDelight)
- [ ] Add optimistic locking for concurrent message edits
- [ ] Configure Docker restart policies for all containers

### P2
- [ ] PostgreSQL read replica for query load distribution
- [ ] Redis Sentinel for automatic failover
- [ ] Dead letter queue for failed message deliveries
- [ ] Automated chaos testing in CI (weekly)
