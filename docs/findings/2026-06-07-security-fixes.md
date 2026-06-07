# Security & Correctness Fixes — 2026-06-07 (Phase 2: Mobile)

Mobile correctness/trust pass on `claude/fix-mobile-polish` (PR base
`claude/fix-firebase-bom-ktx`). The companion backend-security pass (Phase 1 — message IDORs +
JWT dev-secret boot guard + config hygiene) lands separately on `claude/fix-backend-idors`
(PR base `main`); its own `CHANGELOG.md` entry and findings doc live on that branch.

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

## Findings NOT addressed here — code is on other (unmerged) feature branches

The task referenced two findings whose code does **not** exist on the `claude/fix-firebase-bom-ktx`
base this branch is built on, so they could not be fixed without pulling in unrelated feature work:

- **[MED] Scheduled-send near-future race** — `ScheduledSend.kt` + the `ChatScreen` scheduling
  dialog live on `claude/feat-scheduled-send-ui` (commit `4dec0c0`, built **on top of** this base
  but not merged in). **Follow-up:** enforce a ~2-minute minimum lead time in the dialog validation
  on that branch (or once it merges to `main`).
- **[MED] Communities error rendered as empty** — `ui/communities/AddGroupToCommunitySheet.kt`
  (`catch (_) { emptyList() }`) lives on `claude/feat-communities-add-group`. **Follow-up:** add a
  `loadError` state with retry distinct from the empty state on that branch (and the noted
  100-conversation pagination remains a separate follow-up).

## Deferred (explicit non-goals — follow-ups, NOT done here)
- Certificate pinning (mobile).
- SQLCipher / at-rest DB encryption (mobile).
- Full libsignal / E2E re-integration — **BLOCKED** on the libsignal-android API rewrite (see
  `CLAUDE.md`); do not bump or guess crypto. E2E stays flag-OFF. (This pass made the UI honest
  about that, nothing more.)
- Scheduled-pending hydration on app restart.
- XFF / Redis rate-limiter rework.
- Scheduled-send 2-minute lead-time + Communities error-vs-empty (above — code is on feature
  branches, not on this base).
- Wire `BuildInfo.DEBUG` to a real platform `BuildConfig.DEBUG` (currently a hand-flipped constant).
