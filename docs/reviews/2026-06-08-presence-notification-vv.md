# V&V Review — `presence` & `notification` Adapters (Backend)

> **Date:** 2026-06-08 · **Reviewer:** automated SE loop (Task 3 — V&V of least-reviewed feature)
> **Scope:** the presence + push-notification adapters that live inside the `messaging` module plus
> their cross-module touch points:
> - Presence: `messaging/adapter/out/external/RedisPresenceAdapter.kt`,
>   `messaging/domain/port/out/PresencePort.kt`, the online/offline/typing/last-seen flow in
>   `messaging/adapter/in/websocket/ChatWebSocketHandler.kt`, and the REST presence-visibility gate in
>   `auth/adapter/in/web/UserController.kt`.
> - Notification: `messaging/adapter/out/external/FcmPushNotificationAdapter.kt`,
>   `NoOpPushNotificationAdapter.kt`, `messaging/domain/port/out/PushNotificationPort.kt`, the push
>   send paths in `RedisMessageBroadcaster.kt` / `NoOpMessageBroadcaster.kt`
>   (`WebSocketMessageBroadcaster`), and push-token registration
>   (`auth/.../RegisterPushTokenUseCase.kt` → `AuthService.registerPushToken`).
> **Why these:** `docs/loop-ledger.md` lists both as **never reviewed** — `RedisPresenceAdapter` and
> `FcmPushNotificationAdapter` had **zero** dedicated tests, while presence is privacy-sensitive
> (last-seen is PII under KVKK) and push touches every offline delivery.

## Method
- Static read of all 6 in-scope source files + their two call sites (`UserController`,
  `ChatWebSocketHandler`) and both broadcasters.
- Assessed against `CLAUDE.md` SE principles (SOLID / hexagonal / DRY / KISS / no-hardcoded-strings /
  no-`!!`) and the KVKK / data-protection checklist in `docs/qa/02-security.md` §4.
- Traced the presence flow (WS connect/close → Redis TTL key → REST read with visibility gate) and the
  push flow (offline recipient → device push tokens → `PushNotificationPort` → FCM / NoOp).

## Summary

| # | Severity | Title | Status |
|---|----------|-------|--------|
| A | **Medium** (KVKK / privacy) | Realtime WS presence + typing broadcasts ignore `onlineStatusVisibility` — online/offline + `lastSeenAt` pushed to all contacts regardless of the user's "everyone/contacts/nobody" choice, while the REST path honors it | **FIXED 2026-06-08** (shared `PresenceVisibilityPolicy` now gates BOTH presence + typing on the WS path) + 12 tests |
| B | **Medium** (correctness / resource) | FCM `UNREGISTERED` / `INVALID_ARGUMENT` (dead token) is only logged — the stale push token is never removed, so every future offline message re-attempts a doomed send | **FIXED 2026-06-08** (dead-token cleanup via `PushTokenInvalidationPort` out-port) + 11 tests |
| C | **Medium** (no-hardcoded-strings + wrong HTTP status) | `registerPushToken` threw `BusinessException(AUTH_UNAUTHORIZED, "Cihaz bulunamadı")` — inline Turkish message string (CLAUDE.md violation) **and** a 401 for what is a 404 (a device the user doesn't own) | **FIXED 2026-06-08** + 2 tests |
| D | Low (correctness / DRY) | The `@Primary` prod push path (`RedisMessageBroadcaster`) sent raw `message.content.take(100)` as the body for **media** messages (image/voice/doc → empty or a storage key in the notification), while the NoOp broadcaster formatted a placeholder — duplicated, divergent body logic | **FIXED 2026-06-08** (shared `PushNotificationContent` helper) + 5 tests |
| E | Low (dead code / privacy hygiene) | `RedisPresenceAdapter` wrote a `lastseen:{id}` Redis key on every online/offline with **no TTL** and **no reader anywhere** — an unbounded PII timestamp that never expires and is never used (DB `last_seen_at` is the real source of truth) | **FIXED 2026-06-08** (removed) + 8 tests |
| F | Info (test gap) | `RedisPresenceAdapter`, the push-body formatting, and `registerPushToken` had **zero** unit tests | **FIXED** — 15 tests added |

---

## Finding A — WS presence/typing bypasses visibility setting  *(FIXED 2026-06-08)*

**Location:** `ChatWebSocketHandler.broadcastPresence` (online on connect, offline on
close/transport-error) and `handleTypingIndicator`.

The REST path is careful: `UserController.resolvePresenceVisibility` reads the target user's
`onlineStatusVisibility` (`everyone` / `contacts` / `nobody`) and returns `isOnline=false,
lastSeen=null` when the caller isn't entitled. But the **realtime** path does not:

```kotlin
private fun broadcastPresence(userId: UUID, status: PresenceStatus) {
    val lastSeenAt = if (status == PresenceStatus.OFFLINE) System.currentTimeMillis() else null
    val presenceUpdate = WsMessage.PresenceUpdate(userId = userId.toString(), status = status, lastSeenAt = lastSeenAt)
    val json = wsJson.encodeToString<WsMessage>(presenceUpdate)
    val contactUserIds = conversationRepository.findAllContactUserIds(userId)   // ← all contacts
    contactUserIds.forEach { contactId -> if (sessionManager.isOnline(contactId)) sessionManager.sendToUser(contactId, json) }
}
```

So a user who set last-seen / online to **"nobody"** still streams their online/offline transitions
(and an offline `lastSeenAt` epoch) to every conversation partner in real time. `handleTypingIndicator`
is likewise unconditional. This is a **KVKK data-minimization** gap (`docs/qa/02-security.md` §4.1):
the privacy control is only half-enforced — correct on pull, leaky on push.

**Fix (2026-06-08).** The policy was extracted into one shared object —
`auth/domain/model/PresenceVisibilityPolicy.kt` (a framework-agnostic domain `object`, no Spring) — and
now gates **both** access paths so presence can never leak on one while being hidden on the other:

- **REST (`UserController.resolvePresenceVisibility`)** was refactored from its inline `when` to
  `PresenceVisibilityPolicy.isVisibleTo(...)`. Behavior is byte-identical (the existing
  `UserProfilePrivacyIntegrationTest` still asserts `nobody → isOnline=false, lastSeen=null`); contacts
  are still fetched lazily — only the `contacts` branch needs them, so `everyone`/`nobody` skip the query.
- **WS presence (`broadcastPresence`)** now resolves the eligible recipient set via
  `PresenceVisibilityPolicy.eligibleRecipients(visibility, candidates=contacts, contactIds=contacts)`
  before building/sending the `PresenceUpdate`. When the set is empty it returns early — so for
  **`nobody`** *nothing* is emitted (no online, no offline, **no `lastSeenAt`** epoch).
- **WS typing (`handleTypingIndicator`)** applies the **same** gate (see semantics below).

**Chosen semantics (presence vs typing):**

| visibility | presence (online/offline + lastSeen) | typing |
|---|---|---|
| `everyone` | all contacts | all co-members |
| `contacts` | all contacts (candidate set *is* the contact set) | all co-members (co-members are contacts) |
| `nobody`   | **suppressed entirely** — no online, no offline, no lastSeen | **suppressed entirely** |
| unknown / missing user | **fail closed** (suppressed) | **fail closed** (suppressed) |

Typing is treated as presence-ish and gets the identical gate: a user who hides their online status to
`nobody` does not leak a real-time "typing…" either. The product call on `nobody` is **suppress online
entirely** (not just blank the `lastSeenAt`) — consistent with the REST path, which already returns
`isOnline=false` for `nobody`, so a recipient never infers liveness from either channel.

**N+1 avoidance.** Presence: exactly **one** `userRepository.findById` (the visibility setting) **+** the
one `conversationRepository.findAllContactUserIds` the path already issued — the contact set doubles as
*both* the candidate recipient list **and** the "contacts" rule input, so no extra query. Typing: the
already-fetched conversation member list is reused as the candidate set, and because co-members of a
shared conversation *are* by definition the sender's contacts, that same list also serves as the
`contactIds` rule input — so typing adds **only** the single `findById` (visibility) and **zero**
extra membership/contact queries.

**Hexagonal note.** `PresenceVisibilityPolicy` lives in `auth.domain.model`; the messaging handler
importing it is allowed (the ArchUnit boundary rule only forbids `messaging → auth.domain.service`, and
the handler already depends on `auth`'s `UserRepository` out-port). No new cross-module JPA imports.

**Tests (12, all green):** `ChatWebSocketHandlerTest$PresenceVisibility` (7) — `everyone`/`contacts` →
all contacts on **both** online (connect) and offline (close); `nobody` → none on online **and** offline
(so `lastSeenAt` never ships); unknown-value and missing-user both fail closed.
`ChatWebSocketHandlerTest$TypingVisibility` (5) — `everyone`/`contacts` → co-members; `nobody`, unknown,
and missing-user all suppressed.

## Finding B — Stale FCM tokens are never cleaned up  *(FIXED 2026-06-08)*

**Location:** `FcmPushNotificationAdapter.sendPush`.

```kotlin
try {
    val messageId = FirebaseMessaging.getInstance().send(message)
} catch (e: Exception) {
    log.warn("FCM send failed: token={}..., error={}", pushToken.take(10), e.message)   // ← only logs
}
```

When FCM rejects a token with the terminal messaging-error-codes `UNREGISTERED` (the app was
uninstalled / token rotated) or `INVALID_ARGUMENT` (malformed token), the right action is to **delete
that token**. Here it is swallowed with a WARN, so the dead token survives in `devices.push_token` and
is re-selected on **every** subsequent offline message
(`deviceRepository.findByUserId(...).filter { !it.pushToken.isNullOrBlank() }`) — a permanent stream of
guaranteed-to-fail sends per dead device. Correctness + wasted work; not a data leak.

**Fix (2026-06-08).** The adapter now catches `FirebaseMessagingException`, reads
`getMessagingErrorCode()`, and on a **terminal** code (`UNREGISTERED`, `INVALID_ARGUMENT`,
`SENDER_ID_MISMATCH`) removes the dead token so it is never re-selected:

```kotlin
val deadTokenCode = (e as? FirebaseMessagingException)?.messagingErrorCode
    ?.takeIf { it in DEAD_TOKEN_CODES }
if (deadTokenCode != null) {
    pushTokenInvalidationPort.invalidate(pushToken)   // remove the dead token
} else {
    log.warn("FCM send failed ...")                   // transient — keep the token
}
```

**Cross-module mechanism: a domain out-port** (chosen over an `ApplicationEvent`). The push adapter
lives in `messaging`; the token store is `auth`'s `DeviceRepository`. A new messaging out-port
`messaging/domain/port/out/PushTokenInvalidationPort.invalidate(token)` is implemented by
`DeviceRepositoryPushTokenInvalidationAdapter`, which delegates to a new
`DeviceRepository.clearPushToken(token)` (JPA: `findByPushToken` → null the column → `saveAll`). This
follows the existing cross-module pattern — the broadcasters already depend on `auth`'s
`DeviceRepository` **public out-port** (never a JPA import), and the V&V `ArchUnit` rule only forbids
`messaging → auth.domain.service` (the port is in `auth.domain.port.out`, allowed). A port (synchronous,
return-typed, directly verifiable) was preferred over an event because the decision is a simple,
immediate "this exact token is dead → delete it" with no other listeners — KISS/YAGNI; an event would
add async indirection for no benefit.

> Note: the catch still treats **only** the three terminal codes as dead-token. Transient/network
> errors (`UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED`) and generic non-Firebase exceptions are logged
> and the token is **kept** — those tokens are still valid and the message is already persisted
> server-side for sync. The invalidation call is itself wrapped in `runCatching` so a token-store
> failure can never break the (already-persisted) send path.

**Tests (11):** `FcmPushNotificationAdapterTest` (8) — the live `FirebaseMessaging.send` is replaced
by an overridable `sendToFirebase` seam and `FirebaseMessagingException` (package-private ctor) is
MockK-mocked to report a given `MessagingErrorCode`; asserts invalidate-on-(UNREGISTERED /
INVALID_ARGUMENT / SENDER_ID_MISMATCH), no-invalidate-on-(success / UNAVAILABLE / INTERNAL /
generic-exception), and that an invalidation failure is swallowed (no propagation).
`DeviceRepositoryPushTokenInvalidationAdapterTest` (1) — delegates to `clearPushToken`.
`DevicePersistenceAdapterTest` (2) — `clearPushToken` nulls + saves every device holding the token,
and is a no-op when none do.
**Not live-verifiable here:** the actual Firebase round-trip (no credentials on this host) — only the
adapter's error-code classification + invalidation wiring are unit-tested; the real FCM rejection that
produces a `FirebaseMessagingException` is not exercised.

## Finding C — Hardcoded message + wrong status on `registerPushToken`  *(FIXED)*

**Location:** `AuthService.registerPushToken`.

**Before:**
```kotlin
val device = devices.find { it.id == deviceId }
    ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Cihaz bulunamadı")
```

Two issues: (1) an **inline Turkish message string**, which CLAUDE.md forbids ("Use `ErrorCode` enum
for all business errors. Never throw with inline Turkish/English message strings"); (2) the wrong
semantic code — a device the authenticated user doesn't own is a **404 Not Found**, not a 401
`AUTH_UNAUTHORIZED`. There is already a `DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Cihaz bulunamadı")`
ErrorCode carrying the exact same Turkish copy.

**Fix:**
```kotlin
val device = devices.find { it.id == deviceId }
    ?: throw BusinessException(ErrorCode.DEVICE_NOT_FOUND)
```

The user-scoping (look up the device only among *this user's* devices) is good and was preserved — it
prevents registering a push token onto another user's device id (an IDOR the existing code already
closed). **Tests:** `AuthServiceTest` — push token is persisted on the matching device; `DEVICE_NOT_FOUND`
thrown and **no save** when the device isn't the user's.

## Finding D — Media push body + DRY divergence between broadcasters  *(FIXED)*

`RedisMessageBroadcaster` is `@Primary` (the production path). Its offline-push body was:

```kotlin
body = message.content.take(100)
```

For an IMAGE/VOICE/VIDEO/DOCUMENT message, `content` is empty or a storage reference, so the push
showed a blank or garbled body. The NoOp broadcaster (`WebSocketMessageBroadcaster`) already mapped
content types to placeholders ("📷 Fotoğraf", "🎙️ Sesli mesaj", …) — so the two implementations of the
same port had **divergent, duplicated** body logic (DRY violation; the better behavior was on the
non-prod path).

**Fix:** extracted one source of truth —
`messaging/adapter/out/external/PushNotificationContent.bodyFor(message)` — and routed **both**
broadcasters through it. The prod path now shows the media placeholder; the duplication is gone (the
unused domain `ContentType` import was dropped from `NoOpMessageBroadcaster`). **Tests:**
`PushNotificationContentTest` — text passthrough, media never leaks `content` (regression on the
storage-key leak), per-type placeholders, 100-char truncation.

> The Redis path still hardcodes `title = "Yeni mesaj"` instead of the sender's display name (the NoOp
> path resolves the sender). Aligning the title would require threading `UserRepository` into
> `RedisMessageBroadcaster`; out of scope for this contained fix — noted, not changed (and the larger
> title/sender-name + `conversationType` consolidation belongs with the Finding-A handler refactor).

## Finding E — Write-only, TTL-less `lastseen:` Redis key  *(FIXED)*

`RedisPresenceAdapter.setOnline`/`setOffline` each wrote `lastseen:{userId}` =
`System.currentTimeMillis()` **with no expiry**. A repo-wide search confirmed **no reader** anywhere in
`src/main` or `src/test` — the durable last-seen is `users.last_seen_at` in PostgreSQL, persisted by
`ChatWebSocketHandler` on disconnect and read by `UserController`. So these were pure dead writes that
also accumulated an **un-expiring PII timestamp per user** in Redis (a small KVKK-hygiene wart: PII
stored with no retention bound and no purpose).

**Fix:** removed both `lastseen:` writes and the `lastSeenKey` helper. `setOnline` now writes only the
single TTL `presence:{id}` key (the whole online signal — it auto-expires, so liveness stays correct
even if a disconnect is never observed); `setOffline` just deletes it. A comment points to the DB as
the source of truth. **Tests:** `RedisPresenceAdapterTest` (8) — TTL set on online, **no** `lastseen:`
write on online, **nothing** written on offline (delete only), `isOnline` true/false, empty-input
short-circuit (no Redis call), `multiGet` filtering, and the `multiGet == null` guard.

---

## What was good (no action)
- **Hexagonal boundaries are clean.** `PresencePort` and `PushNotificationPort` are minimal out-ports;
  adapters depend only on infra (`StringRedisTemplate`, FCM). The domain never imports them. Good DIP.
- **OCP via `@ConditionalOnProperty`.** FCM vs NoOp push (and Redis vs WS broadcaster via `@Primary`)
  swap by config without touching callers — the documented Spring Boot 4 bean-ambiguity gotcha is
  handled with `@Primary`. *(2026-06-08: the FCM/NoOp split was tightened — the two are mutually
  exclusive on `muhabbet.fcm.enabled`; a non-boolean value loads neither and the context fails fast
  rather than silently using NoOp; and `NoOpPushNotificationAdapter` now logs a startup WARN so
  "push is inert" can never be mistaken for working push.)*
- **`getOnlineUserIds` is correct and N+1-free** — single `multiGet`, order-preserving `zip`, with an
  empty-input short-circuit and a `multiGet == null` guard. No `!!` anywhere in the presence adapter.
- **The `!!` on `device.pushToken!!`** in both broadcasters is guarded by the preceding
  `.filter { !it.pushToken.isNullOrBlank() }`, so it cannot NPE (acceptable, though a `mapNotNull`
  would read better).
- **Push failures don't break delivery.** Both broadcasters wrap the push block in try/catch, so an
  FCM/Redis outage degrades to "WS + DB only" rather than throwing into the send path — correct
  failure mode (the message is already persisted server-side for later sync).
- **Presence REST path honors KVKK visibility** — and as of 2026-06-08 (Finding A FIXED) so does the
  realtime WS path, via the shared `PresenceVisibilityPolicy`.

## Follow-ups for `TODO.md`
- [x] **P2 (KVKK):** honor `onlineStatusVisibility` on the WS presence/typing path (Finding A) — **DONE 2026-06-08.**
- [x] **Finding B** (clean up stale FCM tokens on terminal error codes) — **DONE 2026-06-08.**
- [x] **Finding C** (hardcoded string + wrong status on `registerPushToken`) — **DONE 2026-06-08.**
- [x] **Finding D** (media push body + DRY) — **DONE 2026-06-08.**
- [x] **Finding E** (dead `lastseen:` Redis writes) — **DONE 2026-06-08.**

## Verification
- `./gradlew :backend:test` — see the run record appended below. The only failures are the known
  Testcontainers `*IntegrationTest` errors ("Could not find a valid Docker environment") on this
  no-Docker host — environmental, not regressions.
- New tests: `RedisPresenceAdapterTest` (8), `PushNotificationContentTest` (5), `AuthServiceTest`
  registerPushToken (2) = **15** added.
- **Finding B (2026-06-08):** `./gradlew :backend:test` → **443 tests, 6 failed** — all 6 are the known
  no-Docker `*IntegrationTest` failures ("Could not find a valid Docker environment"), not regressions.
  New for Finding B: `FcmPushNotificationAdapterTest` (8), `DeviceRepositoryPushTokenInvalidationAdapterTest`
  (1), `DevicePersistenceAdapterTest` (2) = **11** added.
- **Finding A (2026-06-08):** `./gradlew :backend:test` → **463 tests, 6 failed** — the same 6 known
  no-Docker `*IntegrationTest` failures (`AuthControllerIntegrationTest`, `UserProfilePrivacyIntegrationTest`,
  `MediaIdorIntegrationTest`, `MessageIdorIntegrationTest`, `SearchIdorIntegrationTest`,
  `ActuatorLockdownIntegrationTest`), not regressions. New for Finding A:
  `ChatWebSocketHandlerTest$PresenceVisibility` (7) + `ChatWebSocketHandlerTest$TypingVisibility` (5)
  = **12** added; shared `PresenceVisibilityPolicy` extracted and routed through both the REST
  (`UserController`) and WS (`ChatWebSocketHandler`) paths.
