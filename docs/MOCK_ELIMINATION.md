# Mock / Placeholder Elimination Ledger

> **Goal:** zero *hidden* mocks, fakes, or `NotImplemented` paths reaching production. This is the
> single source of truth for every production (non-test) placeholder in the codebase, classified so
> the owner can act on each one.
>
> **Scope:** production source only. Test sources (`src/test`, `src/commonTest`) are intentionally
> excluded — mocks there are correct and expected. Every `file:line` below was grep-confirmed against
> `HEAD = b5385ba` on branch `loop3/mock-inventory`.
>
> **Method:** grepped production `*.kt` (excluding `*/src/test/**`, `*/src/commonTest/**`) for
> `Mock`, `NoOp`, `Fake`, `Stub`, `NotYetImplemented`, `NotImplementedError`,
> `UnsupportedOperationException`, `TODO(`, `/* TODO`, "coming soon", "not implemented",
> "placeholder", and empty `onClick = {}`. **No `TODO(` / `/* TODO` markers exist in production
> source.** Date: 2026-06-08.

## Categories

| Code | Meaning |
|------|---------|
| **DEV-FALLBACK** | Graceful degradation that is correct to keep, but MUST be disabled in prod via a flag/env. Document the exact prod config that turns it off. |
| **CRYPTO-BLOCKED** | NoOp / NotYetImplemented crypto, flag-OFF + honest-UI by design. Blocked on the libsignal upgrade (CLAUDE.md → "libsignal upgrade (BLOCKED)"). **Never fake — "do not guess crypto."** |
| **PLATFORM-STUB** | iOS/desktop/wasm `expect`/`actual` gap needing a real toolchain (macOS/Xcode, emulator). Cross-ref `docs/design/PLATFORM_EXPANSION_PLAN.md`. |
| **FALSE-POSITIVE** | Grep hit is a comment, variable name, UI hint-text, or a real handler — not a placeholder. Listed for trust. |

---

## 1. DEV-FALLBACK — must be OFF in prod (and is, by default)

These are real, intentional graceful-degradation beans/adapters. Each is gated by a Spring
`@ConditionalOnProperty` so that the **real** implementation replaces it when the prod env var is set.
The risk is not that the code is wrong — it is that someone forgets to set the env var. See the
**Prod-readiness config checklist** (§5).

| file:line | Stands in for | Category | Current trigger (what activates the mock) | "Make it real" requires | Verifiable on this Linux CI host? |
|-----------|---------------|----------|-------------------------------------------|-------------------------|-----------------------------------|
| `backend/src/main/kotlin/com/muhabbet/auth/adapter/out/external/MockOtpSender.kt:10` (`@ConditionalOnProperty` line 9) | Real SMS OTP delivery (logs OTP to console instead of texting it) | **DEV-FALLBACK** | Active when `muhabbet.sms.provider` is `mock` **or missing** (`matchIfMissing = true`). Bound from `SMS_PROVIDER` env (`application.yml:102`). **`docker-compose.prod.yml:26` defaults `SMS_PROVIDER` to `mock`** → mock is live in prod unless the deployer sets `SMS_PROVIDER=netgsm`. | Set `SMS_PROVIDER=netgsm` + `NETGSM_USERCODE`/`NETGSM_PASSWORD`. Real adapter `NetgsmOtpSender.kt:12` is gated `havingValue = "netgsm"`. | **Partially.** Bean-wiring/`@ConditionalOnProperty` selection is JVM-testable here; **actual SMS delivery is NOT** (no live Netgsm account). |
| `backend/src/main/kotlin/com/muhabbet/messaging/adapter/out/external/NoOpPushNotificationAdapter.kt:10` (`@ConditionalOnProperty` line 9) | FCM push delivery (logs "skipped" instead of pushing) | **DEV-FALLBACK** | Active when `muhabbet.fcm.enabled` is `false` **or missing** (`matchIfMissing = true`). Bound from `FCM_ENABLED` (`application.yml:146`, default `false`). | Set `FCM_ENABLED=true` + Firebase credentials. Real adapter `FcmPushNotificationAdapter.kt:18` gated `havingValue = "true"`. **`docker-compose.prod.yml:31` already sets `FCM_ENABLED: "true"` → real FCM is live in prod.** | **Partially.** Bean selection testable; **real FCM delivery NOT** (no live FCM project/device). |
| `backend/src/main/kotlin/com/muhabbet/messaging/adapter/out/external/LiveKitRoomAdapter.kt:87` (`NoOpCallRoomProvider`, `@ConditionalOnProperty` line 86) | LiveKit room creation + participant tokens (returns empty token / `p2p-` room) | **DEV-FALLBACK** | Active when `muhabbet.livekit.enabled` is `false` **or missing** (`matchIfMissing = true`). Bound from `LIVEKIT_ENABLED` (`application.yml:153`, default `false`). **Not set in `docker-compose.prod.yml` → NoOp is live in prod.** | Set `LIVEKIT_ENABLED=true` + `muhabbet.livekit.api-key/api-secret/server-url`. Real adapter `LiveKitRoomAdapter.kt:25` gated `havingValue = "true"`. **OR** keep calls honestly disabled (signaling-only / no media server). | **Partially.** Bean selection + JWT-token shape testable on JVM; **real LiveKit room join NOT** (no live LiveKit Cloud account). |

**Note on signal flow:** with `NoOpCallRoomProvider` active, WS call **signaling** still works (SDP/ICE
relay), but there is no media server — calls will not connect media. Either enable LiveKit or surface
calls as unavailable in the UI; do not ship a "call" button that silently fails.

---

## 2. CRYPTO-BLOCKED — flag-OFF + honest-UI by design (do NOT fake)

All of these are blocked on the **libsignal upgrade** (CLAUDE.md → "libsignal upgrade (BLOCKED)":
libsignal frozen at `0.86.5` on Maven Central; the Android Signal code targets a ≤0.70-era API and
does not compile against any current pin). They stay until an **owner-driven, on-device /
emulator-verified crypto rewrite** lands. The product is honest about this: E2E flags default OFF and
the UI makes no false padlock claim (CLAUDE.md, PR #61). **"Do not guess crypto" — none of these may
be replaced with home-grown encryption.**

| file:line | Stands in for | Category | Current trigger | "Make it real" requires | Verifiable here? |
|-----------|---------------|----------|-----------------|-------------------------|------------------|
| `shared/src/commonMain/kotlin/com/muhabbet/shared/port/EncryptionPort.kt:25` (`NoOpEncryption`) | Signal Protocol message encrypt/decrypt (passes plaintext through) | **CRYPTO-BLOCKED** | Wired on **both** platforms: `PlatformModule.android.kt:41` and `PlatformModule.ios.kt:29`. Behaviourally inert because text E2E is gated `E2EConfig.ENABLED = false` (`crypto/E2EConfig.kt:27`). | libsignal re-integration (Android) + Kotlin/Native libsignal bridge (iOS), then crypto review + 2-device round-trip on a real build. | **No.** Needs Android emulator + real libsignal; no JVM/common test exercises real libsignal. |
| `shared/src/commonMain/kotlin/com/muhabbet/shared/port/E2EKeyManager.kt:66` (`NoOpKeyManager`) | X3DH / Double-Ratchet key management (returns `"noop-…"` placeholder keys — see lines 73, 80, 86) | **CRYPTO-BLOCKED** | Wired on both platforms: `PlatformModule.android.kt:40`, `PlatformModule.ios.kt:28`. | Same libsignal re-integration as above. | **No.** |
| `mobile/composeApp/src/androidMain/kotlin/com/muhabbet/app/di/PlatformModule.android.kt:40-41` | Android was supposed to use real `SignalKeyManager`/`SignalEncryption`; now falls back to NoOp | **CRYPTO-BLOCKED** | Hard-wired to NoOp because the 4 Signal files are `*.kt.disabled` (don't compile against `0.86.5`). Comment lines 32-39 document the block. | Re-enable the disabled files **after** the libsignal rewrite compiles + is verified. | **No.** `assembleDebug`/`androidMain` does not build on this host (uncached Firebase, no emulator). |
| `mobile/composeApp/src/androidMain/.../crypto/SignalKeyManager.kt.disabled` | Real Android Signal key manager (X3DH + Double Ratchet) | **CRYPTO-BLOCKED** | Disabled (renamed `*.kt.disabled`) — not compiled into the build at all. | libsignal API rewrite (Curve→ECKeyPair, IdentityChange return, 11-arg Kyber PreKeyBundle, SessionCipher localAddress) per CLAUDE.md. | **No.** |
| `mobile/composeApp/src/androidMain/.../crypto/SignalEncryption.kt.disabled` | Real Android `EncryptionPort` over libsignal | **CRYPTO-BLOCKED** | Disabled file. | Same as above. | **No.** |
| `mobile/composeApp/src/androidMain/.../crypto/PersistentSignalProtocolStore.kt.disabled` | EncryptedSharedPreferences-backed Signal store | **CRYPTO-BLOCKED** | Disabled file. | Same as above. | **No.** |
| `mobile/composeApp/src/androidMain/.../crypto/InMemorySignalProtocolStore.kt.disabled` | In-memory Signal store (earlier MVP store) | **CRYPTO-BLOCKED** | Disabled file. | Same as above. | **No.** |
| `mobile/composeApp/src/iosMain/kotlin/com/muhabbet/app/crypto/SymmetricCipher.ios.kt:17` (whole `object`, throws lines 23/26/29/32/35) | iOS AES-256-GCM media crypto (CryptoKit) | **CRYPTO-BLOCKED** | Always — `actual object SymmetricCipher` throws `NotImplementedError("MEDIA_E2E_IOS_NOT_IMPLEMENTED")`. **Fails closed**: `MediaEncryptor.kt:82-84` catches and uploads plaintext (honest — no `MediaKeyMaterial` attached). | Implement with iOS CryptoKit `AES.GCM.seal/open`. | **No.** Needs macOS/Xcode (not available here). |
| `shared/src/commonMain/kotlin/com/muhabbet/shared/port/DeviceLinkCrypto.kt:56` (`NotYetImplementedDeviceLinkCrypto`, throws lines 65-67) | Per-device X3DH-on-link / fan-out / forward-secrecy-on-revoke for companion devices | **CRYPTO-BLOCKED** | Shipped default; selected via `mobile/.../multidevice/DeviceLinkCrypto.kt:21`. Whole multi-device feature gated OFF: `MultiDeviceConfig.ENABLED = false` (mobile) and `MULTI_DEVICE_ENABLED` default `false` (`application.yml:131`). | libsignal multi-device session transfer (blocked). Design: `docs/design/T2-multi-device-linked-sessions.md`; ADR `docs/adr/0007-companion-device-trust.md`. | **No.** Throws loudly by design; the non-crypto registry/transport slice IS testable but the crypto is not. |
| `mobile/composeApp/src/iosMain/kotlin/com/muhabbet/app/platform/FirebasePhoneAuth.ios.kt:31` (`UnsupportedOperationException`) + `isAvailable()=false` (line 20) | iOS Firebase Phone Auth | **CRYPTO-BLOCKED** (auth-provider stub; honest) | Always returns unavailable → app **falls back to backend OTP**, which works. Not a silent failure. | Firebase iOS SDK via CocoaPods/SPM. | **No.** Needs macOS/Xcode + Firebase. |

> The iOS DI also wires NoOp explicitly: `mobile/.../di/PlatformModule.ios.kt:28-29` (`NoOpKeyManager` /
> `NoOpEncryption`). Same posture as Android. Full E2E rollout gating + kill-switch:
> `docs/e2e-rollout-runbook.md`. **Do not flip `E2EConfig.ENABLED` / `MEDIA_ENABLED` /
> `MultiDeviceConfig.ENABLED` without owner sign-off + crypto review.**

---

## 3. PLATFORM-STUB — iOS `expect`/`actual` gaps needing a real toolchain

Cross-reference `docs/design/PLATFORM_EXPANSION_PLAN.md` §3.4 ("Which iOS actuals are stubs"). These
are not crypto and not flag-gated dev-fallbacks — they are platform integrations that need
macOS/Xcode + native SDK bridges that **cannot** be built or verified on this Linux CI host.

| file:line | Stands in for | Category | Current trigger | "Make it real" requires | Verifiable here? |
|-----------|---------------|----------|-----------------|-------------------------|------------------|
| `mobile/composeApp/src/iosMain/kotlin/com/muhabbet/app/platform/CallEngine.ios.kt:10` (stub body — comments lines 15/26/30) | LiveKit WebRTC client on iOS (connect/mute/speaker) | **PLATFORM-STUB** | Always — `actual class CallEngine` is an empty stub; `connect()` just sets `connected=true` with no real media. iOS calls are non-functional. | Bridge LiveKit Swift SDK via CocoaPods/SPM + Kotlin/Native interop. | **No.** macOS/Xcode + iOS device. |
| `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/platform/CrashReporter.kt:7` (`expect object`) | Crash reporting; iOS `actual` is stubbed (CLAUDE.md notes iOS stub) | **PLATFORM-STUB** | Android `actual` uses Sentry; the iOS `actual` is a stub ("implement when deploying to App Store", line 5). | Wire Sentry Cocoa (or equiv) in the iOS `actual`. | **No.** macOS/Xcode. |

> Other iOS `actual`s referenced in CLAUDE.md (AudioPlayer, AudioRecorder, ContactsProvider,
> PushTokenProvider, ImagePicker, FilePicker, ImageCompressor, LocaleHelper, CameraPicker,
> KeychainHelper, SpeechTranscriber) are **implemented** (not stubs) and did not surface in the
> placeholder grep — they are out of scope for this ledger. The three remaining iOS gaps are the two
> rows above plus the iOS crypto/auth stubs already captured in §2 (`SymmetricCipher.ios`,
> `FirebasePhoneAuth.ios`).

---

## 4. FALSE-POSITIVE — grep hits that are NOT placeholders

Listed so the ledger is trustworthy: these matched the grep but are real code, hint-text, comments,
or honest naming.

| file:line | Why it matched | Why it's NOT a placeholder |
|-----------|----------------|----------------------------|
| `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/ui/chat/MessageBubble.kt:130` | `onClick = {}` | The empty tap is intentional inside `combinedClickable` — the real interactions are `onLongClick = onLongPress` (line 131) and `onDoubleClick = onDoubleTap` (line 132). A bare tap on a chat bubble correctly does nothing. |
| `backend/.../auth/domain/service/AuthService.kt:50,89` (`mockEnabled`, `mockCode`) | "mock" in identifier | Real dev-mode plumbing: when `mockEnabled` (bound from `OTP_MOCK_ENABLED`, default `false`), the OTP is echoed back in `mockCode` for local testing. Disabled in prod by env; the field is `null` in prod. |
| `backend/.../auth/domain/port/in/RequestOtpUseCase.kt:10` (`mockCode`) | DTO field name | Honest field carrying the dev-echo code; `null` unless `OTP_MOCK_ENABLED=true`. |
| `backend/.../shared/config/OtpProperties.kt:11` (`mockEnabled = false`) | config property | Real config binding for `OTP_MOCK_ENABLED`, default OFF. |
| `backend/.../shared/config/SmsProperties.kt:7` (`provider = "mock"`) | default value | The `"mock"` literal is the SMS-provider discriminator; the **default** is overridden in prod by `SMS_PROVIDER` (see §1 / §5). |
| `backend/.../shared/config/AppConfig.kt:102` (`mockEnabled = …`) | wiring | Passes the real `OtpProperties.mockEnabled` into `AuthService`. |
| `backend/.../auth/adapter/in/web/AuthController.kt:41` (`mockCode = result.mockCode`) | field passthrough | Surfaces the (null-in-prod) dev code in the response DTO. |
| `shared/.../shared/dto/Dtos.kt:33` (`mockCode: String? = null`) | DTO field | Shared DTO field; null in prod. Comment: "OTP code returned only in mock/dev mode". |
| `mobile/.../navigation/AuthComponent.kt:39,67,82,83,89` + `ui/auth/OtpVerifyScreen.kt:49,55,93,100,148,200` + `ui/auth/PhoneInputScreen.kt:48,149,159,161,169` (`mockCode`) | "mock" in identifier | Mobile plumbing for the dev-mode OTP echo. `OtpVerifyScreen.kt:100` shows "Dev Mode — Code: $mockCode" **only when `mockCode != null`** (i.e. only in dev). In prod the backend sends `null`, so the dev banner never renders. |
| `backend/.../messaging/domain/service/BackupService.kt:23` ("Replaces the previous placeholder…") | word "placeholder" in comment | Documents that the **old** placeholder was REMOVED; this service now produces a real archive. Not a current placeholder. |
| `backend/.../messaging/adapter/out/external/PushNotificationContent.kt:12` ("if the placeholder copy evolves") | word "placeholder" in comment | Refers to notification **copy text**, not a code stub. |
| `mobile/.../ui/auth/ProfileSetupScreen.kt:58` ("// Avatar placeholder") | comment | UI element (initials avatar shown before a photo is set) — real, intended UI, not a TODO. |
| `mobile/.../ui/chat/VideoBubble.kt:63` ("Thumbnail background or placeholder") | comment | Real fallback rendering when no thumbnail exists. |
| `mobile/.../ui/conversations/ConversationListScreen.kt:1038,1047,1056,1066` ("…placeholder" comments) | comments | Skeleton-loader shimmer placeholders — an intentional loading-state UI, not a stub. |
| All `placeholder = { Text(stringResource(Res.string.*_placeholder)) }` hits (`ChatDialogs.kt:373/381/389/433/444`, `GifStickerPicker.kt:122`, `MessageInputPane.kt:250`, `ConversationListScreen.kt:268/598`, `NewConversationScreen.kt:210`, `CreateGroupScreen.kt:248`, `GroupInfoScreen.kt:124`, `HomeShellScreen.kt:104-105/147`, `SettingsScreen.kt:258`, `PhoneInputScreen.kt:108`, `OtpVerifyScreen.kt:119`, `ConversationListScreen.kt:551`) | the Compose `TextField(placeholder = …)` parameter | This is the standard Material `TextField` **hint text** API. Not a placeholder implementation. |
| `mobile/.../crypto/MediaEncryptor.kt:122,133` & `:27,83` ("fake", "NoOp" in comments) | words in comments | KDoc describing the test seam and the iOS-NoOp fail-closed fallback (already counted in §2). Production code path is real. |
| `mobile/.../crypto/SymmetricCipher.kt:10-11` (commonMain, "NoOp stub" in comment) | comment | KDoc on the `expect` describing the iOS `actual`; the iOS stub itself is the §2 row. The Android `actual` is real JCE. |
| `mobile/.../multidevice/MultiDeviceConfig.kt:14` ("NotYetImplemented" in comment) | comment | Documentation pointing at the §2 `DeviceLinkCrypto` block. |
| `mobile/.../di/AppModule.kt:78` ("iOS: NoOpKeyManager + NoOpEncryption (stub)") | comment | Documentation comment; the actual wiring lives in the platform modules (§2). |
| `backend/.../auth/domain/service/DeviceLinkingService.kt:22,105,147` & `auth/domain/port/in/LinkDeviceUseCase.kt:12` ("NotYetImplemented" in comments) | comments | Document the crypto boundary (§2 `DeviceLinkCrypto`). The service's **non-crypto** registry/transport work is real and tested; it does not call the blocked crypto methods. |
| `shared/.../port/E2EEnvelope.kt:20` ("NoOp elsewhere" in comment) | comment | KDoc describing where encryption is real vs NoOp. The type itself just carries opaque ciphertext. |
| `mobile/.../di/PlatformModule.android.kt:18-19,36,38` (imports + comments) | imports/comments around the NoOp wiring | The load-bearing lines are `:40-41` (counted in §2). These are the import statements and explanatory comments. |

---

## 5. Prod-readiness config checklist

To guarantee **no DEV-FALLBACK is live in production**, the following must hold in
`infra/docker-compose.prod.yml` (owned by infra/config agents — **describe only, do not edit here**).
Current state at `HEAD` is noted per row.

| Concern | Required prod setting | Disables which fallback | Current state in `docker-compose.prod.yml` | Action |
|---------|----------------------|-------------------------|--------------------------------------------|--------|
| **Real SMS OTP** | `SMS_PROVIDER=netgsm` **and** `OTP_MOCK_ENABLED=false` **and** `NETGSM_USERCODE`/`NETGSM_PASSWORD` set | `MockOtpSender` (§1) + dev-code echo | `OTP_MOCK_ENABLED` defaults `false` (`:25`) ✓ but **`SMS_PROVIDER` defaults to `mock` (`:26`)** ✗ and Netgsm creds default empty (`:27-28`) | **GAP:** set `SMS_PROVIDER=netgsm` + Netgsm creds, else OTPs are only logged, never texted. |
| **Push notifications** | `FCM_ENABLED=true` + Firebase creds mounted | `NoOpPushNotificationAdapter` (§1) | `FCM_ENABLED: "true"` hard-set (`:31`); creds mounted (`:32,37`) ✓ | **OK.** Real FCM is live. |
| **Voice/video calls** | Either `LIVEKIT_ENABLED=true` + `api-key`/`api-secret`/`server-url`, **or** keep calls disabled and surface them as unavailable in the UI | `NoOpCallRoomProvider` (§1) | `LIVEKIT_ENABLED` not present → defaults `false` (`application.yml:153`) ✗ | **DECISION NEEDED:** configure LiveKit, or honestly disable the call entry points. Do not ship a call button that silently no-ops media. |
| **E2E text encryption** | Keep **OFF** (`E2EConfig.ENABLED=false`) until libsignal rewrite + crypto review | n/a — staying OFF is correct | Mobile constant `false` (`crypto/E2EConfig.kt:27`) ✓ | **HOLD.** Do not flip while NoOp is wired (§2). UI is honest (no false padlock). |
| **E2E media encryption** | Keep **OFF** (`E2EConfig.MEDIA_ENABLED=false`) | n/a | Mobile constant `false` (`crypto/E2EConfig.kt:39`) ✓ | **HOLD.** iOS `SymmetricCipher` fails closed to plaintext (§2). |
| **Multi-device linking** | Keep **OFF** (`MULTI_DEVICE_ENABLED=false`, `MultiDeviceConfig.ENABLED=false`) | n/a — crypto seam throws by design | `MULTI_DEVICE_ENABLED` not set → `false` (`application.yml:131`); mobile `false` (`MultiDeviceConfig.kt:19`) ✓ | **HOLD.** Endpoints return 403 when OFF; crypto is `NotYetImplemented` (§2). |
| **Firebase phone auth (Android)** | `FIREBASE_ENABLED=true` + creds | n/a (backend uses it; iOS falls back to backend OTP) | `FIREBASE_ENABLED: "true"` (`:29`), creds mounted (`:30,37`) ✓ | **OK.** iOS honestly falls back to backend OTP (§2). |

### Cross-linked V&V / context docs
- `docs/e2e-rollout-runbook.md` — E2E rollout gates + no-redeploy kill-switch (CRYPTO-BLOCKED items).
- `docs/design/T2-multi-device-linked-sessions.md` + `docs/adr/0007-companion-device-trust.md` — multi-device crypto seam.
- `docs/design/PLATFORM_EXPANSION_PLAN.md` §3.4 — iOS `expect`/`actual` stub inventory (PLATFORM-STUB).
- `CLAUDE.md` → "libsignal upgrade (BLOCKED)" — the standing dependency gating every CRYPTO-BLOCKED row.
- `docs/findings/2026-06-07-session.md` + `CHANGELOG` 2026-06-07 — why E2E was set to NoOp (PR #49) and the honest-UI change (PR #61).

---

## 6. Category counts

| Category | Count |
|----------|-------|
| **DEV-FALLBACK** | 3 |
| **CRYPTO-BLOCKED** | 10 |
| **PLATFORM-STUB** | 2 |
| **FALSE-POSITIVE** | 22 (groups of related hits) |

**Bottom line:** the only items that can leak into prod incorrectly are the **3 DEV-FALLBACKs**, and
all 3 are env-gated. Of those, **two need action before launch** — `SMS_PROVIDER=netgsm` (otherwise
OTPs are never texted) and a LiveKit decision (configure or honestly disable calls); FCM is already
correctly enabled. Every CRYPTO-BLOCKED and PLATFORM-STUB item is intentionally OFF/honest and gated
on an owner-driven, toolchain-requiring rewrite that **cannot be verified on this Linux CI host** (no
Docker daemon, no Android emulator/KVM, no macOS/Xcode, no live SMS/FCM/LiveKit accounts). None may
be faked.
