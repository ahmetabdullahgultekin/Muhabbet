# V&V Review — Cross-Cutting Security Layer (Backend)

> **Date:** 2026-06-08 · **Reviewer:** automated SE loop (loop2 — V&V of the shared security layer)
> **Scope:** `backend/.../shared/security/**` (`JwtProvider`, `JwtAuthFilter`, `SecurityConfig`,
> `InputSanitizer`, `RateLimitFilter`, `WebSocketRateLimiter`, `SsrfGuard`, `AuthenticatedUser`) +
> `shared/config/WebSocketConfig` + the WS handshake/per-message authorization in
> `messaging/.../websocket/ChatWebSocketHandler` + `GlobalExceptionHandler` + `LinkPreviewController`
> (SSRF wiring).
> **Why this scope:** the cross-cutting auth/transport layer is the highest-blast-radius surface and
> had **no dedicated module-level V&V** (only the PR #55 IDOR/secret-guard pass + the QA checklist).

## Method
- Static read of all 9 `shared/security` source files + their tests, the WS handler + session manager,
  `SecurityConfig`, `WebSocketConfig`, `GlobalExceptionHandler`, `LinkPreviewController`, and
  `MessageService` (the WS in-port).
- Cross-checked against the prior fixed findings (`docs/findings/2026-06-07-security-fixes.md` — the
  `getMessageInfo`/`markViewOnceViewed` IDORs and the JWT dev-secret boot guard are **already DONE** and
  were **not** re-reviewed) and the OWASP-flavoured checklist in `docs/qa/02-security.md`.
- Reviewed: token validation completeness (exp/iss/signature/alg-confusion/claims), filter
  ordering/bypass, header correctness, CSRF posture, sanitizer coverage, WS auth on connect + per-message
  authorization, rate-limit correctness, info leaks in errors/logs.
- Traced data flow for every WS message type from the wire → handler → service → repository.

## Summary

| # | Severity | Title | Status |
|---|----------|-------|--------|
| A | **Medium** (security / spoof) | WS read-receipt spoof: `updateStatus` broadcast a `StatusUpdate` (DELIVERED/READ) to a message's real sender with **no membership check** — a non-member who knew/guessed a `messageId` could forge a read receipt | **FIXED 2026-06-08** + 2 unit tests |
| B | **Medium** (security / spoof) | WS typing-indicator spoof: `handleTypingIndicator` broadcast "typing…" to all members of a **client-supplied** `conversationId` with **no membership check** | **FIXED 2026-06-08** + 1 unit test |
| C | Low (info leak) | WS `ServerAck` on send failure echoed the **raw exception message** (`e.message`) back to the client — can leak DB/driver/internal text | **FIXED 2026-06-08** — stable error code only; 2 unit tests |
| D | Info (test gap) | JWT **validation** had no dedicated unit test (only the boot secret-guard + a WS-level expiry test) — issuer mismatch, tampered signature, `alg:none`/alg-confusion, foreign-key admin forgery were unverified | **FIXED** — `JwtProviderTest` (9 tests) |
| E | Medium (control not deployed) | `InputSanitizer` has **zero production call sites** — `docs/qa/02-security.md` lists it as a *Deployed* XSS/injection control, but it is never invoked | **FIXED 2026-06-08** (loop4) — `normalizeText` wired at the service boundary for stored free-text (control/zero-width/RTL-override strip + trim + clamp); HTML-escape deliberately **deferred to output**; +13 tests |
| F | Low (hardening) | WS `handleAckMessage` trusts a client `conversationId` not cross-checked against the acked `messageId` (self-scoped write → low risk) | **Documented** (TODO.md P2) |

**No issue found** (verified clean) in: JWT signature/alg-confusion robustness (JJWT 0.13.0
`verifyWith(SecretKey)`), the 401-not-403 entry point, the security headers, CORS, the actuator
lockdown, the SSRF guard wiring, the `RateLimitFilter`, and the `WebSocketRateLimiter` window logic —
see **"What was good"** below for the evidence.

---

## Finding A — WS read-receipt spoof via `updateStatus`  *(Medium — FIXED)*

**Location:** `MessageService.updateStatus` (`messaging/domain/service/MessageService.kt`), reached
from `ChatWebSocketHandler.handleAckMessage` for `message.ack` frames.

**Before:**
```kotlin
override fun updateStatus(messageId: UUID, userId: UUID, status: DeliveryStatus) {
    messageRepository.updateDeliveryStatus(messageId, userId, status)
    val message = messageRepository.findById(messageId) ?: return
    messageBroadcaster.broadcastStatusUpdate(messageId, message.conversationId, userId, message.senderId, status)
}
```

`userId` is the authenticated session user, but `messageId` is **fully client-supplied** in the
`AckMessage` frame and was **never checked for conversation membership**. The DB write
(`updateDeliveryStatus`) is keyed on `(messageId, userId)` and no-ops for a non-recipient (no row), so
the **persistence** side is bounded. But the broadcast fired **unconditionally** after `findById`:
a user who is not a member of the conversation but knows/guesses a `messageId` could emit a
`message.status` (DELIVERED/READ) `StatusUpdate` to the **real sender** — a forged read receipt
(privacy/trust spoof; the sender's UI shows "read" when it wasn't). No content is leaked, hence
**Medium**, not High.

**Fix:** authorize membership **before** any write or broadcast:
```kotlin
val message = messageRepository.findById(messageId) ?: return
conversationRepository.findMember(message.conversationId, userId) ?: return   // ← spoof guard
messageRepository.updateDeliveryStatus(messageId, userId, status)
messageBroadcaster.broadcastStatusUpdate(messageId, message.conversationId, userId, message.senderId, status)
```
Mirrors the existing `getMessageInfo`/`markViewOnceViewed` IDOR guards (PR #55) — same `findMember`
authorization primitive.

**Tests:**
- `MessagingServiceTest.updateStatus should not write or broadcast when user is not a conversation member`
- `DeliveryStatusTest.updateStatus ignores a non-member (read-receipt spoof guard)`
- Existing positive tests updated to stub `findMember` (member case still writes + broadcasts).

---

## Finding B — WS typing-indicator spoof  *(Medium — FIXED)*

**Location:** `ChatWebSocketHandler.handleTypingIndicator`.

**Before:** the handler loaded the members of the **client-supplied** `conversationId` and broadcast a
`PresenceUpdate(TYPING)` to all of them, **without checking that the sender is a member**. A user who
knows/guesses a `conversationId` could push a fake "typing…" indicator to every member of a
conversation they do not belong to (privacy/spoof + a cheap fan-out amplification).

**Fix:** reuse the already-fetched member list (no extra query) to gate the broadcast:
```kotlin
val members = conversationRepository.findMembersByConversationId(conversationId)
if (members.none { it.userId == userId }) {
    log.debug("Ignoring typing indicator from non-member {} for conv {}", userId, conversationId)
    return
}
```

**Test:** `ChatWebSocketHandlerTest.should not broadcast typing indicator when sender is not a
conversation member` (member list excludes the sender → `verify(exactly = 0) { sendToUser(...) }`).

---

## Finding C — Raw exception text leaked in WS `ServerAck`  *(Low — FIXED)*

**Location:** `ChatWebSocketHandler.handleSendMessage` catch block.

**Before:** `WsMessage.ServerAck(... errorCode = "MSG_SEND_FAILED", errorMessage = e.message)` —
the raw `e.message` (which for an unexpected failure can be a JDBC/driver/Hibernate string) was sent
back over the wire to the client.

**Fix:** surface the stable `BusinessException` error code (so the client can still react to e.g.
`MSG_NOT_MEMBER` / `MSG_ANNOUNCEMENT_ONLY`) and set `errorMessage = null`; the full cause stays in the
server log only:
```kotlin
val errorCode = (e as? BusinessException)?.errorCode?.name ?: "MSG_SEND_FAILED"
ServerAck(..., errorCode = errorCode, errorMessage = null)
```
This aligns the WS error contract with the REST `GlobalExceptionHandler`, which already never leaks
internal text (returns `INTERNAL_ERROR` + a fixed default message for the generic `Exception` handler).

**Tests:** `ChatWebSocketHandlerTest.should not leak raw exception message in ServerAck` (asserts a
`PSQLException` detail string does **not** appear in the ack) + `should surface BusinessException error
code in ServerAck`.

---

## Finding D — JWT validation completeness had no dedicated test  *(Info / test gap — FIXED)*

`JwtProvider.validateToken` is the single chokepoint for both REST (`JwtAuthFilter`) and WS
(`afterConnectionEstablished`) auth, yet the only existing tests were the boot **secret guard**
(`JwtProviderSecretGuardTest`) and one WS-level expiry test. The validation contract itself — reject on
bad signature / wrong issuer / `alg:none` / expiry / malformed, and never honour a self-asserted admin
claim — was unverified.

**Added `JwtProviderTest` (9 tests):** valid round-trip; reject foreign-secret signature; reject
tampered signature; reject wrong issuer (`requireIssuer`); reject expired; reject malformed
(`""`, `"a.b.c"`, garbage); reject an **unsigned `alg:none`** token (alg-confusion — JJWT 0.13.0's
`verifyWith(SecretKey)` requires a MAC signature, so an unsecured token is rejected); reject a foreign-key
token carrying `admin:true`; and assert `generateAccessToken` never sets the `admin` claim.

**Why this matters (and why it's already safe):** the `admin` claim is read on validation
(`claims["admin"]`) and gates `/actuator/metrics|prometheus` (`hasRole("ADMIN")`) + `requireAdmin()`
in moderation. But **no mint path ever sets it** — `generateAccessToken` writes only `sub`/`deviceId`/
`iss`/`exp`. So admin authority is **fail-closed**: it cannot be obtained without the signing secret
(protected by the boot guard) — there is no privilege-escalation path here, only an unreachable admin
capability. Documented as informational; the test pins it so a future "add admin mint" change is a
deliberate, reviewed step. (Tracked separately in `docs/PROD_READINESS_*` as the RS256/`aud`-claim
hardening item — out of scope for this pass; do not touch signing primitives.)

---

## Finding E — `InputSanitizer` had zero call sites  *(Medium — FIXED 2026-06-08, loop4)*

**Original gap.** `docs/qa/02-security.md` §1.1 listed **"Input validation — InputSanitizer (HTML
escape, control chars, URL validation) — Deployed"**, but `grep -rln InputSanitizer
backend/src/main/kotlin` returned only the class itself: the control was fully unit-tested yet
**never invoked**. Stored free-text reached the DB un-normalised; control-char / RTL-override /
zero-width / homoglyph stripping was a no-op in production. Latent (the only client renders **plain
text**, so stored `<script>` is inert), but a future **web/HTML client** would inherit a stored-XSS /
spoofing gap.

### The contract decision (the load-bearing part)

The naïve fix — `sanitizeHtml` on write — is **wrong** for this app and was explicitly rejected. The
mobile/CMP client renders message and profile text as **plain text** (Compose `Text`), not HTML.
HTML-escaping on input would store `&amp;` / `&lt;` and the user would literally see `Tom &amp; Jerry`
— a regression — and would double-encode the moment any future renderer escapes again.

**Adopted contract — input normalization on write, HTML-escaping deferred to output:**
- Added **`InputSanitizer.normalizeText(input, maxLength)`** (+ `stripInvisible`): strip control chars
  (keep `\n`/`\t`/`\r`), strip zero-width / bidi-override / homoglyph invisible code points
  (U+200B–U+200F, U+202A–U+202E bidi overrides, U+2066–U+2069 isolates, U+2060, U+00AD, U+FEFF), trim,
  clamp. **No HTML-escaping.**
- **HTML-escaping stays DEFERRED** as an OUTPUT concern (`sanitizeHtml` is kept, unused by the write
  path) — it belongs at the render boundary of a future web/HTML surface, applied once, never on write.
- **Anti-double-escape guard** is pinned by tests: legitimate text containing `&`, `<`, `>`, quotes,
  emoji, and Turkish characters is stored **byte-for-byte unchanged**.

### Where it is wired (domain-service boundary, hexagonal — not scattered in controllers)

| Stored field | Site | Clamp |
|---|---|---|
| user `displayName`, `about` | `UserController.updateMe` (input-mapping step; this is the documented "user-in-auth" path with no dedicated service) | `INPUT_HARD_CAP`, validator authoritative |
| group name (create) | `ConversationService.createConversation` | `INPUT_HARD_CAP`, `isValidGroupName` authoritative |
| group name + description (update) | `GroupService.updateGroupInfo` | name `INPUT_HARD_CAP`; description `GROUP_DESCRIPTION_MAX` |
| bot name + description | `BotService.createBot` | name `DISPLAY_NAME_MAX`; description `BOT_DESCRIPTION_MAX` |
| community name + description | `CommunityService.create` | name `GROUP_NAME_MAX`; description `GROUP_DESCRIPTION_MAX` |
| status caption | `StatusService.createStatus` / `createStatusWithAudience` | `STATUS_CAPTION_MAX` (blank → null) |

Normalization runs **before** validation, so an all-invisible value normalizes to blank and is rejected
with `VALIDATION_ERROR`. For fields that have an explicit length validator (display name, group name)
the clamp uses a high DoS-safety ceiling `ValidationRules.INPUT_HARD_CAP` (4096) so the **validator
stays authoritative** and over-length input is **rejected, not silently truncated**; fields without a
validator clamp at their own field MAX.

**Out of scope (deliberately not sanitized): message bodies.** They travel the E2E/plaintext path and
are rendered as-is; normalizing them would corrupt content (and would defeat E2E once enabled).

**Tests (+13):** 8 `InputSanitizerTest` (normalize strips control/zero-width/RTL; clamps; all-invisible
→ blank; **anti-double-escape**: `&`/`<`/`>`, quotes, emoji, Turkish preserved verbatim) +
3 `ConversationServiceTest` (group name normalized; legit text preserved; invisible-only rejected) +
2 `GroupServiceTest` (name+description normalized; legit text preserved).

**Files:** `InputSanitizer.kt` (+`normalizeText`/`stripInvisible`), `ValidationRules.kt`
(`GROUP_DESCRIPTION_MAX`, `BOT_DESCRIPTION_MAX`, `STATUS_CAPTION_MAX`, `INPUT_HARD_CAP`),
`ConversationService.kt`, `GroupService.kt`, `BotService.kt`, `CommunityService.kt`, `StatusService.kt`,
`UserController.kt`, + the 3 test files. **Follow-up:** correct the QA doc's "Deployed" wording for the
*HTML-escape* sub-control to "input-normalization wired; HTML-escape deferred to a web client".

---

## Finding F — WS ack trusts client `conversationId`  *(Low — Documented)*

`handleAckMessage` calls `markConversationRead(msg.conversationId, userId)` using a client-supplied
`conversationId` that is **not cross-checked** against `msg.messageId`. Because the underlying write is
self-scoped (keyed by `userId`), the blast radius is limited to the caller's own unread rows **in a
conversation they belong to** — so this is Low. `updateStatus` is now membership-guarded (Finding A);
this residual cross-field-consistency hardening is **TODO.md P2**.

---

## What was good (verified clean — no action)

- **JWT alg-confusion / signature robustness.** JJWT **0.13.0**; `validateToken` uses
  `Jwts.parser().verifyWith(signingKey).requireIssuer(...).parseSignedClaims(token)`. `verifyWith(SecretKey)`
  binds verification to the HMAC key type, so a forged `alg:none` (unsecured) or `alg:RS256`
  (public-key-as-HMAC-secret) token is rejected. Now pinned by `JwtProviderTest`.
- **401-not-403 entry point.** `SecurityConfig` sets
  `authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))` (matches the documented
  Ktor-refresh gotcha) and `SessionCreationPolicy.STATELESS`.
- **Security headers.** `frameOptions().deny()`, `contentTypeOptions`, HSTS (1y + `includeSubDomains`),
  CSP `default-src 'self'; frame-ancestors 'none'; form-action 'self'`, `Referrer-Policy
  strict-origin-when-cross-origin`, `Permissions-Policy camera=()/microphone=()/geolocation=()/payment=()`.
  Correct for an API origin.
- **CSRF posture.** `csrf().disable()` is **correct** here: stateless JWT-in-`Authorization`-header API
  with no cookie-based auth (CSRF needs ambient cookie credentials). CORS is restrictive
  (`*.rollingcatsoftware.com` only). No regression.
- **Actuator lockdown.** `/actuator/health`+`/info` public; `/metrics`+`/prometheus` gated on
  `hasRole("ADMIN")`; everything else `authenticated()`. Already covered by
  `ActuatorLockdownIntegrationTest` (Testcontainers — Docker-gated on this host).
- **SSRF guard is properly wired.** `LinkPreviewController.fetchSafely` re-validates **every redirect
  hop** through `SsrfGuard.assertSafe` (Jsoup auto-redirect disabled, manual follow), caps the redirect
  chain (5) and body size (1 MB), and resolves all DNS results — blocking the "public URL 302s to
  169.254.169.254" bypass. `SsrfGuard` checks loopback/link-local/site-local/ULA-IPv6/multicast/wildcard
  and non-http(s) schemes. Good defense-in-depth (covered by `SsrfGuardTest`).
- **WS handshake auth.** `afterConnectionEstablished` requires a `?token=` query param, validates it via
  the same `JwtProvider`, and closes with `POLICY_VIOLATION` on missing/invalid/expired tokens before
  registering the session. `WebSocketConfig.setAllowedOrigins` restricts the WS origin. `handleSendMessage`
  delegates membership/announcement authorization to `MessageService.sendMessage` (already guarded).
- **Rate limiters.** `RateLimitFilter` (10/min/IP on `/api/v1/auth/**` via `shouldNotFilter`) and
  `WebSocketRateLimiter` (50 msg / 10 s, per **userId** — not per-connection, so multiple tabs share the
  budget, which is the safer choice) are simple fixed-window counters with correct reset logic and
  `removeUser` cleanup on disconnect. Adequate for the threat model (the XFF-trust caveat below is a
  known, documented follow-up, not a new finding).

## Known limitations (pre-existing, NOT regressions — left as-is)
- **`RateLimitFilter` trusts `X-Forwarded-For` blindly** (first hop). Behind the trusted nginx this is
  fine; a misconfigured/exposed deployment could let a client spoof XFF to dodge the limit. Already
  tracked as "XFF / Redis rate-limiter rework" in `docs/findings/2026-06-07-security-fixes.md` deferred
  list. Out of scope here.
- **In-memory rate-limit state** doesn't share across horizontally-scaled instances (Redis broadcaster
  exists for WS fan-out but not for rate-limit counters). Same deferred item.
- **HS256 (symmetric) + no `aud` claim + no admin mint path.** Documented in `docs/PROD_READINESS_*`
  (RS256/`aud`/roles hardening). Intentionally untouched per the task boundary ("do not touch JWT
  signing/crypto primitives beyond validation hardening").

## Follow-ups for `TODO.md` (added this pass)
- [x] **P2:** Wire `InputSanitizer` into the write paths (Finding E) — **DONE 2026-06-08** (loop4):
  input-normalization wired at the service boundary; HTML-escape deferred to output; +13 tests.
- [ ] **P2:** WS `handleAckMessage` cross-check `conversationId` against the acked message (Finding F).
- [ ] **Doc nit:** correct `docs/qa/02-security.md` §1.1 wording — the *HTML-escape* sub-control of
  InputSanitizer is **not** wired (deliberately deferred to a future web client); the
  control-char/normalization sub-control now **is** wired.

## Verification
- **Findings A–D pass (initial loop2):** `./gradlew :backend:test` — 412 tests pass, 6 Testcontainers
  `*IntegrationTest` errors (Docker-gated). That pass added 15 tests over the 397/6 baseline.
- **Finding E pass (loop4, 2026-06-08):** `./gradlew :backend:test` — **458 tests pass**, the **same 6
  Testcontainers `*IntegrationTest` errors** (`AuthControllerIntegrationTest`,
  `UserProfilePrivacyIntegrationTest`, `MediaIdorIntegrationTest`, `MessageIdorIntegrationTest`,
  `SearchIdorIntegrationTest`, `ActuatorLockdownIntegrationTest`) — all `IllegalStateException … Could
  not find a valid Docker environment`, the documented no-Docker host failures, unrelated to this
  change (464 total). This pass adds **13 tests** (8 `InputSanitizerTest` incl. anti-double-escape
  guards, 3 `ConversationServiceTest`, 2 `GroupServiceTest`), all green. Files changed for Finding E:
  `InputSanitizer.kt`, `ValidationRules.kt` (shared), `ConversationService.kt`, `GroupService.kt`,
  `BotService.kt`, `CommunityService.kt`, `StatusService.kt`, `UserController.kt`,
  `InputSanitizerTest.kt`, `ConversationServiceTest.kt`, `GroupServiceTest.kt`, `TODO.md`, this doc.
- Files changed:
  - `ChatWebSocketHandler.kt` (typing membership guard, ServerAck no-leak)
  - `MessageService.kt` (`updateStatus` membership guard)
  - `MessagingServiceTest.kt`, `DeliveryStatusTest.kt`, `ChatWebSocketHandlerTest.kt` (tests)
  - `JwtProviderTest.kt` (new)
  - `TODO.md` (Findings E, F)
  - this review doc.
