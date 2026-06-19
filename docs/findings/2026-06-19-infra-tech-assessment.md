# Infrastructure Adoption Assessment — Muhabbet (2026-06-19)

**Context:** Owner asked whether Muhabbet needs RabbitMQ, Kafka, Redis (more), Hazelcast, or
Kubernetes. This is the grounded answer. Every architectural claim below was verified against
code during the review.

**Verdict in one line:** With one exception (a Redis *correctness* fix — now applied, see
[§Applied fixes](#applied-fixes-2026-06-19)), **none of these technologies should be adopted now.**
Muhabbet is a single-host, pre-launch, modular-monolith MVP. Adding RabbitMQ, Kafka, Hazelcast, or
Kubernetes would violate the project's own YAGNI/KISS principles and burn the scarcest resource
(one engineer's time) on problems it does not yet have.

## Executive summary

| Technology | Solves | Problem now? | Recommendation | Trigger to adopt |
|---|---|---|---|---|
| **RabbitMQ** | Durable async jobs, retries/DLQ, work distribution | **No** — only async need is FCM push (now `@Async`) | **NOT NEEDED** | Durable job backlog that outgrows Postgres-as-queue *and* needs competing consumers across instances |
| **Apache Kafka** | High-throughput replayable event log, analytics streams | **No** — pre-launch; analytics is a daily Postgres batch | **NOT NEEDED** | Sustained ≫100k events/s or event-sourcing/replay platform |
| **Redis** | Already: presence (TTL), WS Pub/Sub fan-out | **Partially mis-wired** (listener was never registered — fixed) | **KEEP + FIX** (done) | Streams: when ≥2 instances need at-least-once cross-instance WS delivery |
| **Hazelcast** | Distributed in-memory grid / locks / shared state | **No** — Redis already fills this role | **NOT NEEDED** | Practically never; Redis is the lighter substitute |
| **Kubernetes** | Multi-node orchestration, self-healing, autoscaling | **No** — one host, one container each, Compose | **ADOPT LATER** | Sustained load needs ≥2–3 HA app nodes, or a platform team exists |

## How this was grounded (evidence)

- **Single backend instance:** `infra/docker-compose.prod.yml` — one `backend:` service, `memory: 1G`,
  `-Xmx768m`, no `replicas`. Postgres/Redis/MinIO/nginx all singletons on the shared 8 GB host.
- **Redis = Pub/Sub WS fan-out:** `RedisMessageBroadcaster.kt` (`convertAndSend("ws:broadcast:{id}")`).
- **Redis = presence TTL:** `RedisPresenceAdapter.kt` (`presence:$userId` with expiry).
- **WS rate-limiting is IN-PROCESS, not Redis:** `WebSocketRateLimiter.kt` uses `ConcurrentHashMap` +
  `AtomicInteger`. (The CLAUDE.md summary calling this "Redis" is **wrong** — corrected.)
- **OTP & refresh tokens live in Postgres,** not Redis (`OtpRequestJpaEntity`, `RefreshTokenJpaEntity`).
- **`@EnableAsync` + `@EnableScheduling`** already on `MuhabbetApplication.kt`.

## Two real bugs surfaced during the review

1. **Redis Pub/Sub listener was never registered.** `RedisBroadcastListener.handleMessage()` existed
   but no `RedisMessageListenerContainer` bean subscribed it — so cross-instance fan-out silently
   dropped. The "horizontal WS scaling — DONE" claim was **false**. → **FIXED** (see below).
2. **FCM push ran synchronously on the broadcast hot path.** `FirebaseMessaging.send()` was called
   inside the per-recipient broadcast loop, so a slow FCM round-trip stalled message delivery.
   → **FIXED** (now `@Async`).

## Applied fixes (2026-06-19)

- **Async push:** `AsyncConfig.kt` adds a bounded `pushExecutor` (core 2 / max 8 / queue 500);
  `FcmPushNotificationAdapter.sendPush` is now `@Async("pushExecutor")`. Push no longer blocks
  message fan-out. *Compile-verified; full delivery behaviour needs a device/prod check.*
- **Redis listener:** `RedisConfig.kt` registers a `RedisMessageListenerContainer` subscribing
  `ws:broadcast:*` → `RedisBroadcastListener.handleMessage` (String serializer to match the
  publisher). Cross-instance WS transport is now actually wired. *Compile-verified; true
  multi-instance correctness is unverifiable on this single-host CI (no Docker, no 2nd instance).*

### Known multi-instance follow-ups (NOT done — single-instance prod, so latent)

- Publish-side presence check uses **local** `sessionManager.isOnline`. On a 2nd instance a user who
  is online elsewhere is treated as offline → they may get **both** a Redis-routed WS message **and**
  a push. Correct fix = consult Redis presence (`RedisPresenceAdapter`) before suppressing push.
  Defer until multi-instance is real (YAGNI).
- Move WS rate-limiting to Redis only when scaling out (in-process counter is per-instance).

## What to actually invest in next (ranked by value-for-effort)

1. ~~Make FCM push asynchronous~~ — **DONE** (2026-06-19).
2. ~~Fix / honestly downgrade the Redis Pub/Sub claim~~ — **DONE** (listener wired; docs corrected).
3. **Postgres-as-queue** (transactional outbox + `SKIP LOCKED`) — *only when* a durable async need
   appears. This is the RabbitMQ substitute; scales far on one host.
4. **Lean into the prescribed `ApplicationEvent` pattern** for intra-module decoupling (currently used
   in exactly one service). Free architectural hygiene; the in-process "message bus."
5. **Redis read-through caching** — *after* load tests identify a hot query. DB is already tuned; don't
   pre-optimize.
6. **Redis Streams, then K8s** — defer to genuine multi-instance scale, in that order.

**Bottom line:** The project's own principles already answer four of five: **no** to RabbitMQ, Kafka,
Hazelcast, and K8s-now. Redis stays and was *fixed*, not supplemented.

**Sources:** oneuptime — Redis Streams vs Pub/Sub (2026); Imperialis — Message Queue Selection Guide
2026; DEV — Kafka/RabbitMQ/Postgres comparison 2026; Tech Insider — Kafka vs RabbitMQ 2026.
