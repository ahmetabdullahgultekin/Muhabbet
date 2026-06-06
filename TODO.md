# MUHABBET — TODO (TACTICAL)

> **Last refreshed**: 2026-06-06 (Tier 1 = DONE; Tier 2 multi-device NON-CRYPTO scaffolding shipped
> behind `multi-device.enabled` default OFF; Dependabot #40 triaged + closed — breaks the mobile KMP
> build). Aligned to [`ROADMAP.md`](ROADMAP.md).
> **Dev status**: Backend LIVE + healthy at `https://muhabbet-api.rollingcatsoftware.com`
> (db/redis/ssl all UP). **Do NOT deploy** — E2E + multi-device both ship dark (flags OFF).
> **Convention**: P0 = launch-blocker / correctness / security · P1 = needed before public
> launch · P2 = quality/hardening · P3 = growth/optional.
> **Tier alignment**: P0/P1 below = **Tier 1** (core-messaging hardening & trust) in `ROADMAP.md`.
> P2 ≈ Tier-1 polish, P3 ≈ Tier 2/3. Each item: imperative title · `files` · why ·
> **DONE =** verifiable condition · **[Tier x.y]** = ROADMAP task.
>
> **Tier-1 engineering = DONE** (E2E text wired/#31, receipts/#35, media-blob E2E, 6 dead buttons/
> #35+#36). The open P0/P1 below are now mostly **operator/ops** tasks (signed AAB, pen-test, Sentry
> DSN, k6) + the iOS catch-up, not new feature code.

---

## P0 — Launch blockers & security-critical  *(Tier 1)*

- [x] **Wire E2E encryption into the message send/receive path (or stop advertising E2E)** **[Tier 1.1]**
  — *Done in code, behind a default-OFF feature flag, with unit tests. Pushed on branch
  `exec/p0-2026-06-05` (PR #31) for human review + canary; NOT enabled in prod. Rollout gates +
  no-redeploy kill-switch documented in `docs/e2e-rollout-runbook.md`. **Next iter**: run Gate-1
  two-device test (ciphertext-in-DB), then wire the flag to remote config before flipping default ON.
  Remaining: group sender-key fan-out (Tier 3.1), media-body encryption (Tier 1.4, P0-adjacent),
  iOS libsignal bridge (P1 / Tier 2.5).*
  - `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/data/remote/WsClient.kt`
    (`send()` encrypts, frame loop decrypts), `.../crypto/MessageEncryptor.kt` (new),
    `.../crypto/E2EConfig.kt` (flag, default OFF), `.../di/AppModule.kt` (wired),
    `shared/.../port/E2EEnvelope.kt` (new wire format), `shared/.../port/EncryptionPort.kt`
  - **Why**: `EncryptionPort` / `SignalEncryption` are registered in DI and keys are
    registered on login, but **no code path ever calls `.encrypt()`/`.decrypt()` on a
    message body** — only `EncryptionPort` import outside DI is in `AppModule.kt`. Messages
    are sent in **plaintext** today. A "privacy-first E2E" app shipping plaintext is a
    correctness + trust + (arguably) KVKK-marketing problem.
  - **DONE =** `grep -rn '\.encrypt(' mobile/composeApp/src/commonMain` shows the send path
    encrypting body before `WsClient.send()`, `.decrypt()` on `NewMessage` receipt, and a
    manual two-device test confirms server/DB stores ciphertext (not readable text).

- [ ] **Generate release keystore + produce a signed Android AAB** **[Tier 1.6]**
  - `mobile/composeApp/build.gradle.kts` (signingConfig scaffold at L132-160, currently
    env-var gated, no keystore present), new `infra/` keystore handling, `.gitignore`
  - **Why**: Signing config only activates when `MUHABBET_KEYSTORE_FILE` is set; no keystore
    exists in repo or CI. `mobile-ci.yml` builds **debug APK only** — there is no signed
    release artifact to upload to Play Console. Play Store submission cannot start.
  - **DONE =** `./gradlew :mobile:composeApp:bundleRelease` with keystore env vars produces a
    signed `.aab`; `apksigner verify --print-certs` shows the release cert; keystore + creds
    stored outside git (documented in a runbook).

- [ ] **Run the security penetration test pass before public launch** **[Tier 1.6]**
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

- [x] **Lock delivery/read-receipt correctness with explicit group-scenario tests** **[Tier 1.2]**
  — *Done (PR #35). `DeliveryStatusTest` covers 13 scenarios incl. 1:1 SENT→DELIVERED→READ, group
  partial-read stays DELIVERED, group all-read → READ, recipient-own-row isolation. Green in CI.*

- [x] **Encrypt media blobs before MinIO upload (close the E2E media gap)** **[Tier 1.4]**
  — *Done (wired behind `E2EConfig.MEDIA_ENABLED`, default OFF; 1:1 Android). `MediaEncryptor` +
  `SymmetricCipher` (AES-256-GCM) encrypt the compressed bytes; per-media key travels inside the
  (Signal-encrypted) message body; tamper/wrong-key → `MediaDecryptException`. iOS NoOp → plaintext
  fallback. 12 shared `jvmTest` green. **Flag stays OFF; flip needs sign-off + crypto review.***

## P1 — Needed before / during public launch  *(Tier 1)*

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

- [x] **Implement the message backup job (currently a no-op placeholder)**
  — *Done. `BackupService.createBackup` now serializes the user's conversations + messages to a
  JSON archive, uploads it to MinIO via a new `BackupArchivePort` out-port + `MinioBackupArchiveAdapter`,
  and persists the real presigned URL + byte size + message/conversation counts; failures mark the
  backup `FAILED` instead of a false `COMPLETED`. 4 unit tests green (`BackupServiceTest`), ArchUnit
  green. Remaining for full "restore reproduces the data": a download/restore endpoint + media-blob
  inclusion (archive currently captures message metadata + text/refs).*
  - `backend/src/main/kotlin/com/muhabbet/messaging/domain/service/BackupService.kt` (real archive),
    `.../domain/port/out/BackupArchivePort.kt` (new), `.../adapter/out/external/MinioBackupArchiveAdapter.kt`
    (new), `shared/config/AppConfig.kt` (bean rewired)
  - **DONE =** backup serializes the user's messages, uploads to MinIO, and persists a real
    presigned URL + counts; a restore (or download) reproduces the data.

- [ ] **Configure Sentry DSN in production** **[Tier 1.6 — needed to SEE E2E canary failures]**
  - `infra/docker-compose.prod.yml` & `docker-compose.prod.yml` (`SENTRY_DSN: ${SENTRY_DSN:-}`
    → empty), `.env.prod`
  - **Why**: Crash/error reporting code is present but DSN is unset, so no backend errors are
    captured in production — blind to incidents at launch.
  - **DONE =** `SENTRY_DSN` set in `.env.prod`; a test exception appears in the Sentry project.

- [x] **Triage the open Dependabot group PR (#40, 37 updates)** — *Done 2026-06-06: CLOSED.
  Built the branch (JDK21/Kotlin 2.4.0/Gradle 9.5.1): `:backend:test` 346/0 and `:shared:jvmTest`
  48/0 both pass, BUT `:mobile:composeApp:compileCommonMainKotlinMetadata` FAILS (succeeds on main)
  — the Compose-MP 1.10.3→1.11.1 bump can't resolve iOS-native (`ios_x64`) variants under Kotlin
  2.4.0, breaking the whole mobile KMP module. Grouped 37-update bump isn't separable cleanly.
  Triage record posted on the PR. Dependabot also auto-closed it concurrently. **Follow-up (P2):**
  re-attempt as smaller scoped PRs each gated on the `compileCommonMainKotlinMetadata` iOS-resolution
  canary — (a) Gradle wrapper 9.4.1→9.5.1 alone, (b) backend-only deps, (c) Kotlin+Compose+AGP only
  once Compose-MP resolves Apple-native variants under the target Kotlin.*

- [ ] **Run k6 load tests against a production-like environment and record results** **[Tier 1.3]**
  - `infra/load-tests/http-endpoints.js`, `infra/load-tests/websocket-load.js` (scripts exist,
    never executed at scale)
  - **Why**: Single 8GB host; WS fan-out + Redis pub/sub broadcaster unproven under load.
  - **DONE =** a load run report (RPS, p95 latency, error rate, WS concurrency ceiling) committed
    under `docs/qa/` with any tuning follow-ups filed.

## P2 — Quality / hardening / completeness

- [ ] **Close the 6 client-side UI `TODO` stubs** (dead buttons that do nothing) **[Tier 1.5]**
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

## P2/P3 — Tier 2 multi-device (continuation)

- [~] **Multi-device linked sessions — NON-CRYPTO scaffolding** **[Tier 2.4]** — *Shipped 2026-06-06
  behind `multi-device.enabled` / `MultiDeviceConfig.ENABLED` (default OFF). Data model
  (`V18__multi_device_linking.sql`), `DeviceLinkController` (begin/complete/list/revoke),
  `DeviceLinkingService`, mobile `LinkedDevicesScreen`/`LinkDeviceScreen`, i18n TR+EN. 23 backend +
  5 shared tests green. Crypto stubbed at `DeviceLinkCrypto` (NotYetImplemented).*
  - **Remaining (NON-crypto, buildable now):** `message_device_delivery` fan-out rows + per-device
    delivery aggregation (S2 schema in design §5); platform QR **render/scan** (`expect`/adapter —
    Android CameraX/ML-Kit, iOS AVFoundation).
  - **Remaining (BLOCKED on libsignal):** implement `DeviceLinkCrypto` (per-device X3DH-on-link),
    fan-out encrypt-per-device, forward-secrecy on revoke. **DONE (full) =** a second logged-in
    device decrypts + sees new and recent messages.

## P3 — Growth / optional (post-launch)

- [ ] **iOS LiveKit voice-call bridge** — `iosMain/.../platform/CallEngine.ios.kt` is a stub
  (connect sets a bool, mute/speaker no-op). Voice calls work on Android only.
  **DONE =** iOS joins a LiveKit room and carries audio.
- [ ] **Web / Desktop client** (Kotlin/JS or React+TS, QR device-linking, message sync). Power-user demand.
- [ ] **Group voice/video calls** (multi-party LiveKit rooms, participant grid).
- [ ] **CDN for media delivery** at scale (when media traffic justifies it).
- [ ] **iOS Crash reporting** — `CrashReporter.ios.kt` is NSLog-only; wire Sentry CocoaPod for parity.
- [ ] **App Store / TestFlight submission** (gated on the iOS P1 items above).
