# ADR 0017 — E2E library and licence: libsignal at the platform edge; `mobile/` relicensed to AGPL-3.0-or-later; `shared/` and `backend/` stay MIT

**Status:** Proposed — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). The licence change additionally requires preconditions (a)–(c) below, independently of design approval · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0017; §8.3; §13 (1.0.0); §17 Q-O1, Q-O2, Q-L7](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Related to** [ADR-003](003-defer-e2e-encryption.md), which ADR-0016 supersedes (brief §3) · Depends on ADR-0016, ADR-0028, ADR-0029, ADR-0031. Realises the crypto half of [ADR 0007](0007-companion-device-trust.md) via ADR-0018.

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

### 2. Licence boundary

| Tree | Licence after this ADR | Why |
|---|---|---|
| `mobile/` (every module that links, or ships in an artefact that links, libsignal) | **AGPL-3.0-or-later** | The distributed app is a combined work with libsignal |
| `shared/` | **MIT (unchanged)** | The backend links it; it never links libsignal. MIT code may be included in an AGPL work |
| `backend/` | **MIT (unchanged)** | Never links libsignal; talks to clients over the network only |
| `infra/`, `docs/` | unchanged | Not part of the linked work |

Mechanics on acceptance: a `mobile/LICENSE` with the AGPL-3.0 text, SPDX headers (or a
`REUSE`-style mapping) stating the per-tree licence, a README licence section, and an in-app
"source code" link satisfying the AGPL source offer for the exact released version. The root
`LICENSE` continues to cover everything outside `mobile/`.

> **Reviewer note (open):** relicensing `mobile/` is a choice, not a legal requirement. MIT-licensed
> `mobile/` sources could be conveyed inside an AGPL-licensed binary, as `shared/` is. Counsel
> (Q-L7) should confirm which is preferable, and also whether AGPL-3.0 §6 Installation Information
> duties apply to iOS/App Store conveyance.

### 3. Preconditions (all three, before the dependency is restored)

| # | Precondition | Evidence that closes it |
|---|---|---|
| (a) | Owner approval of the public licence change (Q-O1) | Signed line in `APPROVAL.md` naming this ADR |
| (b) | Contributor audit of `mobile/`: `git shortlog -sne -- mobile/`, including agent commits made under the owner's identity (GOV-06) | Written list of copyright holders; each non-owner contributor's consent or removal of their code |
| (c) | Written counsel opinion on distributing an AGPL work through **Google Play and the Apple App Store** (Q-L7) | Filed opinion; negative or qualified ⇒ fallback below |

Until (a)–(c) close, libsignal stays out of the build, and **1.0 entry is blocked** (brief §13).
Precondition (c) runs in Phase 0 alongside the other counsel questions; it does not block 0.x.

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

**If the iOS bridge or KMP-01 is not ready at the 0.6 gate, 1.0 ships Android-only** (Q-O2), with
the honest-UI consequences written down (no "end-to-end encrypted" claim reaches an iOS user,
because there is none). iOS E2E then follows as 1.0.x.

## Why

- **libsignal is the only option that gives PQXDH, a deployed ratchet, sender keys and an upgrade
  path to SPQR without writing crypto.** Every alternative either loses post-quantum handshakes
  (vodozemac), is not designed for 1:1 (OpenMLS) or is hand-rolled (forbidden).
- **Relicensing only `mobile/` is the boundary chosen.** Risk-first's draft relicensed
  `shared/` too, which is inconsistent: the MIT backend links `shared/`, and `shared/` never links
  libsignal. Keeping `shared/` and `backend/` MIT keeps the server free of copyleft obligations it
  does not trigger.
- **The licence is a public, irreversible act for the owner**, so it is gated on the owner's own
  approval, a contributor audit (agents committed under the owner's identity, GOV-06) and counsel —
  not on an engineering decision.
- **The commonMain surface is two ports**, so the 0.5 engine (ADR-0012) is built and tested against
  `FakeSealer` (testkit only, ADR-0016) long before any library is linked.

## Consequences

- **1.0 entry is blocked** until (a)–(c) close and the spike passes. 0.4–0.7 are unaffected.
- `mobile/` becomes AGPL-3.0-or-later on public repositories and in store listings. Anyone
  receiving the app may request the corresponding source for that exact build; the release flow
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
| Ship libsignal under the MIT licence | Not permitted by AGPL-3.0 (KMP-13) |
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
| — | (a) owner approval, (b) contributor audit, (c) counsel opinion filed | Inspection of `APPROVAL.md` and the filed documents | Before libsignal is restored |
| — | Two-device Android round trip on real builds | Demonstration, owner-observed (P13) | 1.0 entry |
| — | iOS bridge compiles and round-trips a golden vector, or the Android-only decision is recorded | CI + inspection | 0.6 gate / 1.0 entry |
| RQ-06 | No libsignal type is importable from commonMain, `:mobile:messaging` or `:shared` | Build-enforced module deps + architecture test | 1.0 |
| — | Contract suite passes on Android adapter, iOS adapter (if in scope) and `FakeSealer` | CI | 1.0 |
| — | No `javax.crypto`/`CryptoKit` primitive outside `:mobile:crypto` adapters and the existing media AEAD | Dependency/grep rule (P12) | 0.5 onward |
| — | Every release artefact has a resolvable source tag and an in-app source link | Release checklist inspection | 1.0 onward |
| RQ-20 | Claims register: no "E2E" claim on any platform where the adapter is absent | Inspection | 1.0 exit |

> **Reviewer note (open):** Q-L7 in the brief frames the question as "AGPL distribution … for a
> single-copyright-holder app linking libsignal". The copyright holder whose terms matter for the
> App Store question is the **libsignal licensor (Signal)**, not Muhabbet: Signal can distribute
> its own code under any terms, a third party cannot add store-imposed restrictions on top of the
> AGPL. Counsel should be asked specifically whether App Store terms impose restrictions
> incompatible with AGPL-3.0 §10 for a *third-party* redistributor of libsignal, and whether
> written permission from Signal would be required. The decision above is unchanged; only the
> question to counsel should be sharpened.
