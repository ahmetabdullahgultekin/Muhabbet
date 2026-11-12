# Muhabbet multi-transport delivery: decision brief

**Date:** 2026-11-12 · **Base branch:** `dev` @ 2434324 · **Status:** Approved by delegated owner review (2026-11-12) with the amendments below: owner decisions D1–D6 and the errata in §18. The binding per-scope record is [`APPROVAL.md`](APPROVAL.md); no code is written for a scope until its block there reads `Status: Approved` (owner decision 4), and no GitHub restructuring or hooks are applied before the `process` scope is approved (owner decision 3). Open items are tracked in [`open-items.md`](open-items.md).
**Audience:** the owner, plus the documentation authors who will turn this brief into the SAD, ADRs, threat model, ICD, capability matrix, roadmap, V&V plan and governance docs.
**Inputs:** the three proposals (evolutionary, target-state, risk-first), the three judges (security-legal, feasibility, architecture-quality), the adversarially verified fact-checks and the register on `dev`.
**How it was built:** the evolutionary proposal is the base, because it won two of three judges. It is grafted with risk-first's type-level fail-closed gate, wake-only push, compliance boundary and CI-trust prerequisites, and with target-state's path scopes, link keys, LAN adapter, tick semantics and own-device custody. Every must-fix from every judge is addressed. The cross-reference is in §3 ("Judge must-fix closure").

> **Owner decisions 2026-11-12 (delegated review).** The owner delegated the review and approval of
> this package ("Kendin hepsini bağımsız review et ve onayla ve devam" — review everything
> independently yourself, approve and continue) and took six decisions. They are binding and
> override any body text of this brief that says otherwise.
>
> - **D1 Licence (ADR-0017, amended).** No relicensing now. `mobile/` stays MIT until 1.0 entry.
>   Whether to relicense `mobile/` to AGPL-3.0-or-later or to keep MIT sources and convey the
>   libsignal-linked binary as a combined work under AGPL-3.0 is decided at 1.0 entry, after counsel
>   answers Q-L7. Not blocking 0.4–0.7.
> - **D2 iOS.** If KMP-01 (iOS cannot launch) is not fixed at the 0.6 gate, 1.0 ships Android-only and
>   the UI and store-listing text say so. Bringing iOS back afterwards needs a new ADR.
> - **D3 Plaintext history at 1.0 (ADR-0021, amended).** Clients download history into the encrypted
>   local DB; the server copy (content, media, server-built backups) is deleted after a notified
>   30-day grace; an encrypted backup under a user-held key is opt-in; a conversation is labelled E2E
>   only once the server holds no readable copy.
> - **D4.** The ≤ 7-day offline revocation window is accepted and disclosed.
> - **D5.** No off-server path before 1.0, enforced by the `SealedFrame` type gate.
> - **D6.** ADR-0033 amendments G3 (`APPROVAL.md` never agent-editable) and G5 (`.claude/active-scope`)
>   are accepted.
>
> **Scope outcomes:** Phase 0, `process` (ADRs 0030–0033), 0.4, 0.5 and 0.6 approved; 0.7 approved
> except the ADR 0009 hosting/budget choice (Q-O8, owner); 1.0 approved in principle, gated on the
> written counsel opinions, the external crypto review and the licence step (D1); 1.1–1.4 remain
> Proposed (gated) behind their own entry gates. Only one milestone scope reads `Status: Approved` in
> `APPROVAL.md` at a time (ADR-0033 D1a); the later approved milestones are recorded as `Deferred`
> with a pre-approval condition and opened by the owner when the previous milestone exits.

> **Naming note.** ROADMAP.md names the live milestone **"0.4 — the app tells the truth"**. "0.4.0 — Installable" is a *retired* framing (ROADMAP §"Milestones are versions"). This brief uses the ROADMAP names: 0.4 truth · 0.5.0 Honest · 0.6.0 Reachable · 0.7.0 Live · 1.0.0 Encrypted. New post-1.0 milestones: 1.1 Direct · 1.2 Nearby · 1.3 Relay · 1.4 Off-grid hardware.

---

## 1. Thesis and scope

### 1.1 What "communicate always and everywhere, somehow" honestly means

Most of "always and everywhere" is a **reliability problem**. Only a small part is a radio problem. Today Muhabbet loses or mis-reports messages on the one path it already has:

- The outbox is at-most-once and lossy (MOB-04).
- DELIVERED is acked from RAM (MOB-03).
- Order is a timestamp with no tie-break (BE-06).
- Acks are accepted for any message id (BE-03).
- WS sessions outlive logout (BE-12).

Every extra path multiplies these defects. So the promise is built in this order.

1. **One message, one identity, any pipe.** A message is an envelope with a stable identity. It sits in a durable outbox, is delivered at least once over whatever link is up, and is received idempotently into a local database that is the single source of truth (SSOT). It is ordered by a server per-conversation sequence that clients *pull* to fill gaps. Push is a hint and pull is the truth.
2. **The server is the backbone** (owner decision 1). It owns identity, the device registry, keys, order, mailbox, wake-up, TURN credentials and compliance. Every other path is an *additional pipe*. It can make delivery faster or possible when the server is unreachable, but it never owns history, identity or moderation.
3. **Every non-server pipe carries only end-to-end ciphertext between server-attested devices.** This is enforced by the type system, so in practice every non-server path ships after 1.0 (Encrypted).
4. **DIRECT mode** means a live, mutually present session: a call (0.7 via SFU/TURN, 1.1 P2P for 1:1), or a Nearby/LAN session while both users have Nearby mode open. **INDIRECT mode** means anything that goes through the durable outbox and a custodian. The custodian hierarchy is: the server mailbox, then the user's own linked devices, then a single-hop sealed uplink through a verified contact's online phone. Multi-hop contact mules are research only.
5. **"Strongest path"** is ranked by: (a) passes its admission gate; (b) gives the strongest delivery guarantee (custody, ordering, multi-device fan-out); (c) latency; (d) cost and battery. Bandwidth never outranks a gate. For a durable CLOUD message the strongest path is the server whenever it is reachable.

### 1.2 What it does NOT promise (and the product must say so)

- No messaging without *any* infrastructure and without a nearby contact's phone. There is no off-grid multi-hop mesh. LoRa is legally gated and may never ship in Turkey.
- **No first contact off-server.** Account creation, adding a contact, creating a conversation, changing group membership and key bootstrap all require the server. You cannot message a brand-new contact while you have no internet.
- No background or always-on proximity discovery. iOS makes cross-OS background discovery impossible (PROX-02). Nearby mode is foreground and user-started.
- No proximity, relay or RF path before 1.0 E2E is real and fail-closed.
- No groups over off-server paths in the first proximity release (1:1 only). Channels, communities and groups above the off-server cap never use off-server paths.
- No total order for LOCAL_ONLY conversations, only per-sender causal order.
- No "relayed to mesh" tick, and no UI claim (range, background delivery, encryption, cross-platform) that was not measured on a real device.
- No serverless identity, DHT, CRDT/Merkle-DAG history, libp2p, birthday hole punching, QUIC migration across BLE, amateur/PMR radio, 5G broadcast/sidelink, satellite-as-transport, or ultrasonic channels (see ADR-0027).

### 1.3 Scope of this brief

**In scope:** mobile and backend architecture for the delivery engine, transports, identity and E2E trust across paths, store-and-forward, wire protocol, data model, roadmap, risks, V&V and governance.

**Out of scope:** UI visual design, call media internals beyond ADR 0009, group E2E beyond sender-key fan-out (MLS is a post-1.x research item with the server as Delivery Service), and web/desktop clients.

---

## 2. Architecture principles (numbered, testable)

| # | Principle | How it is tested |
|---|---|---|
| P1 | **Server authority.** Identity, device registry, key directory, per-conversation order, mailbox, push and moderation are server-side. No adapter mints identity or order. | ArchUnit/Konsist: no transport adapter depends on an identity or ordering port. Inspection of the ADR-0010 boundary. |
| P2 | **No plaintext on a non-server path, by type.** Non-server ports accept only `SealedFrame`, whose constructor is `internal` to `:mobile:crypto`. | A compile error if violated. An architecture test that only `:mobile:crypto` constructs `SealedFrame`. A release-classpath check that no `*-testkit` module is present. |
| P3 | **At-least-once on every hop, idempotent at every receiver, effectively-once to the user.** | SimulatedLink property tests (loss, duplication, reorder, partition, process death): each message appears exactly once in the sender and receiver stores. |
| P4 | **Persist before acknowledge.** No DELIVERED or custody ack is emitted before the receiving store commits. | Crash-point injection test between commit and ack. Inspection of the Inbox. |
| P5 | **Local DB is the mobile SSOT; screens observe DB flows only.** UI never imports wire types. | Build-enforced module dependencies. A `verifyUi` guardrail counting `WsMessage`/`wsClient` usages in `ui/`, ratcheted to 0. |
| P6 | **Pull is truth, push is a hint.** Any lost push, WS frame or link frame is repaired by a seq-gap pull. | Test: drop 100% of hints and the client still converges via pull on reconnect, foreground or WorkManager. |
| P7 | **Every abstraction names its second real implementation, already scheduled on the roadmap.** Otherwise it is not built. | Inspection: the ports catalogue (§5) lists "now / later" for each port, and a reviewer rejects any port with only a speculative second implementation. |
| P8 | **Each new path ships behind a written admission gate:** legal opinion, DPIA, metadata controls, abuse controls and a measured device matrix. | The milestone entry checklist (§13). A milestone cannot start without signed artefacts. |
| P9 | **No stable or timestamp-bearing identifiers outside TLS-to-server.** Outer ids are 16 random bytes, discovery tags rotate, payloads are padded to buckets. | Golden-vector tests. Over-the-air capture during the 1.2 device exit (owner observation). |
| P10 | **Deterministic time and ids in the domain.** `Clock` and `IdGenerator` are injected, with no `System.currentTimeMillis` in domain code. | A detekt (runs and blocks on `dev` since db9e59f; inert on `main` until the dev→main merge, ADR-0030) or Konsist rule. TestClock in every engine test. |
| P11 | **Compliance survives offline.** An age gate is a precondition for a device certificate. Block lists apply on every inbound path. Revocation reaches offline peers within a bounded, disclosed window (≤ 7 days). | Certificate-expiry tests with TestClock. Inbound-pipeline block-list test per transport. |
| P12 | **Buy, don't build, radios and crypto.** Use libsignal, Nearby Connections, LiveKit/WebRTC ICE and Meshtastic firmware. No hand-written crypto primitives, handshakes or software modems. | Dependency inspection. A rule that no `javax.crypto`/`CryptoKit` primitive is called outside the `:mobile:crypto` adapters and the existing media AEAD. |
| P13 | **Device-dependent exit criteria are signed off only by the owner's observation** on emulator-5554 or physical phones. An agent may not report them as passed. | Milestone template field "observed by / where". |
| P14 | **Process ADRs are separate from product ADRs**, and product ADRs for 1.1+ remain *Proposed (gated)* until their spikes and legal opinions exist. | The ADR index groups them. Status lint. |

---

## 3. Decision register (ADR-0010 onward, all *Proposed*)

Product ADRs are 0010–0029. Process ADRs are 0030–0033. Existing documents affected: ADR-003 (superseded by 0016), ADR 0008 (amended by 0021), ARCHITECTURE.md §4 (amended by 0011) and §8 (seams list updated by 0012), ADR 0007 (crypto realised by 0018), ADR 0009 (unchanged; 0023 builds on it).

### ADR-0010: Hybrid backbone. The server is the authority; every other path is an additional pipe
- **Decision.** The backend stays authoritative for:
  - phone-OTP account identity and the age gate
  - the device registry and the per-device key directory
  - device-certificate issuance and revocation
  - the per-conversation sequence
  - the ciphertext mailbox
  - push wake-up and TURN credentials
  - moderation and legal duties

  P2P, LAN, proximity, uplink-relay and RF paths are adapters that move bytes only. Off-server traffic is allowed only between registered devices of users who already share a conversation and an E2E session established through the server.

  A message delivered off-server in a CLOUD or BYO conversation is not *final* until the server assigns it a seq, and it carries a "not yet synced" marker until then. LOCAL_ONLY (ADR 0008 Mode 3) is the only exception: the server holds TTL ciphertext and delivery metadata only.
- **Rationale.**
  - Owner decision 1.
  - A DHT cannot hand out a one-time prekey exactly once (C2, P2P-3), and MLS needs a Delivery Service to order Commits (C3).
  - Law 5809 Art. 9(14) can make an OTT provider an *işletmeci* (operator), and Laws 5651/7578 need a reachable controller for takedown and the under-15 ban.
  - One ordering authority removes the need for CRDT merge logic.
- **Rejected alternatives.**
  - Gemini's fully decentralised design (PeerID = Blake3(Ed25519), Kademlia prekeys, volunteer supernodes, Merkle-CRDT).
  - A peer-equal hybrid in which nearby-delivered messages are authoritative.
  - Federation as the base (BYO stays a per-conversation mode).
  - Server-only forever (fails the product goal).
- **Consequences.** Offline operation is *tolerant*, not *authoritative*. When the server is down, users fall back to proximity plus queued messages. The server stays in the identity TCB. Key transparency can reduce that trust later but cannot remove it. ARCHITECTURE.md §8 gains three seams: `ServerLink`/`SealedLink`, `EnvelopeSealer` and `MailboxPort`.

### ADR-0011: The mobile local database is the single source of truth; no ViewModel layer (amends ARCHITECTURE.md §4)
- **Decision.** SQLDelight becomes the SSOT for conversations, messages, outbox and inbox state. This explicitly **supersedes** the ARCHITECTURE.md §4 statement "the local database is a cache and an outbox, not a source of truth".

  Screens observe SQLDelight `Flow` projections. Commands go through a single Koin-singleton facade, `MessagingEngine`. This is light CQRS: same DB, separate read projections, no buses.

  The documented **no-ViewModel decision stays**. The engine is a domain singleton exposing flows, which is the pattern §4 already sanctions for state that spans screens. Screen-state survival across configuration change (MOB-02) is a separate question. If the owner wants it, it needs its own ADR and is not bundled here.

  SQLDelight migrations (`.sqm` + `verifyMigrations`) are wired before any schema change.
- **Rationale.** The root causes of MOB-03, MOB-06, MOB-08 and MOB-11 are: an optimistic bubble held only in Compose state, incoming WS messages never persisted, and the UI speaking wire frames. A DB SSOT lets every path (server push, pull, Nearby, LAN) converge on one store. None of the three proposals superseded §4 explicitly, which the architecture judge flagged.
- **Rejected alternatives.**
  - Introducing ViewModels/MVI now. It is orthogonal to delivery and would contradict a documented decision without a delivery benefit.
  - Full feature modularisation of the 26.5k-line `composeApp`. No second consumer, so YAGNI.
  - A package-only boundary. Unenforced boundaries rot; BE-08 is the precedent.
- **Consequences.**
  - ARCHITECTURE.md §4 and §5 ("How a message travels") are rewritten.
  - Search, ConversationList and Chat read from the DB.
  - The existing "message sent while the socket is up never touches the database" behaviour is removed.
  - The 17 `wsClient.send` call sites in `composeApp` are the migration inventory, not "ten in ChatScreen".

### ADR-0012: Transport-agnostic delivery engine in `:mobile:messaging`, with ISP-split link ports
- **Decision.** Create `:mobile:messaging`: commonMain plus a JVM test target. It depends on `:shared` and `:mobile:crypto`. It has no Compose, no Koin annotations and no `WsMessage`. It holds:
  - the message domain: `OutboxEntry`, `InboundEvent`, `DeliveryState`, `CausalHeader`
  - the services: `Outbox`, `Inbox`, `SyncCoordinator`, `ReceiptService`
  - the use cases: `SendMessage`, `MarkRead`, `RetryDelivery`, `ObserveConversation`, `SessionTeardown`
  - the ports, split by ISP:
    - `ServerLink` accepts `ServerFrame`. Before 1.0 that may carry TLS-only plaintext for `encryptionState = NONE` conversations, as today.
    - `SealedLink` accepts only `SealedFrame`. It serves LAN, Nearby, uplink and Meshtastic.
    - `InboundSource` is a pull or push stream of `InboundEvent`.

  `WsClient` is strangled. Its tested connect/backoff loop becomes `WsServerLink` plus `WsInboundSource`. Its outbox, dedup and E2E hook move into the engine. It becomes internal to the adapter.

  The `LinkSelector` Strategy is **not** built until a second *outbound* link exists (1.1 P2P calls do not count; it arrives in 1.2 with `LanLink`/`NearbyLink`). Until then the Outbox calls the single server link directly.
- **Rationale.**
  - Inbound already has three real sources today (WS push, REST pull, FCM-triggered sync), which justifies `InboundSource` now.
  - Outbound `ServerLink` has `WsServerLink`, plus `SimulatedLink` for tests. Its second real implementation is scheduled (`LanLink` in 1.2). That satisfies P7.
  - The ISP split replaces a runtime "E2E-only" eligibility check with a compile-time guarantee. This is the graft from risk-first that all three judges endorsed.
- **Rejected alternatives.**
  - Gemini's TransportManager with a fitness score, P0–P3 global dispatcher and a Mutex (GEM-01/02/05, MISS-01).
  - One fat `LinkAdapter` with `requiresE2E` and pull-only links forced into `send()` (an LSP/ISP violation).
  - A full LinkSupervisor, Happy-Eyeballs, HedgeRule, DRR scheduler and fragmenter at 0.5 (speculative generality).
- **Consequences.**
  - Version catalog plus one minimal convention plugin first (KMP-05).
  - `:mobile:messaging` jvmTest runs in CI from day one (KMP-03).
  - No linuxX64 target and no AGP 9 app-module split as prerequisites. The CI host has disk and time limits.
  - The rollout is flag-gated (`engine.enabled`) for one release, with the legacy `WsClient` path kept until emulator parity is shown (see §13).

### ADR-0013: Delivery semantics. Durable outbox, persist-then-ack inbox, idempotency, honest ticks
- **Decision.**
  - **Outbox.** `outbox_entry` holds the full versioned `OutgoingEnvelope`. It is written in the same transaction as the optimistic message row. It moves through the state machine in §6.1, and transitions are compare-and-set. A row is removed only at a terminal state.
  - **Inbox.** Each inbound event is upserted in one transaction: insert, or update only if the incoming `change_seq` is newer. The seq watermark advances in the same transaction. Only after the commit is the DELIVERED receipt enqueued.
  - **Server idempotency.** `UNIQUE (sender_device_id, client_message_id)`. A duplicate submit *replays* the original `ServerAck(seq, serverTs, replay=true)` instead of `MSG_DUPLICATE` (BE-07).
  - **Acks.** Acks are per device: `ack{conversationId, uptoSeq, kind}`, only for seqs that exist (BE-03, BE-18). `uptoSeq` is the conversation's `complete_through_seq` (advanced only when a pull ends with `hasMore=false` or a contiguous `message.new` extends it), never the change-feed watermark or a pushed value (erratum E-45). Tick-affecting DELIVERED/READ are accepted only from devices of members who are not the sender; watermark/custody acks are accepted from any registered device of any member, the sender's own included, and are excluded from ticks (erratum E-39).
  - **Ack levels.**
    - **L1 hop ack:** `ServerAck(seq)` from the server, or `HopAck` from a peer device or custodian.
    - **L2 E2E DELIVERED:** the recipient device has committed.
    - **L3 READ.**
  - **Ticks** (one meaning on every path):
    - clock = not yet in server custody and not yet delivered to the recipient device
    - one tick = server custody (seq assigned)
    - two ticks = every recipient user has at least one device that committed (L2)
    - read = L3, subject to the server-enforced `readReceiptsEnabled` downgrade (#377)
    - A message delivered off-server directly to the recipient device shows two ticks plus "not yet synced" until it has a seq.
    - A handover to an own device or an uplink mule shows the clock plus a secondary label ("handed to <device/contact>"). There is never a "relayed to mesh" tick.
- **Rationale.** At-least-once with idempotent receivers is the only contract that survives multiple paths. It fixes MOB-03/04/05, BE-03/07/18 and meets 0.4's "the tick tells the truth". `SubmitResult.Accepted(seq?)` conflated server commit with hop ack (architecture judge), so hop ack is modelled explicitly.
- **Rejected alternatives.**
  - Patching `PendingMessage` (loses fields, has no FAILED state).
  - Exactly-once transport.
  - Long-term signatures on receipts (non-repudiable).
  - Gemini's `Relayed_To_Mesh` tick.
  - Persisting read receipts on the client (`PendingAckQueue` regenerates them on chat open and stays in memory).
- **Consequences.**
  - The UI gets a real "tap to retry" state.
  - Receipts stay **server-visible metadata** at 1.0. The server must see them anyway to delete mailbox rows. So the #377 read-receipt enforcement keeps working server-side and is not silently weakened. This is a deliberate trade, written down (see ADR-0019).

### ADR-0014: Ordering. Per-conversation server seq plus change feed; client causal header; no HLC authority and no Merkle-DAG
- **Decision.**
  - **Columns.** `conversations.last_seq`, `messages.seq` and `messages.change_seq`, unique on `(conversation_id, seq)`. They are allocated in the send transaction with `UPDATE conversations SET last_seq = last_seq + 1 WHERE id = ? RETURNING last_seq`. Edits, deletes, reactions and expiries bump `last_seq` and stamp `change_seq`.
  - **Authoritative read.** `GET /api/v1/conversations/{id}/changes?afterSeq=&limit=` returns `{items, lastSeq, hasMore}`. The conversation list returns `lastSeq` per conversation. WS `message.new` and FCM are hints carrying `(conversationId, seq)`.
  - **Scheduled messages** get their seq when released. They are invisible before that, which closes BE-02 structurally, and each is released in its own transaction (BEV-M1).
  - **Inside the E2E plaintext** every message carries `CausalHeader{senderDeviceId, deviceCounter (monotonic per device per conversation), clientMessageId (UUIDv7), senderTimeMs (advisory)}`.
  - **Display order** is `seq` when known. Rows without a seq sort by `(lastKnownSeq, senderDeviceId-local receive order, deviceCounter)`, with sender time clamped to `[receivedAt − MAX_SKEW, receivedAt]` (MAX_SKEW = 5 min, companion constant) for display only. They re-sort stably once the seq arrives.
  - **deviceCounter gaps** trigger a pull. A replayed counter is dropped.
  - **Backfill** for existing rows uses `(server_timestamp, id)`.
- **Rationale.** BE-06/C13 (ties, no gap detection). An unbounded HLC is poisonable (C4, GEM-10). Signed hash-chained DAGs destroy deniability and conflict with KVKK erasure and disappearing messages (C5). With one sequencer, an HLC adds complexity without adding a guarantee, which is why target-state's bounded HLC is not adopted.
- **Rejected alternatives.**
  - A Gemini Merkle-CRDT with HLC.
  - A bounded HLC in every message.
  - Vector clocks.
  - A global per-user inbox sequence (revisit only if conversation-list fan-in becomes a measured cost).
  - A backend transactional outbox table for fan-out (pull-by-seq makes push loss harmless; see the judge disagreement in §3).
- **Consequences.**
  - Cursor pagination moves from `serverTimestamp` to `seq` (also BEV-M2).
  - `/messages/since` stays one release as a deprecated shim.
  - Background sync uses the server seq, not the device clock (MOB-10).
  - Hot-row contention on `last_seq` is accepted at the current scale. Measure before sharding.
  - Off-server messages may visibly re-sort once.
  - LOCAL_ONLY has causal order only, and the UI says so.

### ADR-0015: Path-scope model and rule-based path policy (replaces Gemini's link "levels")
- **Decision.** Paths are classified by **scope**, never by physical medium:
  - `SERVER_RELAY` (WS + REST over any IP interface)
  - `IP_RELAYED` (TURN/SFU on our infrastructure)
  - `IP_DIRECT` (ICE host or srflx; calls only)
  - `LAN` (mDNS + TCP + authenticated link)
  - `PROXIMITY` (Nearby Connections: BLE discovery → Wi-Fi upgrade)
  - `UPLINK_RELAY` (single-hop sealed uplink through a contact)
  - `EXTERNAL_RF` (Meshtastic)

  Ethernet, Wi-Fi, 4G/5G and Starlink are interfaces chosen by the OS and ICE. They are not scopes.

  Each link declares `LinkCapabilities(maxFrameBytes, supportsLive, supportsBulk, offServer)` and reports `LinkMetrics(rttMs, lossPpm, goodputBps, costClass, constrained)`. Goodput is measured as acked application bytes per second, never PHY rate. `constrained = true` covers metered, low-bandwidth or satellite-optimised bearers on the *server* path. It is not a satellite transport.

  Selection is a Strategy (`PathPolicy`) over an ordered Chain of Responsibility of rules: `Admission` (the compliance and assurance gate of ADR-0029), `CloudPersistence`, `Preference`. There is no numeric fitness score.
- **Rationale.** Interface choice belongs to the OS (Android `NetworkRequest`, iOS `NWParameters`) and to ICE, which already gathers candidates on all interfaces. Hard constraints ("never plaintext off-server", "CLOUD must reach the server", "RF off in TR") cannot be expressed as weights. Rules are unit-testable one by one, and new ones are added without editing old ones (OCP).
- **Rejected alternatives.**
  - Gemini's Level 0–6 by medium (ultrasonic as Level 6).
  - `ChannelMetrics.computeFitnessScore` with magic weights and hard-coded MTUs (512 B is the ATT limit, not the L2CAP SDU).
  - A static fallback list with sequential timeouts.
  - Per-message hedging onto off-server paths (multiplies exposure; security judge).
- **Consequences.** The policy object exists from 1.2. Before that, the single server link is called directly (ADR-0012). The capability matrix document (§17 doc plan) is the data source for the rules.

### ADR-0016: E2E is a fail-closed gate for every non-server path, enforced by type (supersedes ADR-003)
- **Decision.**
  - `:mobile:crypto` owns `SealedFrame` (constructor `internal`), the `EnvelopeSealer` port and `SealResult{Sealed | NoSession | UntrustedIdentity | Failed}`.
  - The encryptor's silent plaintext fallback (C9) is **deleted**, not flag-gated. A seal failure leaves the entry in `PENDING_SEAL` with honest UI ("waiting for secure session").
  - `NoOpEncryption` is legal only for `encryptionState = NONE` conversations on `ServerLink` until 1.0. At 1.0 it is removed from the E2E conversation path, and `E2EConfig` flags are retired in favour of the type gate plus a server-side minimum protocol version.
  - Test fakes (`FakeSealer` that reports E2E) live in `:mobile:crypto-testkit`. That module is allowed only on test configurations, and a build check fails if it appears on any release runtime classpath.
  - Wording rule: "no plaintext on sealed paths and in E2E conversations". Channels and communities stay server-plaintext *by design*, and the UI labels them as such.
- **Rationale.** C9, and the e2e-rollout-runbook's "fall back to plaintext". Offline, session-setup failure is the normal case, so a fallback would put plaintext on a stranger's phone. A type you cannot construct cannot be passed by mistake.
- **Rejected alternatives.**
  - A runtime boolean per adapter.
  - A capability flag on a fat port.
  - Relying on BLE pairing or Nearby UKEY2. These authenticate the radio peer, not the recipient.
- **Consequences.** No non-server transport can carry content before 1.0, by construction. ADR-003 is marked *Superseded by ADR-0016* (ADR-0017 related).

### ADR-0017: E2E library and licence. libsignal at the platform edge; licence of `mobile/` decided at 1.0 entry after counsel Q-L7 (D1); `shared/` and `backend/` stay MIT
- **Decision.**
  - **Library.** libsignal (PQXDH with Kyber prekeys, Double Ratchet, sender keys, sealed sender; SPQR when the bindings expose it, still to be verified).
    - Android: `org.signal:libsignal-android` from Signal's Maven repo (versions above 0.86.5 are only there).
    - iOS: `LibSignalClient` (Swift) through an `@objc` wrapper consumed by Kotlin/Native cinterop.
    - commonMain sees only `EnvelopeSealer` and `SessionCryptoPort`.
  - **Licence (amended by D1, 2026-11-12).** `mobile/` stays MIT until 1.0 entry. At 1.0 entry, after counsel answers Q-L7, the owner chooses (i) relicensing `mobile/` to AGPL-3.0-or-later, or (ii) keeping the `mobile/` sources MIT and conveying the libsignal-linked binary as a combined work under AGPL-3.0 with a corresponding-source offer. Under both options every libsignal-linked artefact carries an AGPL source offer and a resolvable source tag. `shared/` stays MIT, because the backend links it and it never links libsignal. MIT code is compatible with inclusion in an AGPL work. `backend/` stays MIT.
  - **Preconditions (1.0 entry):**
    - (a) the owner's choice of (i) or (ii), recorded after the written Q-L7 opinion
    - (b) contributor audit (`git shortlog`, agent commits under the owner's identity), required only for option (i)
    - (c) a written counsel opinion on AGPL distribution through Google Play **and** the App Store
  - **Fallback if (c) is negative:** an Apache-2.0 core (vodozemac Olm/Megolm via UniFFI). It loses PQXDH and needs its own ADR.
  - **Pre-1.0 feasibility spike:** a two-device Android round trip, plus the iOS bridge compiles and round-trips a golden vector. If the iOS bridge or KMP-01 is not ready, **1.0 ships Android-only**, with the honest-UI consequences written down. iOS E2E then follows as 1.0.x.
- **Rationale.** KMP-13: libsignal is AGPL and the repo is MIT. The disabled Signal code does not compile, and libsignal is not even a dependency now (KMP-M3). Hand-rolled crypto is forbidden (CLAUDE.md, ADR 0007). Risk-first's draft relicensed `shared/` too, which is inconsistent with the MIT backend linking it, so that part is fixed here.
- **Rejected alternatives.**
  - Hand-written PQXDH or ratchet in Kotlin.
  - Conveying the libsignal-linked binary under MIT terms (not permitted).
  - Deferring E2E past 1.0.
  - OpenMLS for 1:1.
  - A commercial libsignal licence (not offered).
- **Consequences.**
  - 1.0 entry is blocked until (a)–(c) are done. 0.4–0.7 are not blocked (D1).
  - A commonTest contract suite runs against the Android adapter, the iOS adapter and the fake.
  - The envelope is protocol-tagged, so a library change does not ripple into wire, storage or UI.

### ADR-0018: Identity, per-device keys, prekey directory, link keys and server-attested device certificates
- **Decision.**
  - **(1) Account.** E.164 plus OTP, the legal subscriber identity. The age attestation is stored server-side.
  - **(2) Device.** A `devices` row (primary plus up to four companions per ADR 0007).
  - **(3) Crypto identity.** One libsignal `IdentityKeyPair` per device. The directory is keyed by `(user_id, device_id)`. It provides:
    - **atomic** one-time and Kyber prekey claim (`DELETE … WHERE id = (SELECT … FOR UPDATE SKIP LOCKED) RETURNING`), falling back to last-resort Kyber
    - a per-requester and per-pair fetch rate limit
    - a relationship check (existing contact or shared conversation)
    - low-water replenishment signals
    - **no silent replacement**: a changed identity key for an existing `device_id` is rejected; a new key means a new device, which emits `IdentityKeyChanged` → a safety-number-changed system message (BE-14, M1, M2)
  - **(4) Link key.** A separate per-device static key used only for link-layer authentication on off-server hops (domain separation). It is signed by the device identity key and published in `device_link_keys`. Its type follows the handshake chosen by the 1.2 spike: X25519 for a vetted Noise library, Ed25519 for TLS 1.3 with a pinned raw public key. There is no hand-rolled handshake and no key derivation from libsignal ratchet internals. Evolutionary's "MAC under a key derived from the E2E session" is rejected.
  - **(5) DeviceCertificate** `{v, serial, userId, deviceId, identityKey, linkKey, notBefore, notAfter = 7 days, issuerKeyId}`. Signed with Ed25519 by an **online intermediate** (90-day) under an **offline root** whose public key is pinned in the app with a rotation list. It renews on every online session. Revocation is non-renewal plus a signed delta CRL pushed over WS. Issuance requires a valid age attestation and no active ban.
  - **(6) Verification.** A static single-scan QR safety number (libsignal fingerprints). Eligibility for 1.2 proximity and 1.3 uplink requires a **verified** safety number.
  - **(7) Key transparency.** Research, not scheduled. Without independent auditors it would be false assurance.
  - **Timing.**
    - Directory fixes are in two steps: atomic claim and rate limit in 0.4 (cheap, latent), per-device schema in 0.6.
    - Certificates and the CRL are **first built at 1.2**, with their first consumer (proximity). They are not built "dormant" at 0.6.
    - The certificate signing key must never be on a host that runs PR jobs. This depends on ADR-0031 being in force.
- **Rationale.**
  - Proximity peers have no server TLS, so they need device-level mutual authentication.
  - Bans, takedown and the age gate must reach offline peers within a bounded window (security must-fix).
  - The architecture judge's objection (a heavy trust root with no consumer) is met by deferring certificates to their first consumer.
- **Rejected alternatives.**
  - Self-certifying PeerIDs.
  - A per-user identity key shared across devices (needs private-key export, which ADR 0007 forbids faking).
  - Year-long certificates.
  - X.509/PKIX parsing.
  - Link-key rotation as the only revocation (a 30-day window is too loose).
- **Consequences.**
  - New tables (§11).
  - A device offline for more than 7 days cannot authenticate to proximity peers until it reconnects. This is intended and disclosed.
  - Operational runbook for the root and intermediate keys.
  - ADR 0007's `DeviceLinkCrypto` becomes real at 1.0 (per-device fan-out). Companions do not get server-side history for E2E conversations. History transfer is device-to-device (a post-1.0 item) or via the encrypted backup (T3 step 5).

### ADR-0019: Push is wake-only from 0.5; the server stops being plaintext-dependent for E2E conversations at 1.0
- **Decision.**
  - **From 0.5,** FCM (and APNs once iOS exists) payloads are data-only: `{type: "wake", conversationId, seq}`, a high-priority data message with a collapse key. The client fetches over TLS and composes the notification text locally.
  - **OEM-throttling fallback:** pull on foreground, WorkManager periodic sync driven by seq, and an in-app notice of battery-optimisation exemption on known aggressive OEMs. iOS needs a Notification Service Extension when the iOS app exists.
  - **At 1.0, for E2E conversations:**
    - no server-stored plaintext content
    - no server search (on-device FTS inside the encrypted DB instead)
    - no server link previews (the sender generates them and embeds them encrypted)
    - no server media reads
  - **What the server still sees** (metadata, explicitly): sender device, recipient devices, seq, sizes, timestamps, delivery and read acks. Read-receipt enforcement (#377 `readReceiptsEnabled` downgrade) therefore stays server-side. `aboutVisibility` is profile data and stays server-enforced.
- **Rationale.** BE-15. Content in push is an Art. 9 transfer of content to US processors, and removing it needs no cryptography. Both the security and feasibility judges required it before 1.0.
- **Rejected alternatives.**
  - Content in push for latency.
  - Encrypted push payloads before E2E exists.
  - Moving read-receipt enforcement client-side. That would silently weaken a guarantee #377 just made server-enforced.
- **Consequences.**
  - Notification latency depends on the fetch. This is measured on the emulator and a Xiaomi/Oppo-class device.
  - The KVKK aydınlatma metni is updated.

### ADR-0020: Data at rest and erasure. SQLCipher from 0.5; SessionTeardown is crypto-erase
- **Decision.**
  - **SQLCipher.** `sqlcipher-android` with an SQLDelight driver factory. On iOS, SQLCipher is linked into `NativeSqliteDriver` (after KMP-01).
  - **DB key.** A random 256-bit key, wrapped by Android Keystore (StrongBox when present) or stored in the iOS Keychain with `AfterFirstUnlockThisDeviceOnly`, so background wake can still read it.
  - **Migration at 0.5.** Because the current DB is a *cache*, the 0.5 migration creates a fresh encrypted DB with the new SSOT schema. It copies only unsent `PendingMessage` rows into `outbox_entry`, deletes the plaintext file and **re-syncs from the server by seq**. This is low risk because nothing authoritative is lost.
  - **`SessionTeardown(reason)`** runs on logout, account deletion and refresh rejection, in this order:
    1. best-effort server revoke (queued)
    2. cancel WorkManager/BGTask (MISS-02)
    3. close links
    4. destroy the wrapped key
    5. delete the DB, media cache, outbox, identity keys and relay store
    6. verify
  - Local FTS lives only inside the encrypted DB (contentless FTS5 with delete triggers).
  - Relay/custody storage (1.3) is a separate encrypted store with its own key and quota.
- **Rationale.** MOB-01 (logout leaves a plaintext DB), KVKK Art. 7/12, and proximity and offline modes making the phone the primary store. The schema is being migrated at 0.5 anyway, so doing it once avoids a second migration at 1.0 (architecture graft).
- **Rejected alternatives.**
  - Relying on FBE/Data Protection alone.
  - Row-level app crypto (breaks FTS).
  - A passphrase-derived DB key (breaks background delivery; App Lock #378 is a separate UI gate).
- **Consequences.** A small performance cost. An erasure test and forensic check are part of the 0.5 exit. Media erasure in MinIO (#327) is a separate backend item and stays open.

### ADR-0021: Amendment to ADR 0008. Storage modes in an E2E world; one cursor model; mailbox
- **Decision.**
  - **One cursor model in every era:** a per-(device, conversation) seq watermark over `/changes`.
    - Before 1.0, `/changes` items carry plaintext content.
    - From 1.0, for E2E conversations, `/changes` items carry the envelope addressed to the *requesting device*, read from `message_envelopes(message_id, recipient_device_id, ciphertext, expires_at)`.
    - The **mailbox is a projection**, not a second cursor. Rows are deleted on the device's `ack uptoSeq`, or at TTL (default 30 days, `application.yml`).
    - `messages` stays the authoritative *metadata* row (id, conversation, seq, sender device, timestamps, encryptionState) for every conversation. `content` is null for E2E conversations.
  - **Modes from 1.0** (owner decision required, because this is a product change):
    - **CLOUD** = E2E relay plus an optional encrypted history backup under a user-held key (T3 step 5). The server is no longer searchable.
    - **BYO** = the same backend self-hosted, or the user's bucket for backup.
    - **LOCAL_ONLY** = no backup anywhere. Envelopes are deleted on delivery. It becomes offerable only when E2E is real *and* the owner has decided receipt-metadata retention (open question Q-O3).
    - Strictest-wins negotiation is unchanged.
  - **Existing plaintext history at the 1.0 cutover.** Recommended:
    - New messages are E2E.
    - Each client downloads its history into the encrypted local DB.
    - The server copy is deleted after a 30-day notified grace period, or encrypted to the user's backup if they opt in.
    - A conversation is **labelled E2E only once the server holds no readable copy of it**.

    The final call is the owner's (Q-O4).
- **Rationale.** ADR 0008's "Cloud = server-held, searchable" cannot coexist with E2E. The judges required a single SSOT and cursor model across eras, and an explicit amendment instead of an implicit one.
- **Rejected alternatives.**
  - CLOUD as opt-in server plaintext (two privacy classes in one 1:1 product invites dishonest UI).
  - Per-chat opt-in E2E (Telegram secret chats).
  - A per-device mailbox cursor separate from conversation seq (two SSOTs).
- **Consequences.**
  - Server search for 1:1 and small groups ends at 1.0, so local FTS must exist first.
  - 5651 reporting relies on reporter-supplied content. Message franking is a post-1.0 research item that needs legal input.

### ADR-0022: Wire protocol. Versioned envelope, authenticated outer header, protobuf, WS hello
- **Decision.** There are three planes, defined in `:shared` under `protocol/` with golden vectors run in backend and mobile tests.
  - **(A) Server control plane.** It stays JSON `WsMessage` (kotlinx.serialization), with these changes:
    - The first frame is `hello{protocolVersions, minSupported, capabilities, deviceId}` → `welcome{protocolVersion, serverTime, certRenewal?, crlDelta?}`.
    - Unknown types decode to a polymorphic `Unknown` and are answered with `error{UNSUPPORTED_FRAME}` (KMP-11).
    - The JWT moves out of the query string (first-frame or header auth; BE-12/BE-16).
    - `ServerAck` gains `seq` and `replay`. `message.new` gains `seq` and `changeSeq`. A new `sync.hint{conversationId, seq}` is added.
    - The 17 duplicated backend DTOs are replaced by the shared ones first (KMP-07).
  - **(B) Inner `Content`.** protobuf via `kotlinx-serialization-protobuf` with explicit `@ProtoNumber`s, inside the libsignal ciphertext from 1.0 (§10).
  - **(C) Outer `TransportFrame`** for sealed off-server hops. It has a random 16-byte id, never a UUIDv7. Its header is AEAD associated data, and its body is padded to buckets.
- **Rationale.**
  - One schema across transports, compact enough for BLE and LoRa.
  - Versioned because old clients will relay frames.
  - GEM-08: no authenticated header and no TTL.
  - UUIDv7 leaks creation time to carriers.
  - Protobuf over CBOR: first-class kotlinx support on all KMP targets, field-number evolution, and existing tooling for golden vectors. CBOR/COSE is viable but adds a second schema discipline for no current gain.
- **Rejected alternatives.**
  - Gemini's "MU" sync word with CRC-32C and per-chunk Reed-Solomon.
  - Full RFC 9171 BPv7 CBOR with BPSec (concepts borrowed, codec not).
  - JSON everywhere.
  - A per-transport format.
- **Consequences.**
  - JSON stays for `ServerFrame` until 1.0. E2E conversations carry sealed protobuf bodies from 1.0 (protocolVersion 2).
  - Parser fuzz targets are added.
  - Version bumps happen only by ADR.

### ADR-0023: Internet-direct means WebRTC ICE plus our own TURN, for live sessions only
- **Decision.**
  - **DIRECT live sessions sit behind `LiveSessionPort`.**
    - At 0.7: LiveKit SFU with embedded TURN/TLS on 443, on a **separate host** (ADR 0009, placement still open), with short-TTL credentials minted by the backend.
    - At 1.1 "Direct": WebRTC P2P for **1:1 calls** (DTLS-SRTP end to end, so 1:1 calls become E2E), with ICE over our own TURN.
  - **Privacy defaults:**
    - `iceTransportPolicy = relay` unless both parties are mutual contacts with an existing conversation, plus an "always relay" setting.
    - If an SFU handles 1:1 calls, the UI discloses that the server can access media.
    - ICE telemetry contains only candidate type, IP family and ASN, never IPs, and carries a KVKK notice.
  - **No messaging over WebRTC data channels.** No libp2p, DCUtR, Circuit Relay, birthday-paradox punching, QUIC-native P2P or QUIC migration. IPv6 is only an ICE candidate.
- **Rationale.**
  - NAT-1: about 63% (not 86%), and about 0% for EDM↔EDM.
  - P2P-1: Circuit Relay v2 is limited to 2 min / 128 KiB of coordination.
  - P2P-2: about 30% of pairs need a relay.
  - QUIC-1: RFC 9000 migration is client-side and UDP-only.
  - Turkish IPv6 is about 22% overall and about 1% on TT Mobil (NAT-3/4).
  - Data channels add no availability over the server: when the server is up it is better, and when it is down there is no rendezvous.
- **Rejected alternatives.** Everything listed above, plus a separate coturn for messaging.
- **Consequences.** TURN egress is budgeted at 30–50% relayed calls until real statistics exist. Multipath QUIC (RFC 10069) and QUIC NAT traversal stay on the watch-list.

### ADR-0024: Proximity. Build LAN, buy Google Nearby Connections; foreground "Nearby mode"; verified contacts only; 1:1 only
- **Decision.** Two `SealedLink` adapters ship in **1.2 Nearby**:
  - **`LanLink`** (built): mDNS/DNS-SD through Android `NsdManager` and iOS Bonjour/Network.framework, length-prefixed TCP and the vetted link handshake (ADR-0018). It works Android↔Android, iOS↔iOS, Android↔iOS and on HMS phones on the same network without internet.
  - **`NearbyLink`** (bought): Play-services Nearby Connections on Android, and the unofficial Nearby Swift package on iOS **only if the spike passes**. Nearby owns BLE discovery and the Wi-Fi upgrade. Our link handshake binds the Nearby connection to the peer's certified link key. UKEY2 is treated as hygiene only.

  **Rules:**
  - Proximity runs only in a user-started foreground **Nearby mode**: Android `connectedDevice` foreground service, auto-off after 15 minutes; iOS foreground only.
  - Eligibility: 1:1 conversation, E2E, **verified** safety number, valid device certificates on both sides, both users opted in, and Nearby mode open.
  - Advertisements carry only rotating pairwise tags (§8.5). Service instance names and Nearby endpoint names are random per session.
  - minSdk 29 is gated at runtime for proximity features.
  - There is no custom BLE GATT/L2CAP, Wi-Fi Direct or Wi-Fi Aware adapter. A custom BLE floor is reconsidered only if the spike shows Nearby iOS interop failing *and* data shows demand.
- **Rationale.**
  - PROX-06: Nearby already does BLE → Wi-Fi.
  - PROX-02: there is no cross-OS background BLE.
  - PROX-04/05: Wi-Fi Direct and Aware are not cross-platform.
  - PROX-03: stranger meshes were repeatedly broken.
  - Nearby's LAN medium requires Play services, so "Nearby covers LAN" is false for HMS and for iPhone↔Android on shared Wi-Fi. That is why `LanLink` is built (architecture must-fix).
- **Rejected alternatives.**
  - A hand-built BLE L2CAP + Wi-Fi Direct + QUIC-migration stack.
  - A BLE multi-hop mesh.
  - Always-on discovery.
  - Wi-Fi Aware as primary.
  - Ditto (kept only as a paid fallback for the iOS cell if the spike fails).
- **Consequences.**
  - HMS devices get LAN only.
  - iOS shows several consent prompts (Bluetooth, Local Network, possibly Location and hotspot join), which needs a consent matrix.
  - A DPIA on proximity scanning is required before launch.
  - Nothing is claimed in the UI until the device matrix is published.

### ADR-0025: Store-and-forward and indirect mode. Custody hierarchy; contacts-first bundles; single-hop sealed uplink; multi-hop mules are research
- **Decision.** INDIRECT delivery is custody of **bundles**. Concepts are borrowed from BPv7: lifetime, bundle age, status reports `RECEIVED / CUSTODY_ACCEPTED / DELIVERED / DELETED_EXPIRED`, and custody release on an authenticated DELIVERED. Custodians are offered in strict order through the `CustodyTarget` port:
  1. **Server mailbox** (exists today as `messages` + `/changes`; ciphertext from 1.0, ADR-0021).
  2. **Own linked devices** (1.3). The user's tablet on the LAN or Nearby holds bundles for that user's other devices, or hands them up when it has internet. There are no third-party data and no new KVKK controller question.
  3. **Single-hop sealed uplink through a verified contact's online phone** (1.3, gated, droppable).
     - The bundle is the normal per-device E2E envelope plus routing, HPKE-sealed to the server's published uplink key.
     - The contact (the mule) sees only an opaque, bucket-padded blob. It POSTs the blob to `/api/v1/uplink` under its own auth, within a per-mule daily quota and size cap, and deletes it on 2xx or after 24 h.
     - The server unseals, verifies the sender-device signature (preserving 5651 attribution) and ingests through the normal idempotent send path.
  4. **Multi-hop contact mules** (research R-DTN, not scheduled): binary Spray-and-Wait with an L-copy budget of at most 4, rotating recipient tags, Padmé padding, per-origin quotas (≤ 20 MB / ≤ 200 bundles total, ≤ 10 per origin token), admission by **server-issued anonymous rate tokens** (Privacy-Pass-style, N per account per day; replaces PoW), and sealed "vaccination" receipts. Mutual contacts only. Strangers never.

  Groups above the off-server cap, channels and communities never use custodians 2–4.

  **Relation to ADR 0008:**
  - LOCAL_ONLY uses custodian 1 with delete-on-delivery and **may use custodians 2–3 only when Q-O3 (receipt retention) is decided**.
  - CLOUD and BYO always end at custodian 1. Every off-server delivery keeps a `serverOwed` obligation until the server acks.
- **Rationale.**
  - The owner wants INDIRECT mode. The mailbox already provides it with legal clarity.
  - Own-device custody is the concrete second `CustodyTarget` with no third-party data.
  - The sealed uplink is the smallest honest "via other devices" path. It removes C7 (recipient tag visible to mules) and GEM-08 by construction, and reuses idempotency and ordering.
  - Stranger custody fails KVKK (LEG-04) and abuse tests (PROX-03, C6).
- **Rejected alternatives.**
  - Gemini epidemic routing.
  - A custody table with a clear `target_peer_id_hash`.
  - Proof-of-Work admission.
  - The mule delivering directly to the recipient (reintroduces offline ordering and recipient-visible routing).
  - Reed-Solomon/FEC.
- **Consequences.**
  - **Kill criteria** for the uplink: drop it if 1.2 telemetry shows fewer than an agreed share of Nearby sessions where one side lacked internet (owner sets the threshold), or if counsel or the crypto review objects.
  - The uplink private key is a long-lived server secret. It is gated on ADR-0031 and must not live on the CI/prod host. It rotates on a published schedule, and clients pin the current and next keys.
  - The anonymous-token issuer is built only if R-DTN or sealed sender is scheduled.

### ADR-0026: Meshtastic adapter. Optional, legally gated, compiled out of TR builds
- **Decision.**
  - A `:mobile:transport-meshtastic` `SealedLink` may be built in **1.4 Off-grid hardware** only after a **written** Turkish telecom-law opinion on Law 5809 Art. 39/63(6) (encrypted communication over *telsiz*), the 2010 coded/encrypted communications regulation and KET conformity.
  - It talks to certified off-the-shelf Meshtastic nodes over BLE or serial using the Meshtastic protobuf API.
  - Scope: text and receipts only, one frame of about 200 B or less, no fragmentation, contacts only.
  - An `AirtimeLedger` computes time-on-air from SF/BW/CR and enforces EU868 duty cycle over a sliding hour per sub-band (1% on 868.0–868.6 MHz; 10% on 869.4–869.65 MHz).
  - Until cleared, the module is **excluded from TR release flavours by Gradle**, not flag-gated.
- **Rationale.**
  - RF-07/08/09 and LEG-03.
  - Gemini's `delay(200)` gives about 97% duty cycle at SF12.
  - Code behind an off flag still ships, and anyone who flips it creates criminal-law exposure for the owner.
- **Rejected alternatives.** A KISS/TNC software AFSK modem, raw LoRa PHY, Codec2 voice over LoRa, and "plaintext SOS-only" LoRa.
- **Consequences.** This may never ship in Turkey. The architecture only guarantees that it would be one adapter plus one admission rule.

### ADR-0027: Rejected-transports record
- **Decision.** These are recorded as rejected, with evidence, and are reopened only by a new ADR:
  - **Rejected permanently:**
    - amateur VHF/UHF, AFSK, Baofeng-class radios (licence required, encryption banned, non-commercial; RF-08/09)
    - PMR446 data (integral-antenna rule)
  - **Rejected:**
    - 5G Broadcast/FeMBMS/ROM, DVB-T2/S2/SH, RTL-SDR (downlink-only, no handset API; RF-02/03/06)
    - satellite D2C/NTN as a transport (it is an IP bearer, covered by the `constrained` flag on `SERVER_RELAY`; no TR D2C data service exists)
    - ultrasonic/ggwave and animated QR (8–16 B/s; adds nothing over a static QR; PROX-09)
    - 5G NR sidelink (no app API; PROX-07)
    - Kademlia DHT, PeerID, libp2p, volunteer supernodes
    - birthday punching
    - QUIC migration across BLE↔Wi-Fi
    - a custom BLE mesh
    - a custom Wi-Fi Direct negotiation
  - **Watch-list:** Wi-Fi Aware (Android↔iOS interop after NAN pairing ships widely), Multipeer/AWDL (iOS↔iOS), multipath QUIC.
- **Rationale.** The verified fact-checks. Recording them stops re-litigation by future agents.
- **Rejected alternatives.** Leaving them implicit.
- **Consequences.** The feature disposition table (§12) cites this ADR.

### ADR-0028: KMP module structure and build prerequisites
- **Decision.** Modules, introduced only when their milestone needs them:
  - `:shared` (existing, MIT). DTOs, `WsMessage` and a `protocol/` package with envelope types and golden vectors. It is promoted to its own module only if a third consumer appears.
  - `:mobile:crypto` (0.5). `SealedFrame` with an internal constructor, `EnvelopeSealer`, `SessionCryptoPort` and `DeviceCertificateVerifier` (1.2). It holds **no** producer of `SealedFrame` until 1.0. Platform adapters (`androidMain` libsignal, `iosMain` Swift bridge) arrive at 1.0.
  - `:mobile:crypto-testkit` (0.5, test-only). Fakes, golden vectors and the contract suite.
  - `:mobile:messaging` (0.5). The engine (ADR-0012) and `SimulatedLink` in testFixtures.
  - `:mobile:composeApp` (existing). UI plus server adapters (`WsServerLink`, `RestChangeFeedSource`, `FcmHintSource`) and SQLDelight store adapters. Server adapters stay here to limit module count; they are extracted only if a second app shell appears.
  - `:mobile:designsystem` (existing).
  - `:mobile:transport-lan` and `:mobile:transport-nearby` (1.2). `:mobile:transport-meshtastic` (1.4, TR-excluded).

  **Prerequisites, in order:**
  1. `gradle/libs.versions.toml`
  2. one `build-logic` convention plugin (`muhabbet.kmp.library`)
  3. SQLDelight `.sqm` migrations with `verifyMigrations`
  4. `:shared:jvmTest` and `:mobile:messaging:jvmTest` in CI

  **Not prerequisites:** a linuxX64 target, the AGP 9 `:androidApp` split, per-feature modules.

  Stateful platform services are commonMain interfaces bound in platform Koin modules. `expect`/`actual` is used only for leaf functions in `platform/`.
- **Rationale.** These are compiler-enforced boundaries, using the precedent `:mobile:designsystem` set, while respecting a host at about 92% disk and one runner. Target-state's 14-module layout was judged out of reach for one engineer.
- **Rejected alternatives.**
  - Keeping everything in `composeApp`.
  - Target-state's 14+ modules plus linuxX64.
  - Gemini's `tr.gov.*` namespace (claims a government namespace; everything stays `com.muhabbet.*`).
- **Consequences.**
  - The KMP-01 iOS decision is a **blocking gate at 0.6** for every cross-platform claim in 1.0 and 1.2.
  - The guardrail ratchet extends to `:mobile:messaging` (no Compose, Koin annotations or `WsMessage` imports).

### ADR-0029: Compliance boundary across all paths
- **Decision.**
  1. **Age gate at registration** (Law 7578 / 5651 Ek Md. 4, in force 2026-11-01).
     - A **0.4 build item**: date of birth stored server-side, under-15 refused, behind an `AgeAssurancePort` (self-declaration now, e-Devlet/NFC-ID later).
     - **The design does not claim compliance until counsel confirms** whether self-declaration meets *yaş doğrulama*.
     - From 1.2, a valid age attestation is a precondition for a device certificate, so it is a precondition for every off-server path.
  2. Channels, communities and groups above the off-server cap are **server-only**, which keeps 5651 takedown central.
  3. Reports are durable outbox commands.
  4. Block lists are enforced in the inbound pipeline on every transport.
  5. A ban or takedown triggers certificate non-renewal, a CRL delta and closing of all WS sessions. The offline exposure window is ≤ 7 days, stated in the transparency text.
  6. A DPIA (KVKK Art. 12) is triggered by any of: proximity scanning, uplink relay, TURN/ICE logging, contact-discovery changes, a new processor or foreign host.
  7. The counsel request names E2E over **BLE and Wi-Fi** (Nearby/LAN), not only LoRa.
- **Rationale.**
  - Laws 5651/7590 (now the Siber Güvenlik Başkanlığı), 7578, 5809 Art. 9(14)/39, KVKK Art. 9/12.
  - Offline modes must not be where legal duties evaporate.
  - The security judge's must-fixes.
- **Rejected alternatives.**
  - A UI-only age check.
  - Client-side content scanning.
  - Channels over proximity.
- **Consequences.**
  - The registration flow changes in 0.4.
  - The counsel checklist blocks the 1.1/1.2/1.3/1.4 gates and 1.0's licence step, but not the 0.x milestones.
  - The docs/legal corrections (§14) are made.

### Process ADRs (kept separate from product)

#### ADR-0030: Trunk-based `main` and a build-once release flow
- **Decision.**
  - Merge `dev` into `main` once, retarget open PRs, delete `dev`.
  - Squash-only merges with linear history. Branch names `<type>/<issue#>-<slug>`.
  - A ruleset on `main` with required checks (`Build & Test`, `Android Build`, and Gitleaks once it runs on PRs). Admin bypass does not skip checks.
  - **Release:** tag → build **once** → install that exact signed APK/AAB on emulator-5554 and launch it (ROADMAP step 6) → **promote the same artefact** to Play. No rebuild, no `--clobber` (CICD-07). Releases are published non-draft.
  - Deploy deploys the CI-built image or SHA, not `git pull` (CICD-07).
  - The tracker gets a "merged, not verified" → "released" → "verified on device" status axis (GOV-02).
- **Rationale.**
  - GOV-01: default-branch drift makes templates, Dependabot and cron inert.
  - GOV-07.
  - CICD-07/08: the binary that ships was never launched.
- **Rejected alternatives.** Keeping `dev` as a long-lived integration branch. Continuing to rebuild on publish.
- **Consequences.** A one-time tracker cleanup before any multi-transport epic is filed. Applied only after owner approval (decision 3).

#### ADR-0031: CI trust boundary
- **Decision.**
  - No `pull_request` (fork or branch) job ever runs on the production host.
  - Until CI moves off `muhabbet-cx43` (GitHub-hosted runners once the billing lock is fixed, since the repo is public and they are free, or a separate box), PR workflows run only on a non-production runner, or are disabled.
  - The unknown runner `muhabbet-mordecai` is identified or deregistered (CICD-03).
  - Signing and Play credentials move into a `release` environment restricted to tags on `main` with a required reviewer (CICD-02). The production environment is verified.
  - Actions are SHA-pinned (CICD-05). Secret scanning plus push protection are enabled. Gitleaks runs on every PR without a paths filter (GOV MISS-01).
  - **Every value ever committed in `.env.prod` (commit 3925f708) is treated as burned and rotated** (CICD-06). The path allowlist is replaced with a commit-scoped one that records the rotation.
  - **No long-lived signing, device-CA, uplink or crypto key is created until this ADR is in force**, and none lives on a host that runs PR code.
- **Rationale.** CICD-01/02/03/05/06. Docker-group membership is equivalent to root on the production host.
- **Rejected alternatives.** Relying on the fork-approval setting alone. Rotating only `JWT_SECRET`.
- **Consequences.** CI capacity changes. The rotation invalidates old access tokens; users are not logged out, because refresh tokens do not depend on `JWT_SECRET` (corrected 2026-11-12, ADR-0031 reviewer note). Forcing re-login is a separate owner decision (incident record A4).

#### ADR-0032: Agent identity and the verification gate
- **Decision.**
  - Agents act through their own GitHub App or machine user, not the owner's token (GOV-06).
  - The owner approves agent PRs, which makes the approval rule satisfiable.
  - Device-dependent acceptance criteria record "observed by / where". Only the owner can tick them.
- **Rationale.** 231 merged PRs, zero reviews. Agents cannot verify devices, calls or radios.
- **Rejected alternatives.** The status quo.
- **Consequences.** Every milestone in §13 uses this field.


#### ADR-0033: Claude Code hooks: design gate and reproducible cloud environment (owner decision 4, "both")
- **Decision.** Two hooks are added to `.claude/` once this design is approved:
  - **Design gate (`PreToolUse` on `Edit|Write|NotebookEdit`, and on `Bash` commands that write files).** It blocks an agent's edit to any path outside `docs/**`, `.github/ISSUE_TEMPLATE/**`, `.github/pull_request_template.md`, `CODEOWNERS` and `.claude/**` while `docs/architecture/multi-transport/APPROVAL.md` does not contain an owner-signed `Status: Approved` line for the scope being edited. The hook's message names the file to approve. It is a guard for agents, not a security boundary, so `--no-verify`-style bypasses are not a threat model concern. The owner lifts it by editing `APPROVAL.md`, per scope (e.g. `0.4`, `0.5`), so later milestones re-arm the gate.
  - **Environment bootstrap (`SessionStart`, cloud sessions only, `$CLAUDE_CODE_REMOTE == true`).** It installs the Android SDK (platforms 35/36, build-tools 36) if missing, adds a user-level Gradle init script routing Maven Central through Google's mirror (repo.maven.apache.org returns HTTP 429 through the session proxy under Gradle's parallel fetches), writes the CI dummy `google-services.json` if absent (gitignored), and exports `ANDROID_HOME`. It is idempotent and never touches tracked files.
- **Rationale.** The owner wants the no-code rule as a mechanism, not a convention. In this session the environment took about 20 minutes to rediscover. Hooks are executed by the harness, so they hold regardless of agent memory.
- **Rejected alternatives.** A CLAUDE.md rule only (already broken by agents in the past, see the HANDOFF). Git pre-commit hooks (they guard commits, not the edits agents make before committing, and they are not installed in fresh clones). A cloud environment setup script (also proposed, but it lives outside the repo; the hook keeps the knowledge versioned with the code).
- **Consequences.** `.claude/settings.json` and `.claude/hooks/*.sh` become reviewed files under CODEOWNERS. The design-gate hook needs a test: an attempted edit to `backend/**` is refused while `APPROVAL.md` is unsigned.

### Judge disagreements and how they were decided

| Topic | Positions | Decision and why |
|---|---|---|
| Backend transactional outbox / event registry / per-device mailbox before 1.0 | Target-state and risk-first: yes. Evolutionary and architecture judge: redundant with pull-by-seq. | **No server outbox table.** Pull-by-seq (ADR-0014) makes every lost hint harmless at lower cost. The mailbox arrives at 1.0 as a projection of the same cursor (ADR-0021). Revisit if measured hint latency is unacceptable. |
| Device certificates and CRL | Security: required for revocation to reach offline peers. Architecture and feasibility: heavy, and no consumer at 0.6. | **Adopt, but build at 1.2 with the first consumer**, on the offline root plus online intermediate. Security gets its bounded window, and YAGNI is respected. |
| SQLCipher timing | Risk-first 0.5. Evolutionary 1.0. Feasibility: blast radius. | **0.5**, implemented as "fresh encrypted DB + resync from server". This is safe because the old DB was a cache. |
| Wake-only push timing | Evolutionary 1.0. Others 0.5. | **0.5.** It is crypto-independent and ends a KVKK transfer, with an OEM fallback specified. |
| Proximity authentication | Evolutionary: MAC from the E2E session. Target-state and risk-first: link key. | **Separate identity-signed link key plus a vetted handshake.** libsignal does not expose ratchet-key derivation, so the alternative would be guessed crypto. |
| LAN adapter | Evolutionary: Nearby covers LAN. Others: build LanLink. | **Build LanLink.** Nearby needs Play services, so HMS and iOS↔Android on shared Wi-Fi would otherwise get nothing. |
| Internet P2P messaging | Target-state: data link in 1.3. Others: never. | **Never for messaging.** P2P only for 1:1 calls (1.1), where it buys E2E media. |
| Bounded HLC | Target-state: yes. Others: no. | **No.** One sequencer plus a per-device counter plus clamped display time is enough. LOCAL_ONLY uses causal order only. |
| Read-receipt enforcement at 1.0 | Target-state: move it client-side. Security: do not silently weaken it. | **Receipts stay server-visible metadata**, so #377 enforcement holds. Written in ADR-0019. |
| Size of 0.4 | Risk-first: large. Feasibility: trim. | **Trimmed** to non-rewritten fixes plus legal and security emergencies (§13). The MOB-04 stopgap is limited to "delete only on ServerAck" because it ships before 0.5. |
| Off-server cap for groups | Risk-first: ≤ 32 off-server. Evolutionary: 1:1 only. | **1:1 only in 1.2.** Groups ≤ 32 are a later 1.x decision that needs its own ADR. |
| `shared/` licence | Risk-first: AGPL. | **MIT.** The backend links it and libsignal never does. |

### Judge must-fix closure (cross-reference)

- **Security.**
  - Wake-only push at 0.5: ADR-0019.
  - Age gate in 0.4 plus counsel caveat: ADR-0029.
  - Bounded offline revocation: ADR-0018/0029.
  - Fail-closed by type, test fakes unreachable from prod: ADR-0016.
  - Random outer ids, rotating tags, padding: ADR-0022, §8.5.
  - RF compiled out, BLE/Wi-Fi in the counsel request: ADR-0026/0029.
  - CICD before keys: ADR-0031.
  - Licence boundary: ADR-0017.
  - Threat model, DPIA and legal-doc corrections: §14 and §17.
  - Relay-only ICE plus SFU disclosure: ADR-0023.
  - Contact discovery: §8.6.
  - Prekey directory: ADR-0018.
  - Read-receipt guarantee: ADR-0019.
  - Plaintext history at cutover: ADR-0021.
- **Feasibility.**
  - Staged engine rollout: §13 (0.5).
  - Trimmed 0.4 and ROADMAP names: §13.
  - CICD before keys: ADR-0031.
  - iOS libsignal bridge spike or Android-only 1.0: ADR-0017.
  - Blocking iOS gate: ADR-0028, §13 (0.6).
  - Type gate: ADR-0016.
  - Push earlier with fallback: ADR-0019.
  - 17 call sites: ADR-0011.
  - Build prerequisites ordered, no linuxX64: ADR-0028.
  - Owner sign-off on device exits: P13, ADR-0032.
  - LOCAL_ONLY retention and `shared/` licence: ADR-0021/0017, Q-O3.
  - Droppable 1.3/1.4 with kill criteria: ADR-0025/0026, §13.
- **Architecture.**
  - Supersede §4 and ADR-003: ADR-0011/0016.
  - One SSOT and cursor model plus the ADR 0008 amendment: ADR-0021.
  - Link key plus vetted handshake: ADR-0018.
  - Hop ack modelled: ADR-0013, §6.
  - LanLink: ADR-0024.
  - Compile-time gate: ADR-0016.
  - Long-lived secrets gated: ADR-0025/0031.
  - #377 settings: ADR-0019.
  - Per-lane queues from 0.5: §6.6.
  - Build-enforced boundaries plus BE-08 ArchUnit before ports: ADR-0028, §14.
  - Numbering and 1.1+ ADRs stay *Proposed (gated)*: P14.

---

## 4. Building blocks (C4 container and component level)

### 4.1 Containers

| Container | Tech | Responsibility | Milestone |
|---|---|---|---|
| Mobile app (Android, later iOS) | CMP, Koin, Decompose, SQLDelight + SQLCipher, Ktor | UI, delivery engine, local SSOT, crypto edge, transports | existing → 0.5 engine |
| Backend modular monolith | Spring Boot 4.1, Kotlin, hexagonal modules | Identity, devices, keys, conversations, seq, change feed, mailbox, push hints, moderation/compliance, call signalling, TURN credentials | existing |
| PostgreSQL 16 (shared-postgres) | | Authoritative metadata, seq, envelopes (1.0), key directory | existing |
| Redis 7 (shared-redis) | | WS cross-instance fan-out, rate limits (move in-process limits to Redis when scaling out) | existing |
| MinIO (shared-minio) | S3 | Media blobs (ciphertext from 1.0) | existing |
| LiveKit + embedded TURN/TLS | separate host (ADR 0009) | SFU and TURN for calls | 0.7 |
| Device-CA / uplink-key service | file-mounted signer, **not on a host running PR jobs** | Online intermediate signing (certificates, CRL); uplink unseal key | 1.2 / 1.3 |
| FCM / APNs | external processors | Content-free wake hints | 0.5 / iOS |
| Meshtastic node (external hardware) | certified LoRa node | RF carrier | 1.4, gated |

### 4.2 Backend components (by module)

- **messaging**
  - `SendMessageService`: idempotent on `(sender_device_id, client_message_id)`, assigns seq, publishes `MessageAccepted` after commit through `TransactionRunner`.
  - `MessageMutationService`: edit, delete, reaction; bumps `change_seq`. Split from `MessageService` per BE-11.
  - `ConversationSequencer`.
  - `ChangeFeedService`: hides unreleased scheduled messages.
  - `DeliveryAckPolicy`: per-device `uptoSeq`, members who are not the sender, existing seq; applies `ReadReceiptPolicyPort`.
  - `DeliveryHintPublisher`: WS hint plus FCM wake, best effort.
  - `ScheduledReleaseJob`: one transaction per message.
  - `Mailbox` (1.0): the `message_envelopes` projection, delete on ack, TTL purge.
  - `UplinkIngress` (1.3, gated).
- **keys** (new bounded context at 0.6, extracted from messaging's `EncryptionService`): `PrekeyDirectory`, `LinkKeyRegistry`, `IdentityKeyChanged` event.
- **auth**
  - `AuthService` (existing).
  - `AgeAssurance` (0.4).
  - `DeviceRegistry` (ADR 0007).
  - `SessionRevocation`, which emits `SessionRevoked` and `DeviceRevoked`. The WS adapter subscribes and closes sessions on expiry, logout, revoke and deletion (BE-12).
  - Refresh-token reuse detection (BE-13).
  - `DeviceCertificateAuthority` (1.2).
- **shared/websocket**: `WsGateway` (hello/version, first-frame auth, `ConcurrentWebSocketSessionDecorator`, dispatch to in-ports only). `WsSessionRegistry` bound to `(deviceId, jti, exp)`.
- **notification**: `WakePushService` (data-only, collapse keys; presence-aware suppression later).
- **calls/session**: `CallSignalingService` (existing, to be made to work at 0.7), `TurnCredentialService` (≤ 10-min credentials, relay-only policy).
- **moderation → compliance**: reports, takedown leading to revocation, legal-request log, transparency counters. `AccountBanned` → revoke devices and close sessions.
- **contacts**: `ContactLookupService` (rate-limited, block-filtered, discoverability enforced).
- **cross-cutting**: ArchUnit/Konsist module-boundary rules (BE-08), `AbuseRateLimiter` (Redis token buckets).

### 4.3 Mobile components

- **`:mobile:messaging`**
  - `MessagingEngine` (Facade, Koin single).
  - `Outbox` (State machine, Command = persisted entry).
  - `Inbox` (Observer over all `InboundSource`s).
  - `SyncCoordinator` (seq-gap pulls; triggered by reconnect, wake, foreground and WorkManager).
  - `ReceiptService`.
  - `SessionTeardown`.
  - `PathPolicy` + rules (1.2).
  - `CustodyPolicy` (1.3).
  - `LiveSessionCoordinator` (0.7): routes call signalling as CONTROL frames.
  - `ProtocolCodec` adapters (`OutgoingEnvelope` ↔ `ServerFrame` before 1.0, Adapter pattern).
- **`:mobile:crypto`**: `SealedFrame`, `EnvelopeSealer`, `SessionCryptoPort`; `LibsignalAndroidSealer` / `LibsignalIosSealer` (1.0); `DeviceCertificateVerifier` + CRL store (1.2); `LinkAuthenticator` (1.2, vetted library).
- **`:mobile:composeApp`**
  - UI observing SQLDelight flows.
  - `WsServerLink` + `WsInboundSource` (strangled `WsClient`).
  - `RestChangeFeedSource`.
  - `FcmHintSource`.
  - `SqlDelightMessageStore` / `SqlDelightOutboxStore` (SQLCipher).
  - `SecureKeyStore` (Keystore/Keychain).
  - `BackgroundSyncManager` (seq-driven).
  - Nearby-mode screen (1.2), with consent matrix and auto-off.
- **`:mobile:transport-lan` / `-nearby` (1.2)**: `LanLink`, `NearbyLink`, rotating-tag advertiser and scanner.
- **`:mobile:transport-meshtastic` (1.4, TR-excluded)**: `MeshtasticLink`, `AirtimeLedger`.

Diagrams to draw: `c4-context.mmd`, `c4-container.mmd`, `c4-component-backend.mmd`, `c4-component-mobile.mmd` (§17).

---

## 5. Ports catalogue

| Port | Dir | Side | Kotlin sketch | Now | Later |
|---|---|---|---|---|---|
| `ConversationSequencePort` | out | BE | `fun next(conversationId: UUID): Long // inside caller's tx` | `JpaConversationSequenceAdapter` (UPDATE…RETURNING), `InMemoryConversationSequence` | none |
| `GetConversationChangesUseCase` | in | BE | `fun changes(requester: DeviceId, conv: UUID, afterSeq: Long, limit: Int): ChangePage` | `ChangeFeedService` | 1.0: envelope projection per device |
| `SendMessageUseCase` | in | BE | `fun send(cmd: SendMessageCommand): SendOutcome // Accepted(id, seq, ts, replay) \| Rejected(ErrorCode)` | `SendMessageService` | `UplinkIngress` reuses it |
| `AcknowledgeDeliveryUseCase` | in | BE | `fun ack(device: DeviceId, conv: UUID, uptoSeq: Long, kind: AckKind): AckOutcome` | `DeliveryAckPolicy` | 1.0: also deletes mailbox rows |
| `DeliveryHintPort` | out | BE | `fun hint(device: DeviceId, conv: UUID, seq: Long)` | `WsAndFcmHintAdapter`, `RecordingHint` | APNs |
| `WakePushPort` | out | BE | `suspend fun wake(device: DeviceId, hint: WakeHint, collapseKey: String): PushResult` | `FcmWakePush` | `ApnsWakePush` |
| `SessionRevocationPort` | out | BE | `fun revokeDevice(id: DeviceId, r: RevokeReason); fun revokeUser(id: UserId, r: RevokeReason)` | `WsSessionRevocationAdapter` (local + Redis channel) | none |
| `AgeAssurancePort` | out | BE | `fun assess(user: UserId, e: AgeEvidence): AgeVerdict` | `SelfDeclarationAssurance` | e-Devlet/NFC-ID |
| `PrekeyDirectoryPort` + `ClaimPreKeyBundleUseCase` | out/in | BE | `fun claim(requester: DeviceId, target: UserId): List<DevicePreKeyBundle>; fun publish(d: DeviceId, b: PreKeyBatch)` | `JpaPrekeyDirectory` (SKIP LOCKED), in-memory with concurrency test | none |
| `MailboxPort` | out | BE | `fun store(msg: UUID, envs: List<DeviceEnvelope>, exp: Instant); fun deleteUpTo(d: DeviceId, conv: UUID, seq: Long): Int; fun purgeExpired(now: Instant): Int` | 1.0 `JpaMailboxAdapter`, `InMemoryMailbox` | BYO = same backend self-hosted |
| `CertificateSignerPort` + `IssueDeviceCertificateUseCase` | out/in | BE | `val keyId: String; fun sign(tbs: ByteArray): ByteArray` / `fun issue(d: DeviceId): DeviceCertificate` | 1.2 `FileKeyEd25519Signer`, `FakeSigner` | HSM/KMS when a second environment exists |
| `TurnCredentialPort` | out | BE | `fun mint(d: DeviceId, session: UUID, ttl: Duration): TurnCredential` | 0.7 LiveKit token adapter | 1.1 coturn HMAC if P2P uses standalone TURN |
| `ContactLookupUseCase` | in | BE | `fun lookup(requester: UserId, hashes: List<PhoneHash>): LookupResult // capped, block-filtered` | `ContactLookupService` | OPRF/PSI (research) |
| `ServerLink` | out | M | `val state: StateFlow<LinkState>; suspend fun submit(f: ServerFrame): SubmitResult` | `WsServerLink`, `SimulatedLink` | none (second server link not planned; `RestChangeFeedSource` is inbound) |
| `SealedLink` | out | M | `val scope: PathScope; val caps: LinkCapabilities; val state: StateFlow<LinkState>; suspend fun send(to: PeerRoute, f: SealedFrame): SubmitResult` | `SimulatedSealedLink` (tests) | `LanLink`, `NearbyLink` (1.2), `UplinkHandover` (1.3), `MeshtasticLink` (1.4) |
| `SubmitResult` | type | M | `sealed interface SubmitResult { data class ServerAccepted(val seq: Long, val ts: Instant, val replay: Boolean); data class HopAcked(val custodian: Custodian); data class Retry(val after: Duration?); data class Backpressure(val after: Duration); data class Rejected(val code: String) }` | in use | none |
| `InboundSource` | in (driving) | M | `val events: Flow<InboundEvent> // Message \| Change \| Hint(conv, seq) \| Receipt \| KeyChanged \| CrlDelta` | `WsInboundSource`, `RestChangeFeedSource`, `FcmHintSource` | `LanLink`, `NearbyLink` |
| `MessageStore` | out | M | `suspend fun <T> tx(b: suspend MessageStore.() -> T): T; suspend fun upsert(e: InboundEvent): UpsertResult; fun observe(conv: ConversationId): Flow<List<MessageRow>>; suspend fun watermark(conv: ConversationId): Long` | `SqlDelightMessageStore`, `InMemoryMessageStore` | none |
| `OutboxStore` | out | M | `suspend fun enqueue(e: OutboxEntry, optimistic: MessageRow); fun due(now: Instant): Flow<List<OutboxEntry>>; suspend fun cas(id: EntryId, from: OutboxState, to: OutboxState): Boolean; suspend fun clear()` | `SqlDelightOutboxStore`, `InMemoryOutboxStore` | none |
| `ChangeFeedPort` | out | M | `suspend fun changes(conv: ConversationId, afterSeq: Long, limit: Int): ChangePageDto; suspend fun heads(): Map<ConversationId, Long>` | `RestChangeFeedSource`, `FakeChangeFeed` | none |
| `EnvelopeSealer` | out | M | `suspend fun seal(conv: ConversationCrypto, to: List<DeviceAddress>, plain: ByteArray): SealResult; suspend fun open(f: SealedFrame): OpenResult` | none (0.5 has no producer; PENDING_SEAL for E2E) | `LibsignalAndroidSealer`, `LibsignalIosSealer` (1.0), `FakeSealer` (testkit only) |
| `SessionCryptoPort` | out (expect/actual edge) | M | `suspend fun safetyNumber(peer: UserId): SafetyNumber; suspend fun processBundle(b: DevicePreKeyBundle)` | none | libsignal adapters (1.0) |
| `DeviceCertificateVerifier` | out | M | `fun verify(c: DeviceCertificate, now: Instant): CertVerdict; suspend fun applyCrl(d: SignedCrlDelta): Boolean` | none | 1.2 `Ed25519Verifier`, `FixedVerdictVerifier` |
| `LinkAuthenticator` | out | M | `suspend fun handshake(io: RawDuplex, role: Role, expected: DeviceAddress?): AuthResult` | none | 1.2 vetted Noise or TLS-1.3-pinned (spike) |
| `CustodyTarget` | out | M | `val kind: CustodianKind; suspend fun offer(b: Bundle): CustodyResult` | `ServerMailboxCustody` (via `ServerLink`) | `OwnDeviceCustody`, `ContactUplinkCustody` (1.3) |
| `PathPolicy` / `PathRule` | internal | M | `fun interface PathRule { fun apply(ctx: PathContext, c: List<PathCandidate>): List<PathCandidate> }` | none | 1.2 (Admission, CloudPersistence, Preference) |
| `LiveSessionPort` | out | M | `suspend fun join(spec: LiveSessionSpec, creds: SessionCredentials): LiveSession` | 0.7 `LiveKitSession` | 1.1 `WebRtcP2PSession` |
| `SecureKeyStorePort` | out | M | `suspend fun getOrCreateWrappedKey(alias: String): ByteArray; suspend fun destroy(alias: String)` | Android Keystore, in-memory | iOS Keychain |
| `AirtimeLedger` | internal | M | `fun tryReserve(airtime: Duration, band: SubBand, now: Instant): Boolean` | none | 1.4 sliding window |
| `Clock` / `IdGenerator` | out | both | `fun interface IdGenerator { fun next(): String }` plus `kotlinx.datetime.Clock` | System, UUIDv7 | `TestClock`, `SequentialIdGenerator` |

P7 check:
- `ServerLink` has one production implementation. It is justified by `SimulatedLink` and by being the type-level counterpart of `SealedLink`, which gets real implementations at 1.2.
- `InboundSource` has three implementations today.
- `CustodyTarget` gets its second implementation at 1.3. Before that, the Outbox does not route through it.

---

## 6. Delivery engine specification

### 6.1 Outbound message state machine (`OutboxEntry`, State pattern, CAS transitions)

States:
- `PREPARING` (media compress and upload)
- `PENDING_SEAL` (E2E: no session yet)
- `QUEUED`
- `IN_FLIGHT(link, attempt, deadline)`
- `RETRY_WAIT(nextAttemptAt)`
- `HOP_ACKED(custodian, serverOwed)`
- `SERVER_ACCEPTED(seq)`
- terminal: `FAILED_PERMANENT(code)`, `EXPIRED`, `CANCELLED`

DELIVERED and READ are not outbox states. They are *derived* from receipt rows (§6.4).

| From | Event | To | Notes |
|---|---|---|---|
| (new) | Send (text) | QUEUED | Optimistic row and entry written in one transaction |
| (new) | Send (media) | PREPARING | Separate media worker. Does not block the conversation FIFO |
| PREPARING | UploadDone(blobRef) | QUEUED / PENDING_SEAL | Upload resumable, with 1 MiB ranges |
| PREPARING | UploadFailed(retryable) | PREPARING (backoff) | |
| PENDING_SEAL / QUEUED | SealFailed(NoSession) | PENDING_SEAL | Never a plaintext fallback. A prekey fetch is triggered |
| PENDING_SEAL | SessionReady | QUEUED | |
| QUEUED | Dispatch(link) | IN_FLIGHT | Path chosen by direct call (≤ 1.1) or `PathPolicy` (≥ 1.2) |
| IN_FLIGHT | ServerAccepted(seq) | SERVER_ACCEPTED | Message row gets its seq. Entry purged after receipts settle |
| IN_FLIGHT | HopAcked(peer/own device/mule) | HOP_ACKED(serverOwed = mode≠LOCAL_ONLY) | |
| HOP_ACKED(serverOwed=true) | ServerLinkUp | QUEUED (server-only) | Upload obligation. Recipient dedups |
| HOP_ACKED(serverOwed=false) | E2EDelivered | (purge) | LOCAL_ONLY |
| IN_FLIGHT | Timeout / LinkLost / Retry | RETRY_WAIT | Backoff 1 s→30 s, full jitter, injected Clock. Reset to now on an eligible LinkUp |
| IN_FLIGHT | Rejected(code) | FAILED_PERMANENT | Bubble shows retry. `RetryDelivery` → QUEUED |
| any non-terminal | lifetime exceeded | EXPIRED | Default 30 d; call signalling 60 s |
| QUEUED / RETRY_WAIT / PENDING_SEAL | user deletes | CANCELLED | |
| IN_FLIGHT at process start | Recover | QUEUED | Safe because the server is idempotent |

### 6.2 Bundle and custody state machine (1.3+, for INDIRECT custodians)

`CREATED → OFFERED(custodian) → IN_CUSTODY(custodian, expiresAt) → DELIVERED_RECEIPT → RELEASED`, with branches `OFFERED → DECLINED → CREATED (next custodian)` and `IN_CUSTODY → EXPIRED`.

Custodians are offered strictly in hierarchy order. A custodian releases custody only on (a) an authenticated `DELIVERED` (sealed to the sender), (b) handover acknowledged by a higher-ranked custodian (server 2xx / `ServerAccepted`), or (c) expiry (erratum E-10). On the mule side, the store is quota-bounded with FIFO-by-expiry eviction.

### 6.3 Link and session state machine

- **Peer and sealed links:** `DISABLED → UNAVAILABLE → DISCOVERING → CONNECTING → AUTHENTICATING → UP ⇄ DEGRADED → BACKOFF(until) → …`. Overlay states: `CONSENT_REQUIRED` and `BLOCKED_BY_POLICY`.
  - AUTHENTICATING verifies the device certificate, the CRL and the handshake against the certified link key.
  - DEGRADED means goodput is below the class threshold or loss exceeds a bound.
- **Server link:** `Disconnected → Connecting → Hello → Authenticated → Syncing(heads) → Ready ⇄ Degraded → Backoff`, plus `AuthExpired` (the server closes at JWT expiry and the client refreshes).
- Observers (the Outbox and `PathPolicy`) react to state changes. A transition to UP or Ready resets retry backoff for eligible entries.

### 6.4 Ack model

- **L1 hop ack.** `ServerAck{seq, serverTs, replay}` or `HopAck{frameId}` from a peer link.
- **L2 E2E delivered.** `ack{conversationId, uptoSeq = complete_through_seq, DELIVERED}` per device, sent only after the inbox commit (ICD §3.8 R-ACK). Off-server it is a sealed receipt frame, also uploaded to the server when the server is reachable.
- **L3 read.** `ack{…, READ}` when the chat is visible. The server applies the `readReceiptsEnabled` downgrade when publishing.
- **Aggregation.** The sender's status is computed from the server's per-device rows. The client never promotes to READ because of one member's status update (group fix).

### 6.5 QoS classes

| Class | Contents | Queued? |
|---|---|---|
| CONTROL | receipts, call signalling, key-change, CRL delta, link control | yes, short TTL |
| INTERACTIVE | text, edits, deletes, reactions, location, polls | yes |
| BULK | media blob transfer (out-of-band on server; streamed or file payloads off-server) | yes, resumable |
| EPHEMERAL | typing, presence | **never queued**, dropped without a live link |

### 6.6 Scheduling (no head-of-line blocking)

- Each link has its own sender coroutine with three bounded lanes. From 0.5 the server link has CONTROL and INTERACTIVE lanes, and BULK uses HTTP upload. The coroutine uses **biased `select`**: CONTROL first, then INTERACTIVE, then BULK chunk *k*. Preemption happens between chunks.
- Per-conversation FIFO applies only to INTERACTIVE entries *within* one conversation. Conversations drain independently, with no global Mutex (GEM MISS-01).
- Media in PREPARING sits outside the FIFO. It enters QUEUED when its upload finishes, so a stalled upload never blocks text. Send order follows ready order, and display order follows seq.
- From 1.2 each sealed link has its own sender, so a slow BLE link (36–140 kbit/s measured) never blocks the server or LAN.

### 6.7 Chunking and reassembly

- There is no generic frame-fragmentation layer (YAGNI).
- **Server path:** media is uploaded out-of-band, resumable, in 1 MiB ranges, as ciphertext from 1.0. The sealed message carries `{blobId, size, sha256, key, iv}`.
- **LAN:** length-prefixed records of at most 64 KiB, with offset-resume for BULK.
- **Nearby:** BYTES payloads for INTERACTIVE/CONTROL and FILE/STREAM payloads for BULK. The library handles fragmentation.
- **Meshtastic:** a single frame of at most 200 B. No media.
- Reassembly is keyed by `blobId`, bounded by `MAX_REASSEMBLY_BYTES` and TTL, and verified by SHA-256 before the message row is completed.
- FEC: none. Cross-chunk erasure coding is research only, for RF, and only if field loss data justifies it.

### 6.8 Escalation and de-escalation (1.2+)

- Link establishment uses Happy-Eyeballs-style racing when there is demand for a peer: queued entries for that peer, or the user opened the chat in Nearby mode.
  - Start `LanLink` first if both peers are on the same network.
  - Start `NearbyLink` 250 ms later (`ATTEMPT_DELAY_MS`).
  - Use the first UP link. Let a higher-ranked link take over and cancel the rest.
- **Escalation:** request a Nearby bandwidth upgrade when queued BULK bytes for the peer reach `ESCALATE_BYTES` = 256 KiB. The BLE → Wi-Fi switch itself is the library's job. It is make-before-break, and continuity comes from entry ids and acks, never from QUIC migration.
- **De-escalation:** close the costlier link after `IDLE_CLOSE_MS` = 30 s without BULK or live demand, with hysteresis `DEESCALATE_MIN_UP_MS` = 10 s. Nearby mode auto-off is 15 min.

### 6.9 Backpressure

- Lanes are bounded channels.
- `submit/send` may return `Backpressure(after)`.
- The Outbox *pulls* due entries only when a lane has capacity.
- Sealed links use a credit window. The receiver advertises credits in link control frames.
- Inbound has bounded buffers per source. When a buffer is full, the source pauses its reads; it never drops, because pull repairs anything lost.

---

## 7. Path selection and racing algorithm

```text
// Metrics model (per link, rolling 60 s EWMA, injected Clock)
LinkMetrics(rttMs, lossPpm, goodputBps /* acked app bytes/s, not PHY rate */,
            costClass: FREE|METERED|CONSTRAINED, batteryClass: LOW|HIGH)
// `constrained` (satellite/metered/low-bw) is a flag on SERVER_RELAY, not a transport.

fun plan(entry, links, ctx): PathPlan
  cands = links.filter { it.state in {UP, Ready} || (it.state == CONNECTING && entry.class != CONTROL) }
  // Admission (Chain of Responsibility, any Deny removes the candidate, never downgrades content)
  cands = cands.filter { c -> rules.all { r -> r.check(entry, c, ctx) == Allow } }
      // rules: AssuranceRule (offServer ⇒ SealedFrame required — already a type fact),
      //        RelationshipRule (mutual contact, existing conversation, verified safety number),
      //        CertificateRule (valid cert + CRL, both ends), ConversationModeRule (ADR 0008),
      //        ScopeRule (1:1 only off-server; channels/communities/large groups ⇒ server only),
      //        ConsentRule (Nearby mode open, OS permissions), RegulatoryRule (RF excluded in TR),
      //        QuotaRule (custodian/mule budgets), SizeRule (bytes ≤ caps.maxFrameBytes or supportsBulk),
      //        ConstrainedRule (if server link constrained: CONTROL+INTERACTIVE only, defer BULK)
  if cands.isEmpty() -> return Stay(QUEUED)          // custody offer handled by CustodyPolicy (1.3)
  // CloudPersistence
  serverOwed = ctx.mode in {CLOUD, BYO}
  // Preference (data table per traffic class × mode)
  rank = when (ctx.mode, entry.class) {
    (CLOUD|BYO, INTERACTIVE|CONTROL) -> [SERVER_RELAY, LAN, PROXIMITY, EXTERNAL_RF]
    (CLOUD|BYO, BULK)                -> [SERVER_RELAY*, LAN, PROXIMITY]   // *unless server constrained/down and LAN/PROX already UP
    (LOCAL_ONLY, _)                  -> [LAN, PROXIMITY, SERVER_RELAY /*mailbox, delete-on-delivery*/]
  }
  best = cands.sortedWith(compareBy(rank.indexOf(scope), rttMs, -goodputBps, costClass)).first()
  return Send(best, serverOwed = serverOwed && best.scope != SERVER_RELAY)

// Racing happens at link establishment, not per message (no hedged duplicates off-server):
on demand(peer):
  for ((i, adapter) in rankedAdaptersFor(peer).withIndex())
     launch { delay(i * ATTEMPT_DELAY_MS); adapter.open(peer) }
  first UP wins; a higher-ranked later UP may take over; losers cancelled
```

Before 1.2, `plan()` is not built. The Outbox submits to `ServerLink` when it is Ready and otherwise keeps the entry QUEUED. Within IP scopes, ICE already races candidate pairs for calls, and we do not reimplement that.

---

## 8. Identity, keys, E2E and trust model across paths

### 8.1 Trust anchors
- **Server TLS + JWT** for server hops. The JWT is out of the query string, and sessions close at expiry, logout and revoke.
- **libsignal identity per device** for content (1.0).
- **Device certificate** (server-signed, 7 days) plus a **link key** for off-server hops (1.2).
- **Safety-number QR** for the human-verified binding.

### 8.2 Per-path trust table

| Path | Peer authentication | Content protection | Metadata exposure | Revocation reach |
|---|---|---|---|---|
| SERVER_RELAY (pre-1.0) | TLS + JWT, device-bound session | TLS only, honest "not E2E" UI | Server sees all | Immediate (session close) |
| SERVER_RELAY (1.0+) | same | libsignal per device | Server sees metadata only | Immediate |
| IP_RELAYED / IP_DIRECT (calls) | WS-signalled, TURN credentials ≤ 10 min | DTLS-SRTP (E2E for 1:1 P2P; SFU disclosed) | IP hidden from non-contacts (relay-only) | Credential TTL |
| LAN / PROXIMITY | Certificate + CRL + vetted handshake on link key | libsignal envelope inside `SealedFrame` | Rotating pairwise tags, random instance names, padding | ≤ 7 days (certificate expiry) |
| UPLINK_RELAY | Mule = verified contact (link auth). Server verifies the sender-device signature | HPKE to server around the E2E envelope | Mule sees size bucket and time only | Server-side, immediate |
| EXTERNAL_RF | as LAN | as LAN, fixed-size frames | Airtime visible | ≤ 7 days |

### 8.3 E2E
- 1:1 uses one session per recipient device, fanned out to the recipient's devices and to the sender's own other devices.
- Groups use sender keys distributed over 1:1 sessions, with membership changes sequenced by the server.
- Media: the existing `MediaEncryptor` (AES-256-GCM, key in the sealed body) is kept, now behind real sessions.
- There are no long-term signatures on messages (deniability, erasure).
- Ratchet and session state live only in the libsignal store (Keystore-wrapped on Android, Keychain on iOS).

### 8.4 Prekey directory fixes (BE-14, M1, M2)
- Per-device tables.
- Atomic claim.
- Per-requester and per-pair rate limit plus a relationship check.
- Kyber and last-resort columns.
- Low-water signals.
- Rejection of identity replacement.
- `IdentityKeyChanged` → a blocking interstitial for verified contacts, a visible notice for everyone else.

The atomic claim and rate limit ship in 0.4. The per-device schema ships in 0.6.

### 8.5 Discovery privacy (1.2)
- `pairKey` = an agreement or derivation between the two certified link keys, using the vetted library (spike), or a `rendezvousSecret` exchanged *inside* the E2E session so the server never sees it. Preferred: the in-session secret (target-state graft), because the server learns nothing.
- `tag = HMAC-SHA256(pairKey, "mbt-prox-v1" ‖ epoch15m)[0..8]`. A scanner matches tags only against its own contacts.
- Advertisements carry no account, phone or device identifiers. Nearby endpoint names and mDNS instance names are random per session.

### 8.6 Contact discovery fix (C8, M3; 0.6, with the cap in 0.4)
- Stop presenting unsalted SHA-256 phone hashes as privacy. Correct SECURITY.md (GOV-08) and the KVKK texts.
- A server-side lookup with a per-account daily cap on *new* numbers, block-list filtering, and a server-enforced "discoverable by phone" setting **with a reader**.
- An integration test proves enumeration is throttled.
- OPRF/PSI is research.

### 8.7 Fail-closed summary
- Type gate (ADR-0016).
- Plaintext fallback deleted.
- Test fakes kept off the release classpath.
- `UntrustedIdentity` parks the entry and asks the user.

---

## 9. Store-and-forward / indirect mode

- **Three stores, each with one owner:**
  1. **Sender outbox:** durable, leased, expiring after 30 days by default. It has custody until server acceptance, or until peer E2E delivery for LOCAL_ONLY.
  2. **Server mailbox:** messages plus `/changes` before 1.0. From 1.0, the `message_envelopes` projection, deleted on device ack or TTL.
  3. **(1.3)** Own-device custody store and mule uplink cache: separate SQLCipher store, quotas (uplink: ≤ 50 bundles / 10 MB per mule, 24 h, configurable), FIFO-by-expiry eviction.
- **Wake-up:** data-only FCM `{conversationId, seq}` → `SyncCoordinator` pull. WorkManager (and BGAppRefresh on iOS) as backstop, driven by seq.
- **Privacy controls:**
  - opaque padded blobs; mules are verified contacts only
  - HPKE to the server, so the mule cannot route or read
  - sender-device signature inside, so the server attributes the message (5651)
  - rotating tags, only if R-DTN ever starts
  - no stable recipient hash anywhere
- **Abuse controls:**
  - per-mule daily quota and per-bundle size cap enforced by both the mule and the server
  - per-sender upload quota on `/uplink`
  - anonymous rate tokens only if R-DTN or sealed sender is scheduled
  - no PoW
- **Relation to ADR 0008:** see ADR-0021/0025. LOCAL_ONLY = mailbox delete-on-delivery, no history. Receipt-metadata retention is the owner's call (Q-O3) before LOCAL_ONLY ships.
- **Erasure:**
  - Custody blobs expire.
  - Leaving relay mode wipes the relay store immediately.
  - `SessionTeardown` wipes all stores.
  - Media in MinIO is deleted on account erasure (#327, backend fix outside this design).

---

## 10. Wire protocol outline

**Encoding.** The control plane stays JSON (kotlinx.serialization) over WS. Inner content and outer sealed frames are protobuf (`kotlinx-serialization-protobuf`, explicit field numbers). Why protobuf rather than CBOR: kotlinx support on every KMP target, schema evolution by field number, compactness for BLE and LoRa, and golden vectors shared with the backend. All three proposals agreed on it.

**Server WS (control plane, protocolVersion 1 → 2 at 1.0)**
- `hello{protocolVersions:[1,2], minSupported, capabilities:["seq","wake","sealed"], deviceId}` → `welcome{protocolVersion, serverTime, certRenewal?, crlDelta?}`. Auth is in the first frame or a header, never the query string.
- `message.send{clientMessageId, conversationId, encryption: "none" | "e2e/v1", content? | envelopes:[{recipientDeviceId, protocol, ciphertextB64}], clientTs}` → `ack{clientMessageId, seq, serverTs, replay}`.
- `message.new{messageId, conversationId, senderDeviceId, seq, changeSeq, content? | envelope}`.
- `message.ack{conversationId, uptoSeq, kind}`. `sync.hint{conversationId, seq}`. `security.key_changed` (existing frame) extended with `deviceId`: `{userId, deviceId, conversationId?, timestamp}` (erratum E-25). `crl.delta{…}`. `error{code}`, including `UNSUPPORTED_FRAME` and `PROTOCOL_TOO_OLD`.
- REST `GET /api/v1/conversations/{id}/changes?afterSeq&limit` → `{items:[MessageChange], lastSeq, hasMore}`.

**Inner `Content`** (inside libsignal ciphertext):
```text
Content { 1 causal: CausalHeader{senderDeviceId, deviceCounter:uint64, clientMessageId:bytes16(UUIDv7), senderTimeMs}
          2 conversationRef{conversationId, storageMode, expectedSeqHint?}
          3 body: oneof Text{text, mentions, replyTo?} | MediaPointer{blobId, mime, size, sha256, key, iv, thumb?}
                       | Receipt{kind, uptoSeq | ids} | Edit{messageId, editVersion, text} | Delete{messageId}
                       | Reaction{messageId, emoji} | Control{sessionReset | safetyNumberChanged | callOffer…}
          4 expiresAt?  5 viewOnce? }
```

**Outer `TransportFrame`** (sealed off-server hops and the uplink):
```text
TransportFrame { 1 v:uint32=1  2 frameType: DATA|ACK|CONTROL|CREDIT
                 3 frameId: bytes16 (random, NOT UUIDv7)  4 ttlSec:uint32  5 routingTag: bytes8..16 (rotating pair tag)
                 6 linkSeq:uint32  7 body: bytes (Padmé-padded SealedFrame) }
AEAD: link channel encrypts body; fields 1–6 are associated data. No sync word, no CRC (AEAD tag = integrity).
```

**UplinkBundle (1.3):** `HPKE-Seal(serverUplinkPk, info="mbt-uplink-v1", aad=bucketSize, pt = senderDeviceSignature ‖ routing ‖ DeviceEnvelopes)`, POSTed over HTTPS.

**Addressing:** a server hop addresses `deviceId` over TLS. A peer hop addresses a rotating `routingTag`. Within the envelope, `recipientDeviceId`.

**TTL:** frame `ttlSec`, bounded by the outbox lifetime (30 d) and the class (CONTROL 60 s).

**Fragmentation:** delegated to the medium (§6.7). The `frameType = CREDIT` frame carries flow control.

**Rules:**
- ByteArray fields are wrapped in value classes with content equality (GEM-16).
- Anything hashed or signed uses a length-prefixed, domain-separated encoding (GEM-13).
- MTU is negotiated, never constant (MISS-03).
- Golden vectors run on JVM, the backend and iOS native.
- Fuzz targets cover every parser.

---

## 11. Data model changes

### 11.1 Backend Flyway (next is V28)
| Migration | Change | Milestone |
|---|---|---|
| V28 | `conversations.last_seq BIGINT NOT NULL DEFAULT 0`; `messages.seq BIGINT`, `messages.change_seq BIGINT`; backfill by `(server_timestamp, id)`; `UNIQUE(conversation_id, seq)`; seq-based indexes | 0.4 (early, additive) |
| V29 | `messages.sender_device_id`; `UNIQUE(sender_device_id, client_message_id)` (replaces the global client-id PK check); scheduled messages `seq NULL` until release | 0.4/0.5 |
| V30 | `users.birth_date`, `age_assurance_level`, `age_assured_at` (age gate) | 0.4 |
| V31 | `contact_lookup_quota` / discoverability column with reader | 0.4 (cap) / 0.6 |
| V32 | Per-device delivery: `message_device_delivery(message_id, device_id, status, updated_at, PK(message_id, device_id))` (BE-18) | 0.6 |
| V33 | Device-scoped key tables: `identity_keys(user_id, device_id, …)`, `signed_prekeys`, `one_time_prekeys`, `kyber_prekeys(+last_resort)` with `device_id`; atomic-claim indexes | 0.6 (atomic claim and rate limit on the existing tables in 0.4) |
| V34 | `message_envelopes(message_id, recipient_device_id, protocol, ciphertext BYTEA, expires_at, PK(message_id, recipient_device_id))`; `messages.encryption_state`; `content` nullable for E2E | 1.0 |
| V35 | `device_link_keys(device_id, key_type, public_key, identity_signature, expires_at, revoked_at)`; `device_certificates(serial, device_id, not_before, not_after, issuer_key_id, revoked_at)`; `crl_deltas` | 1.2 |
| V36 | `uplink_quota(mule_user_id, day, bytes, count)`; `uplink_keys(key_id, public_key, not_before, not_after)` | 1.3 (gated) |

There is no server outbox table. Migration-compatibility discipline is needed for rollback (CICD-09): additive first, with destructive changes one release later.

### 11.2 Mobile SQLDelight
- **Wire migrations first:** `1.sqm` represents today's schema, `verifyMigrations` runs in CI, and `deriveSchemaFromMigrations`.
- **0.5 schema (SSOT, in a new encrypted DB file; the old cache file is deleted after `PendingMessage` is copied):**
  - `conversation(id, type, title, storage_mode, encryption_state, last_seq, watermark_seq, …)`
  - `message(id, conversation_id, client_message_id UNIQUE, sender_device_id, seq NULL, change_seq, local_order, sender_time, received_at, state, content_type, body_json, edit_version, deleted, synced BOOLEAN)` with an index on `(conversation_id, coalesce(seq, ∞), local_order)`
  - `outbox_entry(id, client_message_id, conversation_id, state, class, envelope_blob, attempt, next_attempt_at, lease_until, link, server_owed, expires_at, error_code)`
  - `inbound_dedup(src_device, frame_or_message_id, seen_at)` with TTL ≥ max lifetime
  - `receipt(message_id, device_id, kind, at)`
  - `message_fts` (contentless FTS5 with delete triggers, 1.0)
- **1.2:** `peer_link(peer_device_id, link_key, cert_serial, cert_not_after, verified_at)`, `crl_entry`.
- **1.3:** a separate encrypted `custody.db`.
- `PendingMessage`, `CachedMessage` and `CachedConversation` are dropped at 0.5, after the release with the `engine.enabled` overlap.

---

## 12. Feature disposition table

Legend: ADOPT (milestone) · PHASE (later milestone) · RESEARCH (unscheduled, gated) · REJECT.

| # | Item (Gemini or owner ask) | Disposition | Reason | Milestone |
|---|---|---|---|---|
| 1 | "Always and everywhere, somehow" | ADOPT as the honest thesis §1 | Reliability first, then gated extra pipes | 0.4→1.4 |
| 2 | Every path behind ports/adapters | ADOPT | `ServerLink`/`SealedLink`/`InboundSource`/`CustodyTarget` | 0.5+ |
| 3 | Pick the strongest available path | ADOPT as rule-based `PathPolicy` | Gates > guarantee > latency > cost | 1.2 |
| 4 | DIRECT mode (live session) | ADOPT | `LiveSessionPort` (calls) plus Nearby-mode sessions | 0.7 / 1.1 / 1.2 |
| 5 | INDIRECT mode (store-and-forward via devices) | ADOPT (custody hierarchy) | Mailbox → own devices → sealed uplink | 0.5/1.0/1.3 |
| 6 | Connected chats across transports | ADOPT by construction | Path-independent `conversationId`/`clientMessageId` | 0.5 |
| 7 | Serverless identity, PeerID = Blake3(Ed25519) | REJECT | Owner decision 1; KVKK/5651 anchor | none |
| 8 | Kademlia DHT for prekeys and lookup | REJECT | Breaks one-time prekeys; Sybil/Eclipse (C2) | none |
| 9 | PQXDH + Double Ratchet | ADOPT via libsignal | Never hand-rolled (C1) | 1.0 |
| 10 | ML-KEM/Kyber-1024 | ADOPT as libsignal ships it | Parameters belong to the library | 1.0 |
| 11 | Decentralised MLS for thousands | REJECT; MLS with server DS = RESEARCH | RFC 9750 needs a DS (C3) | post-1.x |
| 12 | Merkle-CRDT/DAG history, Bloom anti-entropy | REJECT | Deniability, erasure (C5); seq + counters suffice | none |
| 13 | HLC ordering | REJECT as authority | Poisonable (C4); clamped advisory time only | none |
| 14 | Event sourcing | REJECT | KVKK erasure | none |
| 15 | Durable outbox + idempotent inbox | ADOPT | MOB-03/04 | 0.4 min / 0.5 |
| 16 | Per-conversation seq + change feed | ADOPT | BE-06 | 0.4 (V28) / 0.5 |
| 17 | P0–P3 priority scheduler with global Mutex | REJECT → per-link lanes, biased select | GEM-01/02/22 | 0.5 |
| 18 | ChannelMetrics fitness score | REJECT → capabilities + metrics + rules | Hard constraints are not weights | 1.2 |
| 19 | Link "levels 0–6" by medium | REJECT → path scopes | OS/ICE choose interfaces | ADR-0015 |
| 20 | Wired Ethernet / USB-C LAN | ADOPT implicitly | It is an IP interface | none |
| 21 | Wi-Fi LAN without internet | ADOPT (`LanLink`) | Cross-platform, works on HMS | 1.2 |
| 22 | mDNS/DNS-SD discovery | ADOPT | Rotating tags only | 1.2 |
| 23 | Google Nearby Connections (BLE→Wi-Fi) | ADOPT (buy) after spike | PROX-06 | 1.2 |
| 24 | Custom BLE L2CAP/GATT | REJECT (reconsider only if spike fails and demand is shown) | 36–140 kbit/s; minSdk 29 (PROX-01) | none |
| 25 | Background always-on BLE discovery | REJECT | iOS overflow area (PROX-02) | none |
| 26 | BLE multi-hop mesh "km via phones" | REJECT | No API; Bridgefy broken (PROX-03) | none |
| 27 | Wi-Fi Direct zero-click | REJECT custom; Android↔Android via Nearby upgrade | No iOS (PROX-04) | 1.2 implicit |
| 28 | Wi-Fi Aware | RESEARCH/watch | No Android↔iOS (PROX-05) | none |
| 29 | Multipeer/AWDL | RESEARCH/watch | iOS↔iOS only | none |
| 30 | 5G/4G P2P messaging over internet | REJECT for messaging | No availability gain; IP leak | none |
| 31 | WebRTC ICE + STUN + own TURN | ADOPT for calls | Proven; relay-only for non-contacts | 0.7 / 1.1 |
| 32 | P2P 1:1 calls (DTLS-SRTP E2E) | PHASE | Makes 1:1 calls E2E; gated on 0.7 telemetry | 1.1 |
| 33 | WebRTC data channels for chat | REJECT | Second reliability stack, no gain | none |
| 34 | Birthday-paradox punching | REJECT | 63%, scan-like (NAT-1) | none |
| 35 | libp2p / DCUtR / Circuit Relay v2 / supernodes | REJECT | 2 min / 128 KiB limit; KVKK/5651 (P2P-1) | none |
| 36 | IPv6 direct stage | REJECT as a stage; ICE candidate only | ~22% TR, ~1% TT Mobil | 0.7 |
| 37 | Custom QUIC / QUIC migration BLE→Wi-Fi | REJECT | RFC 9000 (QUIC-1) | none |
| 38 | Multipath QUIC (RFC 10069), QUIC NAT traversal | watch-list | OS stacks don't expose it | none |
| 39 | Opt-in NAT survey | REJECT now → LiveKit ICE telemetry (no IPs) | NAT-3 | 0.7 |
| 40 | Happy Eyeballs racing | ADOPT at link establishment | HEv3-inspired | 1.2 |
| 41 | Graceful de-escalation | ADOPT | Idle close + hysteresis | 1.2 |
| 42 | Rolling ephemeral BLE EID | ADOPT as rotating pairwise tags | Not linkable to a public key | 1.2 |
| 43 | Static QR safety numbers | ADOPT | Out-of-band verification; proximity eligibility | 1.0 |
| 44 | Ultrasonic / animated QR | REJECT | 8–16 B/s (PROX-09) | none |
| 45 | Noise or link handshake | ADOPT via vetted library (spike) | Device auth on off-server hops | 1.2 |
| 46 | Server mailbox, TTL ciphertext | ADOPT | ADR 0008/T3 | 1.0 |
| 47 | Own-device custody | PHASE | No third-party data | 1.3 |
| 48 | Single-hop sealed uplink via contact | PHASE (gated, droppable) | Smallest honest relay | 1.3 |
| 49 | Spray-and-Wait / BPv7 custody via contacts | RESEARCH (R-DTN) | DPIA, legal, demand | none |
| 50 | Epidemic routing / stranger mules | REJECT | Storage DoS; KVKK (LEG-04) | none |
| 51 | Custody table with clear target hash | REJECT | Tracking (C7) | none |
| 52 | Proof-of-Work anti-spam | REJECT → anonymous rate tokens | Punishes phones (C6) | with R-DTN/sealed sender |
| 53 | Per-chunk Reed-Solomon | REJECT | Wrong layer (GEM-07, C12) | none |
| 54 | Fountain/erasure coding across chunks | RESEARCH (RF only) | Only with field loss data | none |
| 55 | Chunking to link MTU | ADOPT, delegated to the medium | No generic fragmenter | 0.5/1.2 |
| 56 | "MU" sync word + CRC-32C frame | REJECT → AEAD-authenticated `TransportFrame` | GEM-08 | 1.2 |
| 57 | UUIDv7 id on the outer wire | REJECT; allowed only inside E2E | Leaks creation time | ADR-0022 |
| 58 | LoRa / Meshtastic | PHASE (gated, droppable, compiled out of TR) | Art. 39/63(6); duty cycle (RF-07) | 1.4 |
| 59 | KISS/TNC, software AFSK, app-level CSMA | REJECT | Hardware does PHY/MAC | none |
| 60 | `delay(200)` duty cycle | REJECT → `AirtimeLedger` | ~97% duty cycle | 1.4 |
| 61 | Amateur VHF/UHF, Baofeng, PMR446 | REJECT permanently | Licence, crypto ban (RF-08/09) | none |
| 62 | Codec2 voice over LoRa / radio | REJECT | Legal + airtime | none |
| 63 | 5G Broadcast/ROM, DVB, RTL-SDR, satellite TV | REJECT | No API, downlink-only | none |
| 64 | Satellite D2C / NTN as transport | REJECT → `constrained` flag | IP bearer; no TR service | 1.2 flag |
| 65 | Starlink / LEO broadband | ADOPT implicitly | Ordinary IP | none |
| 66 | 5G sidelink | REJECT | No app API (PROX-07) | none |
| 67 | Opus/AV1 adaptive media | ADOPT via LiveKit/WebRTC SDK | No custom media stack | 0.7 |
| 68 | SQLCipher local DB | ADOPT | MOB-01 | 0.5 |
| 69 | Plaintext FTS5 beside ciphertext | REJECT → FTS inside SQLCipher | GEM-14 | 1.0 |
| 70 | Off-grid mesh radar UI | REJECT → Nearby-mode screen showing measured peers only | No mesh exists | 1.2 |
| 71 | `tr.gov.muhabbet` package | REJECT | Government namespace (GEM-21) | none |
| 72 | CQRS | ADOPT light (facade + DB projections), no buses | KISS | 0.5 |
| 73 | Hierarchical state machine framework | REJECT → flat sealed-class State | Small machines | none |
| 74 | Transactional outbox | ADOPT on mobile; REJECT on backend (pull-by-seq) | Judge disagreement resolved §3 | 0.5 |
| 75 | Backpressure | ADOPT | Lanes, credits, Backpressure results | 0.5 |
| 76 | ViewModel layer | REJECT for this work (no-ViewModel stays; separate ADR if wanted) | ARCHITECTURE §4 | none |
| 77 | Docs: arc42, ADRs, ICD, threat model, DTN spec, data model, chaos/test plan, C4 | ADOPT (DTN spec only if R-DTN starts) | Gating artefacts | Phase 0 |
| 78 | Owner: GitHub restructuring and hooks | ADOPT after approval (ADR-0030/0031/0032) | Owner decision 3 | Phase 0 |
| 79 | Owner: foundation first | ADOPT; the engine is the early exception | Owner decision 2 | 0.4/0.5 |
| 80 | Gemini code as reference | REJECT copying any Gemini code | Reviewed defects GEM-01…22 | none |

---

## 13. Phased roadmap

All new milestones are created in GitHub only after approval (owner decision 3). **Sign-off for every device criterion is the owner's own observation (P13).**

### Phase 0: emergencies and design approval (now, no product code)
- **Scope.**
  - Security emergencies (§14 items 1–6).
  - The owner reviews this brief and the ADRs.
  - Counsel engaged with the question list (§17).
  - Threat model (STRIDE + LINDDUN) and DPIA screening written.
  - The licence decision is deferred to 1.0 entry and counsel Q-L7 is requested (D1).
- **Entry.** This brief is delivered.
- **Exit.**
  - Every `.env.prod` value is rotated: the old JWT fails, and DB, Redis and MinIO credential rotation is evidenced in the rotation log (ADR-0031 F7) (erratum E-31).
  - No PR job runs on `muhabbet-cx43` (workflow inspection plus a test PR).
  - ADRs marked Accepted, Amended or Rejected by the owner.
  - Then the GitHub restructuring (ADR-0030/0031/0032) is applied.

### 0.4: the app tells the truth (live; contains the EARLY delivery-engine start)
- **Scope (trimmed to fixes the engine will not rewrite, plus legal and security):**
  - **Backend**
    - BE-03 per-device ack authorisation.
    - BE-02 scheduled-message visibility and one transaction per release.
    - BE-07 replayed ServerAck.
    - BE-12 session close on expiry, logout, revoke and delete, and JWT out of the query string.
    - BE-13 refresh reuse detection.
    - Atomic prekey claim and fetch rate limit (BE-14 part 1).
    - Contact-lookup daily cap and block filter.
    - **Age gate** (V30, ADR-0029).
    - **EARLY ENGINE:** V28/V29 additive seq and device-scoped idempotency.
    - BE-08 ArchUnit boundary rules for messaging.
  - **Mobile**
    - MOB-01 `SessionTeardown` (wipe tables, queues and WorkManager).
    - MOB-03 persist incoming `message.new` before DELIVERED, and remove the duplicate ack in `ConversationListScreen`.
    - MOB-05 edit key includes `editedAt`.
    - MOB-04 stopgap limited to "delete `PendingMessage` only on `ServerAck(requestId)`".
    - Group receipt aggregation.
    - **EARLY ENGINE:** version catalog, one convention plugin, SQLDelight `.sqm` migrations and `verifyMigrations`, `:shared:jvmTest` in CI.
- **Entry (amended 2026-11-12).** Implementation may start on branches now: P0-5 (ADR dispositions) is met by the delegated approval. Nothing is merged to `main`, deployed or released until P0-1…P0-4 are met (rotation evidenced, runner off cx43, `muhabbet-mordecai` resolved, protected environments). P0-6 (dev→main merge, rulesets) runs in parallel and must be complete before the 0.4 release tag, because release tags exist on `main` only (ADR-0030).
- **Exit conditions carried from ROADMAP.** ROADMAP's four existing 0.4 conditions remain, as **exit** conditions (erratum E-29).
- **Exit.**
  - Emulator, two test numbers:
    - kill mid-send gives no loss or duplicate after relaunch
    - double tick only after the recipient DB holds the row
    - logout then login as another number shows no prior data
    - a revoked device's socket closes within 5 s
  - Registration below the configured minimum age (`muhabbet.age.minimum-years`, currently 18 per the published Terms) is refused (ADR-0029).
  - Testcontainers: 50 concurrent senders give gapless unique seq, and concurrent prekey claims never return the same key.
  - Backend suite measured with Docker running (base versus branch).
  - Release APK installed and launched on emulator-5554.
- **Evidence.** Test reports, the owner's emulator notes and the logcat FATAL grep.

### 0.5.0: Honest (delivery engine, wake-only push, encrypted DB)
- **Scope.**
  - `:mobile:crypto` (type gate; no producer yet), `:mobile:messaging` engine, `:mobile:crypto-testkit`.
  - `WsClient` strangled into `WsServerLink`/`WsInboundSource`.
  - `RestChangeFeedSource`, `FcmHintSource`, `SyncCoordinator`.
  - UI observes DB flows. `verifyUi` ratchet: `WsMessage`/`wsClient` in `ui/` goes to 0 outside `ui/call/` (the 3 call-screen send sites and 1 collector stay frozen in the baseline until 0.7, erratum E-30). Baseline measured when the guardrail is added.
  - ROADMAP's 0.5.0 exit question (every control the app shows either works or is not shown) remains.
  - **SQLCipher via fresh DB plus resync (ADR-0020).**
  - Backend:
    - `/changes`
    - seq and changeSeq on frames
    - hello/version and the `Unknown` frame
    - KMP-07 DTO de-duplication
    - `MessageService` split (BE-11)
    - **wake-only FCM (ADR-0019)** with the OEM fallback
  - MOB-07 client label cleanup.
  - Plaintext fallback code deleted. E2E conversations do not exist yet, so nothing regresses.
  - **Rollout:** `engine.enabled` remote flag. Release 0.5.0 ships both paths with the engine on for internal testers. 0.5.1 switches the default once emulator parity is shown. The legacy path is deleted in 0.5.2 or later.
- **Entry.** 0.4 shipped. ADR-0010/11/12/13/14/16/19/20/22/28 accepted. Docker available for Spring-config changes. A verified production Postgres backup plus a restore test (R30), before the fresh-DB migration makes the server the recovery source.
- **Exit.**
  - SimulatedLink property suite (10k seeded runs: loss, duplication, reorder, partition, process death, 24 h offline, token expiry mid-session): every message ends in sender and receiver stores exactly once, in seq order, with TestClock.
  - `:mobile:messaging` has no Compose, Koin-annotation or `WsMessage` dependencies (build-enforced).
  - The captured FCM payload contains no content, conversation id or seq (ADR-0019).
  - Forensic check after logout: no readable DB.
  - Owner on the emulator: an airplane-mode toggling script recovers with no manual refresh; notification arrives with the app killed.
- **Evidence.** CI reports, the payload capture, the owner's notes.

### 0.6.0: Reachable
- **Scope.**
  - Invite flow (existing ROADMAP).
  - Contact discovery with a discoverability setting and reader (§8.6).
  - Background sync on seq.
  - Per-device delivery rows (V32).
  - Per-device key schema (V33) and `IdentityKeyChanged`.
  - **iOS decision as a BLOCKING gate:** either KMP-01 is fixed (entry point, Xcode project, DI, iOS CI build), or, per owner decision D2, 1.0 ships Android-only and the UI and store-listing text say so (recorded in a scoping ADR). 1.0 and 1.2 cross-platform claims depend on this.
- **Entry.** 0.5 exit.
- **Exit.**
  - A message received while the app is killed is in the DB before the notification is tapped (owner, emulator).
  - Enumeration throttling integration test.
  - iOS launches on a simulator, or the Android-only text required by D2 is present in the UI and the store listing (owner observation).
  - An identity-replacement attempt returns an error.

### 0.7.0: Live (DIRECT live sessions)
- **Scope.**
  - ADR 0009 resolved, with LiveKit on a **separate host**, embedded TURN/TLS 443 and `LIVEKIT_*` in `.env.prod`.
  - The client sends `call.initiate` and publishes the mic track (#367–#373).
  - `LiveSessionPort`/`LiveKitSession`. Signalling as CONTROL through the engine.
  - Relay-only for non-contacts and an "always relay" setting. SFU disclosure.
  - ICE telemetry (no IPs).
- **Entry.** ADR 0009 accepted; host provisioned; 0.6 exit.
- **Exit.**
  - ROADMAP criterion: a call connects, carries audio both ways and appears in history, on **two physical devices on two operators, one of them TT Mobil** (owner).
  - A non-contact call uses only relay candidates (ICE stats).
  - One week of relayed share and TURN egress measured.

### 1.0.0: Encrypted
- **Scope.**
  - ADR-0017 preconditions: the licence step per D1 (after counsel Q-L7, relicense `mobile/` or convey the combined app under AGPL; contributor audit only if relicensing), counsel opinion on Play and the App Store, and the **iOS bridge spike**. If KMP-01 is not fixed at the 0.6 gate, 1.0 is Android-only (D2).
  - libsignal adapters behind `SessionCryptoPort`, PQXDH and Kyber, per-device fan-out, sender-key groups, media E2E.
  - Safety-number QR. `IdentityKeyChanged` UX.
  - `message_envelopes` mailbox (V34). Protocol v2. On-device FTS. Sender-side link previews.
  - ADR 0007 `DeviceLinkCrypto` real.
  - ADR-0021 cutover of existing plaintext history (D3: download into the encrypted local DB → notified 30-day grace → server deletion of content, media and server-built backups; opt-in encrypted backup under a user-held key).
  - External crypto review.
  - LOCAL_ONLY only if Q-O3 is decided.
- **Entry.** 0.7 shipped or explicitly deferred. Crypto reviewer engaged. Two-device Android round trip on real builds. Written counsel opinions Q-L1, Q-L4, Q-L7 and Q-L9 (Q-L9 also gates the D3 deletion schedule). Gradle dependency verification, wrapper SHA and `exclusiveContent` for the Signal repository in place (R24). Backup-key ADR accepted (ADR-0021).
- **Exit.**
  - **Claims register:** every privacy claim maps to a test or a device observation.
  - SQL assertion: no plaintext content for E2E conversations.
  - An injected seal failure leads to PENDING_SEAL or FAILED, never plaintext.
  - Contract test: no `SealedLink` ever receives a non-`SealedFrame`, and the testkit is absent from the release classpath.
  - Erasure leaves no readable data.
  - External review findings closed.
  - Two-device round trip on physical Android (and Android↔iOS if iOS is in scope) (owner).
- ADR-003 is closed as superseded.

### 1.1: Direct (new; P2P 1:1 calls)
- **Scope.** `WebRtcP2PSession` for 1:1 calls (DTLS-SRTP E2E), relay-only for non-contacts, TURN capacity sized from 0.7 data, an optional RFC 5780 probe per ASN only if the data shows a need.
- **Entry.** 1.0 exit. 0.7 telemetry shows benefit (latency or SFU egress) and TURN egress is budgeted. iOS WebRTC bridge spiked if iOS is in scope.
- **Exit.** Per-ASN setup success and latency A/B. No IP exposure to non-contacts (candidate inspection). Two-operator physical-device call (owner).
- **Kill criterion.** If the SFU satisfies the need and the E2E-calls claim is not wanted, drop it.

### 1.2: Nearby (new; LAN + Nearby Connections)
- **Scope.**
  - Device certificates and the CRL (V35, ADR-0018), with the signer **not** on a PR-job host.
  - Link keys and the vetted `LinkAuthenticator`.
  - Rotating tags.
  - `LanLink`, `NearbyLink`, `PathPolicy` and rules, Happy-Eyeballs establishment.
  - Nearby-mode screen with consent matrix and auto-off.
  - 1:1, verified, E2E only. The `serverOwed` obligation.
- **Entry gate (G-PROXIMITY), all of:**
  - 1.0 exit.
  - 0.6 iOS decision honoured.
  - **2-week device-matrix spike** (Pixel, Samsung A, Xiaomi, iPhone 12, iPhone 15+; A↔A, i↔i, A↔i; discovery time, backgrounded behaviour, text RTT, 5 MB goodput, prompts; published pass/fail/prompt matrix).
  - Handshake library chosen by spike.
  - DPIA for proximity scanning signed.
  - Counsel opinion covering E2E over BLE/Wi-Fi.
  - ADR-0031 in force.
  - HMS share in Turkey measured.
- **Exit.**
  - Two Android phones in airplane mode exchange E2E text and a photo through `LanLink` on shared non-isolating Wi-Fi, and through `NearbyLink` with Wi-Fi off.
  - On reconnect each message appears once, in seq order, on both devices and a linked companion.
  - Over-the-air capture shows no stable per-user or per-device identifiers outside TLS-to-server; the app-level fingerprint (fixed service type / serviceId) is a disclosed residual.
  - A revoked device is refused after certificate expiry or a CRL update (TestClock test plus owner observation).
- **Kill criterion.** If the spike fails on iOS, ship Android plus LAN-only cross-platform and say so.

### 1.3: Relay (new; INDIRECT beyond the mailbox; gated, droppable)
- **Scope.** `OwnDeviceCustody` first. Then the single-hop sealed uplink (`UplinkBundle`, `/uplink`, quotas, uplink key on a non-CI host).
- **Entry.**
  - 1.2 shipped.
  - Telemetry shows Nearby sessions where one side lacked internet above the owner's threshold.
  - External crypto review of the uplink sealing.
  - Written KVKK opinion on contacts transiently holding ciphertext.
  - DPIA.
- **Exit.**
  - Phone offline, tablet online: a message reaches the phone through the tablet over LAN and is E2E-acked.
  - A phone with no connectivity delivers through a contact's online phone. The mule store holds only opaque blobs (test). The server attributes the message to the true sender device.
  - Quota abuse stays bounded (test).
- **Kill criteria.** Telemetry below threshold, or any negative opinion or review.

### 1.4: Off-grid hardware (new; gated, droppable, TR-excluded until cleared)
- **Scope.** `MeshtasticLink`, `AirtimeLedger`, RF admission policy (capability- and size-based: `SizeRule`, `AirtimeLedger`, established-session-only; not the server-path `constrained` flag), certified hardware list. RF carries only messages in an established session (no pending `PreKeySignalMessage`).
- **Entry.** A written Art. 39/63(6), 2010-regulation and KET opinion. G-PROXIMITY in place.
- **Exit.** Ledger tests plus a one-hour field log within the duty cycle. Text and receipts delivered between two certified nodes. The TR flavour includes the module only after counsel signs off.
- **Kill criterion.** Negative opinion. This may never ship in TR.

### Research (unscheduled; label `research:transport`)
R-DTN contact mules, sealed sender and anonymous tokens, key transparency, message franking, OPRF contact discovery, Wi-Fi Aware, multipath QUIC, MLS with server DS. Each exits only through an ADR that either opens a milestone or closes the topic.

---

## 14. Prerequisite fixes outside the design (in priority order)

**Security emergencies (Phase 0; incident response, recommended for the owner to authorise now, independent of design approval):**
1. **CICD-06:** rotate every value ever in `.env.prod` (commit 3925f708 on a public repo: `JWT_SECRET`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `MINIO_ROOT_PASSWORD`). This invalidates old access tokens; it does not force a re-login unless the owner also revokes all refresh tokens (corrected 2026-11-12, ADR-0031 reviewer note; incident record A4). Replace the gitleaks path allowlist with a commit-scoped record. Enable secret scanning and push protection.
2. **CICD-01:** stop `pull_request` jobs on the production self-hosted runner. Disable them or route them to a non-prod runner until CI is off the box (CICD-04: fix the GitHub-hosted billing lock, which is free for public repos).
3. **CICD-03:** identify or deregister `muhabbet-mordecai`.
4. **CICD-02:** move signing, Play and deploy secrets into protected environments (`release`, `production`) restricted to `main`/tags with a reviewer.
5. **CICD-05:** SHA-pin actions and remove `trivy-action@master`.
6. **GOV-08:** remove the false SECURITY.md phone-hash claim on both branches.

**Legal (Phase 0 / 0.4):**

7. Age gate (Law 7578; ADR-0029).
8. docs/legal corrections: 5651 authority is the Siber Güvenlik Başkanlığı; add Law 7578 and 5651 Ek Md. 4; cite 5809 Art. 9(14)/39/60 as statute; remove consent as the systematic Art. 9 transfer basis.
9. #327 media erasure stays open.

**Backend correctness and security:**

10. BE-02, BE-03, BE-07, BE-12 (+BE-16), BE-13, BE-14 (atomic claim and rate limit in 0.4, per-device in 0.6), BE-06 seq, BE-08 ArchUnit before new ports, BE-11 split, BE-18 per-device delivery (0.6), BE-15 (wake-only at 0.5; plaintext removal at 1.0).

**Mobile:**

11. MOB-01 teardown (0.4), then SQLCipher (0.5).
12. MOB-03 persist-before-ack (0.4).
13. MOB-04 stopgap (0.4), then the engine (0.5).
14. MOB-05 (0.4).
15. MOB-07, MOB-10, MOB-11 (0.5).
16. SQLDelight migrations wired (0.4).

**KMP and build:**

17. KMP-05 catalog and convention plugin, KMP-03 tests in CI (0.4).
18. KMP-11 hello/version and KMP-07 DTO de-duplication (0.5).
19. **KMP-01** iOS launch or scoping ADR (blocking at 0.6).
20. KMP-13 licence (before 1.0).

**Privacy:**

21. Contact-discovery enumeration (C8/M3): cap in 0.4, discoverability and reader in 0.6.

**Governance (after approval):**

22. GOV-01 trunk on `main`, GOV-02 verification-status axis, GOV-06 agent identity.

---

## 15. Risk register

| ID | Risk | L | I | Mitigation | Owner | Trigger |
|---|---|---|---|---|---|---|
| R1 | Leaked prod secrets (public history) still live | H | Critical | Rotate all; secret scanning; commit-scoped allowlist | Owner | Now |
| R2 | Fork or branch PR code executes on the prod host (docker group = root) | M | Critical | ADR-0031; move CI; no keys on host | Owner | Any PR job on cx43 |
| R3 | Engine refactor regresses the live send path | M | H | `engine.enabled` overlap; keep `WsClient` loop and its tests; SimulatedLink suite; emulator script before default flip | Owner/agents | Any loss or duplicate in the suite or on the emulator |
| R4 | Solo bandwidth: 0.4–1.0 backlog plus new paths | H | H | Post-1.0 paths gated and droppable; the engine fixes existing bugs | Owner | Milestone slip > 1 release |
| R5 | Legal classification as *işletmeci* (5809 Art. 9(14)) imposes retention or interception duties that conflict with E2E | M | H | Counsel before 1.0; server keeps metadata; plan B documented | Counsel/Owner | Opinion received |
| R6 | Art. 39 read to cover E2E over BLE/Wi-Fi, blocking 1.2 in TR | L–M | H | Opinion request names BLE/Wi-Fi; 1.2 gate | Counsel | Opinion |
| R7 | Self-declared age gate judged insufficient (*yaş doğrulama*) | M | H | `AgeAssurancePort`; do not claim compliance; e-Devlet/NFC path | Counsel/Owner | Regulator guidance or opinion |
| R8 | AGPL on the App Store or Play deemed incompatible | M | H | Opinion first; vodozemac fallback; Android-only 1.0 | Counsel/Owner | Opinion |
| R9 | iOS still cannot launch (KMP-01), so cross-platform claims are hypothetical | H | M | Blocking 0.6 gate; scoping ADR | Owner | 0.6 entry |
| R10 | libsignal iOS bridge (Swift-only, `@objc`) harder than planned | M | M | Pre-1.0 spike; Android-only 1.0 | Owner | Spike fails |
| R11 | Wake-only push delays notifications on aggressive OEMs | M | M | High-priority data messages, foreground pull, WorkManager, battery-exemption prompt; measure on Xiaomi/Oppo | Owner | Measured p90 latency above the agreed bound |
| R12 | Hot `last_seq` row in large groups or channels | L | M | Measure; per-channel sequence sharding later | Owner | p99 send latency regression |
| R13 | Nearby iOS interop fails or HMS share is high | M | M | LanLink as the cross-platform floor; Ditto fallback; publish the matrix | Owner | Spike result |
| R14 | Device-CA or uplink key compromise on the single host | L | Critical | Offline root, short intermediate, not on a PR-job host, rotation runbook | Owner | Host incident |
| R15 | Offline revocation window (≤ 7 d) abused by a banned or under-15 user over proximity | L | M | Disclose; 1:1 verified contacts only; block lists inbound | Owner | Abuse report |
| R16 | Provisional off-server ordering visibly reorders | M | L | "Not yet synced" marker; 1:1 only | Owner | UX feedback |
| R17 | 1.0 cutover surprises users (search loss, history deletion) | M | M | Notice, grace period, backup opt-in, owner decision Q-O4 | Owner | 1.0 RC |
| R18 | Moderation under E2E (only reporter-supplied content) conflicts with 5651 | M | M | Franking research; counsel | Counsel | Takedown request on E2E content |
| R19 | Host disk at ~92% and a single runner | H | M | No linuxX64/heavy targets; LiveKit and signer off-host | Owner | `df` > 95% |
| R20 | Agents report device criteria as passed | M | H | P13/ADR-0032 "observed by" field | Owner | Any device-criterion claim without an owner note |
| R21 | KVKK: relaying devices hold third-party data | M | M | Contacts-only, sealed opaque blobs, quotas, DPIA | Counsel/Owner | 1.3 entry |
| R22 | detekt is fixed on `dev` (db9e59f) but `main` still runs the dead config, so the gate is only real after the dev→main merge | M | L | ADR-0030 merge; keep the frozen baseline ratcheting down | Owner | dev→main merge |

---

## 16. Verification and validation strategy

### 16.1 Traceability skeleton

| Req | Requirement | Design element | Method | Evidence |
|---|---|---|---|---|
| RQ-01 | No message lost after the user taps send | Outbox SM (§6.1), ADR-0013 | Test (SimulatedLink property, crash points) | CI report |
| RQ-02 | Each message shown exactly once | Idempotency (server unique key, Inbox upsert) | Test | CI report |
| RQ-03 | Double tick only after the recipient's durable store | Persist-then-ack, P4 | Test + Demonstration (emulator, two numbers) | Owner note |
| RQ-04 | Total order per conversation | Seq (ADR-0014) | Test (Testcontainers concurrency) | CI |
| RQ-05 | Lost hints are self-healing | Pull-by-seq (P6) | Test (drop 100% of hints) | CI |
| RQ-06 | No plaintext on non-server paths | `SealedFrame` type gate | Inspection (compile) + Test (arch rule, classpath check) | CI |
| RQ-07 | Push contains no content | ADR-0019 | Test + Inspection (payload capture) | Capture |
| RQ-08 | Logout or deletion leaves no readable data | ADR-0020 | Test + Demonstration (forensic) | Owner note |
| RQ-09 | Sessions die on revoke within 5 s | `SessionRevocationPort` | Test + Demonstration | CI / owner |
| RQ-10 | Under-15 cannot register | ADR-0029 | Test; Analysis (counsel) | CI / opinion |
| RQ-11 | Prekeys claimed at most once; no silent identity swap | ADR-0018 | Test (concurrency) | CI |
| RQ-12 | Contact enumeration throttled | §8.6 | Test | CI |
| RQ-13 | Server holds no E2E plaintext | ADR-0019/0021 | Test (SQL assertion) + Inspection (DB dump) | CI / review |
| RQ-14 | Calls connect with two-way audio | ADR-0023 | Demonstration (2 phones, 2 operators) | Owner |
| RQ-15 | Non-contacts never see IP | relay-only policy | Test + Inspection (ICE stats) | Capture |
| RQ-16 | Proximity works without internet between verified contacts | ADR-0024 | Demonstration (device matrix) | Owner + matrix |
| RQ-17 | No stable identifiers on air | §8.5 | Inspection (OTA capture) + Test (golden vectors) | Capture |
| RQ-18 | Revoked devices refused off-server within ≤ 7 d | ADR-0018 | Test (TestClock) + Demonstration | CI / owner |
| RQ-19 | Duty cycle respected | `AirtimeLedger` | Test + Demonstration (field log) | Log |
| RQ-20 | Every privacy claim true | Claims register | Inspection + all of the above | Register |

### 16.2 Simulated-transport harness
- `SimulatedLink` / `SimulatedSealedLink` take a seeded RNG and support:
  - latency distributions, loss, duplication, reordering
  - MTU and size caps
  - partitions on a script
  - link flapping
  - crash points (before or after the store commit, before or after ack)
  - token expiry
- `TestClock` and `SequentialIdGenerator` make every run deterministic and replayable from its seed.
- Multi-node scenarios (sender, server fake on `InMemory*` ports, recipient, companion) assert the global invariants:
  - no acked-but-unpersisted row
  - no lost outbound after enqueue
  - per-conversation seq strictly increasing
  - exactly-once display
  - no non-sealed frame on sealed links
  - custody released only on authenticated delivery
- Backend: Testcontainers (Postgres plus Redis) for seq, idempotency, acks, prekey claims and the change feed, with Docker running (CLAUDE.md rule).
- Golden vectors for every protobuf and JSON frame, run on the JVM, the backend and (when present) iOS.
- Fuzz targets for every parser.
- R-DTN only: mobility-trace simulation.

### 16.3 What only the owner's devices can verify
- App launch from the release artefact.
- Notification behaviour with the app killed, including on aggressive OEMs.
- Calls (audio, operators, CGNAT).
- Biometric and Keystore behaviour.
- The Nearby and LAN device matrix, including iOS prompts.
- Over-the-air captures.
- Battery impact.
- Meshtastic field logs.

CI cannot verify any of these, because there is no KVM on the Hetzner host. The emulator exists only on the owner's Windows PC.

---

## 17. Open questions

### For the owner
- **Q-O1.** *Deferred to 1.0 entry after Q-L7 (D1).* Relicense `mobile/` to AGPL-3.0-or-later, or convey the combined app under AGPL with MIT sources (`shared/` and `backend/` stay MIT)?
- **Q-O2.** *Decided (D2): Android-only 1.0 if KMP-01 is not fixed at the 0.6 gate.* Is iOS in scope for 1.0, or does 1.0 ship Android-only if KMP-01 or the bridge is not ready by the 0.6 gate?
- **Q-O3.** LOCAL_ONLY receipt metadata. Does the server keep delivery receipts only as long as the envelope, or keep "who talked to whom" receipts longer? (Open T3 question.)
- **Q-O4.** *Decided (D3).* At the 1.0 cutover, are existing plaintext server histories deleted after a grace period, or encrypted to a user-held backup by default?
- **Q-O5.** Kill thresholds for 1.1 Direct, 1.3 Relay (share of Nearby sessions with one side offline) and 1.4.
- **Q-O6.** *Decided (D4): accepted and disclosed.* Accept the ≤ 7-day offline revocation window and disclose it?
- **Q-O7.** Where will the device-CA intermediate and the uplink key live: a second small host, or a hardware token? They must not be on cx43 while it runs any PR job.
- **Q-O8.** ADR 0009 LiveKit host placement and budget, and the TURN egress budget for 30–50% relayed calls.
- **Q-O9.** *Approved (delegated review 2026-11-12).* Approve the governance changes (trunk-based `main`, agent identity, rulesets) after design approval.
- **Q-O10.** Is a screen-state ADR (ViewModels or Decompose component state for MOB-02) wanted separately? This design does not need it.
- **Q-O11.** Is a separate offline-mode label wanted in Turkish UI copy ("Yakındakiler", "Henüz eşitlenmedi")? Wording to be confirmed.

### For a Turkish telecom / KVKK lawyer
- **Q-L1.** Is Muhabbet an OTT service requiring BTK authorisation under Law 5809 Art. 3(ççç)/9(14)? What retention, interception-cooperation or local-representative duties follow, and do any conflict with E2E?
- **Q-L2.** Does Art. 39 / 63(6) and the 2010 coded/encrypted communications regulation apply to app-level E2E ciphertext carried over licence-exempt **BLE, Wi-Fi/LAN, Wi-Fi Direct (via Nearby)** and **LoRa (EU868, KET-conformant hardware)**? Name each separately.
- **Q-L3.** Does self-declared date of birth satisfy *yaş doğrulama* under Law 7578 / 5651 Ek Md. 4? If not, what is acceptable (e-Devlet, NFC-ID)?
- **Q-L4.** Is Muhabbet a "sosyal ağ sağlayıcı" under 5651 (now overseen by the Siber Güvenlik Başkanlığı), and at what user threshold? What takedown and reporting obligations apply to E2E content?
- **Q-L5.** KVKK roles when a user's phone transiently holds another user's ciphertext (own-device custody, contact uplink mule). Is the relaying user a processor, a controller, or neither? Is a DPIA mandatory?
- **Q-L6.** KVKK Art. 9 basis for FCM/APNs wake hints (content-free) and for LiveKit/TURN hosting. Consent is no longer to be used as the systematic basis.
- **Q-L7.** AGPL-3.0 distribution through Google Play and the Apple App Store of an app linking libsignal, where Muhabbet is a third-party redistributor of Signal's AGPL code; relicensing `mobile/` versus conveying MIT sources in an AGPL combined work (D1).
- **Q-L8.** Is ICE telemetry (candidate type, IP family, ASN; no IPs) personal data requiring notice?
- **Q-L9.** The retention obligation, if any, for server-side delivery metadata under 5651 and 5809, given LOCAL_ONLY's delete-on-delivery promise.

---

## 18. Errata and amendments (delegated review, 2026-11-12)

The derived documents recorded disagreements with this brief as reviewer notes. The delegated review
resolved them. Each erratum below **amends the body text of this brief**; where a section above still
reads otherwise, the erratum wins. Items that could not be resolved by the review are tracked in
[`open-items.md`](open-items.md).

| ID | Section | Amendment | Source |
|---|---|---|---|
| E-01 | §3 ADR-0010 Consequences | ADR-0010 sets the boundary; **ADR-0012** makes the ARCHITECTURE.md §8 amendment (three seams: `ServerLink`/`SealedLink`, `EnvelopeSealer`, `MailboxPort`). | ADR-0010 |
| E-02 | §13 0.5 Entry | ADR-0010 is added to the ADRs accepted before 0.5 (applied above). Approving scopes 0.4–0.6 accepted the 0.4 slices of ADR-0010/0013/0014/0028/0029 before 0.4 starts. | ADR-0010 |
| E-03 | §3 ADR-0012 Rationale bullet 2; §5 `ServerLink` row | Outbound `ServerLink` has `WsServerLink`, `SimulatedLink` for tests, and `RestServerLink` (0.5) for short-lived Android components (#510 notification reply, #596 push-handler ack), fed from the durable outbox and behind the Inbox commit. It is the type-level counterpart of `SealedLink`, whose real implementations start with `LanLink` at 1.2. | ADR-0012; spec R-5 |
| E-04 | §3 ADR-0014 | A gap is *watermark < a hinted seq or a listed `lastSeq`*, never non-contiguity: edits, deletes, reactions and changes hidden from the requester consume seq values. The cursor is server-driven: each `/changes` page returns `nextAfterSeq` (the highest `change_seq` scanned, visible or not, = `lastSeq` when `hasMore=false`), and the client sets `watermark := nextAfterSeq` in the same transaction as the upsert. | ADR-0014; ICD §4.1 |
| E-05 | §7 `LinkMetrics`; §3 ADR-0015 | `LinkMetrics(rttMs, lossPpm, goodputBps, costClass: FREE\|METERED, batteryClass: LOW\|HIGH, constrained: Boolean)`. `CONSTRAINED` is not a `CostClass` value. ADR-0015 lists `batteryClass`. | ADR-0015 |
| E-06 | §3 ADR-0015 Consequences; §4.1 | "§17 doc plan" reads "README.md package index (transport-capability-matrix.md)". | ADR-0015; README; SAD |
| E-07 | §5 `EnvelopeSealer` row; ADR-0028 module plan | `seal(…): SealResult` (produces `SealedFrame`, outbound only); `open(c: ReceivedCiphertext): OpenResult`. `:mobile:crypto-testkit` constructs `SealedFrame` only through the `@TestSealingApi` opt-in factory, and an architecture test fails if `@OptIn(TestSealingApi::class)` appears outside `*-testkit` modules or test source sets. | ADR-0016 |
| E-08 | §5 `SessionCryptoPort` row | Direction "out (platform-bound interface, Koin-provided)"; `safetyNumber(peer: DeviceAddress)`. A single per-user QR is a 1.0 crypto-review question ([OI-01](open-items.md#oi-01)). | ADR-0018; ADR-0028 |
| E-09 | §3 ADR-0022 | JWT out of the query string moves to **0.4** (BE-12); `hello` stays at 0.5. Pre-0.4 clients get a forced-update response (a structured error that triggers the update prompt), not a silent socket failure. | ADR-0022 |
| E-10 | §6.2 | Custody is released only on (a) authenticated DELIVERED, (b) handover acknowledged by a higher-ranked custodian (server 2xx / `ServerAccepted`), or (c) expiry (applied above). | ADR-0025 |
| E-11 | §8.2 | Custodian 3 (mule) learns the sender's identity and device and that this sender submitted something at time T. This is inherent to an authenticated link from a verified contact and is disclosed in the 1.3 DPIA and notice. | ADR-0025; threat model |
| E-12 | §13 1.4 Scope; ADR-0026 | "RF admission policy (capability- and size-based: `SizeRule`, `AirtimeLedger`, established-session-only), not the server-path `constrained` flag." RF carries only messages in an established session; a conversation becomes RF-eligible only after the recipient has replied (no pending `PreKeySignalMessage`). The RF padding bucket is defined at 1.4 entry. | ADR-0026 |
| E-13 | §16.2; ADR-0028 | `SimulatedLink` lives in `:mobile:messaging` commonTest. It moves to a test-only `:mobile:messaging-testkit` (same release-classpath check as `:mobile:crypto-testkit`) when a second module needs it (1.2 transport contract tests). | ADR-0028 |
| E-14 | §3 ADR-0029 rule 6 | Seven DPIA triggers: the brief's five, plus DT-6 "kill-criterion or other new telemetry about off-server sessions", plus DT-7 "age assurance (date of birth, identity evidence)". The 0.4 age-gate DPIA is a 0.4 exit item. | ADR-0029; DPIA screening |
| E-15 | every "DPIA (KVKK Art. 12)" | "a DPIA (best practice serving the KVKK Art. 12 security duty, and a self-imposed release gate)". Q-L5(f) asks whether one is legally required. | ADR-0029; DPIA screening |
| E-16 | §11.1 | V29 = 0.4; V31 = 0.4 (cap); V31.2 = 0.6 (discoverability); contract steps V28.1, V29.1, V31.1, V34.1 per expand/contract (CICD-09). Check the highest version on `dev` (V27 today) before writing each. | data-model §2.2 |
| E-17 | §11.1 V28 | V28 adds columns only; `SeqSweeper` backfills per conversation (resumable); `conversations.seq_ready` blocks numbering a new send before older rows; `UNIQUE(conversation_id, change_seq)`; `seq_ready` dropped in V34.1. | data-model §3.1 |
| E-18 | §11.1 V30 | `user_age_assurance(user_id PK FK users ON DELETE CASCADE, birth_date, level, assured_at)`, mapped only by the age adapter (V27 row-rebuild precedent). | data-model §3.3 |
| E-19 | §11.2 | The `.sq` files stay the schema; today's schema is snapshotted as `1.db` from the shipped APK; `verifyMigrations` runs without `deriveSchemaFromMigrations`; the first migration is `1.sqm` (1→2). | data-model §5.2 |
| E-20 | §11.2 | Message index `(conversation_id, coalesce(seq, anchor_seq), local_order)`; `anchor_seq` = the last seq known to the creator. | data-model §5.3 |
| E-21 | §11.2 | 0.5 uses `receipt_watermark`; the per-message `receipt` table arrives at 1.2 with off-server receipts for seq-less rows. | data-model §5.6 |
| E-22 | §11.2 | Mobile `conversation` gains `delivered_up_to_seq` (client mirror of the server cursor); `outbox_entry` gains `cancel_requested`. | spec R-4, R-8 |
| E-23 | §5 Clock/IdGenerator row | `kotlin.time.Clock` / `kotlin.time.Instant` (`kotlinx.datetime.Clock` is deprecated in the pinned 0.8.0). | spec R-10 |
| E-24 | §6.1 | (1) Any non-terminal state except `SERVER_ACCEPTED` → `EXPIRED` on lifetime; `SERVER_ACCEPTED` is purged at lifetime (the server holds custody). (2) `RetryDelivery` only from `FAILED_PERMANENT`; for `EXPIRED` the UI offers "resend as new" (new `clientMessageId`). (3) `CONVERSATION_INFLIGHT_WINDOW = 1`; the head is held across `IN_FLIGHT` and `RETRY_WAIT` (I4); correctness before throughput, pipelining only on measured latency evidence. (4) A user delete of a submitted entry (`attempt > 0`) follows T32: withdrawn from everyone if the server accepted it. | spec R-2, R-7, R-8; outbox diagram |
| E-25 | §10 | `security.key_changed` (existing) extended with `deviceId` (applied above). | ICD §3.11 |
| E-26 | §10 | REST `GET heads` (ICD §4.3) and `GET receipts` (ICD §4.4) are added at 0.5 to satisfy P6. | ICD |
| E-27 | §7 BULK footnote | "*unless `ConstrainedRule` has deferred BULK on the server link and LAN/PROXIMITY is already UP". | path-scopes diagram |
| E-28 | §9 custodian 1 | "Server mailbox (`messages` today; `/changes` arrives in 0.5, ADR-0014; ciphertext from 1.0, ADR-0021)." | Gemini review |
| E-29 | §13 0.4 | ROADMAP's four 0.4 conditions are exit conditions (applied above). | roadmap RN-R1 |
| E-30 | §13 0.5 Exit | `ui/` count = 0 outside `ui/call/` at 0.5; the call screens close at 0.7 through `LiveSessionCoordinator` (RQ-24) (applied above). | roadmap RN-R10 |
| E-31 | §13 Phase 0 Exit | Rotation evidence for DB, Redis and MinIO credentials (ADR-0031 F7) (applied above). | roadmap RN-R3 |
| E-32 | §14 item 1; ADR-0031 | "Confirm and keep secret scanning and push protection (already enabled 2026-11-12); enable validity checks if offered." Rotate after the runner is off cx43 (item 2), or repeat the rotation after it. Rotating `JWT_SECRET` invalidates access tokens only; clients silently refresh (whether to also revoke all refresh tokens is [OI-09](open-items.md#oi-09)). | github-governance R-1; ADR-0031 |
| E-33 | §14 item 2 | Remove the two required checks from classic protection together with the runner change; re-add them in `main-checks` once PR CI is off-host. The two-ruleset split is needed for "admin bypass does not skip checks". | github-governance R-4; ADR-0030 |
| E-34 | §14 item 6 (Phase 0) | Also correct SECURITY.md:57, CLAUDE.md:4 and CLAUDE.md:188 (soft delete presented as KVKK erasure), and drop the "full verticals with mobile UI" and Phase 4 "DONE" claims for chat folders, pin-in-chat, backup and bots. Wiring or deleting those endpoints is [OI-26](open-items.md#oi-26). | project review |
| E-35 | §13 0.4 / 0.5 Backend | 0.4: "BE-08 ArchUnit boundary rules for messaging, including BE-09". 0.5: "BE-10 delete the dead event layer (`MessagingEvents.kt`, unused `ApplicationEventPublisher` in `EncryptionService`)". | project review |
| E-36 | §6.4; §13 0.4 | The 0.4 group receipt aggregation reads per-user `message_delivery_status` rows; it switches to per-device cursors at 0.6 (V32/BE-18). | project review |
| E-37 | §15 | Add R23 (co-tenant stores), R24 (dependency verification; gate: 1.0 entry), R25 (account takeover; owner question at 1.0), R29 (keystore), R30 (production backup; gate: 0.5 entry), R37 (PR CI disabled; handled by the ADR-0030 transition order), R38 (age rule; the 0.4 age gate) and R44 (D3 history loss). See `risk-register.md`. | risk register RN-K2 |
| E-38 | §16.3 | Once CI runs off cx43 on a runner with KVM (availability to verify), an automated emulator smoke (install, `am start`, no `FATAL`, screenshot) gates every release candidate. It adds to the owner's P13 observation and does not replace it. | vv-plan RN-V1; project review |
| E-39 | §3 ADR-0013 §4; §6.4 | Ack split: tick-affecting receipts only from non-sender members' devices; watermark/custody acks from any registered device of any member (applied above). | ADR-0013; ICD §3.8 |
| E-40 | §3 ADR-0013 §3 | Server idempotency follows ICD §3.6 A-1 (select → allocate → insert; roll back and reselect on a unique violation; no `ON CONFLICT`). `messages.id = client_message_id` is kept beside `UNIQUE(sender_device_id, client_message_id)` for 0.4–1.0; the brief-literal alternative is [OI-10](open-items.md#oi-10). | ADR-0013; ICD; data-model |
| E-41 | §8; §15 | TB-9 (co-tenant `shared-postgres`/`shared-redis`/`shared-minio`) is in scope; the 1.0 external security review records the current isolation. | threat model RN-3 |
| E-42 | §2 P9; §16 RQ-17 | "No stable per-user or per-device identifiers outside TLS-to-server"; app presence (fixed DNS-SD service type and Nearby `serviceId`) is a disclosed residual. | ADR-0024; threat model RN-2 |
| E-43 | §13 1.2 Scope (gated) | 1.2 scope and DPIA include the telemetry "Nearby sessions where one side lacked internet" (screening S-09), which 1.3 entry needs. | roadmap RN-R7 |
| E-44 | §3 ADR-0033 | Includes amendments G3 (`APPROVAL.md` never agent-editable) and G5 (`.claude/active-scope`), accepted under D6. | ADR-0033 |
| E-45 | §3 ADR-0013; §6.4 | DELIVERED and READ `uptoSeq` = `conversation.complete_through_seq`; enqueue after commit; never a pushed or mid-pull value (applied above). | core-engine review |
| E-46 | §3 ADR-0019 | The FCM/APNs data payload is `{t:"wake", v:1}`, optionally with a random per-device hint token mapped server-side. No `conversationId` or seq goes to the push provider. On wake the client calls `GET heads`, then `/changes` per advanced conversation. | ADR-0019 |
| E-47 | §3 ADR-0018 | Prekey relationship check: "target has requester as a contact, or they share a conversation the target created or joined (not merely received an invite to)"; rule 2 (per-pair limit plus exhaustion alert, 0.4) is the primary M1 control. Device-set freshness: the server rejects an envelope set that does not address every active device of each recipient (`MSG_DEVICE_SET_STALE`). | ADR-0018 |
| E-48 | §3 ADR-0021 | D3 applied: the readable server copy includes `messages.content`, search index rows, cached previews, MinIO media objects and thumbnails, and server-built backup archives; a conversation is labelled E2E only when none remain. The opt-in backup key is client-generated and high-entropy, and the backup format has its own ADR before 1.0 entry. | crypto-identity review |
| E-49 | §13 0.4 Mobile | MOB-15 build-type-scoped `BASE_URL` (debug may point at a local or staging backend; release stays pinned to production with cleartext off) is added to 0.4 as a test enabler. | vv-plan RN-V2 |
| E-50 | §3 ADR-0030 Decision 4 | Order: (a) ADR-0031 runner change; (b) PR CI green on non-production runners; (c) only then add `Build & Test` and `Android Build` to `main-checks`. | ADR-0030 |
