# 01 — Performance & Efficiency

> Quality attribute: How well the system performs under expected and peak conditions.

---

## 1. Performance Targets

### 1.1 API Response Times

| Endpoint Category | P50 Target | P95 Target | P99 Target | Current |
|-------------------|-----------|-----------|-----------|---------|
| Auth (OTP request/verify) | <100ms | <200ms | <500ms | Unmeasured |
| GET conversations list | <50ms | <150ms | <300ms | Unmeasured |
| GET message history (page) | <80ms | <200ms | <400ms | Unmeasured |
| POST send message (REST) | <50ms | <100ms | <200ms | Unmeasured |
| Media upload (1MB image) | <500ms | <1s | <2s | Unmeasured |
| Media download URL gen | <30ms | <80ms | <150ms | Unmeasured |
| Contact sync (500 hashes) | <200ms | <500ms | <1s | Unmeasured |
| Search messages | <100ms | <300ms | <500ms | Unmeasured |

### 1.2 WebSocket Latency

| Operation | Target | Description |
|-----------|--------|-------------|
| Message send → ServerAck | <30ms | Time from WS frame sent to ack received |
| Message send → recipient receive | <100ms | End-to-end delivery (same server) |
| Message send → cross-instance delivery | <200ms | Via Redis Pub/Sub |
| Typing indicator propagation | <50ms | Sender types → recipient sees indicator |
| Presence update propagation | <100ms | Online/offline status change |

### 1.3 Throughput Targets

| Metric | Beta Target | GA Target |
|--------|-------------|-----------|
| Concurrent WebSocket connections (single instance) | 5,000 | 10,000 |
| Messages per second (single instance) | 2,000 | 5,000 |
| Messages per second (cluster, 3 nodes) | 5,000 | 15,000 |
| REST API requests/sec | 1,000 | 3,000 |
| Media uploads/sec | 50 | 200 |
| Contact sync batch/sec | 20 | 50 |

### 1.4 Resource Efficiency

| Resource | Current Config | Target (Beta) | Target (GA) |
|----------|---------------|---------------|-------------|
| Backend JVM heap | Default | 512MB-1GB | 1GB-2GB |
| Backend CPU (idle) | Unmeasured | <5% | <5% |
| Backend CPU (1K WS) | Unmeasured | <30% | <20% |
| HikariCP pool (max/min-idle) | 20 / 10 | 30 / 15 | 50 / 20 |
| HikariCP connection timeout | 5s | 5s | 5s |
| Tomcat max threads | 200 | 200 | 400 |
| Tomcat min spare threads | 10 | 20 | 40 |
| Tomcat connection timeout | 20s | 20s | 20s |
| Tomcat keep-alive timeout | 60s | 60s | 60s |
| Hibernate batch size | 25 | 25 | 50 |
| Hibernate JDBC fetch size | 50 | 50 | 100 |
| Redis memory | Unmeasured | <100MB | <500MB |
| Redis connection timeout | 5s | 5s | 5s |
| Mobile app memory | Unmeasured | <150MB | <120MB |
| Mobile app cold start | Unmeasured | <2s | <1.5s |
| APK size | Unmeasured | <30MB | <25MB |

---

## 2. Current Performance Infrastructure

### 2.1 Implemented Optimizations
- **Database indexes** (V14 migration): 12 indexes on frequently queried columns — `messages(conversation_id, created_at)`, `message_delivery_status(message_id)`, `conversations(updated_at)`, `phone_hashes(hash)`, `media_files(uploader_id, type)`, `statuses(user_id, expires_at)`
- **N+1 query fixes**: `@BatchSize(size=50)` on `ConversationJpaEntity.members` and `MessageJpaEntity.deliveryStatuses`
- **Redis connection pooling**: Lettuce pool (min-idle=2, max-active=8)
- **Ktor client connection pooling**: maxConnectionsCount=100, connectTimeout=10s, requestTimeout=30s
- **Nginx optimization**: gzip (text/JSON/JS/CSS), static file caching (30d images, 7d JS/CSS), proxy buffering
- **PostgreSQL tuning**: shared_buffers=256MB, effective_cache_size=1GB, work_mem=16MB, random_page_cost=1.1
- **WebSocket rate limiting**: 50 msg/10s per connection (prevents abuse)
- **Redis Pub/Sub**: Cross-instance WS message delivery

### 2.2 Current Configuration (from application.yml)

| Parameter | Value | Notes |
|-----------|-------|-------|
| `spring.datasource.hikari.maximum-pool-size` | 20 | HikariCP DB connection pool |
| `spring.datasource.hikari.minimum-idle` | 10 | Idle connections kept ready |
| `spring.datasource.hikari.connection-timeout` | 5000ms | Wait for connection |
| `spring.datasource.hikari.idle-timeout` | 600000ms (10min) | Idle connection eviction |
| `spring.jpa.properties.hibernate.jdbc.batch_size` | 25 | Insert/update batching |
| `spring.jpa.properties.hibernate.jdbc.fetch_size` | 50 | JDBC fetch size |
| `spring.jpa.properties.hibernate.in_clause_parameter_padding` | true | Query plan caching |
| `spring.jpa.open-in-view` | false | OSIV anti-pattern disabled |
| `server.tomcat.threads.max` | 200 | Max request handler threads |
| `server.tomcat.threads.min-spare` | 10 | Min idle threads |
| `server.tomcat.connection-timeout` | 20s | TCP connection timeout |
| `server.tomcat.keep-alive-timeout` | 60s | HTTP keep-alive |
| `spring.data.redis.timeout` | 5s | Redis operation timeout |
| `muhabbet.websocket.heartbeat-interval` | 30s | WS ping frequency |
| `muhabbet.websocket.heartbeat-timeout` | 90s | WS disconnect threshold |
| `muhabbet.media.max-image-size` | 10MB (10,485,760) | Image upload limit |
| `muhabbet.media.max-video-size` | 100MB (104,857,600) | Video upload limit |
| `muhabbet.media.thumbnail.width/height` | 320×320 | Thumbnail dimensions |

### 2.3 Monitoring Stack
- **Spring Actuator**: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- **Micrometer + Prometheus**: JVM metrics, HTTP metrics, custom counters
- **SLF4J + Logback**: JSON structured logging in production
- **Sentry**: Mobile crash reporting with breadcrumbs

---

## 3. Test Plan

### 3.1 Micro-Benchmarks

```kotlin
// Example: Message service throughput test
@Test
fun `should process 1000 messages under 2 seconds`() {
    val messages = (1..1000).map { createSendMessageCommand(it) }
    val start = System.nanoTime()
    messages.forEach { messageService.sendMessage(it) }
    val elapsed = Duration.ofNanos(System.nanoTime() - start)
    assertThat(elapsed).isLessThan(Duration.ofSeconds(2))
}
```

**Benchmark targets:**
| Operation | Throughput Target |
|-----------|-------------------|
| `MessageService.sendMessage()` | >500 ops/s |
| `ConversationService.getConversations()` | >1,000 ops/s |
| `AuthService.verifyOtp()` | >200 ops/s |
| `InputSanitizer.sanitize()` | >50,000 ops/s |
| JSON serialization (WsMessage) | >10,000 ops/s |

### 3.2 Load Testing (k6)

```javascript
// k6 script: WebSocket load test
import ws from 'k6/ws';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 100 },   // Ramp to 100 connections
    { duration: '3m', target: 500 },   // Ramp to 500
    { duration: '5m', target: 1000 },  // Sustain 1000
    { duration: '1m', target: 0 },     // Ramp down
  ],
  thresholds: {
    ws_connecting: ['p(95)<500'],       // 95% connect under 500ms
    ws_msgs_sent: ['rate>100'],         // >100 msgs/s total
  },
};

export default function () {
  const token = getAuthToken();
  const url = `wss://staging.muhabbet.app/ws?token=${token}`;

  const res = ws.connect(url, {}, function (socket) {
    socket.on('open', () => {
      socket.setInterval(() => {
        socket.send(JSON.stringify({
          type: 'message.send',
          conversationId: TEST_CONV_ID,
          content: 'load test message',
          contentType: 'TEXT',
          clientMessageId: crypto.randomUUID(),
        }));
      }, 1000); // 1 msg/sec per connection
    });

    socket.on('message', (msg) => {
      const parsed = JSON.parse(msg);
      check(parsed, {
        'received ack': (m) => m.type === 'ack',
      });
    });

    socket.setTimeout(() => socket.close(), 300000); // 5 min
  });
}
```

**Load test scenarios:**

| Scenario | Connections | Duration | Success Criteria |
|----------|------------|----------|------------------|
| Baseline | 100 | 5 min | P95 < 100ms, 0 errors |
| Normal load | 500 | 10 min | P95 < 200ms, <0.1% errors |
| Peak load | 1,000 | 10 min | P95 < 500ms, <1% errors |
| Stress test | 2,000 | 5 min | Graceful degradation, no crashes |
| Soak test | 500 | 1 hour | No memory leaks, stable latency |
| Spike test | 0→1000→0 | 2 min ramp | Recovery < 30s |

### 3.3 REST API Load Testing (k6)

| Endpoint | VUs | Duration | RPS Target | P95 Target |
|----------|-----|----------|------------|------------|
| `GET /conversations` | 100 | 5m | 500 | <200ms |
| `GET /conversations/{id}/messages` | 100 | 5m | 500 | <200ms |
| `POST /auth/otp/request` | 50 | 5m | 100 | <300ms |
| `POST /media/upload` (1MB) | 20 | 5m | 50 | <2s |
| `POST /contacts/sync` (500) | 20 | 5m | 50 | <500ms |
| Mixed workload | 200 | 15m | 1000 | <300ms |

### 3.4 Database Performance

**Query performance targets:**

| Query | Max Execution Time | Index Used |
|-------|--------------------|------------|
| Get conversations for user | <10ms | `conversations(updated_at)` |
| Get messages for conversation (page) | <15ms | `messages(conversation_id, created_at)` |
| Get delivery status for message | <5ms | `message_delivery_status(message_id)` |
| Phone hash lookup (batch 500) | <50ms | `phone_hashes(hash)` |
| Search messages (LIKE) | <100ms | Full-text index (future) |

**Monitoring queries:**
```sql
-- Slow query detection
SELECT query, mean_exec_time, calls, total_exec_time
FROM pg_stat_statements
WHERE mean_exec_time > 100 -- ms
ORDER BY mean_exec_time DESC;

-- Table bloat
SELECT relname, n_dead_tup, n_live_tup,
       round(n_dead_tup::numeric / NULLIF(n_live_tup, 0) * 100, 2) AS dead_pct
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY n_dead_tup DESC;

-- Index usage
SELECT indexrelname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE idx_scan = 0 AND indexrelname NOT LIKE '%pkey';
```

---

## 4. Mobile Performance

### 4.1 Profiling Checklist

| Area | Tool | Target |
|------|------|--------|
| App startup (cold) | Android Profiler / Instruments | <2s |
| App startup (warm) | Android Profiler | <500ms |
| Memory (idle) | LeakCanary / Instruments | <80MB |
| Memory (chat, 1000 msgs) | Profiler | <150MB |
| Frame rate (scrolling) | GPU Profiler | 60fps, <16ms/frame |
| Battery (background, 1hr) | Battery Historian | <2% drain |
| Network (chat, 1 min) | Network Profiler | <50KB (idle), <500KB (active) |
| APK/IPA size | Build output | <30MB / <50MB |

### 4.2 Memory Leak Detection

```kotlin
// LeakCanary configuration for debug builds
dependencies {
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

Known areas to watch:
- WebSocket session retention after screen rotation
- Image cache (Coil) growth during media-heavy chats
- Composable scope leaks in LaunchedEffect/DisposableEffect
- Audio recorder resource cleanup on back navigation

---

## 5. Efficiency Metrics

### 5.1 Network Efficiency

| Metric | Target | Measurement |
|--------|--------|-------------|
| WS frame overhead | <50 bytes | JSON envelope size |
| Message payload compression | N/A (gzip at nginx) | nginx handles |
| Image compression ratio | 5:1 min | Client-side JPEG 80% quality |
| Thumbnail size | <20KB | 320x320 JPEG |
| API response size (conversation list, 20 items) | <10KB | Gzip compressed |
| Heartbeat interval | 30s | Configurable |
| Reconnect backoff | Exponential 1s→30s | Max 5 retries |

### 5.2 Storage Efficiency

| Resource | Size Limit | Cleanup Policy |
|----------|-----------|---------------|
| PostgreSQL total | 50GB | Archive old messages yearly |
| Redis memory | 512MB | TTL-based eviction |
| MinIO media | 500GB | User-initiated cleanup, backup expiry |
| Message backups | 90 day retention | Auto-expire `expires_at` |
| Statuses | 24h | Scheduled cleanup |
| OTP requests | 5 min TTL (300s) | Auto-expire |
| Refresh tokens | 30 day | Rotation on use |

---

## 6. Action Items

### Immediate (P0)
- [ ] Install k6 and create baseline load test scripts
- [ ] Enable `pg_stat_statements` in PostgreSQL
- [ ] Add Micrometer custom timers to critical service methods
- [ ] Measure and record baseline API latencies
- [ ] Profile mobile app cold start time

### Short-term (P1)
- [ ] Set up Grafana dashboards for API latency, WS connections, DB queries
- [ ] Run first WebSocket load test at 500 concurrent connections
- [ ] Profile memory usage under sustained chat load
- [ ] Add database query execution time assertions to integration tests
- [ ] Implement response time budgets in CI (fail build if P95 > threshold)

### Long-term (P2)
- [ ] Full-text search index for message content (PostgreSQL tsvector or Elasticsearch)
- [ ] CDN integration for media delivery
- [ ] WebSocket binary frames (Protocol Buffers) to reduce payload size
- [ ] Client-side message pagination with local cache (SQLDelight)
- [ ] Background message prefetch for recently active conversations
