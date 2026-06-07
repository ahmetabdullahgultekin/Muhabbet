# Security & Correctness Fixes — 2026-06-07

Two-phase hardening pass. Phase 1 (backend security) landed on `claude/fix-backend-idors`
(PR base `main`, PR #55). Phase 2 (mobile correctness) landed on `claude/fix-mobile-polish`
(PR base `claude/fix-firebase-bom-ktx`, PR #61). Each phase has its own `CHANGELOG.md` entry.

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

## Phase 2 — Mobile correctness & trust (`claude/fix-mobile-polish`)

### [CRITICAL-trust] Honest E2E UI
- **Was:** `UserProfileScreen` (padlock + `profile_encrypted`) and `PrivacyDashboardScreen`
  (`privacy_e2e_info`) **unconditionally** claimed end-to-end encryption while E2E is OFF in
  production (`E2EConfig.ENABLED == false` → messages travel as plaintext under TLS). This is a
  false security promise to users.
- **Fix:** Both surfaces gated on `E2EConfig.ENABLED`. When false: the padlock (`Icons.Default.Lock`)
  is replaced with `Icons.Default.Info` and the copy switches to an honest transport state —
  `profile_transport_encrypted` ("Transport-encrypted (TLS) — end-to-end encryption coming soon")
  and `privacy_transport_info`. When the flag is later flipped on (after crypto review), the
  padlock + E2E copy return automatically. No crypto semantics touched; libsignal untouched.
- **Files:** `ui/profile/UserProfileScreen.kt`, `ui/privacy/PrivacyDashboardScreen.kt`,
  `composeResources/values/strings.xml` (TR), `composeResources/values-en/strings.xml` (EN).

### [MED] OTP fallback used localized strings + locale-sensitive lowercasing
- **Was:** `PhoneInputScreen.shouldFallbackToBackendOtp(rawMessage)` decided whether to fall back to
  the backend OTP flow by substring-matching the **localized** Firebase message text, after
  `rawMessage.lowercase()`. On Turkish-locale devices (the entire user base) this false-negatives.
- **Fix:** Carry a **structured** `PhoneAuthErrorCode`
  (`RATE_LIMITED` / `CONFIGURATION` / `INVALID_PHONE` / `UNKNOWN`) on
  `PhoneVerificationResult.Error`. On Android it is mapped from
  `(e as? FirebaseAuthException)?.errorCode` — a stable, locale-invariant `ERROR_*` constant — via
  `classifyFirebaseError`. `shouldFallbackToBackendOtp(code)` now branches on the enum
  (fall back on `RATE_LIMITED`/`CONFIGURATION`; surface `INVALID_PHONE` so the user can fix the
  number; do not fall back on `UNKNOWN`). Substring matching survives **only** as a last resort on
  the generic `catch (e: Exception)` path (`shouldFallbackForRawMessage`), where there is no
  structured code; that path uses Kotlin's **locale-invariant** `String.lowercase()` (Unicode
  default case mapping, not the platform locale — safe under Turkish `i`/`I` folding). The
  error-code approach sidesteps the commonMain lack of `java.util.Locale`.
- **Files:** `platform/FirebasePhoneAuth.kt` (enum + field, commonMain),
  `platform/FirebasePhoneAuth.android.kt` (`classifyFirebaseError`/`classifyFirebaseMessage`),
  `ui/auth/PhoneInputScreen.kt`.

### [MED] Ktor logged the Authorization header
- **Was:** `ApiClient` installed `Logging { level = LogLevel.HEADERS }` — the bearer token in the
  `Authorization` header was written to logs.
- **Fix:** `level = if (BuildInfo.DEBUG) LogLevel.INFO else LogLevel.NONE`. `INFO` logs method +
  URL + status only (no headers/body); release logs nothing. Headers are **never** logged in any
  build, so the token cannot leak. Added the `BuildInfo.DEBUG` flag (default `true`; flip to
  `false` / wire to platform `BuildConfig.DEBUG` for production builds).
- **Files:** `data/remote/ApiClient.kt`, `BuildInfo.kt`.

### Build/verify (Phase 2)
- `cmd //c ".\gradlew.bat :mobile:composeApp:compileCommonMainKotlinMetadata"` → **BUILD SUCCESSFUL**
  (only pre-existing kotlinx-datetime deprecation warnings; no errors).
- Per the project's host constraints, the full Android app / `assembleDebug` does NOT build here
  (uncached Firebase + no KVM emulator) and `commonizeNativeDistribution` times out, so only the
  commonMain-metadata gate was run. The androidMain `classifyFirebaseError` change is therefore
  **not** compile-verified on this host; `FirebaseAuthException.errorCode` is a documented stable
  Firebase API and the change is mechanical.

### Related findings now landed on feature branches (merged to `main`)
The mobile pass was built on `claude/fix-firebase-bom-ktx`; two findings referenced code that lived
on sibling feature branches (since merged to `main` via PRs #57/#58):
- **[MED] Scheduled-send near-future race** — `ScheduledSend.kt` + the `ChatScreen` scheduling
  dialog (PR #57). **Follow-up:** enforce a ~2-minute minimum lead time in the dialog validation.
- **[MED] Communities error rendered as empty** — `ui/communities/AddGroupToCommunitySheet.kt`
  `catch (_) { emptyList() }` (PR #58). **Follow-up:** add a `loadError` state with retry distinct
  from the empty state (the 100-conversation pagination remains a separate follow-up).

## Deferred (explicit non-goals — follow-ups, NOT done here)
- Certificate pinning (mobile).
- SQLCipher / at-rest DB encryption (mobile).
- Full libsignal / E2E re-integration — **BLOCKED** on the libsignal-android API rewrite (see
  `CLAUDE.md`); do not bump or guess crypto. E2E stays flag-OFF. (This pass made the UI honest
  about that, nothing more.)
- Scheduled-pending hydration on app restart (the scheduled-send queue isn't rehydrated yet).
- XFF / Redis rate-limiter rework.
- Scheduled-send 2-minute lead-time + Communities error-vs-empty (code is on feature branches that
  merged to `main`; the dialog/state changes themselves are the follow-up).
- Communities `AddGroupToCommunitySheet` 100-conversation pagination.
- Wire `BuildInfo.DEBUG` to a real platform `BuildConfig.DEBUG` (currently a hand-flipped constant).
