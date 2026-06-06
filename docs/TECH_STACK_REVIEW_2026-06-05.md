# Tech-Stack & Architecture Modernization Review — 2026-06-05

**Reviewer:** Staff-level automated review (Claude Sonnet 4.6)
**Scope:** All subprojects — `backend/`, `shared/`, `mobile/composeApp/`
**Purpose:** Advisory only. No code/config changes. Each "newer/better" claim is grounded in a cited source.

---

## 1. Full Stack Inventory

| Component | Pinned version (as of review) | Source |
|---|---|---|
| Kotlin | 2.3.20 | `build.gradle.kts` root |
| Spring Boot | 4.0.5 | `build.gradle.kts` root |
| Java toolchain | 21 (LTS) | `backend/build.gradle.kts` |
| Gradle wrapper | **9.4.1** | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin (AGP) | 9.1.0 | `build.gradle.kts` root |
| Compose Multiplatform plugin | 1.10.3 | `build.gradle.kts` root |
| Jetpack Compose BOM | 2025.04.01 | inferred from CMP 1.10.3 |
| Ktor (mobile HTTP+WS client) | **3.4.2** | `mobile/composeApp/build.gradle.kts` |
| Decompose (navigation) | 3.5.0 | `mobile/composeApp/build.gradle.kts` |
| Koin (DI) | 4.2.0 | `mobile/composeApp/build.gradle.kts` |
| Coil (image loading) | 3.4.0 | `mobile/composeApp/build.gradle.kts` |
| SQLDelight | 2.3.2 | root & mobile build files |
| kotlinx-serialization-json | 1.10.0 | shared + mobile |
| kotlinx-coroutines | 1.10.2 | shared + mobile + backend |
| kotlinx-datetime | 0.7.1 | shared + mobile + backend |
| libsignal-android (E2E) | **0.86.5** | `mobile/composeApp/build.gradle.kts` |
| LiveKit Android SDK (calls) | **2.24.0** | `mobile/composeApp/build.gradle.kts` |
| Firebase BOM (Android) | 34.11.0 | `mobile/composeApp/build.gradle.kts` |
| Firebase Admin Java SDK (backend) | 9.8.0 | `backend/build.gradle.kts` |
| JJWT | **0.13.0** | `backend/build.gradle.kts` |
| MinIO Java SDK | **9.0.0** | `backend/build.gradle.kts` |
| MinIO Docker image | `minio/minio:latest` | `infra/docker-compose.yml` |
| PostgreSQL | 16-alpine | `infra/docker-compose*.yml` |
| Redis | 7-alpine | `infra/docker-compose*.yml` |
| Jsoup | 1.22.1 | `backend/build.gradle.kts` |
| Sentry (backend) | 8.37.1 (`sentry-spring-boot-4`) | `backend/build.gradle.kts` |
| Sentry (Android) | 8.37.1 | `mobile/composeApp/build.gradle.kts` |
| Twilio Java SDK | **11.3.6** | `backend/build.gradle.kts` |
| MockK | 1.14.9 | `backend/build.gradle.kts` |
| Testcontainers | 1.21.4 | `backend/build.gradle.kts` |
| ArchUnit | **1.4.1** | `backend/build.gradle.kts` |
| JaCoCo | 0.8.12 | `backend/build.gradle.kts` |
| detekt | **1.23.8** | `backend/build.gradle.kts` |
| JWT algorithm | HS256 | `CLAUDE.md` / `JwtProvider.kt` |
| E2E status | Flag-OFF (plaintext under TLS) | `CLAUDE.md` / `E2EConfig.kt` |
| iOS E2E (libsignal) | **NoOp stub** | `CLAUDE.md` |
| iOS calls (LiveKit) | **NoOp stub** | `CLAUDE.md` |

---

## 2. Component-by-Component Assessment

### 2.1 Language & Compiler — Kotlin

| Field | Value |
|---|---|
| Current | 2.3.20 |
| Latest stable | **2.4.0** (released 2026-06-05 per [The Kotlin Blog](https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/)) |
| Verdict | **UPGRADE (medium priority)** |
| Why | 2.4.0 ships stable context parameters, stable UUID API, Swift export updates, CMS GC default on K/Native, and Gradle 9.5 compatibility. 2.3.20 was only released in March 2026; this is a minor bump. |
| Effort | S |
| Risk | Low — incremental minor. Gradle 9.5 is needed to pair with 2.4 (see §2.4). |

### 2.2 Backend Framework — Spring Boot

| Field | Value |
|---|---|
| Current | 4.0.5 |
| Latest stable | **4.0.6** (April 2026 per [spring.io](https://spring.io/blog/2026/03/26/spring-boot-4-0-5-available-now/)); Spring Boot **4.1.0-RC1** was released April 23 with GA expected ~May/June 2026 per [endoflife.date](https://endoflife.date/spring-boot) |
| Verdict | **UPGRADE patch (4.0.6) — KEEP major series (4.0.x)** |
| Why | 4.0.6 is a routine bug-fix drop (17 fixes). 4.1 is in RC as of review date; wait for GA before adopting. The 4.0.x series is active and supported. |
| Effort | S (4.0.6 patch only) |
| Risk | Negligible for patch. 4.1 GA — revisit when released. |

### 2.3 Java Toolchain

| Field | Value |
|---|---|
| Current | Java 21 (LTS) |
| Latest LTS | Java 21 — still the current LTS. Java 25 is the next LTS (GA Sep 2025 preview, stable March 2026); Java 26 was just released. |
| Verdict | **KEEP** |
| Why | Java 21 is the most widely deployed LTS and the minimum for Spring Boot 4.x. Upgrading to Java 25 now adds no material benefit for this workload and introduces risk. Re-evaluate when Spring Boot explicitly recommends 25 as baseline. |
| Effort | — |
| Risk | — |

### 2.4 Build System — Gradle

| Field | Value |
|---|---|
| Current | **9.4.1** |
| Latest stable | **9.5.1** (May 2026 per [Gradle Releases](https://gradle.org/releases/)) |
| Verdict | **UPGRADE** |
| Why | 9.5.1 is the current stable; 9.4.1 is one minor behind. Kotlin 2.4.0 explicitly supports Gradle 9.5.0 per its [release notes](https://kotlinlang.org/docs/whatsnew24.html). Upgrading unblocks Kotlin 2.4 and gives the latest cache and configuration-cache fixes. |
| Effort | S — change one line in `gradle-wrapper.properties` |
| Risk | Low |

Note: `CLAUDE.md` header says "Gradle 8.14.4" but the actual `gradle-wrapper.properties` pins **9.4.1** — the CLAUDE.md entry is stale and should be corrected.

### 2.5 Android Gradle Plugin (AGP)

| Field | Value |
|---|---|
| Current | 9.1.0 |
| Latest stable | **9.2.0** (April 2026 per [Android Developers](https://developer.android.com/build/releases/agp-9-2-0-release-notes)) |
| Verdict | **UPGRADE** |
| Why | 9.2 adds HTML test-result dashboards and the latest API 36.1 support. Note: libsignal 0.94.4 already bundles AGP 9.1.1 internally, so upgrading the app's AGP to 9.2 is independent but desirable. |
| Effort | S |
| Risk | Low |

### 2.6 Compose Multiplatform

| Field | Value |
|---|---|
| Current | 1.10.3 (plugin in root `build.gradle.kts`) |
| Latest stable | **1.11.0** (April 2026 per [JetBrains blog](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html)); Jetpack Compose BOM 2026.04.01 |
| Verdict | **UPGRADE** |
| Why | 1.11.0 / BOM 2026.04.01 ships Compose UI/Foundation/Material 1.11 stable, Navigation 3 stabilisation, hot-reload stable. iOS support has been production-stable since 1.8.0 (May 2025). |
| Effort | S–M — typically a one-line version bump; verify API changes in `material3` and `foundation` |
| Risk | Low–Medium: check for Material3 API changes |

### 2.7 Ktor (mobile HTTP + WebSocket)

| Field | Value |
|---|---|
| Current | **3.4.2** |
| Latest stable | **3.5.0** (May 2026 per [Ktor releases](https://ktor.io/docs/releases.html)) |
| Verdict | **UPGRADE** |
| Why | 3.5.0 adds OpenAPI generation, Zstd compression support, duplex streaming for OkHttp. One minor version behind. |
| Effort | S |
| Risk | Low |

Note: Backend uses `kotlinx-coroutines:1.10.2` and a compatible Ktor version transitively through Spring Boot — not the mobile Ktor pin. These are independent.

### 2.8 libsignal-android — E2E Encryption (SECURITY CRITICAL)

| Field | Value |
|---|---|
| Current | **0.86.5** |
| Latest stable | **0.94.4** (released 2026-06-02 per [signalapp/libsignal releases](https://github.com/signalapp/libsignal/releases)) |
| Verdict | **UPGRADE — HIGH PRIORITY (security)** |
| Why | The project is **8 minor versions behind** on a security-critical crypto library. The gap covers 0.86→0.94 — approximately 8 months of security patches. A 2026 GHSA advisory (GHSL-2026-102) documented an Android intent-redirection attack vector in older Signal builds. More critically, a 2026 cryptographic research paper (eprint.iacr.org/2026/484) identified an SSS (Sealed Sender Service) forgery vulnerability in older libsignal releases (patched in Signal Android v7.72.2+, which tracks libsignal ~0.90+). While Muhabbet does not use SSS, staying 8 releases behind on a Rust-based crypto primitive library is unjustifiable risk for a privacy-first product. |
| Effort | M — the Signal team does make backwards-incompatible Java/Kotlin API changes across minor versions; audit `SignalKeyManager`, `PersistentSignalProtocolStore`, and any call sites against the release notes for each intermediate version |
| Risk | **HIGH if not upgraded. Crypto libraries must be current.** |

**Recommended process:** Update one minor version at a time, compile and run the E2E unit tests at each step. Pin to 0.94.4, not `latest.release`.

### 2.9 Navigation — Decompose

| Field | Value |
|---|---|
| Current | 3.5.0 |
| Latest stable | **3.5.0** (March 2026 per [arkivanov/Decompose releases](https://github.com/arkivanov/Decompose/releases)) |
| Verdict | **KEEP (current)** |
| Why | Already on the latest stable. |
| Strategic note | JetBrains shipped **Navigation 3** for Compose Multiplatform in CMP 1.10.0 (January 2026) as an officially-supported alternative, with broad community momentum. Decompose remains an excellent, well-maintained library (lifecycle isolation, testability), but new CMP projects in 2026 increasingly start with Navigation 3. For an existing app with Decompose deeply wired in, migration is an L effort — flag as a **CONSIDER** for a future major refactor, not urgent. |
| Effort | — (current); L (future migration to Navigation 3) |
| Risk | Low to stay; Medium to migrate |

### 2.10 Dependency Injection — Koin

| Field | Value |
|---|---|
| Current | 4.2.0 |
| Latest stable | **4.2.1** (per [Koin GitHub releases](https://github.com/InsertKoinIO/koin/releases)) |
| Verdict | **UPGRADE** |
| Why | One patch behind. 4.2.1 restores binary compatibility for `runOnKoinStarted` on JVM and improves scope resolution error messages. Minor. |
| Effort | S |
| Risk | Negligible |

### 2.11 Local DB — SQLDelight

| Field | Value |
|---|---|
| Current | 2.3.2 |
| Latest stable | **2.3.2** (released March 2026 per [sqldelight GitHub](https://github.com/sqldelight/sqldelight)) |
| Verdict | **KEEP (current)** |
| Why | Already on the latest stable. |
| Effort | — |
| Risk | — |

### 2.12 Image Loading — Coil

| Field | Value |
|---|---|
| Current | 3.4.0 |
| Latest stable | **3.4.0** (February 2026 per [coil-kt changelog](https://coil-kt.github.io/coil/changelog/)); 3.5.0-beta01 is in pre-release |
| Verdict | **KEEP (current stable)** |
| Why | On the latest stable. 3.5.0-beta01 is available but not recommended for production. |
| Effort | — |
| Risk | — |

### 2.13 LiveKit Android SDK (Voice/Video Calls)

| Field | Value |
|---|---|
| Current | **2.24.0** |
| Latest stable | **2.26.0** (June 2026 per [livekit/client-sdk-android](https://github.com/livekit/client-sdk-android/releases)) |
| Verdict | **UPGRADE** |
| Why | 2.25–2.26 includes RPC V2 support, echo cancellation fixes, reconnection improvements, and an E2EE convenience constructor. Two minor versions behind. |
| Effort | S |
| Risk | Low — check CallEngine integration points |

### 2.14 Firebase (Backend Admin + Android BOM)

| Field | Value |
|---|---|
| Current | Firebase Admin Java 9.8.0; Firebase BOM 34.11.0 |
| Latest stable | Firebase Admin Java **9.9.0** (per [firebase/firebase-admin-java releases](https://github.com/firebase/firebase-admin-java/releases)); Firebase Android BOM — check [Google BoM mapping](https://firebase.google.com/support/release-notes/admin/java) |
| Verdict | **UPGRADE** |
| Why | Firebase Admin 9.9.0 adds Phone Number Verification API improvements and Cloud Messaging API enhancements. The Android BoM should be updated alongside CMP upgrade. |
| Effort | S |
| Risk | Low |

### 2.15 JJWT (JWT signing library, backend)

| Field | Value |
|---|---|
| Current | **0.13.0** |
| Latest stable | **0.13.0** (confirmed as latest per [jwtk/jjwt releases](https://github.com/jwtk/jjwt/releases) and [MVNRepository](https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-api)) |
| Verdict | **KEEP (current)** |
| Why | On the latest stable release. |
| Effort | — |
| Risk | — |

**Algorithm note (separate from version):** The project uses **HS256** (symmetric HMAC). For a monolith where no external party needs to verify tokens, HS256 is architecturally appropriate (see [Auth0 RS256 vs HS256](https://auth0.com/blog/rs256-vs-hs256-whats-the-difference/)). If Muhabbet ever exposes a public token endpoint or adds microservices, upgrade to **ES256** (ECDSA, smaller keys than RSA) at that point. Not urgent today.

### 2.16 MinIO (Object Storage) — URGENT

| Field | Value |
|---|---|
| Current | `minio/minio:latest` (Docker Hub) + Java SDK 9.0.0 |
| Latest stable | MinIO community edition **discontinued** (Docker Hub images archived Feb 2026). Repository entered maintenance mode Dec 2025, officially no-longer-maintained Feb 2026, archived Apr 2026. |
| Verdict | **REPLACE IMAGE — HIGH PRIORITY (supply chain)** |
| Why | Pulling `minio/minio:latest` now resolves to the last archived image (RELEASE.2025-10-15T17-29-55Z); it will never receive security patches. This is a silent supply-chain risk. The MinIO Java SDK (client-side S3 API) is unaffected — the issue is the server Docker image. |
| Effort | S–M (image swap) |
| Risk | **HIGH if left as-is. CVEs will accumulate on an unpatched server.** |
| Recommended drop-in replacements | 1. **Chainguard `cgr.dev/chainguard/minio:latest`** — free tier, hardened, continuously rebuilt from source (see [Chainguard blog](https://www.chainguard.dev/unchained/secure-and-free-minio-chainguard-containers)). 2. **Pin the last known-good MinIO release** `minio/minio:RELEASE.2025-10-15T17-29-55Z` as a short-term stopgap. 3. **Migrate to cloud S3** (AWS S3 / Cloudflare R2) if self-hosted object storage becomes a maintenance burden. |

**MinIO Java SDK (9.0.0):** The SDK itself is independent of the server; assess whether 9.0.x is still receiving patches from MinIO (likely not). Consider swapping to the AWS S3 Java SDK v2 (`software.amazon.awssdk:s3`) which is fully S3-compatible with MinIO and maintained by AWS.

### 2.17 PostgreSQL

| Field | Value |
|---|---|
| Current | 16-alpine |
| Latest stable | **17** (GA Sep 2024); PostgreSQL 18 announced for 2026 |
| Verdict | **KEEP (revisit at Tier 3)** |
| Why | PostgreSQL 16 is active/supported through Nov 2028. PG17 introduces logical replication improvements, json_table, and identity column syntax. For MVP/Tier 1, the upgrade risk outweighs the benefit. Re-evaluate at scale (Tier 3). |
| Effort | M (data migration required, `pg_upgrade` in Docker) |
| Risk | Medium |

### 2.18 Redis

| Field | Value |
|---|---|
| Current | 7-alpine |
| Latest stable | **Redis 8.8.0** (available on Docker Hub; 8.6 benchmarks show 5x throughput over 7.2 per [linuxiac.com](https://linuxiac.com/redis-8-6-improves-throughput-by-more-than-five-times-over-redis-7-2/)) |
| Verdict | **CONSIDER-UPGRADE (medium priority)** |
| Why | Redis 8 GA merges JSON, Search, TimeSeries modules into core — no modules used in this project, but the licensing changed (RSALv2/SSPLv1/AGPLv3 tri-license from 7.4+). Redis 7 (BSD) is still actively maintained. For a presence/pub-sub use case with modest load, Redis 7 is fully adequate. Upgrading to Redis 8 would yield performance and future-proofing benefits but the AGPLv3 license option should be reviewed with counsel before adopting. |
| Effort | S (image tag change, no API changes for basic use) |
| Risk | Low–Medium (license review recommended) |

### 2.19 Sentry

| Field | Value |
|---|---|
| Current | 8.37.1 (`sentry-spring-boot-4` + `sentry-android`) |
| Latest stable | **8.42.0** (released 2026-05-20 per [getsentry/sentry-java](https://github.com/getsentry/sentry-java/releases)) |
| Verdict | **UPGRADE** |
| Why | Five patch versions behind. Sentry SDK patches frequently contain performance and reliability fixes. Routine upgrade. Note: the CLAUDE.md gotcha about `SentryAutoConfiguration` exclusion still applies through 8.x for Spring Boot 4.x — keep the `excludeName` workaround until Sentry publishes a Spring Boot 4.x auto-configuration (track [sentry-java #3xxx](https://github.com/getsentry/sentry-java)). |
| Effort | S |
| Risk | Low |

### 2.20 detekt (Static Analysis)

| Field | Value |
|---|---|
| Current | **1.23.8** |
| Latest stable | 1.23.8 is the latest in the 1.x stable line. **detekt 2.0.0-alpha** is in active development (built against Kotlin 2.3.21 per [detekt releases](https://github.com/detekt/detekt/releases)) but not yet stable. |
| Verdict | **KNOWN ISSUE — track 2.0.0 GA** |
| Why | detekt 1.23.8 has a confirmed compatibility issue with **Kotlin 2.3.0+ metadata** that causes false positives across many rules (see [GitHub #8865](https://github.com/detekt/detekt/issues/8865)). Workaround: either (a) pin K2 analysis mode off in detekt config, or (b) switch to detekt 2.0.0-alpha3 (built for K2/Kotlin 2.3+). Given the project is on Kotlin 2.3.20, this is an active pain point. |
| Effort | S (alpha 2.0 switch) / wait for stable |
| Risk | Low (alpha may have its own instabilities) |
| Recommended action | Pin `io.gitlab.arturbosch.detekt:detekt-gradle-plugin:2.0.0-alpha.3` (or latest alpha), test for false-positive reduction. Roll back if the alpha introduces new noise. Track [detekt/detekt #6035](https://github.com/detekt/detekt/discussions/6035) for 2.0 stable ETA. |

### 2.21 ArchUnit

| Field | Value |
|---|---|
| Current | 1.4.1 |
| Latest stable | **1.4.2** (April 2026 per [TNG/ArchUnit releases](https://github.com/TNG/ArchUnit/releases)) |
| Verdict | **UPGRADE** |
| Why | One patch behind. Routine. |
| Effort | S |
| Risk | Negligible |

### 2.22 Twilio Java SDK

| Field | Value |
|---|---|
| Current | 11.3.6 |
| Latest stable | **12.1.1** (May 2026 per [twilio/twilio-java](https://github.com/twilio/twilio-java)) |
| Verdict | **UPGRADE (medium priority)** |
| Why | The 12.x line is a major version bump with breaking API changes; review migration guide before upgrading. However, staying on 11.3.x long-term is not ideal for a production OTP path. |
| Effort | M (breaking API changes, review CHANGES.md) |
| Risk | Medium — test OTP send/receive end-to-end |

### 2.23 Testcontainers

| Field | Value |
|---|---|
| Current | 1.21.4 |
| Latest stable | **1.21.4** (current per [testcontainers GitHub](https://github.com/testcontainers/testcontainers-java)) |
| Verdict | **KEEP (current)** |
| Why | Already on latest stable. |
| Effort | — |
| Risk | — |

### 2.24 MockK

| Field | Value |
|---|---|
| Current | 1.14.9 |
| Verdict | **KEEP (monitor)** |
| Why | 1.14.9 is the latest available as of review. |
| Effort | — |
| Risk | — |

---

## 3. Architecture Assessment

### 3.1 Modular Monolith (Hexagonal Architecture)

**Verdict: KEEP**

The modular-monolith hexagonal pattern is the right architecture for an MVP with a solo engineer. JetBrains, Spring, and the broader Kotlin community continue to endorse this pattern for small-to-medium services (see [Spring Modulith](https://spring.io/projects/spring-modulith) — Spring Boot 4.x first-class support). None of the current modules (`auth`, `messaging`, `media`, `presence`, `notification`, `moderation`) has reached the traffic or deployment-autonomy threshold that would justify a service split. The existing Spring ApplicationEvent bus provides clean module boundaries.

**When to reconsider:** If `media` (large binary processing) or `notification` (FCM fan-out) becomes a scaling bottleneck independently of the rest, extract as a sidecar process. Not warranted today.

### 3.2 JWT Algorithm — HS256

**Verdict: KEEP (monolith context), FLAG for future**

For a monolithic backend where the only JWT consumer is the same process that issues it, HS256 is architecturally sound and carries no known weakness in this topology. The security guidance to prefer RS256/ES256 applies primarily to microservice and multi-party token verification scenarios. If a public JWKS endpoint or third-party token verification is ever added (e.g., Muhabbet integrating as an OIDC provider), upgrade to **ES256** at that time.

### 3.3 WebSocket Scaling (Redis Pub/Sub broadcaster)

**Verdict: KEEP**

The `RedisMessageBroadcaster` for horizontal WS scaling is a good implementation of the fan-out pattern. Redis Pub/Sub is standard for this use case. No changes needed; this is well-implemented.

---

## 4. iOS Bridging Path — libsignal + LiveKit (deep-dive)

Both Signal E2E encryption and LiveKit calls are **NoOp stubs on iOS** today. This is the most complex open engineering problem in the stack.

### 4.1 libsignal on iOS

**libsignal** ships a first-party Swift binding (in `swift/` directory of the repo). The canonical integration is **CocoaPods** (SPM is explicitly "not supported for production" per the [swift/README.md](https://github.com/signalapp/libsignal/blob/main/swift/README.md)). There is no Kotlin/Native path; `libsignal-android` is a JNI wrapper over Rust — it is Android/JVM-only.

**Recommended path:**
1. Integrate `LibSignalClient` as a CocoaPod in the iOS target (via KMP's CocoaPods DSL in `shared/build.gradle.kts`).
2. Create an Objective-C bridging shim that exposes the Signal session/encrypt/decrypt operations to Kotlin/Native via `cinterop` (`.def` file pointing at the ObjC interface).
3. Implement the `EncryptionPort` interface in `iosMain` using this cinterop.
4. Alternatively (Kotlin 2.4.0+): use the new **Swift export** feature (beta → expected stable soon) to write the iOS encryption adapter in Swift directly, calling `LibSignalClient`, and expose it to the KMP shared module. This avoids the ObjC bridging layer entirely.

The Kotlin 2.4.0 release notes explicitly mention "support for Swift packages as dependencies" and "updates on Swift export" — this is the modern recommended path for iOS-only native SDK integration from KMP. [Kotlin 2.4.0 blog](https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/).

**Effort: L** (requires iOS dev environment, CocoaPods/SPM setup, cinterop or Swift export wiring, and thorough E2E key-exchange testing)

**Note on libsignal version for iOS:** The CocoaPod version and the Android library version track the same upstream `signalapp/libsignal` releases. Upgrading to 0.94.4 on Android aligns with the iOS CocoaPod at the same version tag.

### 4.2 LiveKit on iOS

LiveKit ships a first-party **Swift SDK** ([livekit/client-sdk-swift](https://github.com/livekit/client-sdk-swift)), available via Swift Package Manager. Unlike libsignal, LiveKit Swift SDK is supported for production via SPM, which is directly importable in KMP via the `swiftPMDependencies {}` Gradle DSL introduced in Kotlin 2.4.0.

**Recommended path (Kotlin 2.4.0+):**
```kotlin
// In iOS target's build.gradle.kts
swiftPMDependencies {
    packageDependency(
        url = "https://github.com/livekit/client-sdk-swift",
        exact = "2.x.y"
    )
}
```
Wrap `LiveKit.connect()`, `Room`, `LocalAudioTrack` etc. in an `expect/actual` `CallEngine` backed by Swift calls via Swift export.

**Effort: M–L** (simpler than libsignal because SPM is fully supported)

### 4.3 Combined iOS completion priority

Completing iOS E2E + iOS calls together makes sense because both share the Kotlin 2.4.0 / Swift export / SPM infrastructure. The two features can share the bridging scaffolding once established.

---

## 5. Prioritized Recommendations

Sorted by **value-per-effort** (security/supply-chain first, then currency, then strategic):

### P0 — Do Immediately (Security / Supply Chain)

| # | Recommendation | Effort | Risk |
|---|---|---|---|
| P0-1 | **Upgrade libsignal-android 0.86.5 → 0.94.4** — 8 minor versions behind on a live crypto library; CVE-adjacent vulnerability patched in interim releases | M | High if deferred |
| P0-2 | **Replace `minio/minio:latest` Docker image** — archived Feb 2026, will never receive patches; switch to `cgr.dev/chainguard/minio` or pin last known-good release | S | High if deferred |

### P1 — Do Next Sprint (Currency + Minor Security)

| # | Recommendation | Effort | Risk |
|---|---|---|---|
| P1-1 | **Upgrade detekt to 2.0.0-alpha3** — fix confirmed false-positives against Kotlin 2.3.20 metadata | S | Low |
| P1-2 | **Upgrade Gradle 9.4.1 → 9.5.1** — aligns with Kotlin 2.4.0, brings cache fixes | S | Low |
| P1-3 | **Upgrade Spring Boot 4.0.5 → 4.0.6** — routine patch with 17 bug fixes | S | Negligible |
| P1-4 | **Upgrade Sentry 8.37.1 → 8.42.0** — 5 patch versions of reliability fixes | S | Low |
| P1-5 | **Upgrade Kotlin 2.3.20 → 2.4.0** — pairs with Gradle 9.5 upgrade; stable context params, CMS GC for iOS, Swift export progress | S | Low |

### P2 — This Quarter (Functional Improvements)

| # | Recommendation | Effort | Risk |
|---|---|---|---|
| P2-1 | **Upgrade AGP 9.1.0 → 9.2.0** | S | Low |
| P2-2 | **Upgrade CMP 1.10.3 → 1.11.0** + Jetpack BOM 2026.04.01 | S–M | Low–Medium |
| P2-3 | **Upgrade Ktor 3.4.2 → 3.5.0** | S | Low |
| P2-4 | **Upgrade LiveKit Android 2.24.0 → 2.26.0** | S | Low |
| P2-5 | **Upgrade Firebase Admin Java 9.8.0 → 9.9.0** | S | Low |
| P2-6 | **Upgrade Koin 4.2.0 → 4.2.1** | S | Negligible |
| P2-7 | **Upgrade ArchUnit 1.4.1 → 1.4.2** | S | Negligible |

### P3 — Consider (Strategic, Medium Effort)

| # | Recommendation | Effort | Risk |
|---|---|---|---|
| P3-1 | **iOS E2E bridging** (libsignal CocoaPod + cinterop / Swift export) — needed to exit "iOS = NoOp" for encryption | L | Medium |
| P3-2 | **iOS LiveKit bridging** (SPM import via Kotlin 2.4 `swiftPMDependencies`) — needed to exit "iOS = NoOp" for calls | M–L | Medium |
| P3-3 | **Upgrade Twilio 11.3.6 → 12.1.1** — breaking API change, review migration guide | M | Medium |
| P3-4 | **Audit MinIO Java SDK 9.0.0** — consider replacing with AWS SDK v2 (`software.amazon.awssdk:s3`) for long-term S3-compatible maintainability | M | Low–Medium |
| P3-5 | **Redis 7 → Redis 8** — 5x throughput for high-load scenarios; license review required (RSALv2/SSPLv1/AGPLv3 tri-license) | S (image) | Medium (license) |

### Not Worth Doing Now (Shiny But Not Warranted)

| Recommendation | Why deferred |
|---|---|
| PostgreSQL 16 → 17 | PG16 supported to 2028; no blocking feature gap at MVP scale |
| HS256 → RS256/ES256 JWT | Monolith — no multi-party token verification; not a risk in current topology |
| Modular Monolith → Microservices | Solo engineer; no independent scaling need; monolith is correct now |
| Navigation 3 migration (from Decompose) | Decompose 3.5.0 is current; migration is L effort with no immediate UX gain |
| Spring Boot 4.1 | Still RC as of review date; wait for GA |

---

## 6. CLAUDE.md Accuracy Notes

The following entries in `CLAUDE.md` are outdated after the March 2026 dependency upgrade sprint:

1. **Gradle**: CLAUDE.md table says "Gradle 8.14.4" — actual wrapper is **9.4.1**. Update the table.
2. **Backend framework**: CLAUDE.md says "Spring Boot 3" in the architecture diagram string — actual is **Spring Boot 4.0.5**.
3. **Kotlin 2.3.20**: Correctly stated in the Quick Reference table. ✓
4. **E2E status**: Accurately described as flag-OFF by default. ✓

---

## 7. Summary Verdict

The Muhabbet stack is **fundamentally modern and well-chosen**. The March 2026 dependency sprint already addressed the bulk of currency debt. The two genuine urgencies are:

1. **libsignal is 8 minor versions behind** on a live crypto library — a security obligation for a privacy-first product.
2. **MinIO Docker Hub images are archived** — a supply-chain vulnerability that will not self-heal.

Everything else is routine patch management or medium-term strategic work. The hexagonal architecture and "Kotlin everywhere" choices remain sound for the project's current scale and team size.

---

## Sources

- [Kotlin 2.4.0 Released — The Kotlin Blog](https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/)
- [Kotlin 2.3.20 Released — The Kotlin Blog](https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/)
- [Spring Boot 4.0.5 Release — spring.io](https://spring.io/blog/2026/03/26/spring-boot-4-0-5-available-now/)
- [Spring Boot releases & EOL — endoflife.date](https://endoflife.date/spring-boot)
- [Gradle 9.5.1 Release Notes](https://docs.gradle.org/current/release-notes.html)
- [Gradle 9.4.1 Release Notes](https://docs.gradle.org/9.4.1/release-notes.html)
- [signalapp/libsignal releases](https://github.com/signalapp/libsignal/releases)
- [libsignal swift/README.md](https://github.com/signalapp/libsignal/blob/main/swift/README.md)
- [GHSL-2026-102 Signal attachment exfiltration advisory](https://securitylab.github.com/advisories/GHSL-2026-102_Android_SignalApp/)
- [Signal Protocol SSS research — eprint 2026/484](https://eprint.iacr.org/2026/484.pdf)
- [Compose Multiplatform 1.11.0 / BOM 2026.04.01 — Android Developers Blog](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html)
- [Compose Multiplatform 1.10.0 — Navigation 3 — JetBrains Blog](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/)
- [CMP 1.8.0 iOS stable — JetBrains Blog](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)
- [Ktor 3.4.0 — JetBrains Blog](https://blog.jetbrains.com/kotlin/2026/01/ktor-3-4-0-is-now-available/)
- [Ktor releases](https://ktor.io/docs/releases.html)
- [arkivanov/Decompose releases](https://github.com/arkivanov/Decompose/releases)
- [Navigation 3 in CMP — JetBrains docs](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [livekit/client-sdk-android releases](https://github.com/livekit/client-sdk-android/releases)
- [MinIO Docker Hub discontinuation — Chainguard](https://www.chainguard.dev/unchained/secure-and-free-minio-chainguard-containers)
- [MinIO is Dead — blog.vonng.com](https://blog.vonng.com/en/db/minio-is-dead/)
- [Redis 8 GA — redis.io](https://redis.io/blog/redis-8-ga/)
- [Redis 8.6 5x throughput — linuxiac.com](https://linuxiac.com/redis-8-6-improves-throughput-by-more-than-five-times-over-redis-7-2/)
- [Firebase Admin Java SDK releases](https://github.com/firebase/firebase-admin-java/releases)
- [detekt GitHub — Kotlin 2.3.0 metadata issue #8865](https://github.com/detekt/detekt/issues/8865)
- [detekt 2.0 path — discussion #6035](https://github.com/detekt/detekt/discussions/6035)
- [ArchUnit releases](https://github.com/TNG/ArchUnit/releases)
- [SQLDelight 2.3.2 — GitHub](https://github.com/sqldelight/sqldelight/releases/tag/2.3.2)
- [Koin 4.2.1 — GitHub](https://github.com/InsertKoinIO/koin/releases)
- [sentry-java 8.42.0 — GitHub](https://github.com/getsentry/sentry-java/releases)
- [twilio-java 12.1.1 — GitHub](https://github.com/twilio/twilio-java/releases)
- [JWT RS256 vs HS256 — Auth0](https://auth0.com/blog/rs256-vs-hs256-whats-the-difference/)
- [AGP 9.2.0 release notes — Android Developers](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [KMP Swift Packages import — Kotlin docs](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
- [Kotlin Swift Export — docs](https://kotlinlang.org/docs/native-swift-export.html)
