# ADR 0018 — Identity, per-device keys, prekey directory, link keys and server-attested device certificates

**Status:** Accepted (2026-11-12, delegated) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); code against this ADR is written only while the matching scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits. Items (1)–(3) and (6) bind from acceptance; items (4) and (5) are **Proposed (gated)** and are built only at 1.2 behind gate G-PROXIMITY · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0018; §8.1–§8.4; §11.1 V33, V35; §13; §17 Q-O6, Q-O7](../architecture/multi-transport/decision-brief-2026-11-12.md) · Realises the crypto half of [ADR 0007](0007-companion-device-trust.md) · Depends on ADR-0010, ADR-0016, ADR-0017, ADR-0022, ADR-0029, ADR-0031. Supersedes nothing.

## Context

Three facts force this decision.

1. **The server's key directory is wrong in three ways today** (BE-14, verified on `dev`):
   - One-time prekey consumption is non-atomic: `EncryptionKeyPersistenceAdapter.kt:37-53` loads all
     unused keys, takes `firstOrNull`, then saves `used=true`, with no `@Lock` or `@Version`. Under
     READ COMMITTED two concurrent fetches can return the same key (C10).
   - Any authenticated user can drain any other user's one-time prekeys: no relationship check and
     no per-requester limit (M1). Afterwards every new session to the victim is
     signed-prekey-only, which weakens forward secrecy and makes first messages replayable.
   - Keys are per **user**, not per **device** (M2; tables from `V11__add_encryption_keys.sql`,
     `V16`, `V17`), which is incompatible with ADR 0007 companions and with any per-device identity.
   - An identity key can be silently replaced (BE-14).
2. **Off-server peers have no server TLS.** On LAN or Nearby (1.2) two phones must mutually
   authenticate as *registered devices of the right users*, and a ban, takedown or under-15
   finding must reach them while they are offline (security must-fix; ADR-0029 rule 5).
3. **The identity model was contested.** Gemini proposed PeerID = Blake3(Ed25519) with no phone
   number (C11): no recovery, no multi-device story, and no subscriber identity for Law 5651 / KVKK.
   A DHT cannot dispense one-time prekeys exactly once (C2, P2P-3).

Prekey semantics require a server that dispenses each one-time prekey once and rate-limits fetches
(https://signal.org/docs/specifications/x3dh/, https://signal.org/docs/specifications/pqxdh/).

## Decision

### Identity layers

| # | Layer | Key / record | Authority | Built |
|---|---|---|---|---|
| (1) | **Account** | E.164 + OTP (legal subscriber identity); age attestation stored server-side (ADR-0029) | Server | exists; age gate 0.4 |
| (2) | **Device** | `devices` row: one primary plus up to four companions (ADR 0007) | Server | exists |
| (3) | **Crypto identity** | One libsignal `IdentityKeyPair` **per device**; directory keyed by `(user_id, device_id)` | Device holds private key; server publishes public key | 0.4 (directory fixes) / 0.6 (schema) / 1.0 (keys in use) |
| (4) | **Link key** | Separate per-device static key for **link-layer authentication only**, signed by the device identity key | Device; published in `device_link_keys` | 1.2 (gated) |
| (5) | **DeviceCertificate** | Server-signed binding of (user, device, identity key, link key, validity) | Server CA | 1.2 (gated) |
| (6) | **Human verification** | Static single-scan QR safety number per `(userId, deviceId)` identity key (libsignal fingerprint between two device identity keys) | Users | 1.0 |
| (7) | Key transparency | — | — | **Research, not scheduled** |

No layer is derived from another's private key material. In particular, nothing is derived from
libsignal ratchet internals, which libsignal does not expose.

### (3) Prekey directory rules

1. **Atomic claim.** One statement per key class:
   `DELETE … WHERE id = (SELECT id … WHERE user_id = ? AND device_id = ? ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING …`.
   One-time EC and one-time Kyber prekeys are claimed this way; when exhausted, the bundle falls back
   to the device's **last-resort Kyber** prekey (never to "no PQ key").
2. **Fetch limits.** Per-requester and per-(requester, target) rate limits, values in
   `application.yml` (no magic numbers). An alert fires when one requester exhausts a target's
   one-time prekeys.
3. **Relationship check.** A bundle is served only if **the target has the requester as a contact**, or
   they share a conversation the target created or joined (not merely received an invite to); own
   devices are always allowed (sender fan-out). "Requester lists target" is attacker-controlled and
   is not sufficient. Rule 2 (per-pair limit plus the exhaustion alert, 0.4) is the **primary** M1
   control; rule 3 ships with 1.0, when prekeys first protect content.
4. **Low-water signal.** When a device's one-time stock falls below a threshold, the server emits a
   replenish hint; the device uploads a new batch.
   **Rotation.** The signed prekey and the last-resort Kyber prekey are rotated on a fixed interval
   (a client constant or `application.yml` value, not a magic number), and the previous key is kept
   for a grace period so in-flight session setups still complete. Intervals are set in the 1.0 work
   and are in scope of the 1.0 external crypto review.
5. **No silent replacement.** Publishing a *different* identity key for an existing `device_id` is
   rejected with an `ErrorCode`. A new identity key means a **new device**, which emits
   `IdentityKeyChanged(userId, deviceId)` → a safety-number-changed system message to every peer.
6. **Response shape.** A claim returns `List<DevicePreKeyBundle>`, one per active device of the
   target user, so fan-out is per device (brief §8.3).
7. **Device-set freshness.** At submit, the server rejects an envelope set that does not address
   every active device of each recipient (`MSG_DEVICE_SET_STALE`, ICD §3.5 step 5). The client
   refetches the device list and reseals (delivery-engine-spec T25b).

```kotlin
// backend, keys bounded context (brief §4.2, §5)
interface ClaimPreKeyBundleUseCase {
    fun claim(requester: DeviceId, target: UserId): List<DevicePreKeyBundle>
}
interface PrekeyDirectoryPort {
    fun claim(requester: DeviceId, target: UserId): List<DevicePreKeyBundle>
    fun publish(d: DeviceId, b: PreKeyBatch)
}
```

Timing: rules 1–2 on the existing tables in **0.4** (cheap, latent while E2E is off); per-device
schema (V33: `identity_keys`, `signed_prekeys`, `one_time_prekeys`, `kyber_prekeys` with
`last_resort`, all with `device_id`) plus rules 4–7 and the `keys` bounded context (extracted from
messaging's `EncryptionService`) in **0.6**. Rule 3 (relationship predicate) ships with 1.0. All seven are in force before any key is used (1.0).

### (4) Link key (1.2, gated)

- Purpose: authenticating the **link** on off-server hops (LAN, Nearby, uplink handover, RF).
  Domain-separated from content keys; never used for message content.
- Type follows the handshake chosen by the 1.2 spike (`LinkAuthenticator`, vetted library only):
  **X25519** if a vetted Noise library is chosen, **Ed25519** if TLS 1.3 with a pinned raw public key
  is chosen. No hand-rolled handshake.
- Binding: `identitySignature = Sign(identityKey, "mbt-linkkey-v1" ‖ len‖deviceId ‖ keyType ‖ len‖publicKey ‖ expiresAt)`,
  length-prefixed and domain-separated (GEM-13 rule, ADR-0022). Published in
  `device_link_keys(device_id, key_type, public_key, identity_signature, expires_at, revoked_at)` (V35).
- Rejected: evolutionary's "MAC under a key derived from the E2E session" — libsignal exposes no such
  derivation, so it would be guessed crypto.

### (5) DeviceCertificate (1.2, gated)

```kotlin
// :shared protocol/ (encoding: protobuf, explicit field numbers, ADR-0022)
data class DeviceCertificate(
    val v: Int, val serial: Serial, val userId: UserId, val deviceId: DeviceId,
    val identityKey: PublicKeyBytes, val linkKey: PublicKeyBytes,
    val notBefore: Instant, val notAfter: Instant /* = notBefore + 7 days */,
    val issuerKeyId: String, val signature: Bytes,
)
```

| Element | Rule |
|---|---|
| Chain | **Offline root** (public key pinned in the app, with a rotation list of current + next) → **online intermediate** (Ed25519, 90-day validity, its own root-signed record delivered with each renewal) → device certificate (Ed25519 signature, 7-day validity) |
| Signing input | `"mbt-devcert-v1" ‖` canonical length-prefixed encoding of all fields except `signature` |
| Issuance preconditions | Valid age attestation (ADR-0029 rule 1), no active ban, device not revoked, link key published and signature-valid |
| Renewal | On every online session (`welcome.certRenewal`, ADR-0022) |
| Revocation | **Non-renewal** plus a **signed delta CRL** pushed over WS (`crl.delta`) and fetched on connect. CRL deltas carry a monotonic `crlSeq`, are signed by the current intermediate under the label `mbt-crl-v1` (length-prefixed encoding, ADR-0022 rule 2), and a peer rejects a delta whose `crlSeq` is not newer than the last one applied. Offline exposure window ≤ 7 days (accepted and disclosed, owner decision D4; disclosed in the transparency text) |
| Verification on device | `DeviceCertificateVerifier.verify(cert, now)` checks chain, validity window (with `Clock` injected), CRL, and that `identityKey` equals the libsignal identity of the session peer |
| Format | Our own small protobuf record. **No X.509/PKIX parsing** |
| Key custody | Root offline. Intermediate in a file-mounted signer (`CertificateSignerPort` → `FileKeyEd25519Signer`) **never on a host that runs PR jobs**; depends on ADR-0031 being in force (Q-O7) |

Certificate lifecycle (per device, server view):

| State | Event | Next | Notes |
|---|---|---|---|
| — | first online session after 1.2, preconditions hold | ISSUED(serial, notAfter) | |
| ISSUED | online session, preconditions hold | ISSUED(new serial) | old serial superseded, not revoked (it stays valid until its own `notAfter`, so one device may hold several unexpired serials) |
| ISSUED | ban, takedown, age finding, device revoke, account deletion | REVOKED | CRL delta lists the device by `deviceId` with a `revokedAt` cutoff (all certificates for that device with `notBefore` < cutoff are refused), or else every unexpired serial issued to the device; WS sessions closed (`SessionRevocationPort`) |
| ISSUED | `notAfter` passes without renewal | EXPIRED | peers refuse it; no CRL entry needed |
| REVOKED / EXPIRED | preconditions hold again (e.g. ban lifted) | ISSUED(new serial) | |

Peer view (link state machine, brief §6.3): `AUTHENTICATING` succeeds only if both certificates
verify, neither certificate is refused by the local CRL (by serial or by `deviceId` cutoff), and the
handshake proves possession of the certified link key.

### (6) Verification and eligibility

- Safety number: static single-scan QR of libsignal fingerprints (no animated QR, no ultrasonic;
  ADR-0027). Verified state is stored locally per peer identity key.
- **Verification unit.** Verification is per `(userId, deviceId)` identity key, using libsignal's
  fingerprint between the two devices' identity keys. A peer user counts as verified only when every
  active device key has been verified. A newly linked companion is unverified until scanned, and the
  UI shows it as such rather than as a key change. The port is therefore
  `suspend fun safetyNumber(peer: DeviceAddress): SafetyNumber` (ADR 0017 §1), not keyed by `UserId`
  as the brief §5 sketch has it.
- **1.2 proximity and 1.3 uplink eligibility require a verified safety number** (ADR-0024/0025).
- `IdentityKeyChanged` (a new device replacing a revoked one — a reinstall or replacement is a new
  `device_id`, because a new key for an existing `device_id` is rejected by rule 5 and ADR-0020; not a
  newly linked companion) → **blocking interstitial** for verified contacts (verification is reset),
  visible notice for everyone else (brief §8.4). Outbox entries to that peer park as
  `UntrustedIdentity` (ADR-0016).

## Why

- **Per-device keys are the only model compatible with ADR 0007 without faking private-key export.**
  A per-user identity shared across devices would need key export, which ADR 0007 forbids faking.
- **The directory fixes are cheap and latent**, so they ship in 0.4/0.6, before any key is used in
  anger. Shipping E2E on a dispenser that hands the same one-time key to two initiators would undo
  the property PQXDH relies on (C10).
- **Certificates give revocation a bounded reach offline** (security judge), while building them at
  1.2 with their first consumer meets the architecture judge's objection to a trust root with no
  consumer (brief §3, judge disagreements).
- **A separate link key with a vetted handshake** keeps link authentication independent of the
  ratchet and of library internals (feasibility, architecture must-fix).
- **Seven days** balances offline usability against the window a banned or under-15 user keeps
  working over proximity (R15); a year-long certificate or link-key-rotation-only revocation (30 days)
  is too loose.

## Consequences

- New tables: V33 (0.6, per-device keys), V35 (1.2, `device_link_keys`, `device_certificates`,
  `crl_deltas`). Migrations additive first (CICD-09).
- **App reinstall = new device.** A reinstall generates a new identity key, so it registers a new
  `device_id`; the previous device row must be revoked (by the user, or by the primary-anchored flow
  of ADR 0007) so fan-out stops targeting a dead device. Peers see a safety-number-changed notice.
- A device offline for more than 7 days cannot authenticate to proximity peers until it reconnects.
  Intended and disclosed.
- **ADR 0007's `DeviceLinkCrypto` becomes real at 1.0** (per-device fan-out). Companions do **not**
  get server-side history for E2E conversations; history transfer is device-to-device (post-1.0) or
  via the encrypted backup (T3 step 5).
- An operational runbook for root and intermediate keys (generation ceremony, storage, rotation,
  compromise response) is a 1.2 entry artefact (R14).
- The server stays in the identity trusted computing base. Key transparency could reduce that trust
  later but cannot remove it, and without independent auditors would be false assurance.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Self-certifying PeerIDs (Gemini) | No recovery, no multi-device, no subscriber identity (C11) |
| Kademlia DHT as directory | No atomic take-and-delete; Sybil/Eclipse; lookup leakage (C2, P2P-3) |
| Per-user identity key shared across devices | Needs private-key export (ADR 0007) |
| Year-long certificates | Revocation window too wide |
| X.509/PKIX | Parser attack surface and complexity for a closed, two-party format |
| Link-key rotation as the only revocation | ~30-day window, too loose |
| MAC key derived from the E2E session | libsignal exposes no such derivation; guessed crypto |
| Build certificates "dormant" at 0.6 | Trust root with no consumer; YAGNI |

## Evidence

- Findings: BE-14, C10, M1, M2, C2, C11, P2P-3, GEM-13, KMP-13, CICD-01, CICD-02, R14/R15 (brief §15).
- Code: `backend/src/main/kotlin/com/muhabbet/messaging/adapter/out/persistence/EncryptionKeyPersistenceAdapter.kt:37-53`,
  `backend/src/main/kotlin/com/muhabbet/messaging/domain/service/EncryptionService.kt`,
  `backend/src/main/resources/db/migration/V11__add_encryption_keys.sql`, `V16`, `V17`,
  `shared/src/commonMain/kotlin/com/muhabbet/shared/port/DeviceLinkCrypto.kt` (the contract),
  `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/multidevice/DeviceLinkCrypto.kt` (accessor).
- External (from the crypto digest): https://signal.org/docs/specifications/x3dh/,
  https://signal.org/docs/specifications/pqxdh/, https://getsession.org/session-protocol-v2.

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| RQ-11 | Concurrent claims never return the same one-time prekey | Testcontainers concurrency test (Docker running) | 0.4 exit |
| RQ-11 | Publishing a different identity key for an existing `device_id` returns an error and emits nothing | Integration test | 0.6 exit |
| — | Per-requester and per-pair fetch limits enforced | Integration test | 0.4 |
| — | A requester with no relationship to the target (target has not added the requester and shares no conversation the target created or joined) is refused (own devices allowed) | Integration test | 1.0 |
| — | Exhausted one-time stock falls back to last-resort Kyber, never to a bundle without a PQ key | Integration test | 0.6 |
| — | Certificate issuance refused without age attestation or with an active ban | Unit + integration test | 1.2 |
| RQ-18 | A revoked device is refused off-server after CRL delta or at expiry | `TestClock` test + owner demonstration (P13) | 1.2 exit |
| — | Revoking a device refuses all of its unexpired serials, including superseded ones | `TestClock` test | 1.2 |
| — | A CRL delta with a non-increasing `crlSeq` or a bad `mbt-crl-v1` signature is rejected | Unit test | 1.2 |
| — | Linking a companion leaves existing device verifications intact and shows the companion as unverified (no interstitial) | UI/unit test | 1.0 |
| — | Signing key absent from every host that runs PR jobs | Inspection (ADR-0031 checklist) | 1.2 entry |
| — | Signing inputs are length-prefixed and domain-separated | Golden vectors on JVM, backend and iOS | 1.2 |

> **Open item [OI-01](../architecture/multi-transport/open-items.md#oi-01):** brief §5 now reads `safetyNumber(peer: DeviceAddress)` (erratum E-08); whether a single per-user QR over several identity keys is wanted is a question for the 1.0 external crypto review (P12).

> **Resolved (2026-11-12):** the predicate is fixed now and the schedule kept. Rule 3 reads
> "claim allowed only if the target has the requester as a contact, or they share a conversation the
> target created or joined (not merely received an invite to)". Rule 2 (per-pair claim limit plus
> exhaustion alert, shipping in 0.4 with BE-14 part 1) is the primary M1 control. Rule 3 ships with
> 1.0, when prekeys first protect content. No 0.4 scope change (brief erratum E-47).
