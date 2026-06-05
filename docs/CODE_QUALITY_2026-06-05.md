# Muhabbet — Code Quality & Architecture Review

**Date:** 2026-06-05
**Reviewer:** Senior code-quality & software-architecture review (Claude Opus 4.8)
**Scope:** `main` HEAD `299f34f` (E2E encrypt/decrypt wiring + real backup job, PR #31, just merged)
**Method:** Static read of committed code (backend, shared KMP, mobile CMP), targeted test runs on the
shared module, and an iOS-target compile to characterise the mobile platform state honestly. No full
build / no Docker (shared prod host).

---

## Overall Grade: **B (good, with two ship-blocking honesty gaps)**

This is a genuinely well-structured codebase for a solo MVP. Hexagonal boundaries are real and
ArchUnit-enforced; the new E2E wiring is conservative, correct in design, and shipped behind a
default-OFF flag exactly as a security-critical core-path change should be; the backup job is now a
real archive instead of a placeholder. The grade is held below A by two things that contradict
"DONE" claims in `CLAUDE.md`: **`InputSanitizer` (the advertised XSS defence) is dead code wired into
nothing**, and **the iOS target does not compile** (a pre-existing K/N syntax error), so "all iOS
platform modules implemented — DONE" is not true. Both are documentation-integrity issues as much as
code issues.

### Per-dimension scores (1 = poor, 5 = excellent)

| Dimension | Score | One-line justification |
|---|---|---|
| SOLID + module boundaries (ArchUnit hexagonal) | **5** | Ports/adapters genuinely separated; 14 ArchUnit rules; largest file 415 LOC; domain is Spring-free. |
| DRY / dead code | **3** | Good mapper/util discipline, BUT `InputSanitizer` (72 LOC + test) is never called; duplicate `todo.md`/`TODO.md`. |
| Error handling | **4** | Consistent `ErrorCode` enum + `GlobalExceptionHandler`; backup now fails truthfully. Crypto "swallow-and-fallback" is deliberate but blanket `catch (_: Exception)` is broad. |
| Type safety / null safety (Kotlin) | **4** | Mostly idiomatic; 17 `!!` remain despite a "No `!!`" hard rule, incl. 2 in backend broadcasters that can NPE. |
| Naming / readability / complexity | **5** | Excellent KDoc, intention-revealing names, low nesting; the E2E files are exemplary. |
| Test quality (critical paths) | **4** | E2E crypto + envelope + backup well covered with round-trip/fallback/flag-off; integration coverage of the *server* send path and an Android-instrumented crypto test are absent. |
| Security (E2E correctness, authz, secrets, input) | **3** | E2E design sound & off; JWT/WS auth correct; secrets gitignored. BUT XSS sanitiser unwired, `/actuator/metrics|prometheus` public, offline-send bypassed E2E (fixed here). |
| KMP shared / platform separation | **4** | Clean `expect/actual`; envelope lives in shared; Android=Signal, iOS=NoOp. Knocked down because iOS doesn't compile. |
| Consistency with CLAUDE.md | **3** | Strong adherence to layering/error-code/i18n rules; several "DONE" claims (iOS, InputSanitizer/XSS) are inaccurate. |

---

## Prioritised Findings

Severity: **P0** = correctness/security must-fix before enabling the related feature · **P1** = fix soon ·
**P2** = quality/debt · **P3** = nit.

### P0 — Offline send path bypassed E2E encryption (FIXED in this branch)
- **Where:** `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/data/remote/WsClient.kt`
  — `send()` (encrypted), but `sendOrQueue()` (line ~181) and `drainPendingMessages()` (line ~231) did **not**.
- **Impact:** With `E2EConfig.ENABLED = true`, any message sent while offline (queued by `sendOrQueue`)
  or replayed from the offline queue on reconnect (`drainPendingMessages`) would be transmitted as
  **plaintext** — a silent confidentiality hole in exactly the resilience path users hit on flaky
  mobile networks. The flag is OFF today, so prod is unaffected, but this would have shipped a broken
  E2E guarantee the moment the flag flips.
- **Fix applied:** extracted a single `encryptForWire()` seam that all three wire paths
  (`send`, `sendOrQueue`, `drainPendingMessages`) route through; idempotent on already-enveloped
  bodies, byte-identical pass-through when the flag is OFF. Added 2 regression tests
  (`should_keep_offline_queued_envelope_decryptable_after_resend`,
  `should_pass_through_offline_queued_body_unchanged_when_disabled`). commonMain compiles; the
  underlying `MessageEncryptor` methods are covered by the existing 9 passing tests.

### P1 — `InputSanitizer` is dead code; the advertised XSS defence is not wired in
- **Where:** `backend/src/main/kotlin/com/muhabbet/shared/security/InputSanitizer.kt`. Referenced
  **only** by `InputSanitizerTest.kt` — `grep -rn InputSanitizer backend/src/main` returns the file
  itself and nothing else. No controller, service, or WS handler calls `sanitizeMessageContent`,
  `sanitizeHtml`, or `sanitizeDisplayName`.
- **Impact:** `CLAUDE.md` lists "InputSanitizer (HTML escaping, control char stripping, URL
  validation)" under shipped **Security Hardening**, implying stored content is sanitised. It is not.
  Message content, display names, and group names reach the DB unsanitised. The risk is bounded for
  native CMP clients (Compose `Text` does not render HTML), but the planned **web/desktop client** and
  any HTML email/export path would be XSS-exposed. This is a real gap dressed as a finished feature.
- **Recommended fix:** call `InputSanitizer.sanitizeMessageContent(...)` / `sanitizeDisplayName(...)`
  at the service boundary (e.g. `MessageService.send`, profile update, group create/rename) — **or**
  consciously adopt store-raw + escape-on-render and delete the unused sanitiser. Either way, make
  code and `CLAUDE.md` agree. NOTE: if E2E is ever enabled, server-side content sanitisation must
  **not** run on E2E bodies (the server holds ciphertext it cannot read) — escape on render instead.
  Not auto-fixed: choosing the strategy is a design decision (ask-before-acting).

### P1 — iOS target does not compile (pre-existing); "all iOS modules DONE" is inaccurate
- **Where:** `mobile/composeApp/src/iosMain/kotlin/com/muhabbet/app/platform/CameraPicker.ios.kt:68`
  — `windowScene.windows.firstOrNull { (it as UIWindow).isKeyWindow }` →
  `e: Function invocation 'isKeyWindow()' expected.` (K/N parses the property access as a call).
  Verified to fail on clean HEAD (stashed my changes and reproduced), so it is **not** introduced here.
- **Impact:** `compileKotlinIosSimulatorArm64` fails. The iOS app cannot build, which transitively
  means none of the iOS "DONE" claims (CameraPicker, AudioRecorder, Keychain, etc.) are verifiable and
  the CMP CI "mobile iOS" job cannot be green. Combined with iOS using `NoOpEncryption` +
  `NoOpKeyManager` (`PlatformModule.ios.kt:28-29`), iOS has **no E2E at all** even once the flag flips.
- **Recommended fix:** correct the K/N member access (e.g. bind `val w = it as UIWindow; w.isKeyWindow`
  or use the real cinterop signature) and restore the iOS CI gate. Re-grade the iOS "DONE" entries in
  `CLAUDE.md` to "compiles / stubbed (NoOp E2E, NoOp calls)". Not auto-fixed here: I cannot run the iOS
  toolchain end-to-end to verify the corrected form on this host, and an unverified K/N edit on a
  security/platform file is exactly the kind of change to leave for a verified local build.

### P1 — `/actuator/metrics` and `/actuator/prometheus` are publicly reachable
- **Where:** `backend/src/main/kotlin/com/muhabbet/shared/security/SecurityConfig.kt:52`
  — `.requestMatchers("/actuator/info", "/actuator/metrics", "/actuator/prometheus").permitAll()`.
- **Impact:** Operational metrics (memory, request counts/timings, datasource pool, possibly endpoint
  names) are exposed without auth. Info leakage + a DoS-amplification surface. `/actuator/health` being
  public is fine; metrics/prometheus should be gated (auth, or admin-IP at nginx/Traefik, or moved to a
  separate management port).
- **Recommended fix:** drop `metrics`/`prometheus` from `permitAll()` and scrape over the internal
  network / management port, or require the `isAdmin` JWT claim. Not auto-fixed: changes the prod
  security posture and the Prometheus scrape path — needs operator sign-off.

### P2 — Backup pagination can silently drop messages at exact-timestamp page boundaries
- **Where:** `backend/.../messaging/domain/service/BackupService.kt:116-129` (`fetchAllMessages`)
  uses `before = page.minOf { it.serverTimestamp }`, and the query is strict `<`
  (`SpringDataMessageRepository.kt:27` → `AND m.serverTimestamp < :before`).
- **Impact:** If the oldest message of a full 200-row page shares its `serverTimestamp` with one or more
  older messages, those siblings are skipped by the strict `<` cursor → **missing messages in the
  backup archive**. Collisions on a millisecond `Instant` at an exact 200-boundary are rare, but a
  backup that loses data without erroring is the worst failure mode for a backup. (The MVP cursor on
  the read path has the same property but there the user just scrolls; for an archive it's data loss.)
- **Recommended fix:** make the cursor a `(serverTimestamp, id)` tuple (keyset pagination), or page by
  monotonic message id, or de-dup by id across pages. Document the chosen approach. Not auto-fixed:
  requires a repository query change + integration test against Postgres (no DB build on this host).

### P2 — 17 `!!` non-null assertions remain despite the "No `!!`" hard rule
- **Where (backend, highest risk):**
  `messaging/adapter/out/NoOpMessageBroadcaster.kt:80` and
  `messaging/adapter/out/external/RedisMessageBroadcaster.kt:89` — `pushToken = device.pushToken!!`.
  A device row with a null `pushToken` throws NPE mid-broadcast.
- **Where (mobile, lower risk):** `ChatScreen.kt` (×4), `MessageInfoScreen.kt` (×3),
  `LinkPreviewCard.kt` (×3), `OtpVerifyScreen.kt` (×2), `PhoneInputScreen.kt`, `SharedMediaScreen.kt`,
  `CameraPicker.android.kt` — most are guarded by a preceding `!= null` check (idiomatic-but-banned).
- **Recommended fix:** backend broadcasters should `filter { it.pushToken != null }` (or `mapNotNull`)
  before fan-out; mobile sites should use `?.let`/`val` capture. Not auto-fixed in bulk: the backend
  ones need a behaviour decision (skip vs. log) and the mobile ones can't be test-verified here.

### P2 — `JwtProvider` reads an `admin` claim it never writes
- **Where:** `JwtProvider.kt:31-43` (`generateAccessToken` sets subject/deviceId/issuer only) vs.
  `validateToken` line 63 (`isAdmin = claims["admin"] as? Boolean ?: false`).
- **Impact:** Not a vulnerability — app-issued tokens are always non-admin, which is safe-by-default —
  but it's a latent surprise: any admin authorisation depends on externally-minted tokens, and there's
  no test asserting "app tokens are never admin." Worth a comment or an explicit admin-mint path so the
  intent is legible.

### P3 — Nits
- **Duplicate TODO trackers:** stale lowercase `todo.md` (Mar) duplicated current `TODO.md` (Jun).
  **FIXED:** `todo.md` deleted in this branch.
- **`.kotlin/` not gitignored:** the Kotlin build dir showed as untracked. **FIXED:** added `.kotlin/`
  to `.gitignore`.
- **`InputSanitizer.stripControlChars`** predicate `it == '\n' || it == '\t' || it == '\r' ||
  !it.isISOControl()` is correct but redundant (the first three *are* ISO controls).
- **`E2EEnvelope.decodeOrNull`** uses `content.contains(MAGIC)` as a cheap pre-check; a user message
  literally containing the string `mhbt-e2e-1` triggers a full JSON parse attempt (then safely returns
  null). Harmless, just slightly more work on a crafted message; fine for MVP.

---

## Honest call-outs the prompt asked for

**The flag-off E2E:** This is the right way to ship a security-critical core-path change, and the
implementation matches the claim. `E2EConfig.ENABLED = false` makes both `encryptOutgoing` and
`decryptIncoming` exact pass-throughs (verified by `should_pass_through_unchanged_when_disabled`), the
envelope is self-describing and backward-compatible (legacy/plaintext never matches `MAGIC`), and the
fallbacks (group / unresolved recipient / non-text / no session / any exception) all degrade to
plaintext without dropping or throwing. The honest caveats, all acknowledged in the PR/KDoc: scope is
**TEXT in 1:1 only**; **group, media-blob, and iOS are not covered**; the blanket `catch (_:
Exception) → plaintext` means an encryption *bug* would silently downgrade to plaintext rather than
fail loud (acceptable as a first cut, but before enabling broadly there should be telemetry on the
fallback rate so a systemic downgrade is visible). `SignalKeyManager.decryptMessage` (try
`PreKeySignalMessage` then fall back to `SignalMessage`) is a reasonable heuristic but will mis-route a
genuine PreKey message whose post-parse `decrypt` throws — worth a more precise message-type
discriminator before GA.

**The iOS stubs:** Not "done." iOS doesn't compile (P1 above), and where it would, E2E and calls are
explicit `NoOp` stubs (`PlatformModule.ios.kt`, `CallEngine.ios.kt` self-labels "stub"). The Android
path (libsignal X3DH + Double Ratchet, `EncryptedSharedPreferences`-backed store) is real; iOS is a
placeholder. `CLAUDE.md` should say so.

## Honest strengths
- **Architecture is real, not aspirational.** 14 ArchUnit rules enforce domain-has-no-Spring,
  services-don't-touch-adapters, controllers-don't-touch-JPA-entities, and cross-module isolation.
  These would catch regressions in CI. Largest source file is 415 LOC — the "no God class" rule holds.
- **The backup job is now correct.** Real JSON archive → MinIO via a new `BackupArchivePort` out-port
  (hexagonal-clean, no S3 types in the domain), real presigned URL + byte size + counts, paged reads
  (200/page, 50k cap) to bound memory, and failures mark `FAILED` instead of a false `COMPLETED`. Four
  focused tests, including the empty-history and upload-throws cases.
- **Error handling and i18n discipline** are consistently applied (`ErrorCode` enum, no inline UI
  strings, error-code repository pattern).
- **The new code is a pleasure to read** — intention-revealing KDoc that explains *why*, not just
  *what*, especially in `E2EEnvelope`, `MessageEncryptor`, and `E2EConfig`.

---

## Roadmap professionalisation (larger refactors — documented, not done here)

1. **Biggest refactor — make E2E real before the flag flips:** finish the matrix the first cut left
   open: (a) **keyset/id-based backup + send-path pagination** to kill the timestamp-collision data
   loss; (b) **group sender-key fan-out** and **media-blob encryption** so "E2E" isn't TEXT-1:1-only;
   (c) **iOS libsignal bridge** to replace `NoOpEncryption` so iOS users get the same guarantee;
   (d) **fallback-rate telemetry** so a silent plaintext downgrade is observable; (e) a precise
   PreKey-vs-Signal message discriminator in `decryptMessage`. Track as an E2E GA epic with the flag
   as the gate.
2. **Wire or remove `InputSanitizer`** and reconcile the XSS claim in `CLAUDE.md` (decide sanitise-on-
   store vs. escape-on-render, knowing E2E ciphertext must not be touched server-side).
3. **Fix the iOS build + restore the iOS CI gate**, then re-grade every iOS "DONE" in `CLAUDE.md`.
4. **Lock down actuator** (metrics/prometheus off public) as part of a small "prod security posture"
   pass alongside an explicit admin-token mint path.
5. **`!!` sweep** with a detekt rule promoted to error so the "No `!!`" rule is enforced, not aspired.

---

## What was changed in branch `quality/2026-06-05` (small, safe only)
- **P0 fix:** `WsClient.kt` — unified `encryptForWire()` seam so `sendOrQueue` / `drainPendingMessages`
  also apply E2E (was send-only); + 2 regression tests in `MessageEncryptorTest.kt`.
  *Verified:* `shared:jvmTest` green; commonMain compiles (iOS build fails only on the pre-existing
  `CameraPicker.ios.kt` error, unrelated). Android-instrumented run blocked by missing Firebase
  versioned deps in this checkout — behaviour-preserving extract-method over already-tested methods.
- **Hygiene:** deleted stale `todo.md`; added `.kotlin/` to `.gitignore`.

Everything larger is documented above and left for a verified local build / explicit sign-off
(per the reversible-risky-change and ask-before-acting rules). E2E flag untouched (stays OFF).
