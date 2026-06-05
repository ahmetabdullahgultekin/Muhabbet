# MUHABBET — TODO (TACTICAL)

> **Last refreshed**: 2026-06-04 (from deep code analysis at HEAD `97626f8`, 2026-03-31).
> **Dev status**: PAUSED since 2026-03-31. Backend LIVE + healthy at
> `https://muhabbet-api.rollingcatsoftware.com` (db/redis/ssl all UP).
> **Convention**: P0 = launch-blocker / correctness / security · P1 = needed before public
> launch · P2 = quality/hardening · P3 = growth/optional.
> Each item: imperative title · `files` · why · **DONE =** verifiable condition.

---

## P0 — Launch blockers & security-critical

- [ ] **Wire E2E encryption into the message send/receive path (or stop advertising E2E)**
  - `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/data/remote/WsClient.kt`
    (`SendMessage` build at ~L231), `.../data/repository/E2ESetupService.kt`,
    `.../di/AppModule.kt`, `shared/.../port/EncryptionPort.kt`
  - **Why**: `EncryptionPort` / `SignalEncryption` are registered in DI and keys are
    registered on login, but **no code path ever calls `.encrypt()`/`.decrypt()` on a
    message body** — only `EncryptionPort` import outside DI is in `AppModule.kt`. Messages
    are sent in **plaintext** today. A "privacy-first E2E" app shipping plaintext is a
    correctness + trust + (arguably) KVKK-marketing problem.
  - **DONE =** `grep -rn '\.encrypt(' mobile/composeApp/src/commonMain` shows the send path
    encrypting body before `WsClient.send()`, `.decrypt()` on `NewMessage` receipt, and a
    manual two-device test confirms server/DB stores ciphertext (not readable text).

- [ ] **Generate release keystore + produce a signed Android AAB**
  - `mobile/composeApp/build.gradle.kts` (signingConfig scaffold at L132-160, currently
    env-var gated, no keystore present), new `infra/` keystore handling, `.gitignore`
  - **Why**: Signing config only activates when `MUHABBET_KEYSTORE_FILE` is set; no keystore
    exists in repo or CI. `mobile-ci.yml` builds **debug APK only** — there is no signed
    release artifact to upload to Play Console. Play Store submission cannot start.
  - **DONE =** `./gradlew :mobile:composeApp:bundleRelease` with keystore env vars produces a
    signed `.aab`; `apksigner verify --print-certs` shows the release cert; keystore + creds
    stored outside git (documented in a runbook).

- [ ] **Run the security penetration test pass before public launch**
  - target: live API + WebSocket; record findings in `docs/qa/02-security.md`
  - **Why**: Never run (ROADMAP 4.9 / 5.x "Remaining"). Required before exposing a messaging
    app to the public; auth, OTP brute-force, JWT (HS256), media presigned-URL scope, WS
    rate-limit bypass all need adversarial testing.
  - **DONE =** OWASP ZAP/Burp baseline scan completed against a staging instance, findings
    triaged with severity, all High/Critical fixed or risk-accepted in writing.

- [ ] **Bump `versionCode`/`versionName` for first release**
  - `mobile/composeApp/build.gradle.kts` L125-126 (`versionCode = 1`, `versionName = "0.1.0"`)
  - **Why**: `0.1.0` / code `1` is dev default; fine for first upload but confirm before AAB.
  - **DONE =** values reflect the intended public release (e.g. `1.0.0` / appropriate code).

## P1 — Needed before / during public launch

- [ ] **Implement iOS APNs delivery end-to-end**
  - `mobile/composeApp/src/iosMain/.../platform/PushTokenProvider.ios.kt` (token relies on an
    AppDelegate callback that must call `onTokenReceived`; `registerForRemoteNotifications`
    noted as not bound in K/N 2.3.x), iOS `AppDelegate`, backend FCM→APNs or direct APNs adapter
  - **Why**: iOS push is non-functional. `getToken()` waits on an AppDelegate hook that the
    comment says is wired "externally"; no APNs server-side path. iOS users get no notifications.
  - **DONE =** an iOS device registers an APNs token, backend delivers a push, device shows it
    while backgrounded (verified on TestFlight build).

- [ ] **Bridge iOS Firebase Phone Auth or commit to backend-only OTP on iOS**
  - `mobile/composeApp/src/iosMain/.../platform/FirebasePhoneAuth.ios.kt`
    (`isAvailable()` hard-returns `false`, `verifyCode` throws `UnsupportedOperationException`)
  - **Why**: iOS auth currently can ONLY use the backend OTP fallback. Decide: integrate
    Firebase iOS SDK (CocoaPods/SPM) OR officially make backend OTP the iOS path and remove the
    dead Firebase branch (KISS/YAGNI per CLAUDE.md).
  - **DONE =** iOS login works on a real device via the chosen path; no `UnsupportedOperationException`
    reachable in the login flow.

- [ ] **Decide & wire iOS E2E encryption (libsignal bridge) or scope iOS E2E out for launch**
  - `mobile/composeApp/src/iosMain/.../di/PlatformModule.ios.kt` L28-29
    (`NoOpKeyManager` + `NoOpEncryption`)
  - **Why**: iOS uses NoOp encryption while Android uses libsignal. Even after P0 wires the
    send path, iOS would send plaintext with no Signal session. Cross-platform E2E requires a
    libsignal-client Kotlin/Native bridge — large effort; may justify Android-first launch.
  - **DONE =** explicit decision recorded in `docs/decisions.md`; if bridged, iOS establishes a
    Signal session and encrypts; if deferred, iOS launch is gated/flagged accordingly.

- [ ] **Implement the message backup job (currently a no-op placeholder)**
  - `backend/src/main/kotlin/com/muhabbet/messaging/domain/service/BackupService.kt` (~L29:
    marks backup `COMPLETED` with `backupUrl=null`, `messageCount=0`, `fileSizeBytes=0`)
  - **Why**: `createBackup` immediately reports success but produces no archive. Any "backup
    your chats" UX is misleading; restore is impossible.
  - **DONE =** backup serializes the user's messages, uploads to MinIO, and persists a real
    presigned URL + counts; a restore (or download) reproduces the data.

- [ ] **Configure Sentry DSN in production**
  - `infra/docker-compose.prod.yml` & `docker-compose.prod.yml` (`SENTRY_DSN: ${SENTRY_DSN:-}`
    → empty), `.env.prod`
  - **Why**: Crash/error reporting code is present but DSN is unset, so no backend errors are
    captured in production — blind to incidents at launch.
  - **DONE =** `SENTRY_DSN` set in `.env.prod`; a test exception appears in the Sentry project.

- [ ] **Merge or close the two open Dependabot PRs and unblock CI**
  - PR #30 (38-update group: Kotlin 2.3.20→2.3.21, Spring Boot 4.0.5→4.0.6, AGP 9.1→9.2.1,
    Compose 1.10.3→1.11.1, coroutines/serialization 1.11, Twilio 11→12, etc.) — `MERGEABLE`
    but `BLOCKED`; PR #21 (androidx.security-crypto, open 66 days) — `MERGEABLE` but `BLOCKED`.
  - **Why**: Both blocked by required CI checks/branch protection on a paused repo. #30 is a
    large coordinated bump (note Twilio major 11→12 and kotlinx-datetime 0.7.1→0.8.0 may need
    code changes); #21 is stale.
  - **DONE =** CI green on each (or checks re-run), then merged; or closed with rationale.
    Verify `gh pr list -R ahmetabdullahgultekin/Muhabbet --state open` is empty/intentional.

- [ ] **Run k6 load tests against a production-like environment and record results**
  - `infra/load-tests/http-endpoints.js`, `infra/load-tests/websocket-load.js` (scripts exist,
    never executed at scale)
  - **Why**: Single 8GB host; WS fan-out + Redis pub/sub broadcaster unproven under load.
  - **DONE =** a load run report (RPS, p95 latency, error rate, WS concurrency ceiling) committed
    under `docs/qa/` with any tuning follow-ups filed.

## P2 — Quality / hardening / completeness

- [ ] **Close the 6 client-side UI `TODO` stubs** (dead buttons that do nothing)
  - `HomeShellScreen.kt` L98 (search), `MessageBubble.kt` L91 (view-once mark),
    `WallpaperPickerScreen.kt` L191 (gallery picker), `InviteLinkSheet.kt` L149 (platform share),
    `CommunityDetailScreen.kt` L167 (add group), `BroadcastListScreen.kt` L191 (open detail)
  - **Why**: Visible controls with `onClick = { /* TODO */ }` look broken to users.
  - **DONE =** each either implemented or hidden; no `/* TODO */` remains in those onClick blocks.

- [ ] **Run backend test suite on current HEAD and confirm the headline count**
  - `backend/src/test/...` (34 test files; ArchUnit `HexagonalArchitectureTest`,
    controller + service tests), CHANGELOG claims "332/333 pass"
  - **Why**: Claim is from Feb 2026 docs; verify against current code (memory rule: verify by
    run, not by stale docs) before trusting the gate.
  - **DONE =** `./gradlew :backend:test` output recorded (pass/fail count, any skips).

- [ ] **Verify mobile/iOS actually compiles at HEAD**
  - `mobile-ci.yml` builds Android debug APK + iOS shared framework only (not full iOS app)
  - **Why**: iOS app target is never fully built in CI; compile drift possible after the
    Kotlin 2.3.20 bump.
  - **DONE =** `./gradlew :mobile:composeApp:assembleDebug` green; iOS framework task green;
    note whether a full iOS app/IPA build has ever succeeded.

- [ ] **Resolve the Sentry-on-Spring-Boot-4 exclusion debt**
  - backend `@SpringBootApplication(excludeName=[...SentryAutoConfiguration])` (per CLAUDE.md)
  - **Why**: Auto-config is force-excluded pending a Sentry release compatible with Spring Boot 4;
    re-enable once available (also relevant to the P1 Sentry DSN item).
  - **DONE =** exclusion removed after upgrading to a compatible Sentry, or a tracking note added.

## P3 — Growth / optional (post-launch)

- [ ] **iOS LiveKit voice-call bridge** — `iosMain/.../platform/CallEngine.ios.kt` is a stub
  (connect sets a bool, mute/speaker no-op). Voice calls work on Android only.
  **DONE =** iOS joins a LiveKit room and carries audio.
- [ ] **Web / Desktop client** (Kotlin/JS or React+TS, QR device-linking, message sync). Power-user demand.
- [ ] **Group voice/video calls** (multi-party LiveKit rooms, participant grid).
- [ ] **CDN for media delivery** at scale (when media traffic justifies it).
- [ ] **iOS Crash reporting** — `CrashReporter.ios.kt` is NSLog-only; wire Sentry CocoaPod for parity.
- [ ] **App Store / TestFlight submission** (gated on the iOS P1 items above).
