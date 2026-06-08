# V&V Review — `shared` KMP Module

> **Date:** 2026-06-08 · **Reviewer:** automated SE loop (Task 3 — V&V of least-reviewed area)
> **Scope:** `shared/src/commonMain/.../{model,dto,protocol,validation,port}` — domain models
> (`Models.kt`), DTOs (`Dtos.kt`), the `WsMessage` sealed protocol + its `@SerialName`
> discriminators, `ValidationRules`, and the ports (`EncryptionPort`, `E2EKeyManager`,
> `E2EEnvelope`, `MediaKeyMaterial`, `DeviceLinkCrypto`).
> **Why this area:** per `docs/loop-ledger.md` it was the **last never-reviewed area**. It is the
> single source of truth shared by BOTH backend and mobile, so a serialization-contract or
> nullability bug here is high-leverage — it breaks two clients at once and is invisible to the
> per-module backend tests.

## Method
- Static read of all 9 `commonMain` source files + 5 existing test files.
- Cross-checked every `@SerialName` discriminator against the documented strings in `CLAUDE.md`
  ("WsMessage type discriminators"), and confirmed the backend (`ChatWebSocketHandler`,
  `RedisMessageBroadcaster`, `ReactionController`) and mobile both use the **single** `wsJson`
  instance.
- Traced `ValidationRules` usage across backend + mobile (`grep ValidationRules\.`) to find
  defined-but-unused or inconsistently-used limits.
- Assessed against `CLAUDE.md` SE principles (no `!!`, DRY, KISS, YAGNI) and
  serialization-compat safety (new fields default-valued? any non-nullable field that breaks an
  old payload? marker-based decode robust to plaintext?).
- Verified with `./gradlew :shared:jvmTest` and `./gradlew :mobile:composeApp:compileCommonMainKotlinMetadata`.

## Summary

| # | Severity | Title | Status |
|---|----------|-------|--------|
| A | Low (code style) | `NoOpKeyManager.generateIdentityKeyPair` used `return identityKey!!` — violates the `CLAUDE.md` "no `!!`" hard rule | **FIXED 2026-06-08** + regression test |
| B | Info (test gap) | `EncryptionPort`/`NoOpKeyManager`, `ValidationRules`, and the full WS discriminator set had **no** dedicated tests | **FIXED** — 3 new test files (~23 tests) |
| C | Low (currency) | `kotlinx.datetime.Instant` is deprecated in Kotlin 2.3.20 in favour of `kotlin.time.Instant` (compile warning on the shared wire type) | Documented → TODO P2 |
| D | Low (YAGNI / dead code) | `ValidationRules.ALLOWED_VIDEO_TYPES` + `MAX_VIDEO_SIZE_BYTES` have **zero** consumers (no video-upload path) | Documented → TODO P2 |

**No serialization-contract regressions found.** The wire format (discriminators, default-valued
optional fields, nullability, marker-based envelope decode) is correct and old-client-compatible.
The crypto-blocked seams (`DeviceLinkCrypto`, NoOp encryptors) behave as desired (loud throw /
transparent passthrough). Details below.

---

## Finding A — `!!` in `NoOpKeyManager`  *(FIXED)*

**Location:** `shared/.../port/E2EKeyManager.kt`, `NoOpKeyManager.generateIdentityKeyPair`.

**Before:**
```kotlin
override fun generateIdentityKeyPair(): String {
    identityKey = "noop-identity-key-${kotlin.random.Random.nextLong()}"
    return identityKey!!          // ← non-null assertion
}
```
`CLAUDE.md` → Code Style: **"No `!!` (non-null assertion) — handle nulls properly."** This is the
only `!!` in `shared/src/commonMain` (verified by grep). It is benign at runtime (the field was
just assigned a non-null value on the line above), so this is a **style/hard-rule** fix, not a
correctness bug.

**Fix:** assign to a local and return the local, so the value is never re-read through the nullable
field:
```kotlin
override fun generateIdentityKeyPair(): String {
    val key = "noop-identity-key-${kotlin.random.Random.nextLong()}"
    identityKey = key
    return key
}
```

**Regression test:** `EncryptionPortTest.identityKey_is_null_until_generated_then_stable` asserts
`getIdentityPublicKey()` is null before generation and returns exactly the generated value after —
pinning the no-`!!` identity path.

---

## Finding B — Test gaps in the shared contract  *(FIXED)*

Before this review the shared module had good coverage for the **newer** ports (`E2EEnvelope`,
`MediaKeyMaterial`, `DeviceLinkCrypto`) and for a representative slice of `WsMessage`, but **zero**
tests for:
- the `EncryptionPort` / `E2EKeyManager` NoOp seams (the MVP plaintext path the whole system runs
  on),
- `ValidationRules` (the limits backend + mobile both enforce — a drift here silently rejects valid
  input or accepts what a client never sends),
- the **complete** set of WS `@SerialName` discriminators. The existing `WsMessageSerializationTest`
  exercises most variants by round-trip but does not *pin every discriminator string* — a rename of
  a less-common one (e.g. `group.role_updated`, `security.key_changed`, `call.group_start`) would
  pass the suite yet break cross-client routing.

**Added (3 files, ~23 tests):**
- `protocol/WsDiscriminatorContractTest.kt` — pins **every** discriminator string verbatim against
  `CLAUDE.md`, plus that the discriminator field name is `type`. This is the guard that catches a
  silent wire-contract rename.
- `port/EncryptionPortTest.kt` (commonTest, non-suspend) + `jvmTest/.../EncryptionPortSuspendTest.kt`
  (suspend cases via `runBlocking`). Pins the NoOp passthrough as *transparent and total* (no
  exceptions, identity round-trip) — explicitly **not** a security assertion. (Split across source
  sets because `commonTest` has no coroutine test runner — only `kotlin("test")` — on the classpath;
  adding `kotlinx-coroutines-test` was out of scope, so the few suspend cases live in `jvmTest`.)
- `validation/ValidationRulesTest.kt` — boundary tests for phone/OTP/display-name/about/message/
  group-name limits + the media allowlists, all referencing the named constants (so the test tracks
  the rule, not a copied magic number).

---

## Finding C — `kotlinx.datetime.Instant` deprecated  *(documented → TODO P2)*

`shared/.../model/Models.kt` imports `kotlinx.datetime.Instant` and uses it on `Message`,
`Conversation`, and `UserProfile`. Kotlin 2.3.20 emits
`'typealias Instant = Instant' is deprecated. This type is deprecated in favor of kotlin.time.Instant`
on both the `:shared:compileKotlinJvm` and the mobile metadata compile. Latent (compiles &
serializes fine today — both serialize to ISO-8601), but it is a **cross-module wire type** touched
by backend + mobile serialization, so it must be migrated atomically with round-trip tests rather
than as a drive-by. Filed as TODO P2 (Finding C) with the explicit "JSON must stay byte-identical"
done-criterion.

## Finding D — Unused video validation rules  *(documented → TODO P2)*

`ValidationRules.ALLOWED_VIDEO_TYPES` and `MAX_VIDEO_SIZE_BYTES` have **zero** consumers
(`grep` across the repo finds only the definition). There is no `uploadVideo` path — `MediaService`
has image (`ALLOWED_IMAGE_TYPES` + `MAX_IMAGE_SIZE_BYTES`), voice (`ALLOWED_VOICE_TYPES` +
`MAX_VOICE_SIZE_BYTES`), and document (`MAX_DOCUMENT_SIZE_BYTES`, no type allowlist) paths only;
videos travel through the document path. So `ALLOWED_VIDEO_TYPES` is dead and the 100 MB
`MAX_VIDEO_SIZE_BYTES` is a redundant duplicate of `MAX_DOCUMENT_SIZE_BYTES` (YAGNI). Left
documented (not deleted) because a dedicated video path is plausibly near-term — TODO P2 (Finding D)
gives both options (delete vs. wire into a video-upload validation).

---

## What was verified good (no action)

- **WS discriminators are correct and complete.** All 9 discriminators documented in `CLAUDE.md`
  (`message.send`, `message.ack`, `presence.typing`, `presence.online`, `message.new`,
  `message.status`, `ack`, `error`, `call.room`) match the source exactly, as do all the other
  variants. Backend + mobile share the **single** `wsJson` (`classDiscriminator = "type"`,
  `ignoreUnknownKeys = true`, `encodeDefaults = true`, `isLenient = false`). The
  `ignoreUnknownKeys` + default-valued new fields combination is exactly the right forward-compat
  posture (proven by the existing `should_ignore_unknown_fields_gracefully` test and the
  `mentions`/`scheduledAt` defaults).
- **No old-client-breaking fields.** Every field added to the wire types since the contract froze
  (`mentions`, `mentionsEveryone`, `scheduledAt`, `viewOnce`, multi-device DTOs) is **default-valued
  or nullable**, so an old payload that omits them still deserializes. New non-nullable fields would
  be the danger; none were introduced.
- **Nullability matches the documented gotchas.** `Conversation.name` / `ConversationResponse.name`
  are nullable (the "null for DMs" gotcha); `UserProfile.phoneNumber` /
  `UserProfileDetailResponse.phoneNumber` are nullable with explicit KVKK comments (only the caller's
  own profile exposes the number). `NewMessage.senderName` is nullable. Correct.
- **Backend↔shared enum duplication is in sync.** `ContentType`
  (`TEXT, IMAGE, VOICE, VIDEO, DOCUMENT, LOCATION, CONTACT, POLL, STICKER, GIF`), `ConversationType`
  (`DIRECT, GROUP, CHANNEL`), `MemberRole` (`OWNER, ADMIN, MEMBER`) are value-for-value identical
  between `backend/.../messaging/domain/model` and shared. Enums serialize by **name**, so the
  append-at-end additions (STICKER/GIF) were wire-safe. The intentional split (`MessageStatus` adds a
  client-only `SENDING` vs. the backend's `DeliveryStatus`) is consistent — `AckMessage` only ever
  carries `DELIVERED`/`READ`.
- **The crypto-blocked seams fail the *right* way.** `DeviceLinkCrypto`'s shipped
  `NotYetImplementedDeviceLinkCrypto` **throws** `NotYetImplementedException` (with a libsignal-block
  pointer) on every method — it can never silently ship fake/plaintext "linking" (pinned by the
  existing `DeviceLinkCryptoTest`). The NoOp `EncryptionPort`/`E2EKeyManager` are the deliberate
  **transparent passthrough** for the flag-OFF MVP (TLS-only) — correct and now test-pinned.
  Boundary respected: **no crypto implemented in this review.**
- **Envelope decode is robust against plaintext confusion.** `E2EEnvelope.decodeOrNull` /
  `MediaKeyMaterial.decodeOrNull` do a cheap `contains(MAGIC)` pre-check, then a full JSON parse, and
  reject mismatched versions — so a user typing the marker or JSON-looking text is never
  misinterpreted (covered by existing tests). The two markers (`mhbt-e2e-1`, `mhbt-media-1`) are
  distinct and the media payload only ever lives *inside* an already-E2E body, so no cross-parse.

## Follow-ups for `TODO.md`
- [ ] **P2:** migrate `kotlinx.datetime.Instant` → `kotlin.time.Instant` (Finding C).
- [ ] **P2:** remove or wire the unused video validation rules (Finding D).

## Verification
- `./gradlew :shared:jvmTest` — **76 tests, 0 failures** (was 53; +23 from this review).
- `./gradlew :mobile:composeApp:compileCommonMainKotlinMetadata` — **green** (the shared changes do
  not break the mobile commonMain metadata compile). Only pre-existing `kotlinx.datetime`
  deprecation warnings remain (Finding C).
