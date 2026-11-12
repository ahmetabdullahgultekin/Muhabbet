# ADR 0028 — KMP module structure and build prerequisites

**Status:** Accepted (2026-11-12, delegated) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); code against this ADR is written only while the matching scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits. Each module is created only in the milestone that first needs it · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0028; §4.3; §13 (0.4, 0.5, 0.6); §14 items 16–20](../architecture/multi-transport/decision-brief-2026-11-12.md) · Depends on ADR-0012, ADR-0016, ADR-0017. Referenced by ADR-0024, ADR-0026. Supersedes nothing.

## Context

State of the build on `dev` @ 2434324 (kmp-cmp-structure digest):

| Area | Fact | Finding |
|---|---|---|
| Modules | `:backend`, `:shared`, `:mobile:designsystem`, `:mobile:composeApp` (`settings.gradle.kts:26-32`) | — |
| `composeApp` | 26,505 lines in commonMain, organised by technical layer; no `domain/`; 146 `koinInject` calls in `ui/`; 14 `ui` files import `data.remote` | KMP-08, MOB-08 |
| Build config | No version catalog, no `build-logic`; versions copied as literals; `compileSdk` 35 in `shared`, 36 elsewhere | KMP-05 |
| AGP | 9.3.1 with temporary opt-outs (`android.builtInKotlin=false`, `android.newDsl=false`); KMP + `com.android.application/library` is deprecated | KMP-04 |
| Tests | `:shared` tests never run in CI; commonTest runs only on the Android JVM | KMP-03, KMP-14 |
| iOS | Cannot launch: no entry point, no Xcode project, iOS DI never wired; no iOS compile in CI; ≈ 10 of ≈ 23 platform capabilities are stubs | KMP-01, KMP-02, KMP-09 |
| Platform layer | Mix of `expect class` and interface + Koin; stateful `expect class` services cannot be faked in commonTest | KMP-10 |
| Protocol | `WsMessage` has no version and no `Unknown` subtype | KMP-11 |
| Migrations | SQLDelight has no `.sqm` migrations | GEM-15, brief §11.2 |
| Licence | libsignal is AGPL-3.0; repo is MIT | KMP-13 → ADR-0017 |
| Positive | commonMain has no `java/javax/android` imports; `:mobile:designsystem` is a compiler-enforced boundary with ratcheted guardrails | KMP-M2 |
| Host | CI runs on one production host at ≈ 92% disk with a single runner | R19, CICD-04 |

The delivery engine (ADR-0012) and the type gate (ADR-0016) both need **compiler-enforced**
boundaries: a package boundary inside `composeApp` would rot, as the backend's unenforced module
rules did (BE-08). `:mobile:designsystem` is the in-repo precedent that works.

## Decision

### 1. Modules, introduced only when their milestone needs them

| Module | Created | Licence (D1)¹ | Contains | Must not contain / depend on |
|---|---|---|---|---|
| `:shared` (existing) | — | MIT | DTOs, `WsMessage`, and a `protocol/` package with envelope types (`ServerFrame`, `TransportFrame`, inner `Content`) and golden vectors (ADR-0022). `protocol/` is promoted to its own module only if a third consumer appears | libsignal; Compose; Android/iOS APIs |
| `:mobile:crypto` | 0.5 | MIT | `SealedFrame` (`internal` constructor), `EnvelopeSealer`, `SealResult`, `SessionCryptoPort`; `DeviceCertificateVerifier`, `LinkAuthenticator`, `ProximityTagSource` at 1.2. **No producer of `SealedFrame` until 1.0**; platform adapters (`androidMain` libsignal, `iosMain` Swift bridge) arrive at 1.0 | Compose; Koin annotations; `WsMessage`; any transport |
| `:mobile:crypto-testkit` | 0.5, **test-only** | MIT | `FakeSealer`, golden vectors, the commonTest contract suite run against every sealer | Any release runtime classpath (build check) |
| `:mobile:messaging` | 0.5 | MIT | The engine (ADR-0012): `MessagingEngine`, `Outbox`, `Inbox`, `SyncCoordinator`, `ReceiptService`, `SessionTeardown`; ports `ServerLink`, `SealedLink`, `InboundSource`, `MessageStore`, `OutboxStore`, `ChangeFeedPort`; later `PathPolicy` (1.2), `CustodyPolicy` (1.3); `SimulatedLink` for tests | Compose; Koin annotations; `WsMessage`; Ktor; SQLDelight; platform APIs |
| `:mobile:composeApp` (existing) | — | MIT | UI; server adapters `WsServerLink`, `WsInboundSource`, `RestChangeFeedSource`, `FcmHintSource`; SQLDelight store adapters; Koin wiring. Server adapters stay here to limit module count, extracted only if a second app shell appears | `SealedFrame` construction |
| `:mobile:designsystem` (existing) | — | MIT | Unchanged | `composeApp` (already enforced) |
| `:mobile:transport-lan`, `:mobile:transport-nearby` | 1.2 | MIT | `LanLink`, `NearbyLink`, advertiser/scanner (ADR-0024) | UI; server adapters |
| `:mobile:transport-meshtastic` | 1.4 | MIT | `MeshtasticLink`, `AirtimeLedger` (ADR-0026) | Any **TR** release classpath (build check) |

¹ Until 1.0 entry all of `mobile/` is MIT (owner decision D1, 2026-11-12). At 1.0 entry, after counsel
Q-L7, ADR-0017 records either relicensing to AGPL-3.0-or-later or keeping MIT sources and conveying the
libsignal-linked binary under AGPL. This table is then amended. (`:mobile:designsystem` never links
libsignal.)

All packages stay `com.muhabbet.*` (never `tr.gov.*`, GEM-21, LEG-05).

### 2. Allowed dependency edges

| From → To | `:shared` | `:mobile:crypto` | `:mobile:crypto-testkit` | `:mobile:messaging` | transports | `:mobile:composeApp` |
|---|---|---|---|---|---|---|
| `:mobile:crypto` | ✓ | — | ✗ | ✗ | ✗ | ✗ |
| `:mobile:crypto-testkit` | ✓ | ✓ | — | ✗ | ✗ | ✗ |
| `:mobile:messaging` | ✓ | ✓ | test only | — | ✗ | ✗ |
| `:mobile:transport-*` | ✓ | ✓ | test only | ✓ (ports) | ✗ | ✗ |
| `:mobile:composeApp` | ✓ | ✓ | test only | ✓ | ✓ | — |

`:shared` depends on none of them. `:backend` depends on `:shared` only, as today.

### 3. Prerequisites, in order (0.4, "early engine")

1. `gradle/libs.versions.toml` with every version single-sourced (incl. `compileSdk`, `minSdk`,
   JVM target).
2. **One** `build-logic` convention plugin: `muhabbet.kmp.library` (targets, JVM target,
   Android config from the catalog). Further plugins only when a second module needs a different
   shape. `muhabbet.kmp.library` declares androidTarget + iosArm64 + iosSimulatorArm64 + jvm for
   `:mobile:crypto`, `:mobile:crypto-testkit` and `:mobile:messaging` (matching composeApp's targets,
   which dropped iosX64 because Compose Multiplatform 1.11.1 no longer publishes it, plus jvm for
   `jvmTest`; `:shared` already does). **iosX64 is not declared** (`mobile/composeApp/build.gradle.kts:21-23`,
   `shared/build.gradle.kts:26-31`). The plugin applies `com.android.kotlin.multiplatform.library` to
   every module created from 0.5 on (KMP-04; check the pinned AGP supports it when the plugin is
   written); `composeApp` is untouched. `:mobile:crypto` jvmMain has no `SealedFrame` producer (tests use the testkit). Transport
   modules declare android + iOS only.
3. SQLDelight migrations: the `.sq` files stay the schema, today's schema is snapshotted as `1.db`
   from the shipped APK, `verifyMigrations` runs in CI without `deriveSchemaFromMigrations`, and the
   first migration is `1.sqm` (1→2) (brief erratum E-19; data-model §5.2).
4. `:shared:jvmTest` in CI now; `:mobile:messaging:jvmTest` in CI from the day the module exists.
   `settings.gradle.kts:28-32` includes every `:mobile:*` module only when `SKIP_MOBILE != true`,
   because applying an Android plugin needs an SDK just to configure the project, and backend and
   security CI set `SKIP_MOBILE=true`. The new `:mobile:*` modules are therefore included **inside**
   that guard, and their `jvmTest` runs in the **mobile** CI job, which has the Android SDK. The
   alternative — keeping `:mobile:crypto` and `:mobile:messaging` free of Android plugins so they can
   sit outside the guard — is an implementer/owner choice to record before the modules are created
   ([OI-31](../architecture/multi-transport/open-items.md#oi-31)).

**Not prerequisites:** a `linuxX64` target; the AGP 9 `:androidApp` split (KMP-04); per-feature
modules (KMP-08). They are not rejected; they are not on the critical path for a disk- and
time-limited single-runner host.

### 4. Platform abstraction rule

- Stateful platform services are **commonMain interfaces** with implementations bound in the
  platform Koin modules (KMP-10). This applies to every transport, `SessionCryptoPort`,
  `SecureKeyStorePort` and `LiveSessionPort`.

> **Resolved (2026-11-12):** brief erratum E-08: the brief §5 `SessionCryptoPort` row reads "out
> (platform-bound interface, Koin-provided)".

- `expect`/`actual` is used only for leaf functions in `platform/` (and `@Composable` launchers).
- **Swift-only libraries.** Kotlin/Native cinterop reaches only C and Objective-C APIs, not pure-Swift
  modules; the libsignal Swift client and the Nearby Connections Swift package are both Swift. The
  bridge is a commonMain interface (e.g. `SessionCryptoPort`, `SealedLink`) **implemented in Swift in
  the iOS app target** and registered with Koin at startup (or Swift export, if adopted later). The
  Swift Package Manager dependency is declared in the Xcode project, not in Gradle. This is how the
  `iosMain` bridge for `:mobile:crypto` (1.0) and `:mobile:transport-nearby` on iOS (1.2) are built;
  both depend on KMP-01 being fixed (§6).
- `:mobile:messaging` code receives `Clock` and `IdGenerator` by injection (P10).

### 5. Enforcement

| Rule | Mechanism |
|---|---|
| Engine has no Compose, Koin annotations or `WsMessage` | Not on its classpath (build-enforced) + `verifyUi`-style guardrail extended to `:mobile:messaging` with baseline 0 |
| Only `:mobile:crypto` constructs `SealedFrame` | `internal` constructor (compile) + architecture test |
| No `*-testkit` on a release runtime classpath | Gradle build check in CI |
| No `:mobile:transport-meshtastic` on a TR release classpath | Gradle build check in CI (ADR-0026) |
| UI does not speak wire frames | `verifyUi` counter over `ui/`, regex `\bWsMessage\b\|\bwsClient\b` counted per line. The baseline is **measured when the guardrail is added** (on HEAD there are 16 `wsClient.send` calls in `ui/` — the 17th is in `App.kt:148`, outside the scan roots — and 52 lines across 10 `ui/` files mention `WsMessage` or `wsClient`). Ratcheted to 0 outside `ui/call/` by 0.5 exit; `ui/call/` closes at 0.7 (brief erratum E-30) |
| No crypto primitive outside the crypto adapters (P12) | Guardrail grep for `javax.crypto`/`CryptoKit` outside `:mobile:crypto` adapters and the existing media AEAD |

### 6. iOS gate

The KMP-01 iOS decision is a **blocking gate at 0.6**: either iOS launches (entry point, Xcode
project, DI wired, an iOS CI build), or, per owner decision D2 (2026-11-12), 1.0 ships Android-only
and the UI and store-listing text say so. Bringing iOS back after that needs a new ADR. Every
cross-platform claim in 1.0 (ADR-0017) and 1.2 (ADR-0024) depends on it.

## Why

- Compiler-enforced boundaries are the only ones that hold for agent-written code (BE-08 precedent;
  `:mobile:designsystem` as the positive precedent).
- The type gate (ADR-0016) is only a gate if the constructor's module is separate from everything
  that sends.
- Six new modules across three milestones (0.5, 1.2, 1.4), each created only when first needed, fit
  one engineer and one runner; target-state's 14-module
  layout plus `linuxX64` did not (feasibility judge, R19).
- A catalog and one convention plugin stop the version drift (KMP-05) before the module count grows.

## Consequences

- `composeApp` loses the delivery logic it hosts today (`WsClient` outbox, dedup, E2E hook); the
  16 executable `wsClient.send*` sites are the migration inventory (ADR-0011).
- New modules compile on CI from day one; their JVM tests are cheap (no emulator or device needed),
  and run in the mobile CI job because the modules sit inside the `SKIP_MOBILE` guard (§3 item 4).
- iOS remains unclaimed until the 0.6 gate is met (KMP-01, KMP-09, MOB-14); if it is not met, 1.0 is
  Android-only and says so (D2).
- AGP 9 deprecation debt (KMP-04) remains open and is scheduled separately.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Keep everything in `composeApp` (package boundaries) | Unenforced; the engine and type gate would be reachable from every composable (KMP-08) |
| Target-state's 14+ modules plus `linuxX64` | Out of reach for one engineer on a ≈ 92%-disk single runner |
| Full feature modularisation now (`:feature:chat`, `:core:*`) | No second consumer; YAGNI (ADR-0011) |
| AGP 9 `:androidApp` split as a prerequisite | Valuable but not on the delivery critical path |
| Gemini's `tr.gov.*` namespace | Claims a government namespace (GEM-21, LEG-05) |

## Evidence

- Findings: KMP-01, KMP-02, KMP-03, KMP-04, KMP-05, KMP-07, KMP-08, KMP-09, KMP-10, KMP-11, KMP-13,
  KMP-14, KMP-M1, KMP-M2, KMP-M3, MOB-08, MOB-11, MOB-14, BE-08, GEM-11, GEM-15, GEM-21, LEG-05,
  CICD-04, CICD-17.
- Repo: `settings.gradle.kts:26-32`, `gradle.properties` (AGP opt-outs),
  `mobile/composeApp/build.gradle.kts`, `gradle/ui-guardrails.gradle.kts`.
- External sources (from the KMP digest):
  - https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
  - https://developer.android.com/build/releases/agp-9-0-0-release-notes
  - https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html
  - https://github.com/signalapp/libsignal

## Verification

| Check | Method | When |
|---|---|---|
| Catalog and convention plugin in use; no hard-coded versions in module build files | Inspection + grep guardrail | 0.4 exit |
| `verifyMigrations` passes in CI | CI job | 0.4 exit |
| `:shared:jvmTest` and `:mobile:messaging:jvmTest` run in CI | Workflow inspection + test reports | 0.4 / 0.5 |
| `:mobile:messaging` has no Compose, Koin-annotation or `WsMessage` dependencies | Build-enforced + guardrail baseline 0 | 0.5 exit |
| Only `:mobile:crypto` constructs `SealedFrame` | Compile + architecture test | 0.5 |
| No `*-testkit` on a release classpath; no Meshtastic module on a TR release classpath | Gradle build checks | 0.5 / 1.4 |
| `ui/` uses of `WsMessage`/`wsClient` = 0 | `verifyUi` ratchet | 0.5 exit |
| iOS launches on a simulator, or the Android-only text required by D2 is present in the UI and the store listing | Owner observation | 0.6 exit |

> **Resolved (2026-11-12):** YAGNI (P7): `SimulatedLink` lives in `:mobile:messaging` commonTest. It
> moves to a test-only `:mobile:messaging-testkit`, under the same release-classpath check as
> `:mobile:crypto-testkit`, when a second module needs it (1.2 transport contract tests) (brief
> erratum E-13).

> **Resolved (2026-11-12):** the `muhabbet.kmp.library` convention plugin applies
> `com.android.kotlin.multiplatform.library` to every module created from 0.5 on; `composeApp` is
> untouched (KMP-04). Condition: check that the pinned AGP supports it when the plugin is written (§3
> item 2).
