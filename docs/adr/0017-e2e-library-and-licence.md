# ADR 0017 — E2E library and licence: libsignal at the platform edge; licence of `mobile/` decided at 1.0 entry after counsel Q-L7 (D1); `shared/` and `backend/` stay MIT

**Status:** Accepted with D1 (2026-11-12, delegated; licence step deferred to 1.0 entry) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); code against this ADR is written only while the matching scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits. Under D1 `mobile/` stays MIT until 1.0 entry; the licence step requires preconditions (a)–(c) below, decided after the written Q-L7 opinion · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0017; §8.3; §13 (1.0.0); §17 Q-O1, Q-O2, Q-L7](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Related to** [ADR-003](003-defer-e2e-encryption.md), which ADR-0016 supersedes (brief §3) · Depends on ADR-0016, ADR-0028, ADR-0029, ADR-0031. Realises the crypto half of [ADR 0007](0007-companion-device-trust.md) via ADR-0018.

## Context

ADR-0016 makes E2E a fail-closed type gate for every non-server path, which means no proximity,
relay or RF path can carry content until a real E2E library produces `SealedFrame`s. This ADR picks
that library and settles the licence question that has silently blocked it.

State on `dev` @ 2434324:

- **No crypto library is linked.** `mobile/composeApp/build.gradle.kts:126` has
  `// implementation("org.signal:libsignal-android:0.100.0")` commented out; the four Signal source
  files are `*.kt.disabled` and do not compile against any current API (C9, KMP-M3, SSOT-05).
  CLAUDE.md still describes a "pinned 0.86.5" dependency that no longer exists (KMP-M3).
- **libsignal is AGPL-3.0; the repository is MIT** (`LICENSE` line 1). Shipping an APK linked
  against libsignal makes the distributed combined work subject to AGPL obligations (KMP-13,
  verified against https://github.com/signalapp/libsignal: "Licensed under the GNU AGPLv3",
  Java/Swift/TypeScript bindings over Rust, "Use outside of Signal is unsupported").
- **No KMP binding exists.** libsignal-android covers `androidMain` only; iOS needs the Swift
  `LibSignalClient` package, which has no Objective-C surface, so Kotlin/Native needs an `@objc`
  wrapper (KMP-13, and the same bridge shape MISS-3 describes for LiveKit Swift).
- **Distribution moved.** libsignal versions above 0.86.5 are published only to Signal's own Maven
  repository, already added to `settings.gradle.kts:18-22`.
- **iOS cannot launch at all** (KMP-01): no entry point, no Xcode project, no wired DI.
- Hand-written crypto is forbidden (CLAUDE.md "do not guess crypto"; ADR 0007; principle P12).
  Gemini proposed a "PQXDH + Double Ratchet engine in Kotlin" (C1); that is exactly the thing this
  ADR rules out.

The post-quantum frontier has moved: PQXDH protects only the initial handshake, and Signal's SPQR
("Triple Ratchet") adds post-quantum protection to the ratchet (C1,
https://signal.org/docs/specifications/pqxdh/, https://signal.org/blog/spqr/). Whether the
Java/Android and Swift bindings expose SPQR to third-party apps, and from which version, is
**unverified** (crypto digest, open question).

## Decision

### 1. Library

| Item | Decision |
|---|---|
| Protocol library | **libsignal**: PQXDH with Kyber prekeys, Double Ratchet, sender keys (groups), sealed sender (available, not scheduled), SPQR **when the bindings expose it — to be verified by the spike, not assumed** |
| Android binding | `org.signal:libsignal-android` from Signal's Maven repository (versions > 0.86.5 exist only there). Exact version chosen by the spike and pinned in `gradle/libs.versions.toml` (ADR-0028) |
| iOS binding | `LibSignalClient` (Swift) behind an `@objc` Swift wrapper consumed through Kotlin/Native cinterop |
| commonMain surface | Only the ports `EnvelopeSealer` and `SessionCryptoPort` (ADR-0016, brief §5). No libsignal type crosses into commonMain, `:mobile:messaging` or `:shared` |
| Module | Adapters `LibsignalAndroidSealer` (`androidMain`) and `LibsignalIosSealer` (`iosMain`) live in `:mobile:crypto` (ADR-0028), arriving at 1.0 |
| Parameters | Owned by the library. We do not choose KEM sizes, curves or ratchet parameters ("ML-KEM/Kyber-1024 as libsignal ships it", brief §12 item 10) |
| Envelope | Every ciphertext is **protocol-tagged** (`protocol` field, ADR-0022), so a library change does not ripple into wire, storage or UI |

```kotlin
// :mobile:crypto, commonMain — the only crypto surface the engine sees (signatures from brief §5,
// except safetyNumber: per device identity key, ADR 0018 §6 — libsignal fingerprints cover one key per side)
interface SessionCryptoPort {
    suspend fun safetyNumber(peer: DeviceAddress): SafetyNumber
    suspend fun processBundle(b: DevicePreKeyBundle)
}
// EnvelopeSealer / SealResult / SealedFrame: defined by ADR-0016.
```

### 2. Licence boundary (amended by owner decision D1, 2026-11-12)

**`mobile/` stays MIT until 1.0 entry.** No relicensing happens now, and nothing in 0.4–0.7 depends on
the licence. At 1.0 entry, after counsel answers Q-L7, the owner chooses one of two options:

- **(i)** relicense `mobile/` to AGPL-3.0-or-later; or
- **(ii)** keep the `mobile/` sources MIT and convey the libsignal-linked binary as a combined work
  under AGPL-3.0, with a corresponding-source offer (as `shared/` is already MIT code inside it).

Under **both** options every libsignal-linked artefact carries an AGPL source offer and a resolvable
source tag, because the distributed binary is a combined work with libsignal either way.

| Tree | Licence | Why |
|---|---|---|
| `mobile/` (every module that links, or ships in an artefact that links, libsignal) | **MIT until 1.0 entry**; then (i) AGPL-3.0-or-later or (ii) MIT sources conveyed in an AGPL-3.0 combined binary | The distributed app becomes a combined work with libsignal when libsignal is linked (1.0) |
| `shared/` | **MIT (unchanged)** | The backend links it; it never links libsignal. MIT code may be included in an AGPL work |
| `backend/` | **MIT (unchanged)** | Never links libsignal; talks to clients over the network only |
| `infra/`, `docs/` | unchanged | Not part of the linked work |

Mechanics at 1.0 entry, once the owner has chosen: under (i), a `mobile/LICENSE` with the AGPL-3.0
text, SPDX headers (or a `REUSE`-style mapping) stating the per-tree licence and a README licence
section; under (ii), a NOTICE stating that the distributed binary is conveyed under AGPL-3.0 while the
`mobile/` sources remain MIT. Under both, an in-app "source code" link satisfying the AGPL source
offer for the exact released version. The root `LICENSE` continues to cover everything else.

> **Resolved (2026-11-12):** decision D1 (2026-11-12): `mobile/` stays MIT until 1.0 entry. Whether to
> relicense `mobile/` to AGPL-3.0-or-later, or to convey MIT sources inside an AGPL-licensed binary, is
> decided after counsel answers Q-L7 (now including Q-L7(g) on §6 Installation Information for App
> Store conveyance). This does not block 0.4–0.7. The decision text above is amended accordingly.

### 3. Preconditions (before the dependency is restored, at 1.0 entry)

| # | Precondition | Evidence that closes it |
|---|---|---|
| (a) | The owner's choice of option (i) or (ii), recorded **after** the written Q-L7 opinion (Q-O1, D1) | Signed line in `APPROVAL.md` naming this ADR and the option |
| (b) | **Only for option (i):** contributor audit of `mobile/`: `git shortlog -sne -- mobile/`, including agent commits made under the owner's identity (GOV-06) | Written list of copyright holders; each non-owner contributor's consent or removal of their code |
| (c) | Written counsel opinion on distributing an AGPL work through **Google Play and the Apple App Store** (Q-L7) | Filed opinion; negative or qualified ⇒ fallback below |

Until (a)–(c) close (with (b) only for option (i)), libsignal stays out of the build, and **1.0
entry is blocked** (brief §13). Precondition (c) is requested in Phase 0 alongside the other counsel
questions; it does not block 0.x ([OI-29](../architecture/multi-transport/open-items.md#oi-29)).

### 4. Fallback if (c) is negative

An **Apache-2.0 core: vodozemac (Olm/Megolm) via UniFFI** on both platforms, behind the same
`EnvelopeSealer`/`SessionCryptoPort`. It loses PQXDH and needs its own ADR (protocol, group
semantics, crypto review). The protocol tag in the envelope (§1) is what makes this a swap rather
than a rewrite.

### 5. Pre-1.0 feasibility spike (entry criterion for 1.0)

| Track | Pass criterion | Observed by |
|---|---|---|
| Android | Two-device round trip on real builds: PQXDH session setup from a server-claimed bundle, 1:1 text both ways, session survives app restart | Owner (P13) |
| iOS | `@objc` bridge compiles under Kotlin/Native cinterop and round-trips a **golden vector** produced on Android | CI build + owner |
| SPQR | Binding exposes it or not, with the version | Spike report |
| Size | Release APK size delta with libsignal (the `libsignal_jni_testing.so` exclusion at `build.gradle.kts:195-203` retained) | CI |

**If KMP-01 is not fixed at the 0.6 gate, 1.0 ships Android-only** (owner decision D2,
2026-11-12), and the store listing and in-app text say so (no "end-to-end encrypted" claim reaches an
iOS user, because there is none). If KMP-01 is fixed but the iOS bridge is not ready by 1.0 entry, the
same Android-only rule applies to E2E. Bringing iOS back after that needs a new ADR.

## Why

- **libsignal is the only option that gives PQXDH, a deployed ratchet, sender keys and an upgrade
  path to SPQR without writing crypto.** Every alternative either loses post-quantum handshakes
  (vodozemac), is not designed for 1:1 (OpenMLS) or is hand-rolled (forbidden).
- **Only the libsignal-linked app is affected, and relicensing is a choice, not a requirement.**
  MIT sources can be conveyed inside an AGPL combined work, as `shared/` already is. Risk-first's
  draft relicensed `shared/` too, which is inconsistent: the MIT backend links `shared/`, and `shared/`
  never links libsignal. Keeping `shared/` and `backend/` MIT keeps the server free of copyleft
  obligations it does not trigger.
- **A relicence is a public, irreversible act for the owner**, so the choice between (i) and (ii) is
  deferred until the written counsel opinion exists (D1), and (i) is additionally gated on a
  contributor audit (agents committed under the owner's identity, GOV-06).
- **The commonMain surface is two ports**, so the 0.5 engine (ADR-0012) is built and tested against
  `FakeSealer` (testkit only, ADR-0016) long before any library is linked.

## Consequences

- **1.0 entry is blocked** until (a)–(c) close and the spike passes. 0.4–0.7 are unaffected.
- From 1.0, the distributed app is an AGPL-3.0 combined work under either option; under (i)
  `mobile/` is also AGPL-3.0-or-later in the repository. Anyone receiving the app may request the
  corresponding source for that exact build; the release flow
  (ADR-0030, build once, promote the same artefact) must tag the source that built each artefact.
- A **commonTest contract suite** in `:mobile:crypto-testkit` runs the same cases against the
  Android adapter, the iOS adapter and `FakeSealer` (seal/open round trip, `NoSession`,
  `UntrustedIdentity`, replayed frame rejected, golden vectors).
- Two platform implementations must be maintained; the iOS one depends on KMP-01 being fixed.
- CLAUDE.md's "libsignal upgrade (BLOCKED)" section becomes obsolete on acceptance and is replaced
  by a pointer to this ADR (SSOT-05). Correcting it is a separate doc change.
- An external crypto review is a 1.0 exit item (brief §13); the reviewer reviews the *integration*
  (store, session boundaries, fan-out, fail-closed paths), not the library.
- `docs/e2e-rollout-runbook.md` is obsolete on acceptance (ADR-0016) and is rewritten as part of
  the 1.0 work.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Hand-written PQXDH / Double Ratchet in Kotlin (Gemini, `gemini-chat.md:490`) | Forbidden by P12 and CLAUDE.md; unreviewable for a solo team (C1) |
| Convey the libsignal-linked binary under MIT terms | Not permitted by AGPL-3.0 (KMP-13) |
| Defer E2E past 1.0 | Every off-server path and the "Encrypted" milestone depend on it (ADR-0016) |
| OpenMLS (MIT) for 1:1 | MLS needs a Delivery Service and is built for groups (C3); a second schema discipline for 1:1 with no gain |
| Commercial libsignal licence | Not offered |
| Relicense `shared/` as AGPL too | `shared/` never links libsignal and the MIT backend links it |
| vodozemac now | Loses PQXDH; kept as the documented fallback (§4) |

## Evidence

- Findings: KMP-13, KMP-M3, KMP-01, C1, C9, SSOT-05, GOV-06, MISS-3 (networking verifier, Swift
  bridge cost).
- Code: `LICENSE:1`, `mobile/composeApp/build.gradle.kts:118-126, 195-203`,
  `settings.gradle.kts:18-22`, `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/crypto/`.
- External (from the digests): https://github.com/signalapp/libsignal,
  https://signal.org/docs/specifications/pqxdh/, https://signal.org/blog/spqr/,
  https://signal.org/docs/specifications/x3dh/.

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| — | (a) owner's choice of (i) or (ii) after Q-L7, (b) contributor audit if (i), (c) counsel opinion filed | Inspection of `APPROVAL.md` and the filed documents | Before libsignal is restored (1.0 entry) |
| — | Two-device Android round trip on real builds | Demonstration, owner-observed (P13) | 1.0 entry |
| — | iOS bridge compiles and round-trips a golden vector, or the Android-only decision is recorded | CI + inspection | 0.6 gate / 1.0 entry |
| RQ-06 | No libsignal type is importable from commonMain, `:mobile:messaging` or `:shared` | Build-enforced module deps + architecture test | 1.0 |
| — | Contract suite passes on Android adapter, iOS adapter (if in scope) and `FakeSealer` | CI | 1.0 |
| — | No `javax.crypto`/`CryptoKit` primitive outside `:mobile:crypto` adapters and the existing media AEAD | Dependency/grep rule (P12) | 0.5 onward |
| — | Every release artefact has a resolvable source tag and an in-app source link | Release checklist inspection | 1.0 onward |
| RQ-20 | Claims register: no "E2E" claim on any platform where the adapter is absent | Inspection | 1.0 exit |

> **Open item [OI-29](../architecture/multi-transport/open-items.md#oi-29):** counsel Q-L7(h) (App Store/Play terms vs AGPL-3.0 §10 for a third-party redistributor of libsignal; whether Signal's written permission is needed) feeds the D1 licence step at 1.0 entry.
