# Release plan

Living document. Update it in the same PR that changes what it describes.

## How versions work here

`versionName` / `versionCode` live in `mobile/composeApp/build.gradle.kts` and are the source of
truth. `BuildInfo.kt` duplicates them so commonMain can display a version without depending on the
Android `BuildConfig`; the `verifyBuildInfoVersion` Gradle task, wired into `check`, fails the build
if the two disagree. They did once — Gradle on 0.1.0 while the settings screen told users 1.0.0.

Pre-1.0, the **minor** number carries breaking changes. **1.0.0 is reserved for the first build that
ships end-to-end encryption switched on**, because the product's stated promise is privacy and the
version number should not claim readiness the crypto does not have.

Every release: bump both versions, add a CHANGELOG section, tag `v<version>`, attach the APK to a
GitHub release.

## Shipped

### 0.2.0 — 2026-08-12
The first build a user can actually log in with, and the first with working media. Login had been
impossible in every prior build (wrong API host), the Updates tab returned 500, and no media file
could load. See CHANGELOG for detail.

## Planned

### 0.3.0 — make it honest and operable
Nothing here is a feature; it is the gap between "works when I drive it" and "works for someone
else".

- **Close the two IDORs and the SSRF** recorded in `PRODUCT_ROADMAP_2026-06-06.md`. They are the
  oldest known-open defects in the repo and they are authorization bugs, which is the class users
  cannot see and cannot mitigate.
- **Take the actuator endpoints off the public internet** — `/actuator/prometheus`, `/actuator/metrics`
  and detailed `/actuator/health` answer unauthenticated today.
- **Fix CI** (#108). Until the billing lock clears or a repo-scoped runner exists, nothing is
  verified by anything but a human running Gradle locally.
- **Move the provider call out of the OTP transaction** (#105) before Twilio Verify is switched on.
- **Give the app a real domain.** `cdn-muhabbet.116.203.222.213.nip.io` is not a name to ship.

### 0.4.0 — a stranger can use it
- Real SMS delivery on by default, so signing up does not require reading a server log.
- The camera-badge dead tap (#109) and a pass over the other overlay affordances.
- Contact sync verified against a device that actually has contacts.
- Crash reporting confirmed to be receiving events.

### 0.5.0 — polish
- Lottie motion pass (#101) — empty states, delivery ticks, typing, voice waveform.
- Play Store internal testing track, once a keystore exists and the store listing is written.

### 1.0.0 — E2E on
Gated on the standing libsignal blocker: the Android Signal code does not compile against its own
pinned `0.86.5` API, and iOS is stubbed. Requires a crypto review and a two-device round-trip test
on real hardware. Until then the UI keeps telling users the truth: transport-encrypted only.

## Release checklist

1. `./gradlew :backend:test :shared:jvmTest :mobile:composeApp:testDebugUnitTest`
2. Bump `versionCode` / `versionName`; `./gradlew :mobile:composeApp:verifyBuildInfoVersion`
3. CHANGELOG section, dated, with a *Known issues* list that does not flatter the build
4. Drive the real journey on a device or emulator — login, send, media — before tagging
5. `git tag v<version>` and push the tag
6. Build the signed artifact, attach the APK to the GitHub release
7. Deploy the backend **with `--env-file .env.prod`** (see CLAUDE.md; omitting it takes the API down)

## Play Store status

Not published. What is missing, in order:

1. **A Google Play Developer account** — one-time \$25, needs the owner's identity and payment
   details. Nobody else can create it.
2. **An upload keystore.** `signingConfigs.release` already reads `MUHABBET_KEYSTORE_FILE`,
   `MUHABBET_KEYSTORE_PASSWORD`, `MUHABBET_KEY_ALIAS` and `MUHABBET_KEY_PASSWORD` from the
   environment, so the build side is ready. **Losing this file means never being able to update the
   app again** under the same listing, so it must be generated deliberately and backed up somewhere
   the owner controls — not committed, and not left only on a build machine.
3. **A store listing**: icon, feature graphic, screenshots, description, and a privacy policy at a
   public URL. The privacy policy in `docs/legal/` is a draft and, per its own header, contains
   claims that no longer match where the data actually lives.
4. **Data safety declarations** — the form asks what is collected and shared. Phone numbers,
   contacts hashes and media are all in scope, so this needs answering honestly rather than quickly.
5. **A release track.** Internal testing first; it needs no review wait and is the right place for
   the first real users.

Until 1 and 2 exist, the APK on the GitHub release is the distribution channel.
