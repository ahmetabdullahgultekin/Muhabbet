# Design: Multi-Platform Expansion — iOS · Desktop · Web · Wasm (Muhabbet)

| | |
|---|---|
| **Status** | Draft — for owner review |
| **Author** | Engineering (2026-06-08) |
| **Reviewers** | (owner) |
| **Feature flag** | n/a (new build targets are additive; no runtime flag — a missing actual is a compile error, not a prod regression) |
| **ADRs** | (to be written per slice — see §11) |
| **Tracking** | ROADMAP §3.5 (Web/desktop client, Tier 3.5) + iOS completion (Phase 5 carryover) |

> **House process reminder (from `_TEMPLATE_feature-design.md`).** Design-doc first; ADR per
> significant decision; contract-first; **vertical slices** each independently shippable; verify in
> the real product. This plan is sequenced as vertical slices (one *platform target* at a time),
> not as a horizontal "add all targets at once" big-bang. Each target is DONE only when it builds on
> a real toolchain and runs end-to-end — which, per §10, **cannot happen on this CI host**.

---

## 1. Context & problem

Muhabbet today ships **one** runnable client surface: the Android Compose Multiplatform app
(`mobile/composeApp`). The owner wants to reach **complete iOS**, **Desktop (JVM Compose)**,
**Web (Compose/Wasm)**, and **WasmJS**. The architecture is already Kotlin-everywhere and Compose
Multiplatform, so this is largely an exercise in (a) declaring new KMP targets, (b) supplying the
missing `actual` implementations per target, and (c) being honest about which third-party SDKs
(libsignal, LiveKit, Firebase) do **not** exist on the new targets.

The good news, verified below: the **`shared/` KMP module is 100% common code** (no platform source
sets at all), and the heavy lifting in `composeApp` is in `commonMain`. The platform coupling is
concentrated in one package — `mobile/.../platform/` — behind `expect`/`actual` seams. That is
exactly the shape that makes multi-target expansion tractable.

The bad news: the **iOS targets are declared but there is no iOS app shell at all** (no `iosApp/`
Xcode project, no `MainViewController`, no Swift), several iOS `actual`s are **stubs**
(calls/Firebase/Signal/media-crypto), and the host crypto/RTC SDKs are Android-JVM-only. Web/Wasm
add their own engine-availability constraints (Ktor, SQLDelight, crypto).

## 2. Goals / Non-goals

**Goals**
- A concrete, code-grounded, **sequenced** plan to add **iOS (complete) → Desktop (JVM) → WasmJS web**
  as runnable clients, reusing the existing `commonMain` UI + the `shared/` core.
- An explicit **`expect`/`actual` gap matrix**: for every interface in `mobile/.../platform/`, which
  targets already have an actual and which need a new one.
- Honest **blocker accounting** per target (Ktor engine, SQLDelight driver, libsignal, LiveKit,
  Firebase, media/audio APIs, push) and the **KVKK/security** implications of each.

**Non-goals (this plan)**
- Shipping any of these targets in this change. **This is a docs-only plan** — no gradle, no code.
- **E2E on the new targets.** E2E is flag-OFF in prod and libsignal is a standing BLOCK (CLAUDE.md →
  "libsignal upgrade (BLOCKED)"). New targets get the same `NoOpEncryption`/`NoOpKeyManager` path
  Android+iOS already use. **Do not** add home-grown crypto to make a target "work."
- **Calls (LiveKit) on Desktop/Web/iOS.** LiveKit is wired only on Android (`livekit-android`); iOS is
  already a stub (`CallEngine.ios.kt`). Bringing calls to new targets is its own tier.
- A native iOS UI. The iOS plan finishes the **Compose Multiplatform** iOS surface, not a SwiftUI app.

## 3. Current state (code-grounded)

### 3.1 Declared KMP targets — what actually exists today

**`shared/build.gradle.kts`** (the shared KMP core) declares **three** target families:
```
kotlin {
    jvm { ... JVM_21 }          // consumed by :backend
    androidTarget { ... }       // consumed by :mobile:composeApp (Android)
    iosX64(); iosArm64(); iosSimulatorArm64()   // framework baseName "shared", isStatic
}
```
Crucially, **`shared/` has NO platform source dirs** — `find shared/src -path '*Main*' ! -path
'*commonMain*'` returns nothing. The only sources are `shared/src/commonMain/kotlin/com/muhabbet/shared/{dto,model,port,protocol,validation}`. So the entire shared core is pure common Kotlin
(`kotlinx-serialization-json`, `kotlinx-coroutines-core`, `kotlinx-datetime`) — it will compile to a
Desktop-JVM, WasmJS, or any other KMP target **with no new code**, because it has no `expect`s.

**`mobile/composeApp/build.gradle.kts`** declares **two** target families:
```
kotlin {
    androidTarget { ... JVM_21 }
    iosX64(); iosArm64(); iosSimulatorArm64()   // framework baseName "ComposeApp", isStatic
}
```
There is **no `jvm()`/Desktop target**, **no `wasmJs()`**, **no `js()`** target in `composeApp`
today. Adding Desktop/Web is a new target declaration + new source set + new actuals.

`settings.gradle.kts` gates the mobile module on `SKIP_MOBILE != "true"`, includes `:backend`,
`:shared`, `:mobile:composeApp`, and already adds Signal's Maven repo (additive, for the BLOCKED
libsignal bump).

### 3.2 iOS: targets declared, **no app shell**

`composeApp` declares the three `ios*` targets and emits a static `ComposeApp.framework`, **but there
is no consumer**: `find mobile -name 'iosApp' -o -name '*.swift' -o -name 'MainViewController*'`
returns nothing, and the only app entrypoints are `androidMain/.../MainActivity.kt` and
`commonMain/.../App.kt`. So iOS is "compiles as a framework, but there is no `iosApp/` Xcode project,
no `MainViewController`, no `@main` Swift `App`, no `Info.plist`/entitlements, no APNs config." That
shell is the **first** missing piece for iOS, independent of any stub.

### 3.3 commonMain vs androidMain-only — the platform seam

The platform coupling lives in **`mobile/composeApp/src/commonMain/.../platform/`** as `expect`
declarations, with `actual`s in `androidMain` and `iosMain`. The full set of `expect` seams (verified
by grep):

| `expect` seam (commonMain file) | shape |
|---|---|
| `AudioPlayer.kt` | `expect class AudioPlayer` + `expect fun rememberAudioPlayer()` |
| `AudioRecorder.kt` | `expect class AudioRecorder` + `rememberAudioRecorder()` + `rememberAudioPermissionRequester(...)` |
| `ImagePicker.kt` | `expect class ImagePickerLauncher` + `rememberImagePickerLauncher(...)` |
| `FilePicker.kt` | `expect class FilePickerLauncher` + `rememberFilePickerLauncher(...)` |
| `CameraPicker.kt` | `expect class CameraPickerLauncher` + `rememberCameraPickerLauncher(...)` |
| `ContactsProvider.kt` | `interface ContactsProvider` (common) + `expect fun rememberContactsPermissionRequester(...)` |
| `PushTokenProvider.kt` | `interface PushTokenProvider` (common; actuals are concrete classes) |
| `CrashReporter.kt` | `expect object CrashReporter` |
| `PlatformInfo.kt` | `expect fun getPlatformName()` / `getDeviceModel()` |
| `ShareLauncher.kt` | `expect fun rememberShareLauncher()` |
| `LocaleHelper.kt` | `expect fun rememberRestartApp()` |
| `ImageCompressor.kt` | `expect fun compressImage(bytes, maxDimension, quality)` |
| `SpeechTranscriber.kt` | `expect class SpeechTranscriber` |
| `BackgroundSyncManager.kt` | `expect class BackgroundSyncManager` |
| `CallEngine.kt` | `expect class CallEngine()` |
| `FirebasePhoneAuth.kt` | `interface FirebasePhoneAuth` + `expect fun rememberFirebasePhoneAuth(): FirebasePhoneAuth?` |

Plus two non-`platform/` seams that also need per-target actuals:
- `data/local/DatabaseDriverFactory.kt` (SQLDelight driver) — `android-driver` / `native-driver`.
- `crypto/SymmetricCipher.kt` (AES-256-GCM for media E2E) — `android` real, `ios` **throws** (NoOp).
- DI: `di/PlatformModule.android.kt` / `PlatformModule.ios.kt` (`TokenStorage`, driver factory,
  contacts, push, sync, E2E ports) — every new target needs its own `xPlatformModule()`.

### 3.4 Which iOS actuals are stubs (already in CLAUDE.md, verified in code)

| iOS actual | status | evidence |
|---|---|---|
| `CallEngine.ios.kt` | **stub** — flips a bool, no LiveKit | `connected = true` only; "LiveKit Swift SDK not yet bridged" |
| `FirebasePhoneAuth.ios.kt` | **stub** — `isAvailable() = false` | falls back to backend OTP; `verifyCode` throws |
| `SymmetricCipher.ios.kt` | **NoOp that throws** | every method `throw NotImplementedError("MEDIA_E2E_IOS_NOT_IMPLEMENTED")` — fail-closed, honest |
| `PlatformModule.ios.kt` E2E | **NoOp** | `single<E2EKeyManager> { NoOpKeyManager() }`, `single<EncryptionPort> { NoOpEncryption() }` |
| `PushTokenProvider.ios.kt` | **partial** — needs AppDelegate | token arrives via `IosPushTokenProvider.onTokenReceived(...)` from a Swift AppDelegate that **does not exist yet** |
| Audio/Image/File/Camera/Contacts/Locale/Compressor/Transcriber `.ios.kt` | implemented | present in `iosMain/.../platform/` |

Android's E2E is **also** NoOp right now (the 4 `crypto/*.kt.disabled` Signal files don't compile
against the pinned `libsignal-android:0.86.5`; `PlatformModule.android.kt` wires `NoOpKeyManager` +
`NoOpEncryption`). So **no platform has real E2E today** — the new targets inherit the same honest
plaintext-under-TLS posture. This is the single most important fact for §2's "do not guess crypto."

## 4. Target matrix (feasibility · effort · blockers · KVKK)

Effort is relative (S/M/L/XL), assuming the seams in §3.3 are the unit of work.

### 4.1 iOS (complete)
- **Feasibility:** High for the Compose surface (targets already declared, most actuals exist).
- **CMP 2026 support:** Compose Multiplatform iOS is stable; `UIViewController` host via
  `ComposeUIViewController { App() }` is the standard entrypoint.
- **Effort:** **L.** Most is *non-Kotlin*: create the `iosApp/` Xcode project, `@main` SwiftUI/UIKit
  `App` hosting the Compose framework, `Info.plist` + entitlements (mic, contacts, notifications),
  APNs cert, and an AppDelegate that forwards the device token into
  `IosPushTokenProvider.onTokenReceived(...)`. Then de-stub Firebase (CocoaPods/SPM bridge) and
  decide calls (LiveKit Swift bridge — defer).
- **Blockers:** **Ktor** ✅ `ktor-client-darwin` already declared in `iosMain`. **SQLDelight** ✅
  `native-driver` already declared. **libsignal** — no Kotlin/Native build (Android-only jar) →
  stays NoOp (same as Android). **LiveKit** — Swift SDK, needs K/N bridge → calls stay stubbed.
  **Firebase phone-auth** — needs CocoaPods/SPM; until then backend-OTP fallback is the real path.
  **Push** — APNs (not FCM) + AppDelegate token plumbing.
- **KVKK/security:** Token storage already correct (`KeychainHelper` via `IosTokenStorage`). No new
  data-residency change. E2E remains NoOp (no regression). APNs payloads must stay content-free
  (notification = "new message," body fetched in-app) to avoid pushing message content through Apple.

### 4.2 Desktop (JVM Compose)
- **Feasibility:** High — JVM is the most forgiving CMP target and the `shared/` core already has a
  `jvm()` target the backend consumes (so the common code is proven on JVM).
- **CMP 2026 support:** `org.jetbrains.compose` desktop (Skiko/Swing) is mature; `application { Window { App() } }`.
- **Effort:** **M.** Add a `jvm("desktop")` target to `composeApp`, a `desktopMain` source set, and
  desktop actuals for the seams. Most desktop actuals are *easier* than mobile (file dialogs via AWT
  `FileDialog`, no runtime-permission dance).
- **Blockers:** **Ktor** — add `ktor-client-okhttp` (or `cio`) to `desktopMain` (OkHttp is already the
  engine in `androidMain`). **SQLDelight** — add `sqldelight-sqlite-driver` (JDBC) for desktop.
  **Audio** — `AudioPlayer`/`AudioRecorder` need JVM actuals (Java Sound `javax.sound.sampled`, or a
  small library); recording OGG/OPUS is the fiddly part. **Contacts/Camera/Push** — **N/A on desktop**
  → actuals return "unsupported"/no-op (these are LSP-safe because the UI gates them; see §11).
  **libsignal/LiveKit** — same as iOS: NoOp / no calls.
- **KVKK/security:** Desktop has **no Keychain/EncryptedSharedPreferences equivalent by default** —
  `TokenStorage` actual must use an OS-appropriate secret store (macOS Keychain / Windows Credential
  Manager / libsecret) or at minimum an encrypted file with a user-derived key. **Do not** drop
  tokens into a plaintext file. This is the headline desktop security task.

### 4.3 Web — Compose for Web (Wasm, `wasmJs` + Compose UI)
- **Feasibility:** Medium. CMP `wasmJs` with the full Compose UI (canvas-rendered) is supported in
  2026 but is the youngest target; expect rough edges in text input, IME, and accessibility.
- **CMP 2026 support:** `wasmJs()` target + `compose.ui` renders to a `<canvas>`. Resources
  (`compose.components.resources` — already a dep) work on Wasm.
- **Effort:** **XL.** New `wasmJs()` target, `wasmJsMain` source set, **and** the web is where the
  most actuals are missing or fundamentally different (no contacts, no file system, browser file
  picker, WebCrypto for symmetric, IndexedDB for storage).
- **Blockers:**
  - **Ktor** — `ktor-client-js` engine for `wasmJs` (the JS/Wasm engine); the WS auth path uses the
    `?token=` **query param** (per CLAUDE.md WS contract: `wss://host/ws?token={jwt}`), which the
    browser `WebSocket` supports — custom handshake headers (which browsers forbid) are **not** needed.
  - **SQLDelight** — **no first-class browser driver.** Options: the web-worker SQL.js driver
    (community), or skip the offline cache on web (degrade `LocalCache` to in-memory). MVP-web should
    likely **drop SQLDelight on web** and keep an in-memory `LocalCache` actual.
  - **CORS** — backend currently returns `401` at root and is consumed only by native clients; a
    browser origin needs explicit CORS config on the backend (`SecurityConfig`) — a backend change
    outside this docs plan, noted as a dependency.
  - **Media/audio** — `MediaRecorder`/`getUserMedia` via browser APIs; `compressImage` via canvas.
  - **libsignal/LiveKit** — none on Wasm → NoOp / no calls (LiveKit has a *JS* SDK, but bridging it to
    `wasmJs` Kotlin is a separate spike).
- **KVKK/security:** Browser is the **highest-risk** surface — token in `localStorage`/IndexedDB is
  XSS-exposed; CSP must be strict; this is the surface where "honest no-padlock" (PR #61) matters most
  because users *expect* WhatsApp-Web-style E2E. Until libsignal lands, web E2E is **not** available —
  the UI must not imply otherwise.

### 4.4 WasmJS (headless / non-Compose Wasm)
- **Clarification:** "WasmJS" and "Web (Compose/Wasm)" are the **same Kotlin target** (`wasmJs()`).
  There is no separate non-Compose web client in scope. A `js()` (Kotlin/JS IR) target is an
  *alternative* engine if `wasmJs` proves too immature for the UI — `js()` has broader Ktor/driver
  ecosystem support (DOM-rendered Compose via `compose.html` is a different, more limited API).
- **Recommendation:** Treat **Web = `wasmJs()` + Compose UI** as the single web target (§4.3). Keep
  `js()` as a documented fallback engine only if `wasmJs` blocks; do not build two web clients.

### 4.5 Summary matrix

| Target | Decl. today | Ktor engine | SQLDelight | Effort | Calls | E2E | Hardest blocker |
|---|---|---|---|---|---|---|---|
| **iOS** | targets ✅, no app shell | darwin ✅ | native ✅ | L | stub | NoOp | Xcode shell + APNs + Firebase bridge |
| **Desktop JVM** | none | add okhttp/cio | add JDBC | M | none | NoOp | secure `TokenStorage` (no Keychain) |
| **Web (wasmJs)** | none | add js | drop/in-mem | XL | none | NoOp | SQLDelight driver + CORS + browser secret storage |
| **WasmJS (=Web)** | — | (same as Web) | (same) | (same) | — | — | (same — not a separate client) |

## 5. `expect`/`actual` gap analysis (per target)

Legend: ✅ actual exists · ➕ new actual needed · N/A platform has no equivalent (provide a
fail-closed/no-op actual so the seam still resolves; UI must gate the feature) · ⚠️ exists but stub.

| Seam (commonMain) | Android | iOS | Desktop (new) | Web/Wasm (new) |
|---|---|---|---|---|
| `AudioPlayer.kt` | ✅ | ✅ | ➕ Java Sound | ➕ HTMLAudio |
| `AudioRecorder.kt` (+permission) | ✅ | ✅ | ➕ `javax.sound` | ➕ `MediaRecorder`+`getUserMedia` |
| `ImagePicker.kt` | ✅ | ✅ | ➕ AWT `FileDialog` | ➕ `<input type=file accept=image>` |
| `FilePicker.kt` | ✅ | ✅ | ➕ AWT `FileDialog` | ➕ `<input type=file>` |
| `CameraPicker.kt` | ✅ | ✅ | N/A (no-op / unsupported) | ➕ `getUserMedia` (or N/A) |
| `ContactsProvider.kt` (+perm) | ✅ | ✅ | N/A (empty list) | N/A (empty list) |
| `PushTokenProvider.kt` | ✅ FCM | ⚠️ needs AppDelegate | N/A (no push) → return null | ➕ Web Push (VAPID) or N/A |
| `CrashReporter.kt` | ✅ Sentry-android | ✅ | ➕ sentry-java | ➕ sentry-js or no-op |
| `PlatformInfo.kt` | ✅ | ✅ | ➕ `System.getProperty` | ➕ `navigator.userAgent` |
| `ShareLauncher.kt` | ✅ | ✅ | ➕ AWT clipboard/Desktop | ➕ Web Share API / clipboard |
| `LocaleHelper.kt` (`rememberRestartApp`) | ✅ | ✅ | ➕ recompose/restart window | ➕ `location.reload()` |
| `ImageCompressor.kt` | ✅ | ✅ | ➕ `ImageIO` | ➕ canvas re-encode |
| `SpeechTranscriber.kt` | ✅ SpeechRecognizer | ✅ SFSpeech | N/A (return unsupported) | ➕ Web Speech API or N/A |
| `BackgroundSyncManager.kt` | ✅ WorkManager | ✅ BGTask | N/A (foreground only) | N/A (service worker, defer) |
| `CallEngine.kt` | ✅ LiveKit | ⚠️ stub | ➕ stub (no calls) | ➕ stub (no calls) |
| `FirebasePhoneAuth.kt` | ✅ | ⚠️ `isAvailable()=false` | ➕ return null → backend OTP | ➕ return null → backend OTP |
| `data/local/DatabaseDriverFactory.kt` | ✅ android-driver | ✅ native-driver | ➕ JDBC driver | ➕ drop / in-mem |
| `crypto/SymmetricCipher.kt` | ✅ | ⚠️ throws (NoOp) | ➕ JCA AES-GCM (or throw) | ➕ WebCrypto (or throw) |
| `di/PlatformModule.*.kt` | ✅ | ✅ | ➕ `desktopPlatformModule()` | ➕ `webPlatformModule()` |

Two LSP guardrails (house rule: "No method should throw `UnsupportedOperationException`"): for N/A
seams, prefer a **fail-closed actual** that returns a benign empty/`null`/`false` result (e.g.
contacts → `emptyList()`, push token → `null`, Firebase → `null`→backend-OTP) **and** gate the UI
affordance off, rather than throwing. Where a security downgrade would otherwise be silent (media
crypto), throwing is correct — that is exactly why `SymmetricCipher.ios.kt` throws (fail-closed to
plaintext path, never a fake "encrypted" upload).

## 6. Shared-core leverage

### 6.1 Already shared (reused by every target for free)
`shared/src/commonMain/.../`:
- `model/Models.kt` — domain models (Message, Conversation, MessageStatus, ContentType, …).
- `dto/Dtos.kt` — API request/response DTOs (`kotlinx.serialization`).
- `protocol/WsMessage.kt` — the WS sealed protocol (`message.send`, `message.new`, `ack`, … — the
  exact `@SerialName` discriminators every client must use, per CLAUDE.md).
- `validation/ValidationRules.kt` — input validation.
- `port/` — `EncryptionPort`, `E2EKeyManager`, `E2EEnvelope`, `MediaKeyMaterial`, `DeviceLinkCrypto`
  (the crypto seams; NoOp/`NotYetImplemented` today).

Because `shared/` has **zero** `expect`s, adding `jvm("desktop")`/`wasmJs()` *consumers* needs **no
change to `shared/`** — it already builds for `jvm`, `android`, and `ios`, and pure-common code
ports to new targets without new source sets. (One caveat: confirm every transitive dep —
`kotlinx-serialization-json` 1.10, `kotlinx-coroutines-core` 1.10.2, `kotlinx-datetime` 0.7.1 — ships
a `wasmJs` artifact; all three do as of 2026, but this is the one thing to verify when the target is
actually added.)

### 6.2 What should move *down* into `commonMain` to maximize reuse
Candidates currently in `composeApp/commonMain` that are platform-agnostic and could (later) live in
`shared/` so a future non-Compose client (e.g. a CLI bot, or the `infra/scripts/test_bot.py`
rewritten in Kotlin) could reuse them:
- **Repository contracts + DTO mappers** (`data/repository/*`, mapper extension functions) — these are
  Ktor-client + serialization, both KMP-common; they only live in `composeApp` for historical
  reasons. Moving them to `shared/` would let backend integration tests and future clients reuse the
  same client-side contract. *Defer* — not required for the four targets, and it's a refactor with
  test churn; flag as a follow-up, not a prerequisite.
- **`crypto/MessageEncryptor.kt` / `MediaEncryptor.kt` / `E2EConfig.kt`** — already common; they sit
  in `composeApp` but depend only on `shared/port/*`. Fine where they are; **do not** touch under this
  plan (crypto boundary).

**Rule for this plan:** maximize reuse by *adding targets that consume the existing common code*, not
by reshuffling modules. Module moves are a separate, test-guarded refactor.

## 7. Files to add / change (per target — illustrative, additive)

`(+)` add · `(~)` change. No existing code is modified except gradle target declarations and DI
registration.

```
# iOS (complete) — the shell is the bulk of the work, mostly non-Kotlin
iosApp/iosApp.xcodeproj                                   (+)  Xcode project consuming ComposeApp.framework
iosApp/iosApp/iOSApp.swift                                (+)  @main App
iosApp/iosApp/ContentView.swift                           (+)  ComposeUIViewController { App() } host
iosApp/iosApp/AppDelegate.swift                           (+)  APNs token → IosPushTokenProvider.onTokenReceived
iosApp/iosApp/Info.plist (+entitlements)                  (+)  mic/contacts/notification usage strings, APNs
mobile/composeApp/src/iosMain/.../MainViewController.kt   (+)  fun MainViewController() = ComposeUIViewController { App() }
mobile/composeApp/src/iosMain/.../platform/FirebasePhoneAuth.ios.kt (~) de-stub once CocoaPods bridge lands

# Desktop (JVM Compose)
mobile/composeApp/build.gradle.kts                        (~)  jvm("desktop") target + desktopMain deps (compose.desktop, ktor-okhttp, sqldelight-jdbc, sentry-java)
mobile/composeApp/src/desktopMain/.../main.kt            (+)  application { Window { App() } }
mobile/composeApp/src/desktopMain/.../di/PlatformModule.desktop.kt (+)  desktopPlatformModule()
mobile/composeApp/src/desktopMain/.../data/local/DatabaseDriverFactory.desktop.kt (+)  JDBC driver
mobile/composeApp/src/desktopMain/.../platform/*.desktop.kt (+)  AudioPlayer, AudioRecorder, ImagePicker, FilePicker, CrashReporter, PlatformInfo, ShareLauncher, LocaleHelper, ImageCompressor (+ N/A no-op actuals)
mobile/composeApp/src/desktopMain/.../crypto/SymmetricCipher.desktop.kt (+)  JCA AES-GCM (or throw, like iOS)

# Web (wasmJs + Compose UI)
mobile/composeApp/build.gradle.kts                        (~)  wasmJs { browser() } target + wasmJsMain deps (ktor-client-js, no sqldelight)
mobile/composeApp/src/wasmJsMain/.../main.kt             (+)  CanvasBasedWindow { App() }
mobile/composeApp/src/wasmJsMain/.../di/PlatformModule.web.kt (+)  webPlatformModule() (in-memory LocalCache, browser TokenStorage)
mobile/composeApp/src/wasmJsMain/.../platform/*.web.kt   (+)  browser-API actuals + N/A no-ops
backend/.../shared/config/SecurityConfig.kt              (~)  CORS allow-list for the web origin  ← DEPENDENCY, separate PR
docs/adr/00NN-platform-targets.md                        (+)  one ADR per target decision
docs/diagrams/platform-targets.mmd                       (+)  container view of clients × shared core
```

## 8. Rollout & flags

New build targets are **not** runtime feature-flags — a missing `actual` is a **compile-time** error,
so a half-built target simply doesn't build; it cannot regress the Android/iOS apps. The
reversible-rollout posture maps to **per-target branches that don't touch the Android assemble path**:
- Each target lands behind its own gradle target block; `SKIP_MOBILE` and the existing Android/iOS
  source sets are untouched, so the green Android build (PR #49) is unaffected.
- Distribution gating is per-store/channel (TestFlight for iOS, a desktop installer channel, a staging
  web origin) — these are deployment gates, not code flags.
- **E2E stays OFF on every target** (NoOp). No target ships a padlock claim while NoOp is wired
  (consistent with PR #61's honest-UI fix).

## 9. Agile iteration plan (vertical slices — one runnable target per slice)

Ordered **lowest-risk / highest-reuse first**. Each slice is DONE only when it runs end-to-end on a
real toolchain (NOT verifiable here — see §10).

- **S1 — iOS app shell + run** *(highest value: a 2nd real client, targets already declared).*
  Create `iosApp/` Xcode project, `MainViewController()`, AppDelegate APNs plumbing into
  `IosPushTokenProvider`, Info.plist usage strings. **Done =** the existing `App()` Compose UI launches
  on an iOS simulator/device, login works via **backend OTP** (Firebase stub stays), 1:1 chat
  send/receive over WS works. Calls + Firebase-phone-auth remain stubbed (tracked separately).
- **S2 — iOS de-stub: Firebase phone-auth + APNs delivery.** Bridge Firebase iOS SDK (CocoaPods/SPM),
  implement real `FirebasePhoneAuth.ios`, verify APNs push delivery. **Done =** phone-auth OTP via
  Firebase on iOS + a real push wakes the app.
- **S3 — Desktop JVM client.** Add `jvm("desktop")`, `main.kt`, `desktopPlatformModule()`, desktop
  actuals (file dialogs, Java Sound audio, JDBC SQLDelight), and a **secure `TokenStorage`** (OS
  secret store). **Done =** desktop app launches, login (backend OTP), 1:1 chat + media view work;
  tokens are stored encrypted, not plaintext.
- **S4 — Web (wasmJs) read-mostly client.** Add `wasmJs { browser() }`, `CanvasBasedWindow { App() }`,
  `webPlatformModule()` with **in-memory** `LocalCache` (no SQLDelight on web), browser actuals.
  Depends on **backend CORS** (separate PR). **Done =** web client loads in a browser, login (backend
  OTP), conversation list + 1:1 chat over browser WebSocket work. Offline cache, contacts, push, calls
  are N/A.
- **S5 — Web hardening.** CSP, secure token handling (the XSS-exposed surface), Web Push (VAPID)
  evaluation, IME/a11y polish for canvas Compose. **Done =** security review of the web token/CSP path
  passes; honest no-E2E UI confirmed.

Calls (LiveKit) and real E2E (libsignal) are **explicitly out of every slice** — both are standing
BLOCKs (CLAUDE.md) and ride their own tiers.

## 10. Test plan / verification — and what is NOT verifiable here

- **Cheap gate that DOES run on this host:** `:mobile:composeApp:compileCommonMainKotlinMetadata`
  (compiles `commonMain` incl. generated `Res.string.*`), `:shared:jvmTest` (53 tests), and
  `:backend:test` (369 tests). These confirm the **common** code and the shared core still build — but
  they do **not** exercise any new target.
- **What CANNOT be verified on the CI host (be honest):** per CLAUDE.md "Build & test," the full
  Android app does not even `assembleDebug` here (no uncached Firebase, no KVM/emulator). It follows
  that **iOS (needs macOS + Xcode), Desktop (`run`/`packageDistributionForCurrentOS`), and Wasm
  (`wasmJsBrowserRun` + a browser) cannot be assembled or run on this Linux CI host at all.** Every
  new-target slice's "Done =" requires a developer machine with the right toolchain (macOS+Xcode for
  iOS; any JVM desktop for Desktop; a browser for Wasm). This plan must not claim a target "works"
  from CI green — only `commonMain`/`shared`/`backend` compilation is provable here.
- **Per-target verification (on the right machine):** S1 — iOS sim launch + WS round-trip; S3 —
  desktop window + WS round-trip + token-store encryption assertion; S4 — browser load + WS over
  `wss://?token=` + CORS preflight passes.

## 11. Risks & open questions / non-goals

- **No iOS app shell exists at all** — biggest hidden cost; iOS is "framework compiles" not "app
  runs." S1 is mostly Swift/Xcode plumbing, not Kotlin.
- **Desktop secret storage** — there is no Keychain/EncryptedSharedPreferences on plain JVM; shipping
  tokens to a plaintext file is a KVKK/security failure. Needs an OS-keystore integration. Open
  question: which library (or per-OS native calls).
- **Wasm SQLDelight + CORS** — no first-class browser SQL driver → web likely **drops** the offline
  cache (in-memory `LocalCache`); and the backend currently has no CORS for a browser origin (a
  backend change, out of this docs scope, but a hard dependency for S4).
- **libsignal & LiveKit are Android-JVM-only** — iOS/Desktop/Web get **NoOp E2E** and **no calls**.
  This plan does **not** add crypto or RTC to new targets ("do not guess crypto"). E2E for any new
  surface is gated on the standing libsignal BLOCK.
- **Wasm immaturity** — canvas Compose text-input/IME/a11y is the youngest CMP surface; budget rework.
  `js()` is a documented fallback engine, **not** a second web client.
- **Dependency Wasm artifacts** — verify `kotlinx-serialization`, `coroutines`, `kotlinx-datetime`,
  Ktor, Koin, Decompose, Coil all publish `wasmJs` artifacts at the pinned versions before committing
  to S4 (most do in 2026; Decompose/Coil on wasmJs are the ones to spot-check).
- **Non-goals restated:** no E2E on new targets, no calls on new targets, no native iOS UI, no module
  reshuffle of `shared/` (it already builds everywhere), no shipping any target in this change.

## 12. Rollback

Every target is **additive**: a new `kotlin { … }` target block + a new source set + new actuals.
Removing the target block (or never merging it) leaves the Android/iOS source sets and the green
Android build byte-identical. No migrations, no runtime flags, no data changes — rollback is "delete
the target declaration."
