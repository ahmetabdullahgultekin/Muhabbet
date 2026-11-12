# ADR 0016 — E2E is a fail-closed gate for every non-server path, enforced by type

**Status:** Proposed — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0016; §8.7](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Supersedes** [ADR-003](003-defer-e2e-encryption.md) (together with ADR-0017 for the library and licence). Depends on ADR-0012, ADR-0028.

## Context

ADR-003 (2026-02-11) deferred E2E and prepared an `EncryptionPort` seam with `NoOpEncryption`.
What exists on `dev` @ 2434324:

- `E2EConfig.ENABLED = false` and `MEDIA_ENABLED = false`; Android and iOS bind `NoOpKeyManager` and
  `NoOpEncryption`; the four libsignal files are `*.kt.disabled`; libsignal is not a dependency at
  all (C9 verification, KMP-M3).
- The send path is **fail-open by design**. `MessageEncryptor.encryptOutgoing` "falls back to
  plaintext on any failure", and a missing recipient or session means "plaintext fallback (never
  dropped, never thrown)" (`docs/e2e-rollout-runbook.md`, eligibility section and step 6;
  `mobile/.../crypto/MessageEncryptor.kt`). `MediaEncryptor` mirrors this for blobs.

On a TLS-to-own-server path a fallback is tolerable while the UI says "not E2E". On an off-server
path it is a data-exposure defect: offline, session-setup failure is the **normal** case (no prekey
reachable), so a fallback would put plaintext on a contact's or stranger's phone (C9). Gemini's
design assumed "X cannot read" while the code it would run on sends plaintext.

BLE pairing and Nearby's UKEY2 authenticate the radio peer, not the recipient, so they are not a
substitute.

## Decision

### 1. Types in `:mobile:crypto`

```kotlin
class SealedFrame internal constructor(val protocol: ProtocolTag, val bytes: Bytes) // Bytes: value class, content equality

interface EnvelopeSealer {
    suspend fun seal(conv: ConversationCrypto, to: List<DeviceAddress>, plain: ByteArray): SealResult
    suspend fun open(frame: SealedFrame): OpenResult
}
sealed interface SealResult {
    data class Sealed(val frames: List<SealedFrame>) : SealResult
    data object NoSession : SealResult
    data class UntrustedIdentity(val device: DeviceAddress) : SealResult
    data class Failed(val code: String) : SealResult
}
```

- `SealedFrame`'s constructor is `internal` to `:mobile:crypto`; only `EnvelopeSealer`
  implementations there can produce one. `SealedLink.send` accepts only `SealedFrame` (ADR-0012).
- `:mobile:crypto` holds **no producer** of `SealedFrame` until 1.0. At 0.5 the gate exists and is
  unreachable for content: an E2E entry can only sit in `PENDING_SEAL`.

### 2. Fail-closed behaviour

| Seal result | Outbox effect (ADR-0013) | UI |
|---|---|---|
| `Sealed` | QUEUED → dispatch | normal |
| `NoSession` | stays `PENDING_SEAL`; prekey fetch triggered | "waiting for secure session" |
| `UntrustedIdentity` | parked; user decision required | safety-number interstitial |
| `Failed` | `FAILED_PERMANENT(code)` or retry per code | "tap to retry" |

The silent plaintext fallback in `MessageEncryptor`/`MediaEncryptor` (C9) is **deleted, not
flag-gated**, at 0.5. No E2E conversations exist yet, so nothing regresses.

### 3. `NoOpEncryption` scope

- Legal only for conversations with `encryptionState = NONE`, on `ServerLink`, until 1.0.
- At 1.0 it is removed from the E2E conversation path. `E2EConfig` flags are retired in favour of
  the type gate plus a server-side minimum protocol version (`PROTOCOL_TOO_OLD`, ADR-0022).

### 4. Test fakes off the release classpath

`FakeSealer` (which reports E2E) and other fakes live in `:mobile:crypto-testkit`, allowed only on
test configurations. A build check fails if any `*-testkit` module appears on a release runtime
classpath.

### 5. Wording rule

"**No plaintext on sealed paths and in E2E conversations.**" Channels and communities remain
server-plaintext **by design**, and the UI labels them as such. No screen may claim E2E for a
conversation whose server copy is readable (see ADR-0021 for the 1.0 cutover).

## Why

- C9: a fallback turns the most common offline condition into plaintext exposure.
- A type that cannot be constructed outside one module cannot be passed by mistake. A runtime
  boolean per adapter or a capability flag on a fat port can be forgotten, mis-set or bypassed by a
  future adapter (GEM-17).
- Deleting the fallback now, while no E2E conversation exists, costs nothing and removes the option
  of turning it back on under pressure.
- ADR-003's premises are stale: libsignal is not linked, its licence is unresolved (KMP-13,
  ADR-0017), and the "graceful fallback" it enabled is the defect.

## Consequences

- **No non-server transport can carry content before 1.0, by construction.** Every proximity, relay
  and RF milestone (1.2–1.4) is downstream of 1.0.
- ADR-003 is marked *Superseded by ADR-0016 / ADR-0017* once approved (banner added now as
  "proposed supersession"). It is closed at 1.0 exit.
- `docs/e2e-rollout-runbook.md` (plaintext-fallback language, step 6 "verify fallback") is obsolete
  on acceptance and must be rewritten by the 1.0 work; it must not be followed meanwhile.
- `SealResult` gives the UI honest states instead of a silent downgrade.
- A release build cannot contain a fake that reports E2E.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Runtime boolean per adapter ("requires E2E") | Can be mis-set; not compiler-checked |
| Capability flag on a fat port | Same, plus ISP/LSP violation (GEM-17) |
| Rely on BLE pairing or Nearby UKEY2 | Authenticates the radio peer, not the recipient |
| Keep the fallback behind a flag | Code behind an off flag still ships and can be flipped |

## Evidence

- Findings: C9, C1, KMP-13, KMP-M3, GEM-17, GEM-16, SSOT-05.
- Code and docs: `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/crypto/{E2EConfig,MessageEncryptor,MediaEncryptor}.kt`,
  `docs/e2e-rollout-runbook.md`, `docs/adr/003-defer-e2e-encryption.md`.
- External sources (from the crypto digest): https://signal.org/docs/specifications/pqxdh/,
  https://signal.org/docs/specifications/x3dh/.

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| RQ-06 | Only `:mobile:crypto` constructs `SealedFrame` | Compile + architecture test | 0.5 |
| RQ-06 | No `*-testkit` module on any release runtime classpath | Build check in CI | 0.5 |
| — | No plaintext fallback code path remains in the send pipeline | Inspection + `grep` guardrail for the removed API | 0.5 |
| — | An injected seal failure yields `PENDING_SEAL` or `FAILED`, never plaintext | Engine test with a failing sealer | 0.5 (fake) / 1.0 (libsignal adapters) |
| — | No `SealedLink` ever receives a non-`SealedFrame` | Contract test over all `SealedLink` implementations | 1.0, re-run at 1.2 |
| RQ-13 | Server holds no plaintext for E2E conversations | SQL assertion (ADR-0021) | 1.0 exit |
