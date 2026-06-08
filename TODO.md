# MUHABBET — TODO (TACTICAL)

> **Last refreshed**: 2026-06-07 (Tier 1 = DONE; Tier 2 multi-device NON-CRYPTO scaffolding shipped
> behind `multi-device.enabled` default OFF; Android debug build unblocked via PRs #49–#53; **E2E now
> TEMPORARILY DISABLED — NoOp placeholder, NOT secure**, libsignal disabled pending re-integration).
> Aligned to [`ROADMAP.md`](ROADMAP.md).
> **Dev status**: Backend LIVE + healthy at `https://muhabbet-api.rollingcatsoftware.com`
> (db/redis/ssl all UP). **Do NOT deploy** — E2E is DISABLED (NoOp = plaintext) and multi-device ships
> dark (flag OFF).
> **Convention**: P0 = launch-blocker / correctness / security · P1 = needed before public
> launch · P2 = quality/hardening · P3 = growth/optional.
> **Tier alignment**: P0/P1 below = **Tier 1** (core-messaging hardening & trust) in `ROADMAP.md`.
> P2 ≈ Tier-1 polish, P3 ≈ Tier 2/3. Each item: imperative title · `files` · why ·
> **DONE =** verifiable condition · **[Tier x.y]** = ROADMAP task.
>
> **Tier-1 engineering = DONE** (E2E text wired/#31, receipts/#35, media-blob E2E, 6 dead buttons/
> #35+#36). The open P0/P1 below are now mostly **operator/ops** tasks (signed AAB, pen-test, Sentry
> DSN, k6) + the iOS catch-up, not new feature code. **Caveat (2026-06-07):** the E2E code path is
> wired but the Android Signal impl is **disabled** (NoOp = plaintext) — re-integrating libsignal is
> now a hard prerequisite before E2E can be turned on at all (new P0 below).

---

## P0 — Launch blockers & security-critical  *(Tier 1)*

- [ ] **[VERIFY ON DEVICE] PR #61 not yet runtime-tested on a phone**
  - `mobile/.../ui/profile/UserProfileScreen.kt` (padlock → info icon when `E2EConfig.ENABLED=false`),
    `mobile/.../ui/settings/PrivacyDashboardScreen.kt` (E2E card → transport-encrypted state),
    `mobile/.../platform/FirebasePhoneAuth.kt` + `FirebasePhoneAuth.android.kt` (`PhoneAuthErrorCode`),
    `mobile/.../ui/auth/PhoneInputScreen.kt` (`shouldFallbackToBackendOtp`), `mobile/.../BuildInfo.kt` (`DEBUG`)
  - **Why**: PR #61 (honest-E2E UI — no padlock, transport-encrypted state gated on `E2EConfig.ENABLED`;
    locale-safe OTP error-code fallback; stopped logging the auth header) **compiles** (commonMain +
    androidMain green) but was **NOT confirmed on a physical device**. UI-state and locale-fallback bugs
    don't always surface at compile time.
  - **DONE =** reinstall the APK and verify on a real phone that the profile + privacy screens show the
    **transport-encrypted (TLS)** state — *not* "end-to-end encrypted" / no false padlock — while E2E is
    OFF, and that the OTP backend fallback still works (incl. on a Turkish-locale device).

- [ ] **Re-integrate the libsignal API (E2E currently NoOp = plaintext, NOT secure)** **[Tier 1.1]**
  - `mobile/composeApp/src/androidMain/.../crypto/SignalKeyManager.kt.disabled`,
    `SignalEncryption.kt.disabled`, `InMemorySignalProtocolStore.kt.disabled`,
    `PersistentSignalProtocolStore.kt.disabled` (the 4 disabled files);
    `mobile/.../di/PlatformModule.android.kt` (currently wires `NoOpKeyManager()` + `NoOpEncryption()`);
    `mobile/composeApp/build.gradle.kts` (pinned `libsignal-android:0.86.5`); `settings.gradle.kts`
    (Signal Maven repo added by PR #42)
  - **Why**: To unblock the 2026-06-07 Android debug build, the Signal files (which don't compile
    against the pinned 0.86.5 API — Curve removed, `saveIdentity` contract, Kyber `PreKeyBundle`,
    `SessionCipher` `localAddress`; see `CLAUDE.md` → "libsignal upgrade (BLOCKED)") were disabled and
    Android fell back to NoOp. **NoOp returns plaintext** — so E2E is now not just flag-OFF, it has no
    real implementation. This is the standing blocker for the whole crypto track (Tier 1.1, 2.4, 3.1).
    **Do not re-enable the disabled files or flip the E2E flag until this rewrite lands + is crypto-reviewed.**
  - **DONE =** the 4 files are rewritten against the current libsignal API, compile + two-device
    round-trip verified on a real Android build + emulator, DI re-wires `SignalKeyManager`/`SignalEncryption`,
    and a crypto review signs off — only then is flipping `E2EConfig.ENABLED` even on the table.

- [ ] **Make login + notifications actually work in prod (Twilio/Netgsm SMS + FCM)** **[Tier 1.6]**
  - `mobile/.../ui/auth/PhoneInputScreen.kt` (`shouldFallbackToBackendOtp()` fallback),
    `mobile/.../platform/FirebasePhoneAuth.android.kt`, backend OTP sender (`MockOtpSender` →
    `NetgsmOtpSender`/Twilio via `@ConditionalOnProperty`), `infra/docker-compose.prod.yml`
    (`OTP_MOCK_ENABLED`, `FCM_ENABLED`, `FIREBASE_CREDENTIALS_PATH`)
  - **Why**: Firebase phone-auth is API-key-restricted on the current build (login now degrades to
    backend OTP), and the prod backend still uses the mock OTP sender — so real users can't receive a
    code. FCM push must also be verified end-to-end so notifications actually arrive. Until both work,
    a real user cannot log in or be notified.
  - **DONE =** a real Turkish number receives an OTP SMS (via Twilio or Netgsm) and logs in; a push
    notification is delivered to a backgrounded device — both verified on a physical device against prod.

- [ ] **Merge PR #49 (build unblock), then retarget the stacked feature/fix PRs**
  - PRs: #49 (`claude/fix-firebase-bom-ktx` → main), #50 (scheduled-send), #51 (communities add-group),
    #52 (mute-duration), #53 (Ktor test fix) — all currently based on `claude/fix-firebase-bom-ktx`
  - **Why**: #50–#53 are stacked on #49 so their diffs show only the feature. Once #49 merges to main,
    GitHub auto-retargets the stacked PRs to main; review/merge them after.
  - **DONE =** #49 merged to main; #50–#53 retargeted to main, reviewed, and merged (or closed) as
    appropriate.

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

- [x] **Force `Content-Disposition: attachment` on media presigned URLs (media V&V Finding B)**
  — *Done. `MinioMediaStorageAdapter.getPresignedUrl` now signs
  `extraQueryParams(mapOf("response-content-disposition" to "attachment"))` into every presigned GET
  (MinIO Java SDK 9.0.0 — `BaseArgs.Builder.extraQueryParams(Map)`), so a document uploaded with an
  inline-rendering type (`text/html` / `image/svg+xml`) downloads instead of rendering from the media
  origin (stored-XSS / phishing surface). Applied uniformly (the in-app image/audio loader uses raw
  bytes, not the header). Endpoint-rewrite seam preserves the signed query param. 3 unit tests in
  `MinioMediaStorageAdapterTest`. See `docs/reviews/2026-06-08-media-module-vv.md` Finding B.*

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

- [ ] **Honor `onlineStatusVisibility` on the realtime WS presence path** (2026-06-08 presence/notif V&V, Finding A — KVKK)
  - `backend/.../messaging/adapter/in/websocket/ChatWebSocketHandler.broadcastPresence` (+ `handleTypingIndicator`)
  - **Why**: The REST presence path (`UserController.resolvePresenceVisibility`) carefully gates
    online/last-seen on the target's `everyone`/`contacts`/`nobody` setting, but the WebSocket
    `broadcastPresence` (online/offline + `lastSeenAt`) and typing indicators are pushed to **all**
    contacts unconditionally — a privacy regression vs. the user's chosen visibility (KVKK data
    minimization). A user who set last-seen to "nobody" still leaks online/offline transitions in
    real time.
  - **DONE =** WS presence/typing broadcasts consult the sender's visibility (load `User` once,
    filter recipients the same way the REST path does, or suppress `lastSeenAt`/online for `nobody`);
    add MockK tests asserting suppression. (Larger change — needs `UserRepository` threaded into the
    handler + a recipient filter; left documented rather than fixed in the V&V pass.)

- [x] **Clean up stale FCM push tokens on `UNREGISTERED`/`INVALID_ARGUMENT`** (2026-06-08 presence/notif V&V, Finding B) — **DONE 2026-06-08**
  - `FcmPushNotificationAdapter.sendPush` now catches `FirebaseMessagingException`, reads
    `getMessagingErrorCode()`, and on a terminal code (`UNREGISTERED` / `INVALID_ARGUMENT` /
    `SENDER_ID_MISMATCH`) calls the new messaging out-port
    `PushTokenInvalidationPort.invalidate(token)` → `DeviceRepositoryPushTokenInvalidationAdapter` →
    `DeviceRepository.clearPushToken(token)` (JPA `findByPushToken` → null → `saveAll`). Transient
    errors keep the token; invalidation is `runCatching`-guarded so it never breaks the send path.
  - **Cross-module mechanism: a domain out-port** (not an event) — synchronous, return-typed, directly
    testable; matches the existing pattern where broadcasters depend on `auth`'s `DeviceRepository`
    public out-port (no JPA import; ArchUnit `messaging → auth.domain.service` ban not tripped).
  - **NoOp hardening:** FCM/NoOp are mutually exclusive on `muhabbet.fcm.enabled`; a non-boolean value
    loads neither (fail-fast) and `NoOpPushNotificationAdapter` logs a startup WARN.
  - Tests (11): `FcmPushNotificationAdapterTest` (8), `DeviceRepositoryPushTokenInvalidationAdapterTest`
    (1), `DevicePersistenceAdapterTest` (2). Not live-verifiable: the real Firebase round-trip (no
    credentials on this host) — only the error-code classification + invalidation wiring are unit-tested.
    See `docs/reviews/2026-06-08-presence-notification-vv.md` Finding B.

- [ ] **Make storage-usage document bucketing exhaustive** (2026-06-08 media V&V, Finding C)
  - `backend/.../media/domain/service/MediaService.getStorageUsage`
  - **Why**: documents are bucketed by `application/` prefix only; non-`application/*` documents are
    stored but uncounted in `documentBytes`/`totalBytes`.
  - **DONE =** documents counted as "not image/* and not audio/*" (or aligned with a doc allowlist).

- [ ] **Migrate `kotlinx.datetime.Instant` → `kotlin.time.Instant`** (2026-06-08 shared KMP V&V, Finding C)
  - `shared/.../model/Models.kt` (typealias usages on `Message`/`Conversation`/`UserProfile`),
    plus every backend + mobile consumer of the shared `Instant` type and the `kotlinx-datetime` dep
    in `shared/build.gradle.kts` / `mobile/composeApp/.../util/DateTimeFormatter.kt`.
  - **Why**: Kotlin 2.3.20 deprecates `kotlinx.datetime.Instant` in favour of stdlib
    `kotlin.time.Instant` (compile emits `'typealias Instant = Instant' is deprecated`). Latent —
    compiles & serializes fine today — but it is a cross-module wire-type touching both backend and
    mobile serialization, so it must be migrated atomically with round-trip tests, not drive-by.
  - **DONE =** shared models use `kotlin.time.Instant`, backend + mobile compile, the
    `WsMessage`/DTO JSON wire format is byte-identical (Instant still ISO-8601), deprecation warning
    gone.

- [ ] **Remove or wire the unused `ALLOWED_VIDEO_TYPES` / `MAX_VIDEO_SIZE_BYTES` rules** (2026-06-08 shared KMP V&V, Finding D)
  - `shared/.../validation/ValidationRules.kt`
  - **Why**: both constants are defined but have **zero** consumers (grep finds only the definition).
    There is no `uploadVideo` path — videos go through the document upload path, which validates
    against `MAX_DOCUMENT_SIZE_BYTES` and no type allowlist. So `ALLOWED_VIDEO_TYPES` is dead and the
    100 MB `MAX_VIDEO_SIZE_BYTES` ≡ `MAX_DOCUMENT_SIZE_BYTES` is redundant (YAGNI). Either delete them
    or, if a dedicated video-upload path is wanted, wire them in `MediaService` like the image/voice
    allowlists. Left documented (not deleted) because a video path is plausibly near-term roadmap.
  - **DONE =** the constants are either consumed by a video-upload validation path or removed.

- [ ] **Wire `InputSanitizer` into the write paths (it has zero production call sites)** (2026-06-08 security V&V, Finding B)
  - `backend/.../shared/security/InputSanitizer.kt` (defined + 15 tests, but `grep -rln InputSanitizer
    backend/src/main` returns only the class itself), `MessageService.sendMessage`/`editMessage`
    (message content), profile/group display-name update paths (`sanitizeDisplayName`).
  - **Why**: `docs/qa/02-security.md` lists InputSanitizer (HTML-escape / control-char strip) as a
    **Deployed** XSS/injection control, but it is never invoked — stored content reaches the DB and
    clients un-sanitised. Today the mobile/CMP client renders text (not HTML) so this is latent, but a
    future web client or any HTML-context render would inherit a stored-XSS gap. Stripping control
    chars (homoglyph/RTL-override/invisible-char injection) is also currently a no-op.
  - **Caution**: HTML-escaping at write time is **lossy/double-encoding-prone** if a client also
    escapes on render — prefer control-char stripping + length caps at the service layer now and push
    HTML-escaping to the (future) HTML render boundary, OR escape once on write and never on render.
    Decide the contract before wiring. This is a behavioural change to stored content → not a drive-by
    fix; do it deliberately with round-trip tests.
  - **DONE =** message content + display/group names pass through `InputSanitizer` (control-char strip
    + length cap at minimum) on the write path, with tests; `docs/qa/02-security.md` updated to match
    reality (status corrected if still not wired).

- [ ] **WS `handleAckMessage`: validate `conversationId` belongs to the acked message** (2026-06-08 security V&V, Finding A note)
  - `backend/.../messaging/adapter/in/websocket/ChatWebSocketHandler.handleAckMessage`
  - **Why**: `markConversationRead(msg.conversationId, userId)` trusts a client-supplied
    `conversationId` independently of `msg.messageId`. The DB write is self-scoped (keyed by `userId`),
    so the blast radius is only the caller's own unread rows in a conversation they belong to — low
    risk — but the two client fields are not cross-checked. `updateStatus` is now membership-guarded
    (fixed this pass); this is the residual hardening.
  - **DONE =** the ack path verifies the message's `conversationId` matches the client-supplied one (or
    derives the conversationId from the message server-side and ignores the client field).

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
