# ADR 0015 — Path-scope model and rule-based path policy (replaces Gemini's link "levels")

**Status:** Proposed (gated) — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). The scope vocabulary binds from acceptance; `PathPolicy` is built only at 1.2 behind gate G-PROXIMITY · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0015; §7](../architecture/multi-transport/decision-brief-2026-11-12.md) · Depends on ADR-0010, ADR-0012, ADR-0016, ADR-0029. Supersedes nothing.

## Context

Gemini classified links into "Level 0–6" by physical medium (Ethernet as Level 0, internet as
Level 2, ultrasonic as Level 6) and chose among them with `ChannelMetrics.computeFitnessScore`.
Verified problems:

- Apps do not choose the link layer: the OS picks interfaces (Android `ConnectivityManager` /
  `NetworkRequest`, iOS `NWParameters`), and ICE already gathers candidates on all interfaces
  (LINK-1, ICE-1). "Ethernet vs internet" confuses link layer with network path.
- The fitness score mixes units with arbitrary weights (20 ms × 0.05 weighs the same as 1 s of
  transmission), ignores priority, and excludes DTN traffic from DTN links (GEM-05).
- Hard-coded MTUs are wrong: 512 B is the ATT attribute limit, not the L2CAP SDU (GEM-06, PROX-01).
- The constraints that matter are not weights: "never plaintext off-server", "a CLOUD message must
  reach the server", "RF is off in TR builds" (LEG-03).

## Decision

### 1. Scopes, not media

| Scope | What it is | Who chooses the interface | First milestone |
|---|---|---|---|
| `SERVER_RELAY` | WS + REST to our server over any IP interface | OS | existing |
| `IP_RELAYED` | TURN/SFU on our infrastructure | ICE | 0.7 (calls) |
| `IP_DIRECT` | ICE host or server-reflexive pair | ICE | 1.1 (1:1 calls only) |
| `LAN` | mDNS/DNS-SD + TCP + authenticated link | OS + `LanLink` | 1.2 |
| `PROXIMITY` | Nearby Connections: BLE discovery → Wi-Fi upgrade | library | 1.2 |
| `UPLINK_RELAY` | Single-hop sealed uplink through a verified contact | — | 1.3 (gated) |
| `EXTERNAL_RF` | Meshtastic node | — | 1.4 (gated, TR-excluded) |

Ethernet, Wi-Fi, 4G/5G and Starlink are **interfaces chosen by the OS and ICE, not scopes**.
`IP_RELAYED` and `IP_DIRECT` carry live sessions only (`LiveSessionPort`, ADR-0023), never queued
messages.

### 2. Capabilities and metrics

```kotlin
data class LinkCapabilities(val maxFrameBytes: Int, val supportsLive: Boolean,
                            val supportsBulk: Boolean, val offServer: Boolean)
data class LinkMetrics(val rttMs: Int, val lossPpm: Int, val goodputBps: Long,
                       val costClass: CostClass, val constrained: Boolean)   // rolling 60 s EWMA, injected Clock
enum class CostClass { FREE, METERED, CONSTRAINED }
```

- `goodputBps` is **acked application bytes per second**, never PHY rate.
- `maxFrameBytes` is negotiated per link, never a constant (MISS-03, Gemini verifier).
- `constrained = true` marks a metered, low-bandwidth or satellite-optimised bearer on the
  **server** path. It is not a satellite transport (ADR-0027).

### 3. Selection: Strategy over a Chain of Responsibility

`PathPolicy` (Strategy) applies ordered `PathRule`s. There is **no numeric fitness score**.

```kotlin
fun interface PathRule { fun apply(ctx: PathContext, c: List<PathCandidate>): List<PathCandidate> }
```

| Stage | Rules | Effect |
|---|---|---|
| 1. Admission (ADR-0029 gate) | `AssuranceRule` (off-server ⇒ `SealedFrame`, already a type fact), `RelationshipRule` (mutual contact, existing conversation, verified safety number), `CertificateRule` (valid certificate + CRL, both ends), `ConversationModeRule` (ADR 0008), `ScopeRule` (1:1 only off-server; channels, communities, large groups ⇒ server only), `ConsentRule` (Nearby mode open, OS permissions), `RegulatoryRule` (RF excluded in TR), `QuotaRule` (custodian/mule budgets), `SizeRule` (`bytes ≤ maxFrameBytes` or `supportsBulk`), `ConstrainedRule` (constrained server link: CONTROL + INTERACTIVE only, defer BULK) | Removes candidates. A Deny never downgrades content |
| 2. CloudPersistence | `serverOwed = mode ∈ {CLOUD, BYO} && chosen.scope ≠ SERVER_RELAY` | Records the upload obligation (ADR-0013) |
| 3. Preference | Data table by (mode × QoS class); tie-break `rttMs`, then `-goodputBps`, then `costClass` | Picks one link |

Preference table:

| Mode | Class | Order |
|---|---|---|
| CLOUD / BYO | INTERACTIVE, CONTROL | `SERVER_RELAY`, `LAN`, `PROXIMITY`, `EXTERNAL_RF` |
| CLOUD / BYO | BULK | `SERVER_RELAY` (unless constrained or down and `LAN`/`PROXIMITY` already UP), `LAN`, `PROXIMITY` |
| LOCAL_ONLY | any | `LAN`, `PROXIMITY`, `SERVER_RELAY` (mailbox, delete-on-delivery) |

If no candidate survives admission, the entry stays QUEUED; custody offers belong to
`CustodyPolicy` (1.3, ADR-0025).

### 4. "Strongest path"

Ranked by: (a) passes its admission gate; (b) strongest delivery guarantee (custody, ordering,
multi-device fan-out); (c) latency; (d) cost and battery. Bandwidth never outranks a gate. For a
durable CLOUD message the strongest path is the server whenever it is reachable.

### 5. Racing happens at link establishment, not per message

Happy-Eyeballs-style: on demand for a peer, start ranked adapters `ATTEMPT_DELAY_MS = 250` apart;
the first UP wins, a higher-ranked later UP may take over, losers are cancelled. No per-message
hedging onto off-server paths. Escalation, de-escalation and their constants are specified in the
brief §6.8 and belong to the 1.2 design.

### 6. Timing

The policy object exists **from 1.2**. Before that, the Outbox calls the single `ServerLink`
directly (ADR-0012 §5). Within IP scopes, ICE already races candidate pairs for calls; it is not
reimplemented.

## Why

- Interface choice belongs to the OS and to ICE; modelling it again in the app is both redundant
  and wrong (LINK-1, ICE-1).
- Hard constraints cannot be expressed as weights; a weight can always be outvoted. Rules either
  admit or remove.
- Each rule is unit-testable on its own, and new rules are added without editing old ones (OCP).
- Per-message hedging onto off-server paths multiplies exposure (security judge).

## Consequences

- The capability matrix document is the data source for `LinkCapabilities` and the preference
  table; values are measured, not assumed (P13).
- New paths add one adapter plus, at most, one admission rule (ADR-0026 relies on this).
- `ConstrainedRule` covers satellite and metered bearers without a satellite transport (ADR-0027).
- Nothing here is built before 1.2; accepting this ADR binds only the vocabulary used by ADR-0012,
  the capability matrix and the threat model.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Gemini Level 0–6 by medium (ultrasonic as Level 6) | LINK-1; ultrasonic rejected (PROX-09, ADR-0027) |
| `computeFitnessScore` with magic weights and hard-coded MTUs | GEM-05, GEM-06; 512 B is the ATT limit, not the L2CAP SDU |
| Static fallback list with sequential timeouts | Slow; cannot express admission |
| Per-message hedging onto off-server paths | Multiplies exposure (security judge) |

## Evidence

- Findings: LINK-1, ICE-1, GEM-05, GEM-06, GEM-17, MISS-02 and MISS-03 (Gemini verifier), PROX-01,
  PROX-09, LEG-03.
- External sources (from the networking and proximity digests):
  https://developer.android.com/reference/android/net/ConnectivityManager,
  https://datatracker.ietf.org/doc/draft-ietf-happy-happyeyeballs-v3/,
  https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createL2capChannel(int).

## Verification

| Check | Method | When |
|---|---|---|
| Each admission rule denies its own case and admits otherwise | One unit test per rule | 1.2 |
| A CLOUD message delivered over `LAN` keeps `serverOwed = true` and re-queues on server link-up | `SimulatedLink` multi-node test | 1.2 |
| A channel, community or group message never gets an off-server candidate | Unit test (`ScopeRule`) | 1.2 |
| A TR release flavour admits no `EXTERNAL_RF` candidate | Unit test + Gradle flavour inspection (module absent, ADR-0026) | 1.4 |
| `goodputBps` counts acked application bytes only | Unit test over a `SimulatedSealedLink` with known payloads | 1.2 |
