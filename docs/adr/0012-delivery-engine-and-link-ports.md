# ADR 0012 — Transport-agnostic delivery engine in `:mobile:messaging`, with ISP-split link ports

**Status:** Accepted (2026-11-12, delegated) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); code against this ADR is written only while the matching scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0012; §5; §6](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Amends** [ARCHITECTURE.md §8](../ARCHITECTURE.md) (seams list). Depends on ADR-0010, ADR-0011, ADR-0016, ADR-0028.

## Context

`WsClient` (`mobile/.../data/remote/WsClient.kt`, ~680 lines) fuses four responsibilities: the
connect/backoff loop, an at-most-once offline queue (MOB-04: `session?.outgoing?.send(...)` followed
unconditionally by `cache.deletePendingMessage(...)`), in-memory dedup that drops every edit after
the first (MOB-05), and the E2E hook with its plaintext fallback (C9). The UI addresses it directly
(ADR-0011). There is no seam a second transport could sit behind (MOB-11).

The Gemini proposal's answer was a `TransportManager` with a numeric fitness score, a global P0–P3
dispatcher behind one `Mutex`, and a fat `TransportPort` whose RF adapter returns fake success. The
review confirmed each defect: the dispatcher suspends only on P1 (GEM-01); one large envelope blocks
all sending (GEM-02); the fitness score mixes units (GEM-05); carrier-sense backoff on one link
stalls every transport (MISS-01, Gemini verifier); the port is fat and violates LSP (GEM-17).

Inbound already has three real sources today: WS push, REST history pull, FCM-triggered background
sync. Outbound has one real link.

## Decision

### 1. Module

Create `:mobile:messaging` (commonMain + a JVM test target). Dependencies: `:shared`,
`:mobile:crypto`. **Forbidden:** Compose, Koin annotations, `WsMessage`, platform APIs. Koin wiring
of the engine happens in `composeApp`.

| Package | Contents |
|---|---|
| `domain` | `OutboxEntry`, `OutboxState`, `InboundEvent`, `DeliveryState`, `CausalHeader`, `QosClass` |
| `service` | `Outbox`, `Inbox`, `SyncCoordinator`, `ReceiptService` |
| `usecase` | `SendMessage`, `MarkRead`, `RetryDelivery`, `ObserveConversation`, `SessionTeardown` |
| `port` | `ServerLink`, `SealedLink`, `InboundSource`, `MessageStore`, `OutboxStore`, `ChangeFeedPort`, `Clock`, `IdGenerator` |
| facade | `MessagingEngine` (Koin single, bound in `composeApp`) |

### 2. Ports, split by Interface Segregation

```kotlin
interface ServerLink {                       // carries ServerFrame (TLS to our server)
    val state: StateFlow<LinkState>
    suspend fun submit(frame: ServerFrame): SubmitResult
}
sealed interface ServerFrame {
    class Plain internal constructor(val envelope: OutgoingEnvelope) : ServerFrame // only via ConversationCrypto.None
    data class Envelopes(val frames: List<SealedFrame>) : ServerFrame
}
interface SealedLink {                       // carries only SealedFrame (ADR-0016); 0.5 shape
    val state: StateFlow<LinkState>
    suspend fun send(to: PeerRoute, frame: SealedFrame): SubmitResult
}
interface InboundSource { val events: Flow<InboundEvent> } // Message | Change | Hint(conv, seq) | Receipt | KeyChanged | CrlDelta

sealed interface SubmitResult {
    data class ServerAccepted(val seq: Long, val ts: Instant, val replay: Boolean) : SubmitResult
    data class HopAcked(val custodian: Custodian) : SubmitResult
    data class Retry(val after: Duration?) : SubmitResult
    data class Backpressure(val after: Duration) : SubmitResult
    data class Rejected(val code: String) : SubmitResult
}
```

Rules:

1. `ServerLink` may carry TLS-only plaintext **only** for conversations with
   `encryptionState = NONE`, until 1.0, as today. From 1.0, E2E conversations reach it only as
   sealed envelopes (ADR-0016, ADR-0022). This is a type fact: `ServerFrame.Plain` can be built
   only from a `ConversationCrypto.None` token, and an E2E conversation can only produce
   `ServerFrame.Envelopes`.
2. `SealedLink` accepts only `SealedFrame`, whose constructor is `internal` to `:mobile:crypto`.
   Plaintext on a non-server path is a compile error, not a runtime check.
3. Pull-only sources are `InboundSource`s and never implement `send()` (no LSP violation).
4. At 0.5 `SealedLink` is `send(to, frame)` + `state` only. `scope: PathScope` and
   `caps: LinkCapabilities` are added at 1.2 together with ADR-0015 and `PathPolicy`, their first
   consumer (KISS, P7).
5. No adapter returns success it did not observe. `HopAcked` requires an authenticated hop ack;
   `ServerAccepted` requires a `ServerAck` with a `seq`.

| Port | Implementations now | Second real implementation (P7) |
|---|---|---|
| `ServerLink` | `WsServerLink` (0.5), `SimulatedLink` (tests) | Type-level counterpart of `SealedLink`; no second server link planned |
| `SealedLink` | `SimulatedSealedLink` (tests) | `LanLink`, `NearbyLink` (1.2); `UplinkHandover` (1.3); `MeshtasticLink` (1.4, gated) |
| `InboundSource` | `WsInboundSource`, `RestChangeFeedSource`, `FcmHintSource` (0.5) | `LanLink`, `NearbyLink` (1.2) |

### 3. Strangling `WsClient`

| Today in `WsClient` | Moves to |
|---|---|
| connect / backoff loop (tested by `WsClientReconnectTest`) | `WsServerLink` + `WsInboundSource` adapters in `composeApp`; tests kept |
| `PendingMessage` offline queue | `Outbox` + `OutboxStore` (ADR-0013) |
| in-memory `processedMessageIds` dedup | `Inbox` upsert keyed by id and `change_seq` (ADR-0013) |
| `MessageEncryptor` hook and plaintext fallback | `EnvelopeSealer` in `:mobile:crypto`; fallback deleted (ADR-0016) |

`WsClient` becomes internal to the adapter. Mutable state is confined to one actor coroutine per
link (MOB-13).

### 4. Scheduling

- One sender coroutine per link with bounded lanes CONTROL, INTERACTIVE and (from 1.2 on sealed
  links) BULK, drained with a biased `select` in that order. BULK on the server path uses resumable
  HTTP upload outside the lanes. EPHEMERAL traffic (typing, presence) is never queued.
- Per-conversation FIFO applies only to INTERACTIVE entries within one conversation. Conversations
  drain independently. There is **no global Mutex**.
- Media in PREPARING sits outside the FIFO and enters QUEUED when its upload finishes.
- Lanes are bounded; the Outbox pulls due entries only when a lane has capacity; a link may return
  `Backpressure(after)`.

### 5. No `LinkSelector` until a second outbound link exists

The `PathPolicy` Strategy (ADR-0015) is **not** built until a second *outbound* link exists. 1.1
P2P calls do not count (they sit behind `LiveSessionPort`, ADR-0023). It arrives in 1.2 with
`LanLink`/`NearbyLink`. Until then the Outbox submits to the single `ServerLink` when it is `Ready`
and otherwise keeps the entry QUEUED.

### 6. Rollout

Behind a remote flag `engine.enabled` for one release: 0.5.0 ships both paths with the engine on for
internal testers; 0.5.1 switches the default after emulator parity; the legacy `WsClient` path is
deleted in 0.5.2 or later.

## Why

- `InboundSource` has three real implementations today, so the abstraction pays for itself now.
- This is a recorded P7 exception: `ServerLink` exists to make the plaintext/sealed split a type
  distinction (ADR-0016), not for substitutability. Its only other implementation is
  `SimulatedLink`. `SealedLink` has scheduled real implementations in 1.2.
- The ISP split turns "E2E-only on off-server paths" from a runtime boolean into a compile-time
  guarantee. All three judges endorsed this graft from the risk-first proposal.
- Per-link senders and per-conversation FIFO fix GEM-01, GEM-02 and the Gemini-verifier MISS-01 by
  construction: a slow link stalls only itself.

> **Resolved (2026-11-12):** brief erratum E-03: outbound `ServerLink` has `WsServerLink`,
> `SimulatedLink` for tests and `RestServerLink` (0.5) for short-lived Android components
> (delivery-engine-spec R-5); `LanLink` is a `SealedLink` (1.2). The brief §5 `ServerLink` row reads
> "Later: `RestServerLink` (0.5)".

## Consequences

- Build prerequisites first: version catalog and one `muhabbet.kmp.library` convention plugin
  (KMP-05), SQLDelight migrations, and `:mobile:messaging:jvmTest` in CI from day one (KMP-03).
- No linuxX64 target and no AGP 9 `:androidApp` split are prerequisites (CI host disk and time).
- The engine is testable without Android: `SimulatedLink`, `InMemoryMessageStore`,
  `InMemoryOutboxStore`, `TestClock`, `SequentialIdGenerator`.
- ARCHITECTURE.md §8 lists `ServerLink`/`SealedLink` as seams (banner added now).
- Risk R3 (engine regresses the live send path) is mitigated by the flag overlap and by keeping the
  reconnect loop and its tests.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Gemini `TransportManager`: fitness score, P0–P3 global dispatcher, one Mutex | GEM-01, GEM-02, GEM-05, MISS-01 (Gemini verifier) |
| One fat `LinkAdapter` with `requiresE2E` and pull-only links forced into `send()` | ISP/LSP violation (GEM-17); E2E becomes a runtime boolean |
| Full LinkSupervisor, Happy-Eyeballs, HedgeRule, DRR scheduler and fragmenter at 0.5 | Speculative generality; no second outbound link until 1.2 |
| Keep `WsClient` and add an outbox inside it | Leaves transport, dedup and crypto fused (MOB-11); nothing for 1.2 to plug into |

## Evidence

- Findings: MOB-04, MOB-05, MOB-11, MOB-13, C9, GEM-01, GEM-02, GEM-05, GEM-17, GEM-22,
  MISS-01 (Gemini verifier), KMP-03, KMP-05.
- Code: `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/data/remote/WsClient.kt:534-569` (`drainPendingMessages`),
  `.../commonTest/kotlin/com/muhabbet/app/data/remote/WsClientReconnectTest.kt`.

## Verification

| Check | Method | When |
|---|---|---|
| Only `:mobile:crypto` constructs `SealedFrame`; no `SealedLink` receives anything else | Compile + architecture test | 0.5 |
| An E2E conversation cannot produce `ServerFrame.Plain` | Compile + architecture test | 1.0 |
| `:mobile:messaging` has no Compose, Koin-annotation or `WsMessage` dependency | Build-enforced + guardrail | 0.5 |
| A slow or backpressured link does not delay another link or another conversation | `SimulatedLink` test with two conversations and a stalled lane | 0.5 |
| No transport adapter depends on identity/ordering ports (ADR-0010) | Konsist rule | 0.5 |
| Engine path reaches parity with the legacy path before the default flips | Owner emulator script (airplane-mode toggling) | 0.5.1 |
| `:mobile:messaging:jvmTest` runs in CI | CI job | 0.5 |
