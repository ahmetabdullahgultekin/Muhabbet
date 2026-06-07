# Security & Correctness Fixes — 2026-06-07

Two-phase hardening pass. Phase 1 (backend security) lands on `claude/fix-backend-idors`
(PR base `main`). Phase 2 (mobile correctness) lands on `claude/fix-mobile-polish`
(PR base `claude/fix-firebase-bom-ktx`). Each phase has its own `CHANGELOG.md` entry on its
own branch.

## Phase 1 — Backend security (`claude/fix-backend-idors`)

### [HIGH] `getMessageInfo` IDOR
- **Was:** `MessageController.getMessageInfo` called `messageRepository.findById(messageId)` and
  `getDeliveryStatuses(...)` directly, with **no membership check**. Any authenticated user who
  knew/guessed a messageId could read the message content, `senderId`, conversationId and the
  full per-recipient delivery list.
- **Fix:** Lookup + authorization moved behind a use case:
  `GetMessageHistoryUseCase.getMessageInfo(messageId, requesterId)` →
  `MessageService.getMessageInfo`. It loads the message, then authorizes
  `conversationRepository.findMember(message.conversationId, requesterId)` **before** loading
  delivery statuses — non-members get `MSG_NOT_MEMBER` (HTTP 403), missing messages get
  `MSG_NOT_FOUND` (404). The controller no longer injects `MessageRepository` (hexagonal — no
  out-port in the adapter-in layer). Mirrors the existing `getMediaMessages` guard.
- **Files:** `MessageController.kt`, `domain/port/in/GetMessageHistoryUseCase.kt` (new
  `getMessageInfo` + `MessageInfo` carrier), `domain/service/MessageService.kt`.

### [HIGH] `markViewOnceViewed` IDOR
- **Was:** `MessageService.markViewOnceViewed` validated only viewOnce / not-already-viewed /
  not-sender — **no membership check**. A non-member who knew the messageId could burn (consume)
  a view-once message for the legitimate recipient.
- **Fix:** Added `conversationRepository.findMember(message.conversationId, userId)` guard
  (`MSG_NOT_MEMBER`/403) before any mutation, immediately after the not-found check.
- **Files:** `domain/service/MessageService.kt` (exposed via
  `MessageController.markViewOnceViewed`).

### [HIGH] JWT dev-secret has no startup guard
- **Was:** `application.yml` defaults `JWT_SECRET` to a world-known dev string; the prod profile
  did not override it and nothing failed boot if it leaked into production — a forgeable HS256
  signing key.
- **Fix:** `JwtProvider.validateSecret()` (`@PostConstruct`, runs on **every** profile) calls
  `check(...)` to abort boot (`IllegalStateException`) when the live secret equals the dev default
  **or** has fewer than 32 bytes (HS256 minimum). The signing key is now built lazily so this
  guard reports a clear message before JJWT's own `WeakKeyException` would fire.
- **Files:** `shared/security/JwtProvider.kt` (added `Environment` dependency — test constructors
  updated to pass `MockEnvironment()`).

### [LOW] Config hygiene
- `docker-compose.prod.yml`: `MINIO_ACCESS_KEY` now `${MINIO_ACCESS_KEY:-minioadmin}` (env-driven,
  matching `infra/docker-compose.prod.yml`); the `mc alias set` line in the bucket-init service
  uses the same var.
- `application.yml`: `logging.level.com.muhabbet` default `DEBUG` → `INFO`
  (dev DEBUG stays in `application-dev.yml`).

### Tests (Phase 1)
- `MessagingServiceTest` — service-layer guards: `getMessageInfo` member-allowed / non-member
  `MSG_NOT_MEMBER` (and verifies statuses are **not** loaded) / not-found; `markViewOnceViewed`
  member-allowed / non-member blocked (verifies the message is **not** burned).
- `MessageIdorIntegrationTest` — end-to-end through the real Spring Security filter chain
  (MockMvc + minted JWTs + Testcontainers Postgres): member reads info, non-member 403
  `MSG_NOT_MEMBER`; non-member cannot burn a view-once (asserts `viewedAt == null` afterward).
- `JwtProviderSecretGuardTest` — boot fails on dev default, fails on <32-byte secret, passes on a
  strong unique secret.

### Build/verify (Phase 1)
- `cmd //c ".\gradlew.bat :backend:compileKotlin :backend:compileTestKotlin"` → **BUILD SUCCESSFUL**.
- Affected unit tests (`:backend:test --tests *MessagingServiceTest *MessageControllerTest
  *JwtProviderSecretGuardTest *AuthServiceTest *ChatWebSocketHandlerTest`) → **BUILD SUCCESSFUL**
  (MessagingServiceTest 21/0/0, MessageControllerTest$GetMessageInfo 4/0/0,
  JwtProviderSecretGuardTest 3/0/0, AuthServiceTest 9/0/0, all 0 failures).
- `:backend:test --tests *MessageIdorIntegrationTest` (Testcontainers) → **BUILD SUCCESSFUL**, 2/0/0.

## Phase 2 — Mobile correctness (`claude/fix-mobile-polish`)

See the `CHANGELOG.md` entry on that branch for the landed detail. Summary of fixes:

1. **[MED] OTP fallback locale bug** — structured `errorCode` carried through
   `PhoneVerificationResult.Error`, populated from `FirebaseAuthException.errorCode` on Android;
   `shouldFallbackToBackendOtp` branches on the code, not localized substrings. Substring match
   kept only as last-resort for the generic catch path, using `lowercase(Locale.ROOT)`-equivalent
   invariant folding.
2. **[MED] Scheduled-send near-future race** — dialog validation now requires a ~2-minute minimum
   lead time so the UI "scheduled + cancel" state can't lie about a message the backend sends
   immediately.
3. **[MED] Communities error rendered as empty** — `AddGroupToCommunitySheet` distinguishes a
   load error (with retry) from a genuinely empty group list instead of `catch { emptyList() }`.
4. **[MED] Ktor logs Authorization header** — header logging gated off for release / sanitized so
   the bearer token never lands in logs.
5. **[CRITICAL-trust] Honest E2E UI** — profile padlock and privacy-dashboard E2E copy gated on
   `E2EConfig.ENABLED`; when OFF (production default = plaintext under TLS) the padlock is hidden
   and an honest "transport-encrypted (TLS)" state is shown. TR+EN strings updated. No crypto
   semantics changed.

## Deferred (explicit non-goals — follow-ups, NOT done here)
- Certificate pinning (mobile).
- SQLCipher / at-rest DB encryption (mobile).
- Full libsignal / E2E re-integration — **BLOCKED** on the libsignal-android API rewrite
  (see project `CLAUDE.md`); do not bump or guess crypto. E2E stays flag-OFF.
- Scheduled-pending hydration on app restart (the scheduled-send queue isn't rehydrated yet).
- XFF / Redis rate-limiter rework.
- Communities `AddGroupToCommunitySheet` 100-conversation pagination (only the error/empty
  distinction was addressed).
