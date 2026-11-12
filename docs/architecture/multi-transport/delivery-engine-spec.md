# Muhabbet delivery engine — implementer specification

> **Status: Proposed — pending owner approval.** No code may be written for any scope described here
> until the owner records approval for that scope in [`APPROVAL.md`](APPROVAL.md). This document is
> derived from, and must agree with, the [decision brief](decision-brief-2026-11-12.md) (the single
> source of truth), in particular §5 (ports), §6 (engine), §7 (path selection), §11.2 (mobile schema)
> and §16.2 (harness). Where this spec and the brief disagree, the brief wins and the disagreement is
> a defect here; known gaps are recorded as `> **Reviewer note (open):**` callouts.

| | |
|---|---|
| **Date** | 2026-11-12 |
| **Base** | `dev` @ 2434324 (branch `claude/muhabbet-p2p-architecture-wdan5r`) |
| **Decides nothing new** | Every decision is in ADR-0011 … ADR-0016, ADR-0020, ADR-0022, ADR-0024, ADR-0025, ADR-0028. This spec fixes the *implementer-level* detail those ADRs leave open: full transition tables, invariants, timers, error mapping, harness, package layout and the migration path from today's code. Anything marked **(spec)** below is such a detail and is open to change without an ADR; anything marked **(brief)** is fixed by the brief. |
| **Milestones** | 0.4 (stopgap only, legacy code) · **0.5 (engine)** · 0.7 (call signalling) · 1.0 (sealing) · 1.2 (sealed links, `PathPolicy`, racing) · 1.3 (custody) |
| **Diagrams (planned)** | `docs/diagrams/outbox-state-machine.mmd`, `link-state-machine.mmd`, `custody-state-machine.mmd`, `seq-send-receive.mmd`, `seq-nearby-reconcile.mmd` |
| **Conformance words** | MUST / MUST NOT / SHOULD are used in the RFC 2119 sense. |

---

## 1. Scope and non-goals

**In scope:** the `:mobile:messaging` engine (outbox, inbox, sync, receipts, scheduling, links as seen
from the engine), its ports, its persistence contract with SQLDelight, its error model, the test
harness, and the strangler migration of today's `WsClient` / `PendingMessage` code.

**Not in scope:** wire encodings (→ `icd-wire-protocol.md`), Flyway and SQL DDL (→ `data-model.md`),
crypto internals (→ ADR-0017/0018, `:mobile:crypto`), call media (→ ADR 0009/0023), UI design.

**Non-goals (brief §1.2, §6.7, ADR-0012):** no generic fragmentation layer, no FEC, no global
priority dispatcher or Mutex (GEM-01/02, GEM MISS-01), no HLC, no per-message hedging across paths,
no `LinkSelector`/`PathPolicy` before 1.2, no `CustodyTarget` routing before 1.3, no copied Gemini
code (brief §12 #80).

---

## 2. Domain vocabulary (Kotlin sketches, `:mobile:messaging` `domain/`)

Signatures only. Value classes wrap every id; `ByteArray` fields are wrapped in value classes with
content equality (GEM-16, brief §10 rules).

```kotlin
@JvmInline value class EntryId(val v: String)            // = clientMessageId of the command
@JvmInline value class ClientMessageId(val v: String)    // UUIDv7 from IdGenerator (brief §6, ADR-0014)
@JvmInline value class ConversationId(val v: String)
@JvmInline value class DeviceId(val v: String)
@JvmInline value class Seq(val v: Long)                  // server per-conversation sequence
@JvmInline value class ChangeSeq(val v: Long)

enum class TrafficClass { CONTROL, INTERACTIVE, BULK, EPHEMERAL }          // brief §6.5
enum class StorageMode { CLOUD, BYO, LOCAL_ONLY }                          // ADR 0008 / 0021
enum class CustodianKind { SERVER_MAILBOX, PEER_DEVICE, OWN_DEVICE, CONTACT_UPLINK } // ADR-0025

sealed interface OutboxState {                                             // brief §6.1
    data object Preparing : OutboxState
    data class PendingSeal(val reason: SealWait) : OutboxState             // SealWait: NO_SESSION | UNTRUSTED_IDENTITY | SEAL_RETRY (spec)
    data object Queued : OutboxState                                       // serverOnly ⇔ entry.serverOwed (see §4.1)
    data class InFlight(val link: LinkRef, val attempt: Int, val deadline: Instant) : OutboxState
    data class RetryWait(val nextAttemptAt: Instant) : OutboxState
    data class HopAcked(val custodian: CustodianKind, val serverOwed: Boolean) : OutboxState
    data class ServerAccepted(val seq: Seq) : OutboxState
    data class FailedPermanent(val code: String) : OutboxState            // terminal (user may retry)
    data object Expired : OutboxState                                      // terminal
    data object Cancelled : OutboxState                                    // terminal
}

data class OutboxEntry(
    val id: EntryId, val conversationId: ConversationId, val cls: TrafficClass,
    val state: OutboxState, val envelope: EnvelopeBlob,   // full versioned OutgoingEnvelope (ADR-0013); sealed bytes from 1.0
    val attempt: Int, val nextAttemptAt: Instant?, val leaseUntil: Instant?,
    val serverOwed: Boolean, val expiresAt: Instant, val errorCode: String?,
)

sealed interface InboundEvent {                                            // brief §5 InboundSource
    data class Message(val row: InboundMessage, val source: SourceRef) : InboundEvent
    data class Change(val change: MessageChange, val source: SourceRef) : InboundEvent
    data class Hint(val conv: ConversationId, val seq: Seq) : InboundEvent
    data class Receipt(val conv: ConversationId, val device: DeviceId, val uptoSeq: Seq, val kind: AckKind) : InboundEvent
    data class KeyChanged(val user: String, val device: DeviceId) : InboundEvent
    data class CrlDelta(val delta: SignedCrlDelta) : InboundEvent          // 1.2
}

sealed interface SubmitResult {                                            // brief §5, verbatim shape
    data class ServerAccepted(val seq: Long, val ts: Instant, val replay: Boolean) : SubmitResult
    data class HopAcked(val custodian: Custodian) : SubmitResult
    data class Retry(val after: Duration?) : SubmitResult
    data class Backpressure(val after: Duration) : SubmitResult
    data class Rejected(val code: String) : SubmitResult
}
```

`ServerFrame` is an engine type (not `WsMessage`). `ProtocolCodec` (in `:mobile:messaging`) maps
`OutgoingEnvelope → ServerFrame`; `WsServerLink` (in `:mobile:composeApp`) maps
`ServerFrame ↔ WsMessage`. This keeps `WsMessage` out of `:mobile:messaging` (ADR-0012, ADR-0028).

---

## 3. Invariants

Every invariant has a checker in the harness (§13) that runs after every simulated step.

| # | Invariant | Source | Checked by |
|---|---|---|---|
| I1 | An outbox entry and its optimistic `message` row are written in **one** transaction; neither exists without the other until a terminal purge. | ADR-0013 | Crash point between the two writes is impossible by construction; checker asserts row ⇔ entry. |
| I2 | An entry is deleted only (a) from a terminal state by user action or retention, or (b) by purge after `SERVER_ACCEPTED` settles, or (c) by purge after `HOP_ACKED(serverOwed=false)` + E2E delivered. **Never** because of an attempt count. | ADR-0013, MOB-04 | Checker: no entry disappears from a non-terminal state. |
| I3 | Every state change is a compare-and-set on `(id, fromState)`. A failed CAS is a no-op, never an error. | brief §6.1 | Concurrency test: two actors race the same transition; exactly one wins. |
| I4 | For one conversation, at most `CONVERSATION_INFLIGHT_WINDOW` INTERACTIVE entries are `IN_FLIGHT`, and dispatch follows `local_order`. | brief §6.6 (spec window) | Checker over the in-flight set. |
| I5 | No byte of an E2E-conversation entry reaches any link unsealed; `SealedLink.send` only accepts `SealedFrame`. | P2, ADR-0016 | Compile-time + arch test + harness assertion on every `SimulatedSealedLink` frame. |
| I6 | No DELIVERED receipt (L2) and no custody/hop ack is emitted before the receiving store has committed. | P4 | Crash points `AFTER_COMMIT_BEFORE_ACK` / `BEFORE_COMMIT`. |
| I7 | `watermark_seq` never decreases and advances only by rule W1/W2 (§6.3). | ADR-0014 | Checker. |
| I8 | `(conversation_id, client_message_id)` is unique in `message`; the user sees each message once. | RQ-02 | Checker + DB constraint. |
| I9 | A row's `seq` is set at most once and never changes. Display order is `seq` when known (§6.5). | ADR-0014 | Checker. |
| I10 | Domain code reads time only from the injected `Clock` and ids only from `IdGenerator`. | P10 | Konsist/detekt rule (brief P10). |
| I11 | EPHEMERAL frames are never written to any store and never retried. | brief §6.5 | Checker: no EPHEMERAL in `outbox_entry`. |
| I12 | A CLOUD/BYO entry that left over a non-server path ends in `SERVER_ACCEPTED` or `EXPIRED`, never in a purge. | ADR-0010 `serverOwed` | Checker on terminal set. |
| I13 | Inbound events from a blocked sender are dropped before any row is written, on every source. | ADR-0029 rule 4 | Per-source block-list test. |
| I14 | No unbounded buffer exists in the engine (`Channel.UNLIMITED`, unbounded `SharedFlow` replay, unbounded lists). | brief §6.9 | Konsist rule + review. |
| I15 | No engine-wide lock. Conversations and links progress independently. | brief §6.6, GEM MISS-01 | Test: a stalled conversation/link does not delay another. |
| I16 | Custody of a bundle is released only on an authenticated DELIVERED, a server 2xx/ack, or expiry. | ADR-0025 | 1.3 harness. |
| I17 | After `SessionTeardown` returns `Verified`, every store is empty, no link is open, no engine coroutine is alive and no background job is scheduled. | ADR-0020, MOB-01, MISS-02 (mobile) | Teardown test on fakes and on the SQLDelight adapter. |

---

## 4. `OutboxEntry` state machine (State pattern, CAS)

### 4.1 State semantics and persistence

| State | Persisted as (`outbox_entry`) | Dispatchable? | Lease | Message-row effect |
|---|---|---|---|---|
| `PREPARING` | `state='PREPARING'` | no (media worker owns it) | upload lease | row visible, upload progress |
| `PENDING_SEAL(reason)` | `state='PENDING_SEAL'`, `error_code=reason` | no | — | "waiting for secure session" (ADR-0016) |
| `QUEUED` | `state='QUEUED'`; `server_owed=true` here means **server-only** (no off-server dispatch) | yes when due | — | clock |
| `IN_FLIGHT(link, attempt, deadline)` | `state='IN_FLIGHT'`, `link`, `attempt`, `lease_until=deadline` | no | `lease_until` | clock |
| `RETRY_WAIT(nextAttemptAt)` | `state='RETRY_WAIT'`, `next_attempt_at` | when `now ≥ next_attempt_at` | — | clock |
| `HOP_ACKED(custodian, serverOwed)` | `state='HOP_ACKED'`, `link`, `server_owed` | only to the server if `serverOwed` | — | per §5 |
| `SERVER_ACCEPTED(seq)` | `state='SERVER_ACCEPTED'` (seq lives on the message row) | no | — | one tick, row gets `seq` in the same tx |
| `FAILED_PERMANENT(code)` | `state='FAILED'`, `error_code` | no | — | "tap to retry" |
| `EXPIRED` | `state='EXPIRED'` | no | — | "not sent" |
| `CANCELLED` | row and entry deleted | — | — | row removed |

"Terminal" means **no automatic transition**. `FAILED_PERMANENT` can still be left by the user
(`RetryDelivery`), as the brief's table states.

### 4.2 Events

| Event | Raised by | Payload |
|---|---|---|
| `Send` / `SendMedia` | `SendMessage` use case | `OutgoingContent` |
| `UploadDone` / `UploadFailed` | media worker | `blobRef` / `(retryable, code)` |
| `SealOk` / `SealFailed` | `EnvelopeSealer` (1.0) | sealed bytes / `NoSession \| UntrustedIdentity \| Failed(retryable)` |
| `SessionReady` / `IdentityApproved` | `SessionCryptoPort`, user on the safety-number interstitial | conversation |
| `Dispatch(link)` | scheduler (§9) | `LinkRef` |
| `ServerAccepted(seq, ts, replay)` | `SubmitResult.ServerAccepted` | |
| `HopAcked(custodian)` | `SubmitResult.HopAcked` | |
| `Retry(after?)`, `Backpressure(after)` | `SubmitResult` | |
| `Timeout` | lease expiry (`now > lease_until`) | |
| `LinkLost` | link state leaves `Ready`/`UP` while the entry is in flight | |
| `Rejected(code)` | `SubmitResult.Rejected` | |
| `ServerLinkUp` | server link enters `Ready` | |
| `SeqObserved(seq)` | Inbox sees the entry's own `clientMessageId` with a seq (pull or push) | |
| `E2EDelivered` | sealed DELIVERED receipt from every recipient device | |
| `ReceiptsSettled` | ReceiptService: every recipient user has ≥ 1 L2, or `ACCEPTED_RETENTION` elapsed | |
| `LifetimeExceeded` | `now > expires_at` | |
| `UserDelete`, `RetryDelivery` | use cases | |
| `Recover` | engine start | |
| `EligibleLinkUp` | a link the entry may use enters `Ready`/`UP` | |

### 4.3 Transition table

`(brief)` rows restate brief §6.1; `(spec)` rows fill gaps the brief leaves implicit. "tx" = the
side effects are committed in the same SQLDelight transaction as the CAS.

| # | From | Event | Guard | To | Side effects (tx unless noted) | |
|---|---|---|---|---|---|---|
| T01 | (new) | `Send` (non-media) | conversation not E2E, or sealing not required | `QUEUED` | insert optimistic row + entry; `expires_at = now + lifetime(cls)` | brief |
| T02 | (new) | `Send` (non-media) | conversation E2E (1.0+) | `PENDING_SEAL(NO_SESSION)` → immediately attempt seal (T07/T08) | insert row + entry; request seal (outside tx) | spec |
| T03 | (new) | `SendMedia` | — | `PREPARING` | insert row + entry; enqueue media job (outside tx). Does **not** block the conversation FIFO | brief |
| T04 | `PREPARING` | `UploadDone(blobRef)` | not E2E | `QUEUED` | write `blobRef` into envelope | brief |
| T05 | `PREPARING` | `UploadDone(blobRef)` | E2E | `PENDING_SEAL(NO_SESSION)` → seal | write `blobRef` | brief |
| T06 | `PREPARING` | `UploadFailed(retryable=true)` | — | `PREPARING` | backoff `next_attempt_at` (resumable, 1 MiB ranges) | brief |
| T06b | `PREPARING` | `UploadFailed(retryable=false)` | — | `FAILED_PERMANENT(code)` | — | spec |
| T07 | `PENDING_SEAL` / `QUEUED`* | `SealOk(bytes)` | — | `QUEUED` | replace envelope with sealed bytes (sealed **once**; retries resend the same ciphertext) | spec |
| T08 | `PENDING_SEAL` / `QUEUED`* | `SealFailed(NoSession)` | — | `PENDING_SEAL(NO_SESSION)` | trigger prekey fetch (outside tx). **Never** plaintext fallback (C9) | brief |
| T09 | `PENDING_SEAL` | `SessionReady` | — | (re-seal) → T07 | — | brief |
| T10 | `PENDING_SEAL` / `QUEUED`* | `SealFailed(UntrustedIdentity)` | — | `PENDING_SEAL(UNTRUSTED_IDENTITY)` | surface interstitial (brief §8.7) | spec |
| T11 | `PENDING_SEAL(UNTRUSTED_IDENTITY)` | `IdentityApproved` | — | (re-seal) → T07 | — | spec |
| T12 | `PENDING_SEAL` / `QUEUED`* | `SealFailed(Failed(retryable))` | — | `PENDING_SEAL(SEAL_RETRY)` | backoff | spec |
| T13 | `PENDING_SEAL` / `QUEUED`* | `SealFailed(Failed(non-retryable))` | — | `FAILED_PERMANENT("SEAL_FAILED")` | — | spec (1.0 exit: "PENDING_SEAL or FAILED, never plaintext") |
| T14 | `QUEUED` | `Dispatch(link)` | due; link Ready/UP; lane has capacity; window free (I4); if `server_owed` then link is the server link | `IN_FLIGHT(link, attempt+1, now+ackTimeout(link))` | `lease_until = deadline` | brief |
| T15 | `RETRY_WAIT` | `Dispatch(link)` | `now ≥ next_attempt_at` and T14 guards | `IN_FLIGHT(...)` | as T14 | spec (RETRY_WAIT is due-able; brief implies) |
| T16 | `IN_FLIGHT` | `ServerAccepted(seq, ts, replay)` | — | `SERVER_ACCEPTED(seq)` | `message.seq = seq`, `synced = true`, `server_ts`; replay is not an error (BE-07) | brief |
| T17 | `IN_FLIGHT` | `HopAcked(peer / own device / mule)` | — | `HOP_ACKED(custodian, serverOwed = mode ≠ LOCAL_ONLY)` | message row: per §5 | brief |
| T18 | `IN_FLIGHT` | `ServerAccepted` from the **server mailbox** for a LOCAL_ONLY conversation | mode = LOCAL_ONLY | `HOP_ACKED(SERVER_MAILBOX, serverOwed=false)` | — | spec, see reviewer note R-1 |
| T19 | `HOP_ACKED(serverOwed=true)` | `ServerLinkUp` | — | `QUEUED` (server-only) | `server_owed` stays true; recipient dedups (I8) | brief |
| T20 | `HOP_ACKED(serverOwed=true)` / `QUEUED`(server-only) / `RETRY_WAIT`(server-only) | `SeqObserved(seq)` | a custodian already uploaded it | `SERVER_ACCEPTED(seq)` | as T16; avoids a redundant upload | spec |
| T21 | `HOP_ACKED(serverOwed=false)` | `E2EDelivered` | LOCAL_ONLY | (purge) | delete entry; row keeps two ticks | brief |
| T22 | `IN_FLIGHT` | `Timeout` / `LinkLost` / `Retry(after)` | — | `RETRY_WAIT(now + backoff(attempt, after))` | full-jitter backoff 1 s → 30 s on the injected Clock | brief |
| T23 | `IN_FLIGHT` | `Backpressure(after)` | — | `RETRY_WAIT(now + after)` | `attempt` **not** incremented (decrement the T14 increment) | spec |
| T24 | `IN_FLIGHT` | `Rejected(code)` with `code` permanent (§11) | — | `FAILED_PERMANENT(code)` | bubble shows retry | brief |
| T25 | `IN_FLIGHT` | `Rejected(code)` with `code` transient/link-level (§11) | — | `QUEUED` / `RETRY_WAIT` per §11 | — | spec |
| T26 | `FAILED_PERMANENT` / `EXPIRED` | `RetryDelivery` | message not deleted | `QUEUED` (or `PENDING_SEAL` if E2E and unsealed) | `attempt = 0`, new `expires_at` | brief (FAILED) / spec (EXPIRED) |
| T27 | any non-terminal except `SERVER_ACCEPTED` | `LifetimeExceeded` | — | `EXPIRED` | default 30 d; call signalling 60 s; CONTROL 60 s | brief (see R-2) |
| T28 | `SERVER_ACCEPTED` | `ReceiptsSettled` or `LifetimeExceeded` | — | (purge) | delete entry only; row and receipts stay | brief ("purged after receipts settle") |
| T29 | `QUEUED` / `RETRY_WAIT` / `PENDING_SEAL` | `UserDelete` | — | `CANCELLED` | delete row + entry | brief |
| T29b | `PREPARING` | `UserDelete` | — | `CANCELLED` | cancel media job, delete partial blob ref | spec |
| T30 | `IN_FLIGHT` at process start | `Recover` | — | `QUEUED` | clear lease. Safe: server idempotency (V29) | brief |
| T31 | `RETRY_WAIT` | `EligibleLinkUp` | — | `RETRY_WAIT(now)` | backoff reset to now | brief |
| T32 | `IN_FLIGHT` | `UserDelete` | — | (no transition) | recorded as a follow-up `Delete` command entry after acceptance (delete-for-everyone) | spec |

\* `QUEUED` appears as a source of seal events only when a recipient device-list change forces a
re-seal (see §11, `DEVICE_LIST_STALE`, and reviewer note R-3).

### 4.4 Ignored and illegal events

| Event in state | Handling |
|---|---|
| Any result event (`ServerAccepted`, `HopAcked`, `Retry`, `Rejected`, `Timeout`) for an entry **not** `IN_FLIGHT`, or `IN_FLIGHT` on a different `link`/`attempt` | **Ignored** (late result from a superseded attempt). CAS fails, logged at debug. A late `ServerAccepted` for an entry already `SERVER_ACCEPTED` with the same seq is a no-op; with a different seq it is a protocol violation → error report, keep the first seq (I9). |
| `Dispatch` for an entry not due or not `QUEUED`/`RETRY_WAIT` | Programming error → `check()` failure in debug, ignored in release. |
| Any event after `CANCELLED` (row gone) | Ignored. |
| `SeqObserved` in `SERVER_ACCEPTED` | No-op if seq equal (I9). |

### 4.5 Timers and retry policy

| Timer | Value | Constant | Source |
|---|---|---|---|
| Retry backoff | `random(0, min(30 s, 1 s · 2^attempt))` (full jitter); `Retry(after)` raises the floor to `after` | `RETRY_BASE_MS = 1_000`, `RETRY_CAP_MS = 30_000` | brief |
| Ack deadline (server) | 15 s from dispatch | `SERVER_ACK_TIMEOUT_MS = 15_000` | spec (proposed; tune from telemetry) |
| Ack deadline (sealed link) | `max(5 s, 4 × link RTT EWMA)` | `SEALED_ACK_TIMEOUT_FLOOR_MS = 5_000` | spec |
| Lifetime | INTERACTIVE/BULK 30 d; call signalling 60 s; CONTROL 60 s | `DEFAULT_LIFETIME = 30.days`, `CALL_SIGNAL_LIFETIME = 60.seconds`, `CONTROL_LIFETIME = 60.seconds` | brief |
| Accepted retention | 7 d after `SERVER_ACCEPTED` if receipts never settle | `ACCEPTED_RETENTION = 7.days` | spec |
| Attempt cap | **none**; only lifetime ends retries (I2) | — | ADR-0013 (rejects MOB-04's `MAX_RETRY_COUNT` drop) |

---

## 5. Tick derivation (one meaning on every path, ADR-0013)

Ticks are a **pure function** of the row, the entry and the receipt rows. No code path sets a tick
directly.

```kotlin
fun tickOf(row: MessageRow, entry: OutboxEntry?, agg: ReceiptAggregate): Tick
```

| Condition (first match wins) | Tick | Secondary label |
|---|---|---|
| entry `FAILED_PERMANENT` / `EXPIRED` | failed | "tap to retry" / "not sent" |
| entry `PENDING_SEAL(NO_SESSION / SEAL_RETRY)` | clock | "waiting for secure session" |
| entry `PENDING_SEAL(UNTRUSTED_IDENTITY)` | clock | "safety number changed" |
| `agg.read` (server-aggregated, `readReceiptsEnabled` downgrade already applied server-side, #377) | read | — |
| `agg.deliveredAllUsers` (every recipient user has ≥ 1 device at L2) | two ticks | if `row.seq == null`: "not yet synced" |
| `row.seq != null` | one tick | — |
| entry `HOP_ACKED(OWN_DEVICE / CONTACT_UPLINK)` | clock | "handed to <device/contact>" |
| otherwise | clock | — |

Rules: there is **no** "relayed"/"mesh" tick (ADR-0013). The client never promotes to READ from one
member's status in a group (MISS-01 mobile verifier, brief §6.4). Aggregation uses the server's
per-device rows (BE-18, V32 at 0.6); before V32 the server's per-user status is used as today.

---

## 6. Inbox, watermark and sync

### 6.1 Inbound pipeline (every `InboundSource`)

Order is fixed (ADR-0024 §7 for sealed sources; the same order is used for server sources):

| Step | Server sources (`WsInboundSource`, `RestChangeFeedSource`, `FcmHintSource`) | Sealed sources (1.2+) |
|---|---|---|
| 1. Dedup | `inbound_dedup(src_device, message_id/change_seq)`; or `change_seq ≤ row.change_seq` | `inbound_dedup(src_device, frameId)` |
| 2. Block list | sender user blocked → drop (I13) | sender from the authenticated link certificate → drop |
| 3. Open | pre-1.0 none; 1.0+ `EnvelopeSealer.open` per device envelope | `EnvelopeSealer.open(SealedFrame)` |
| 4. Upsert (one tx) | row upsert (insert, or update iff incoming `change_seq` newer), receipt rows, watermark (§6.3), `inbound_dedup` insert | row upsert by `client_message_id` (`seq = null`, `synced = false`), `inbound_dedup` insert |
| 5. After commit | enqueue L2 DELIVERED (ReceiptService) for new message rows; raise `SeqObserved` for own messages; `Hint` → SyncCoordinator | send sealed DELIVERED to the peer; L2 also to the server when reachable |

`Hint` events carry no content and only feed `SyncCoordinator`. Open failures (`OpenResult` not
`Opened`) are written as a placeholder row ("message could not be decrypted") and **acked** as
DELIVERED only if the envelope is recorded; a missing session triggers a session-reset control
message (1.0, ICD). Edits merge by `(messageId, editVersion)`; `editedAt` is part of the dedup key
(MOB-05).

### 6.2 Merging an off-server row with its server copy

When a row first delivered off-server (`seq = null`) arrives from the server: match by
`client_message_id`, set `seq`, `change_seq`, `synced = true`, keep the local primary key and
`received_at`; never create a second row (I8). The display re-sorts stably once (R16).

### 6.3 Watermark rules

`watermark_seq` (per conversation, per device) is the highest `change_seq` *W* such that every change
with `change_seq ≤ W` that still exists on the server has been applied.

| Rule | Trigger | Effect |
|---|---|---|
| W1 | push event with `changeSeq == W + 1` | apply, `W := W + 1` (contiguous) |
| W2 | `/changes?afterSeq=W` page committed | `W := hasMore ? max(change_seq in page) : page.lastSeq` |
| W3 | push event with `changeSeq > W + 1`, or `Hint(seq > W)`, or `heads()[conv] > W` | apply (if content) **without** moving W; schedule a pull from W |
| W4 | any event with `changeSeq ≤ W` | dedup; never moves W backwards (I7) |

This is the gap definition of ADR-0014 (its reviewer note): a gap is *watermark < hinted or listed
seq*, not non-contiguity of message seqs, because edits, deletes and reactions consume `last_seq`
values and later edits overwrite a row's `change_seq`.

### 6.4 `SyncCoordinator`

| Trigger | Action |
|---|---|
| Server link enters `Syncing(heads)` | `ChangeFeedPort.heads()` → for each `conv` with `head > W`: schedule pull |
| `Hint(conv, seq > W)` (WS `sync.hint`, `message.new` gap, FCM wake) | schedule pull for `conv` |
| App foreground | `heads()` |
| Background job (WorkManager / BGAppRefresh) | `heads()` + pulls, seq-driven (MOB-10); runs Inbox commit before any DELIVERED |
| `KeyChanged`, `CrlDelta` | forwarded to crypto ports; no pull |

Pull loop per conversation: `changes(conv, afterSeq = W, limit = CHANGES_PAGE_LIMIT)` until
`hasMore = false`; at most `SYNC_MAX_PARALLEL_CONVERSATIONS` conversations in parallel; one pull
in flight per conversation (coalesce triggers). Hints are conflated per conversation to the max seq.

### 6.5 Display order

`ORDER BY coalesce(seq, +∞), local_order`. For rows without `seq`, `local_order` is assigned at
insert from `(lastKnownSeq, per-sender receive order, deviceCounter)`, with sender time clamped to
`[receivedAt − MAX_SKEW, receivedAt]` for display only (brief ADR-0014). A `deviceCounter` gap
schedules a pull; a replayed counter is dropped.

### 6.6 `ReceiptService`

| Receipt | When | Durability | Coalescing |
|---|---|---|---|
| L2 DELIVERED `ack{conv, uptoSeq = W, DELIVERED}` | after an Inbox commit that advanced W or inserted a message row | **regenerated**, not persisted: on server `Ready`, send for every conversation with `watermark_seq > delivered_reported_seq` | one pending per conversation (max `uptoSeq`) |
| L3 READ `ack{conv, uptoSeq, READ}` | chat visible and row on screen (`MarkRead`) | in memory only (brief: client-persisted read receipts rejected); regenerated on chat open | one pending per conversation |
| Sealed L2 to a peer (1.2) | after Inbox commit of an off-server row | regenerated while the link is UP | per peer |

Receipts are CONTROL-class frames in the CONTROL lane; they are **not** `outbox_entry` rows.
`delivered_reported_seq` is a column on `conversation` (the brief's schema lists
`conversation(…, watermark_seq, …)`; see reviewer note R-4).

---

## 7. Server link state machine (`WsServerLink`)

States (brief §6.3): `Disconnected → Connecting → Hello → Authenticated → Syncing(heads) → Ready ⇄
Degraded → Backoff`, plus `AuthExpired`; **(spec)** overlay `UpgradeRequired`.

| # | From | Event | To | Action |
|---|---|---|---|---|
| S01 | `Disconnected` | `start()` (session active) | `Connecting` | open TLS + WS (no JWT in the URL, BE-12/BE-16) |
| S02 | `Connecting` | socket open | `Hello` | send `hello{protocolVersions, minSupported, capabilities, deviceId}` + first-frame auth |
| S03 | `Connecting` | I/O error / timeout | `Backoff(until)` | existing capped exponential backoff with ±25 % jitter (`WsClient.kt:244-251`) |
| S04 | `Hello` | `welcome{protocolVersion, serverTime, certRenewal?, crlDelta?}` | `Authenticated` | record negotiated version; hand `certRenewal`/`crlDelta` to crypto (1.2) |
| S05 | `Hello` | `error{PROTOCOL_TOO_OLD}` | `UpgradeRequired` | stop reconnecting; UI "update the app"; entries stay `QUEUED` |
| S06 | `Hello` | `error{AUTH_TOKEN_EXPIRED}` / close 4401-class | `AuthExpired` | refresh token |
| S07 | `Authenticated` | — | `Syncing(heads)` | `heads()` (one request), drain receipts (§6.6) |
| S08 | `Syncing` | heads applied | `Ready` | raise `ServerLinkUp`, `EligibleLinkUp`; Outbox may dispatch |
| S09 | `Ready` | RTT EWMA > `DEGRADED_RTT_MS` or `MISSED_PONGS_DEGRADED` pongs missed | `Degraded` | still dispatches; `PathPolicy` (1.2) sees it |
| S10 | `Degraded` | metrics back under threshold for `DEGRADED_RECOVER_MS` | `Ready` | — |
| S11 | `Ready` / `Degraded` | socket closed / heartbeat dead | `Backoff(until)` | in-flight entries get `LinkLost` (T22) |
| S12 | `Ready` / `Degraded` | server close at JWT expiry (BE-12) | `AuthExpired` | in-flight → `LinkLost` |
| S13 | `AuthExpired` | refresh OK | `Connecting` | — |
| S14 | `AuthExpired` | refresh rejected | `Disconnected` | invoke `SessionTeardown(REFRESH_REJECTED)` |
| S15 | `Backoff` | timer fires / network-available callback / app foreground | `Connecting` | foreground and network callbacks shortcut the timer |
| S16 | any | `stop()` (logout, teardown) | `Disconnected` | generation token invalidated (existing `WsClient.kt:91-121` pattern) |
| S17 | any | server `SessionRevoked` close (BE-12) | `Disconnected` | invoke `SessionTeardown(REVOKED)` |

Rules: (1) the REST probe inside the reconnect loop (`WsClient.kt:254`, MOB-11) is removed from the
link; reachability is the link's own concern. (2) Single-flight connect and generation tokens are kept
verbatim from `WsClient` together with `WsClientReconnectTest` (12 tests), which moves with it.
(3) Writes to the socket go through one writer coroutine (MOB-13, BE-16 mirror).

> The engine requires a server that negotiates the 0.5 control plane (`seq` on `ServerAck`, replay,
> `/changes`). If `welcome` is absent or does not offer it, `engine.enabled` is forced off and the
> legacy path stays active. **(spec)**

---

## 8. Sealed link state machine (`LanLink`, `NearbyLink`, `UplinkHandover`, `MeshtasticLink`; 1.2+)

States (brief §6.3): `DISABLED → UNAVAILABLE → DISCOVERING → CONNECTING → AUTHENTICATING → UP ⇄
DEGRADED → BACKOFF(until)`; overlays `CONSENT_REQUIRED`, `BLOCKED_BY_POLICY`.

| # | From | Event | To | Action |
|---|---|---|---|---|
| L01 | `DISABLED` | Nearby mode on (user) and policy allows | `UNAVAILABLE` | Android: start `connectedDevice` foreground service; arm `NEARBY_MODE_AUTO_OFF` |
| L02 | `UNAVAILABLE` | radio/network precondition met (Wi-Fi on same network for LAN; BT on for Nearby) | `DISCOVERING` | advertise rotating tags (§8.5 brief), scan; instance/endpoint names random per session |
| L03 | `DISCOVERING` | demand for a matched verified peer (§10) | `CONNECTING` | racing per §10 |
| L04 | `CONNECTING` | transport connected | `AUTHENTICATING` | `LinkAuthenticator.handshake(io, role, expected)` |
| L05 | `CONNECTING` | failure | `BACKOFF(until)` | per-peer backoff, same full-jitter formula |
| L06 | `AUTHENTICATING` | handshake OK, certificate valid at `now`, not in CRL, link key matches certificate | `UP` | raise `EligibleLinkUp` for entries to that peer |
| L07 | `AUTHENTICATING` | any check fails | `BACKOFF(until)` + security event | never fall back to an unauthenticated session |
| L08 | `UP` | goodput < class threshold or loss > bound for `DEGRADED_WINDOW_MS` | `DEGRADED` | — |
| L09 | `DEGRADED` | recovered | `UP` | — |
| L10 | `UP` / `DEGRADED` | disconnect | `DISCOVERING` | in-flight → `LinkLost` |
| L11 | `UP` / `DEGRADED` | idle `IDLE_CLOSE_MS` without BULK/live demand and up ≥ `DEESCALATE_MIN_UP_MS` | `DISCOVERING` | close the costlier link (§10) |
| L12 | `BACKOFF` | timer | `DISCOVERING` | — |
| L13 | any active | Nearby mode off / auto-off / app background (iOS) / teardown | `DISABLED` | stop FGS, close, wipe per-session names |
| L14 | any | OS permission missing/revoked | overlay `CONSENT_REQUIRED` | UI consent flow (capability matrix §7) |
| L15 | any | admission fails (certificate expired > 7 d offline, TR-excluded RF, policy) | overlay `BLOCKED_BY_POLICY` | — |
| L16 | `CONSENT_REQUIRED` / `BLOCKED_BY_POLICY` | condition cleared | `UNAVAILABLE` | — |

`MeshtasticLink` replaces `DISCOVERING` with "node paired over BLE/serial" and has no BULK; its sends
are additionally gated by `AirtimeLedger.tryReserve` (ADR-0026).

---

## 9. Bundle custody state machine (1.3+, ADR-0025)

Two records: the **sender-side** `OutboxEntry` (unchanged: `HOP_ACKED(custodian, serverOwed)`) and a
**custody record** in the separate encrypted `custody.db` on the custodian (brief §11.2).

### 9.1 Offer side (`CustodyPolicy`, sender device)

| # | From | Event | To | Notes |
|---|---|---|---|---|
| C01 | `CREATED` | custodian available in hierarchy order (server mailbox → own device → contact uplink) | `OFFERED(custodian)` | strict order; groups above the cap, channels and communities only ever get custodian 1 |
| C02 | `OFFERED` | `CustodyResult.Accepted(expiresAt)` | `IN_CUSTODY(custodian, expiresAt)` | outbox T17 |
| C03 | `OFFERED` | `CustodyResult.Declined(reason)` (quota, policy, link lost) | `DECLINED` → `CREATED` (next custodian) | |
| C04 | `IN_CUSTODY` | authenticated DELIVERED (sealed to sender) or `SeqObserved` | `DELIVERED_RECEIPT` → `RELEASED` | outbox T20/T21 |
| C05 | `IN_CUSTODY` | `now > expiresAt` | `EXPIRED` → `CREATED` if outbox lifetime remains | |

### 9.2 Custodian side (`custody.db`)

| # | From | Event | To | Notes |
|---|---|---|---|---|
| K01 | — | offer received, quota available (uplink: ≤ 50 bundles / 10 MB per mule, 24 h) | `HELD(expiresAt)` | commit **before** `CustodyResult.Accepted` (P4) |
| K02 | — | quota exhausted | (decline) | FIFO-by-expiry eviction only for already-held bundles past expiry |
| K03 | `HELD` | own device: recipient device reachable → handed over and DELIVERED authenticated | `RELEASED` (delete) | |
| K04 | `HELD` | own device / mule: server reachable → `POST /api/v1/uplink` or server submit returns 2xx/accepted | `RELEASED` (delete) | mule deletes on 2xx or after 24 h |
| K05 | `HELD` | `now > expiresAt` | `EXPIRED` (delete) | |
| K06 | any | leave relay mode / `SessionTeardown` | (wipe store) | brief §9 erasure |

---

## 10. QoS, lanes and scheduling

### 10.1 Classes (brief §6.5)

| Class | Contents | Outbox row? | Lane |
|---|---|---|---|
| CONTROL | receipts (not rows, §6.6), call signalling (row, 60 s), reports (row, ADR-0029), key-change, CRL delta, link control | depends | CONTROL |
| INTERACTIVE | text, edits, deletes, reactions, location, polls, GIF/sticker pointers, media pointers once uploaded | yes | INTERACTIVE |
| BULK | media blob transfer | yes (`PREPARING`) | server: HTTP upload worker; sealed links: BULK lane |
| EPHEMERAL | typing, presence, conversation focus | **never** | conflated single slot, only while Ready/UP |

### 10.2 Per-link sender

Each link has exactly one sender coroutine owning bounded lanes. Kotlin's `select` is biased towards
the first ready clause, so clause order is the priority order:

```kotlin
// pseudo-signature, :mobile:messaging engine/LinkSender.kt
internal class LinkSender(link: AnyLink, lanes: Lanes, clock: Clock) {
    suspend fun run() = coroutineScope {
        while (isActive) select<Unit> {
            lanes.control.onReceive     { send(it) }        // 1
            lanes.ephemeral.onReceive   { send(it) }        // 2 (conflated, dropped if link not Ready/UP)
            lanes.interactive.onReceive { send(it) }        // 3
            lanes.bulk.onReceive        { sendChunk(it) }   // 4: one chunk, then back to select (preemption point)
        }
    }
}
```

| Rule | Detail |
|---|---|
| R1 | Server link from 0.5: CONTROL + EPHEMERAL + INTERACTIVE lanes; BULK is the HTTP media worker, separate from the socket. |
| R2 | Sealed links from 1.2: all four lanes; a slow BLE link (36–140 kbit/s measured, PROX-01) blocks only itself. |
| R3 | Preemption only between BULK chunks; no frame is split for priority. |
| R4 | Per-conversation FIFO applies to INTERACTIVE entries within one conversation only (I4). Conversations drain independently (I15). |
| R5 | Media in `PREPARING` is outside the FIFO; it enters `QUEUED` when its upload finishes. Send order follows ready order; display order follows seq. |
| R6 | The Outbox **pulls** due entries into a lane only when `lane.trySend` succeeds; otherwise the entry stays `QUEUED` (backpressure, §11.1). |
| R7 | No global Mutex; per-entry exclusivity is the CAS (I3). The Outbox runs one dispatcher coroutine per link, reading `OutboxStore.due(now)`. |

`CONVERSATION_INFLIGHT_WINDOW = 1` **(spec)**: with retries, a window > 1 lets a later message take a
lower seq than an earlier one that timed out. At 80–100 ms RTT, draining 50 queued messages in one
conversation costs ~5 s; conversations drain in parallel. Raising the window is a later, measured
optimisation and must document the inversion it allows.

### 10.3 Structured concurrency

Engine scope = `SupervisorJob` child of the session scope; cancelled by `SessionTeardown` (I17).
`CancellationException` is always rethrown (`runCatchingCancellable`, MOB-09). No `GlobalScope`, no
fire-and-forget launches outside the engine scope (GEM-22).

---

## 11. Backpressure and error model

### 11.1 Backpressure (brief §6.9)

| Point | Mechanism |
|---|---|
| Lanes | Bounded `Channel(capacity)`; `CONTROL_LANE_CAPACITY = 64`, `INTERACTIVE_LANE_CAPACITY = 32`, `BULK_LANE_CAPACITY = 4` chunks, EPHEMERAL `CONFLATED` **(spec values)** |
| Link | `submit/send` may return `Backpressure(after)` → T23 (no attempt penalty) |
| Server | `RATE_LIMITED` error → `Backpressure(RATE_LIMIT_BACKOFF)` |
| Sealed links | credit window: receiver advertises credits in `TransportFrame{frameType = CREDIT}`; sender stops at 0 credits; `INITIAL_CREDITS = 16` frames **(spec)** |
| Inbound | bounded buffer per source (`INBOUND_BUFFER = 256` events); when full the source **pauses reads** (WS: stop collecting Ktor `incoming`, TCP applies pressure; REST: no next page; FCM: hints conflated to max seq). Never drop: pull repairs (P6). |

### 11.2 Error taxonomy

Exceptions do not cross a port. Adapters convert every failure into a sealed result; the engine never
throws to the UI (replaces `MessageQueuedException`, `MessageCouldNotBeQueuedException`,
`ViewOnceNotQueueableException`, `WsClient.kt:638-671`).

| Layer | Type | Variants |
|---|---|---|
| UI ← engine | `SendOutcome` | `Enqueued(entryId)`, `Refused(SendRefusal)` — `SendRefusal`: `CONTENT_TOO_LONG`, `EMPTY`, `NOT_MEMBER_LOCAL`, `STORE_UNAVAILABLE` |
| engine ← link | `SubmitResult` | as §2 |
| engine ← crypto | `SealResult` / `OpenResult` | `Sealed \| NoSession \| UntrustedIdentity \| Failed(retryable)` |
| engine ← store | `StoreResult` / exception | store failure is fatal for the command: `Refused(STORE_UNAVAILABLE)`; nothing is "sent anyway" |

### 11.3 Server error code → outbox transition

| `error.code` (`ErrorCode` today / ICD) | Class | Transition |
|---|---|---|
| `RATE_LIMITED` | backpressure | T23 |
| `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID` | link | link → `AuthExpired` (S06/S12); entry → `QUEUED` (T25), attempt not counted |
| `AUTH_TOKEN_REVOKED` | session | `SessionTeardown(REVOKED)` |
| `PROTOCOL_TOO_OLD` | link | `UpgradeRequired` (S05); entry stays `QUEUED` |
| `UNSUPPORTED_FRAME` | bug | `FAILED_PERMANENT`, error report |
| `MSG_DUPLICATE` | legacy only | must not occur once V29 replay is live (engine precondition, §7); if seen, treat as `Retry` and let `SeqObserved` resolve it |
| `MSG_CONVERSATION_NOT_FOUND`, `CONV_NOT_FOUND`, `MSG_NOT_MEMBER`, `MSG_ANNOUNCEMENT_ONLY`, `MSG_CONTENT_TOO_LONG`, `MSG_EMPTY_CONTENT`, `MSG_MEDIA_NOT_ACCESSIBLE`, `MSG_NOT_SENDER`, `MSG_NOT_FOUND`, `MSG_ALREADY_DELETED`, `MSG_EDIT_WINDOW_EXPIRED`, `MSG_INVALID_REACTION`, `MSG_PIN_LIMIT_REACHED`, `MEDIA_TOO_LARGE` | permanent | T24 `FAILED_PERMANENT(code)` |
| `DEVICE_LIST_STALE` (1.0, proposed for the ICD) | re-seal | `PENDING_SEAL(SEAL_RETRY)` with the server's device list (R-3) |
| 5xx / unknown | transient | T22 `RETRY_WAIT` |

UI copy for each code comes from `composeResources` keyed by the code (no hardcoded strings).

---

## 12. Chunking, reassembly, escalation

### 12.1 Chunking by path (brief §6.7)

| Path | INTERACTIVE/CONTROL | BULK |
|---|---|---|
| Server | one WS frame per entry | out-of-band HTTP upload, resumable, `UPLOAD_RANGE_BYTES = 1 MiB`; ciphertext from 1.0; sealed message carries `{blobId, size, sha256, key, iv}` |
| `LanLink` | length-prefixed record ≤ `LAN_MAX_RECORD_BYTES = 64 KiB` | records with offset-resume |
| `NearbyLink` | BYTES payload (size limit read from the library at runtime, MISS-03 proximity) | FILE/STREAM payloads; the library fragments |
| `MeshtasticLink` | one frame ≤ `MESHTASTIC_MAX_FRAME_BYTES = 200` | none |

No generic fragmenter, no FEC. MTU is negotiated, never constant (brief §10).

### 12.2 Reassembly (receiver, BULK off-server)

Keyed by `blobId`; holds **ciphertext** (the media blob is AES-GCM encrypted by `MediaEncryptor`).

| From | Event | To | Action |
|---|---|---|---|
| — | first chunk, declared `size ≤ MAX_REASSEMBLY_BYTES` | `RECEIVING(offset)` | allocate temp file inside the app's encrypted media cache |
| — | declared `size > MAX_REASSEMBLY_BYTES` | (refuse) | `CONTROL` reject to the peer |
| `RECEIVING` | chunk at `offset` | `RECEIVING(offset + n)` | out-of-order chunk → request resume from `offset` |
| `RECEIVING` | `offset == size` | `VERIFYING` | SHA-256 over ciphertext |
| `VERIFYING` | hash matches | `COMPLETE` | Inbox completes the message row (one tx) → L2 |
| `VERIFYING` | mismatch | `CORRUPT` → delete | request resume from 0 once; then fail the row |
| any | `now > startedAt + REASSEMBLY_TTL` or link closed > TTL | `EXPIRED` → delete | sender retries via outbox |

`MAX_REASSEMBLY_BYTES = 100 MiB` mirrors the server's `spring.servlet.multipart.max-file-size: 100MB`
(`backend/src/main/resources/application.yml:61`), so no off-server path accepts what the server
would refuse. `REASSEMBLY_TTL = 24.hours` **(spec)**.

### 12.3 Escalation, de-escalation and racing (1.2+, brief §6.8, §7)

Racing happens at **link establishment**, never per message (no hedged duplicates off-server).

```
on demand(peer):                                  // queued entries for the peer, or chat opened in Nearby mode
  ranked = rankedAdaptersFor(peer)                // LanLink first if same network, then NearbyLink
  for ((i, adapter) in ranked.withIndex()) launch { delay(i * ATTEMPT_DELAY_MS); adapter.open(peer) }
  first UP wins; a higher-ranked later UP may take over; losers cancelled
```

| Rule | Constant | Value | Source |
|---|---|---|---|
| Stagger between attempts | `ATTEMPT_DELAY_MS` | 250 ms | brief |
| Request Nearby bandwidth upgrade when queued BULK for the peer reaches | `ESCALATE_BYTES` | 256 KiB | brief |
| Close the costlier link after no BULK/live demand for | `IDLE_CLOSE_MS` | 30 s | brief |
| Minimum time UP before de-escalation | `DEESCALATE_MIN_UP_MS` | 10 s | brief |
| Nearby mode auto-off | `NEARBY_MODE_AUTO_OFF` | 15 min | brief |

The BLE → Wi-Fi switch itself belongs to Nearby Connections (make-before-break). Continuity comes
from entry ids and acks, never from QUIC migration (QUIC-1).

---

## 13. Constants (companion objects, `config/`)

All magic numbers live in companion objects of the owning class (CLAUDE.md "no magic numbers");
values marked **spec** are proposals to be tuned from telemetry, not ADR decisions.

| Object | Constant | Value | Source |
|---|---|---|---|
| `OutboxTimings` | `RETRY_BASE_MS` / `RETRY_CAP_MS` | 1 000 / 30 000 | brief |
| | `SERVER_ACK_TIMEOUT_MS` | 15 000 | spec |
| | `SEALED_ACK_TIMEOUT_FLOOR_MS` | 5 000 | spec |
| | `DEFAULT_LIFETIME` / `CALL_SIGNAL_LIFETIME` / `CONTROL_LIFETIME` | 30 d / 60 s / 60 s | brief |
| | `ACCEPTED_RETENTION` | 7 d | spec |
| | `CONVERSATION_INFLIGHT_WINDOW` | 1 | spec |
| `Lanes` | `CONTROL_LANE_CAPACITY` / `INTERACTIVE_LANE_CAPACITY` / `BULK_LANE_CAPACITY` | 64 / 32 / 4 | spec |
| `InboxLimits` | `INBOUND_BUFFER` | 256 | spec |
| | `INBOUND_DEDUP_TTL` | ≥ `DEFAULT_LIFETIME` (30 d) | brief ("TTL ≥ max lifetime") |
| `Ordering` | `MAX_SKEW` | 5 min | brief |
| `SyncLimits` | `CHANGES_PAGE_LIMIT` / `SYNC_MAX_PARALLEL_CONVERSATIONS` | 200 / 4 | spec |
| `ServerLinkTimings` | reconnect backoff | as `WsClient` today (capped exponential, ±25 % jitter) | existing code |
| | `HEARTBEAT_INTERVAL_MS` | 30 000 | existing (`WsClient.kt:62`) |
| | `DEGRADED_RTT_MS` / `MISSED_PONGS_DEGRADED` / `DEGRADED_RECOVER_MS` | 2 000 / 1 / 30 000 | spec |
| `Chunking` | `UPLOAD_RANGE_BYTES` | 1 MiB | brief |
| | `LAN_MAX_RECORD_BYTES` | 64 KiB | brief |
| | `MESHTASTIC_MAX_FRAME_BYTES` | 200 | brief |
| | `MAX_REASSEMBLY_BYTES` / `REASSEMBLY_TTL` | 100 MiB / 24 h | spec (server limit) |
| `EscalationTimings` | `ATTEMPT_DELAY_MS`, `ESCALATE_BYTES`, `IDLE_CLOSE_MS`, `DEESCALATE_MIN_UP_MS`, `NEARBY_MODE_AUTO_OFF` | 250 ms, 256 KiB, 30 s, 10 s, 15 min | brief |
| `SealedLinkLimits` | `INITIAL_CREDITS`, `DEGRADED_WINDOW_MS` | 16, 10 000 | spec |
| `CustodyLimits` (1.3) | `UPLINK_MAX_BUNDLES` / `UPLINK_MAX_BYTES` / `UPLINK_HOLD` | 50 / 10 MB / 24 h | brief §9 |

---

## 14. Test harness specification

Lives with the engine's tests (see reviewer note in ADR-0028 on `testFixtures` vs a
`:mobile:messaging-testkit`; this spec is agnostic). All tests run on `jvmTest`
(`:mobile:messaging:jvmTest` in CI from day one, KMP-03, ADR-0028).

### 14.1 Components

| Component | Implements | Behaviour |
|---|---|---|
| `TestClock` | `kotlinx.datetime.Clock` | Reads the `TestCoroutineScheduler`'s virtual time, so `delay` and `Clock.now()` agree; `advanceBy(d)` |
| `SequentialIdGenerator` | `IdGenerator` | Deterministic UUIDv7-shaped ids from a counter |
| `SimulatedLink` | `ServerLink` | Seeded `Random`; `LinkProfile` below; delivers to a `FakeServer` |
| `SimulatedSealedLink` | `SealedLink` | Same profile; asserts every frame is a `SealedFrame` produced by the testkit sealer (I5) |
| `FakeServer` | — | Built on `InMemoryConversationSequence`, in-memory idempotency `(senderDevice, clientMessageId)` with replay, change feed with `change_seq`, per-device ack policy (BE-03/BE-18), hint emission; optional mailbox (1.0) |
| `InMemoryMessageStore` / `InMemoryOutboxStore` | `MessageStore` / `OutboxStore` | Transactional (copy-on-write snapshot); survive a simulated process death |
| `FakeSealer` | `EnvelopeSealer` | **Testkit only** (`:mobile:crypto-testkit`, ADR-0016); scriptable `SealResult`s |
| `CrashPoints` | — | Named injection points that throw `SimulatedProcessDeath` |
| `Scenario` DSL | — | Nodes (sender, recipient, recipient companion, own device, mule), scripted user actions, partitions, clock advances |

```kotlin
data class LinkProfile(
    val latency: LatencyDistribution, val lossPpm: Int, val duplicatePpm: Int,
    val reorderWindow: Int, val maxFrameBytes: Int?, val partitions: List<PartitionWindow>,
    val flapEvery: Duration?, val tokenExpiryAt: Instant?,
)
enum class CrashPoint { BEFORE_ENQUEUE_COMMIT, AFTER_ENQUEUE_COMMIT, AFTER_SUBMIT_BEFORE_RESULT,
    AFTER_RESULT_BEFORE_CAS, BEFORE_INBOX_COMMIT, AFTER_INBOX_COMMIT_BEFORE_ACK, AFTER_ACK,
    MID_REASSEMBLY, MID_TEARDOWN }
```

A process death discards every in-memory engine object and rebuilds the engine from the surviving
in-memory stores, then raises `Recover` (T30).

### 14.2 Suites

| Suite | Content | Gate |
|---|---|---|
| Transition unit tests | Every row T01–T32, every ignored-event row, every error-code row; pure function `transition(state, event, ctx) → Decision` | each PR |
| Property suite | 10 000 seeded runs: loss, duplication, reorder, partition, flapping, process death at every `CrashPoint`, 24 h offline, token expiry mid-session; invariants I1–I17 after every step; final: every message exactly once in sender and receiver stores, in seq order | 0.5 exit (brief §13). **(spec)**: PRs run a 1 000-seed subset; the full 10 000 runs on merge to the trunk and nightly, given the single runner (R19) |
| Hint-loss test | drop 100 % of WS hints and FCM wakes; convergence via pull on reconnect, foreground and background job | RQ-05 |
| Concurrency | CAS races on the same entry; two links completing the same entry | I3 |
| Teardown | `SessionTeardown` at every `CrashPoint`; then I17 | RQ-08 (unit half) |
| Contract tests | `ServerLink`/`SealedLink`/`InboundSource` contract run against `SimulatedLink`, and later against `WsServerLink` with a mock socket | per adapter |
| Golden vectors | `ServerFrame` ↔ JSON, inner `Content` and `TransportFrame` protobuf (ICD) on JVM, backend and iOS when present | per wire change |
| Fuzz | every parser (ICD) | per wire change |

Every failing property run prints its seed; a seed is a permanent regression test once fixed.

---

## 15. Module and package layout

```
mobile/messaging/                       (:mobile:messaging — commonMain + jvm test target; ADR-0028)
  src/commonMain/kotlin/com/muhabbet/messaging/
    domain/      OutboxEntry, OutboxState, OutboxEvent, InboundEvent, DeliveryState, CausalHeader,
                 TrafficClass, StorageMode, ids (value classes), Tick, tickOf()
    port/        ServerLink, SealedLink (1.2), InboundSource, MessageStore, OutboxStore,
                 ChangeFeedPort, CustodyTarget (1.3), LiveSessionPort (0.7), IdGenerator
    engine/      MessagingEngine (facade), Outbox, OutboxTransitions (pure), Inbox, SyncCoordinator,
                 ReceiptService, LinkSender, Lanes, MediaJobQueue
    usecase/     SendMessage, MarkRead, RetryDelivery, ObserveConversation, SessionTeardown
    codec/       ProtocolCodec (OutgoingEnvelope → ServerFrame)
    policy/      (1.2) PathPolicy, PathRule + rules; (1.3) CustodyPolicy
    live/        (0.7) LiveSessionCoordinator
    config/      OutboxTimings, Lanes, InboxLimits, SyncLimits, Chunking, EscalationTimings, …
  src/commonTest/ or testkit            SimulatedLink, SimulatedSealedLink, FakeServer, TestClock,
                                        SequentialIdGenerator, InMemory* stores, Scenario DSL
mobile/crypto/            (:mobile:crypto) SealedFrame (internal ctor), EnvelopeSealer, SessionCryptoPort
mobile/crypto-testkit/    (:mobile:crypto-testkit, test configurations only) FakeSealer, vectors
mobile/composeApp/        adapters: WsServerLink, WsInboundSource, RestChangeFeedSource, FcmHintSource,
                          SqlDelightMessageStore, SqlDelightOutboxStore, Koin module `messagingModule`
```

Dependency rules (build-enforced, ADR-0012/0028; guardrail ratchet extended to the module):

| Rule | Enforcement |
|---|---|
| `:mobile:messaging` depends only on `:shared`, `:mobile:crypto`, kotlinx-coroutines/datetime/serialization | Gradle dependencies; Konsist |
| No `androidx.compose.*`, no `org.koin.*` annotations, no `io.ktor.*`, no `com.muhabbet.shared.protocol.WsMessage` | Konsist + `verifyUi`-style guardrail at 0 |
| No `System.currentTimeMillis`, `Clock.System`, `Random.Default`, UUID generation outside `IdGenerator` | Konsist (P10) |
| Only `:mobile:crypto` constructs `SealedFrame` | `internal` constructor + arch test (P2) |
| `*-testkit` absent from every release runtime classpath | build check (ADR-0016) |
| `ui/` contains 0 `WsMessage` / `wsClient` references at end of 0.5 | `verifyUi` ratchet (P5) |

Koin wiring stays in `:mobile:composeApp` (`single { MessagingEngine(...) }`); the engine has no DI
annotations.

---

## 16. Strangler plan: from `WsClient` / `PendingMessage` to the engine

### 16.1 Today's responsibilities and their target (`mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/…`)

| Today | Lines (dev @ 2434324) | Responsibility | Target | Slice |
|---|---|---|---|---|
| `data/remote/WsClient.kt` `connect`, connect loop | 122–286 | single-flight connect, generation tokens, backoff, **REST probe at 254** (MOB-11) | `WsServerLink` (probe removed) | S2 |
| `WsClient.send` | 287–333 | encrypt hook, queue-on-disconnect, view-once refusal, write | `MessagingEngine.send` → `Outbox` (T01–T03); write in `WsServerLink` | S4 |
| `WsClient.sendAck` | 334–357 | receipt send or in-memory queue | `ReceiptService` (§6.6) | S3 |
| `WsClient.sendOrQueue` | 358–381 | best-effort send | `Outbox` | S4 |
| `WsClient.setConversationFocus` / `reassertConversationFocus` | 382–407 | presence focus, re-asserted on reconnect | `MessagingEngine.setFocus` (EPHEMERAL, re-sent on `Ready`) | S4 |
| `WsClient.encryptForWire` | 408–428 | E2E hook with **silent plaintext fallback** (C9) | deleted; `EnvelopeSealer` at 1.0 (ADR-0016) | S4 / 1.0 |
| heartbeat, `disconnect` | 429–501 | ping/pong, teardown by generation | `WsServerLink` | S2 |
| `queuePendingMessage` | 502–533 | lossy copy into `pendingMessage` (drops `viewOnce`, `thumbnailUrl`, `forwardedFrom`) | `OutboxStore.enqueue` with the **full** envelope (MOB-04) | S4 |
| `drainPendingMessages` | 534–583 | `session?.outgoing?.send` + delete without waiting for an ack; drop after `MAX_RETRY_COUNT` | deleted; Outbox dispatch + CAS (T14–T24) | S6 |
| `drainPendingAcks` | 584–597 | replay in-memory receipts | `ReceiptService` regeneration | S3 |
| dedup (`processedMessageIds`, 500 ids, edit key without `editedAt`) | 598–626 | in-memory dedup | persistent `inbound_dedup` + `change_seq` (MOB-05) | S3 |
| exceptions | 638–671 | `MessageQueuedException` etc. | `SendOutcome` / `SubmitResult` | S4 |
| `connectionState` | 70 | UI connection strip | `MessagingEngine.connectivity: StateFlow` | S5 |
| `data/remote/PendingAckQueue.kt` | — | in-memory receipt queue | `ReceiptService` | S3 |
| `data/local/PendingMessageCache.kt`, `LocalCache` pending methods, `.sq` `pendingMessage` queries | `MuhabbetDatabase.sq:122-139` | offline queue | `SqlDelightOutboxStore` on the new encrypted DB; rows copied once at the 0.5 migration (ADR-0020) | S3 |
| `crypto/MessageEncryptor.kt` | — | "single send chokepoint" encryptor | `EnvelopeSealer` adapter (1.0) | 1.0 |
| `util/MessageIds.kt` `generateMessageId()` | 14–25 | UUIDv4 from non-crypto `chars.random()` (MOB-11) | `IdGenerator` (UUIDv7, CSPRNG) | S4 |
| `data/repository/MessageRepository.syncMessagesSince` + `BackgroundSyncManager.android.kt:68` | 193–198 | device-clock watermark (MOB-10) | `SyncCoordinator` by seq | S3 |
| `MessageRepository.sendMessage` (REST) via `NotificationReplyReceiver.kt:101` (#510) | 80–92 | inline reply without a socket | `MessagingEngine.send` (durable enqueue) + expedited background flush (R-5) | S4 |
| `MessageRepository.markDelivered` via `MuhabbetFirebaseMessagingService.kt:84` (#596) | ~104 | DELIVERED from a push handler **before** any local commit | wake-only FCM (ADR-0019) → `SyncCoordinator` pull → Inbox commit → L2 (P4) | S3 |

### 16.2 The 17 `wsClient.send` inventory (ADR-0011)

`grep -rn 'wsClient.send' mobile/composeApp/src/commonMain` on `dev` @ 2434324 returns 17 lines: 14
`wsClient.send(…)` calls, 2 `wsClient.sendAck(…)` calls and 1 comment (reviewer note R-6).

| # | File:line | Today | `WsMessage` | Class | Target | Slice |
|---|---|---|---|---|---|---|
| 1 | `ui/conversations/ConversationListScreen.kt:191` | DELIVERED ack from the list collector — **duplicate** of #17, before any persistence (MOB-03) | `AckMessage(DELIVERED)` | CONTROL | **delete**; Inbox emits L2 after commit | S3 |
| 2 | `ui/chat/ChatDialogs.kt:279` | forward to another conversation | `SendMessage(forwardedFrom)` | INTERACTIVE | `SendMessage(OutgoingContent.Forward)` | S4 |
| 3 | `ui/chat/ChatScreen.kt:310` | voice note after upload | `SendMessage(VOICE, mediaUrl)` | BULK→INTERACTIVE | `SendMessage(Media.Voice)` via `PREPARING` | S4 |
| 4 | `ui/chat/ChatScreen.kt:329` | document after upload | `SendMessage(DOCUMENT)` | BULK→INTERACTIVE | `SendMessage(Media.Document)` | S4 |
| 5 | `ui/chat/ChatScreen.kt:361` | photo (`PhotoSend.outgoingPhoto`, view-once) | `SendMessage(IMAGE, viewOnce)` | BULK→INTERACTIVE | `SendMessage(Media.Image(viewOnce))` — view-once now queueable (full envelope) | S4 |
| 6 | `ui/chat/ChatScreen.kt:363` | comment, not a call | — | — | removed with the code | S6 |
| 7 | `ui/chat/ChatScreen.kt:761` | location | `SendMessage(LOCATION)` | INTERACTIVE | `SendMessage(Location)` | S4 |
| 8 | `ui/chat/ChatScreen.kt:776` | poll | `SendMessage(POLL)` | INTERACTIVE | `SendMessage(Poll)` | S4 |
| 9 | `ui/chat/ChatScreen.kt:792` | scheduled text | `SendMessage(TEXT, scheduledAt)` | INTERACTIVE | `SendMessage(Text(scheduledAt))`; server assigns seq at release (BE-02) | S4 |
| 10 | `ui/chat/ChatScreen.kt:1096` | text / reply | `SendMessage(TEXT, replyToId)` | INTERACTIVE | `SendMessage(Text)` | S4 |
| 11 | `ui/chat/ChatScreen.kt:1156` | GIF (external URL, no upload) | `SendMessage(GIF)` | INTERACTIVE | `SendMessage(ExternalMedia.Gif)` | S4 |
| 12 | `ui/chat/ChatScreen.kt:1173` | sticker | `SendMessage(STICKER)` | INTERACTIVE | `SendMessage(ExternalMedia.Sticker)` | S4 |
| 13 | `ui/chat/ChatScreen.kt:1186` | typing | `TypingIndicator` | EPHEMERAL | `MessagingEngine.setTyping` | S4 |
| 14 | `ui/call/ActiveCallScreen.kt:272` | end call | `CallEnd` | CONTROL (60 s) | `LiveSessionCoordinator` (0.7) | 0.7 |
| 15 | `ui/call/IncomingCallScreen.kt:129` | decline | `CallAnswer(false)` | CONTROL (60 s) | `LiveSessionCoordinator` | 0.7 |
| 16 | `ui/call/IncomingCallScreen.kt:162` | accept | `CallAnswer(true)` | CONTROL (60 s) | `LiveSessionCoordinator` | 0.7 |
| 17 | `App.kt:148` | global DELIVERED pump on `incoming` — acks from RAM (MOB-03) | `AckMessage(DELIVERED)` | CONTROL | Inbox + `ReceiptService` (persist then ack) | S3 |

Other wire touch-points in `ui/` that the 0.5 ratchet also drives to 0:

| File:line | Today | Target |
|---|---|---|
| `App.kt:142`, `ui/chat/ChatScreen.kt:533`, `ui/conversations/ConversationListScreen.kt:182`, `ui/call/ActiveCallScreen.kt:118` | `wsClient.incoming.collect` (4 collectors) | screens observe SQLDelight flows (`ObserveConversation`); calls observe `LiveSessionCoordinator` |
| `ui/conversations/ConversationListScreen.kt:88`, `ui/chat/ChatScreen.kt:162` | `wsClient.connectionState` | `MessagingEngine.connectivity` |
| `ui/chat/ChatScreen.kt:489`, `:500` | `wsClient.setConversationFocus` | `MessagingEngine.setFocus` |
| `ui/chat/ReadReceipts.kt:73-89` `sendReadReceipt(send = wsClient.sendAck)` | READ receipts | `MarkRead` use case (decision logic kept, it is already socket-free) |
| `ui/chat/MessageExpiry.kt`, `PhotoSend.kt`, `SendFailureMessages.kt` | `WsMessage` types in UI | domain types / `SendOutcome` |

### 16.3 Slices and rollout (brief §13, ADR-0012)

| Slice | Milestone | Content | Done when |
|---|---|---|---|
| S0 | 0.4 (legacy code, no engine) | MOB-04 stopgap: delete `pendingMessage` only on `ServerAck(requestId)`; MOB-03: persist `message.new` before DELIVERED, delete call site #1; MOB-05 edit key with `editedAt`; MOB-01 minimal `SessionTeardown`; `.sqm` migrations + `verifyMigrations` | 0.4 exit criteria (brief §13) |
| S1 | 0.5 | modules, domain, pure `OutboxTransitions`, in-memory stores, harness, property suite green; no app wiring | suite in CI |
| S2 | 0.5 | `WsServerLink` + `WsInboundSource` extracted (connect loop, heartbeat, disconnect, `WsClientReconnectTest` moved); legacy `WsClient` delegates to them | reconnect tests green on both paths |
| S3 | 0.5 | encrypted DB (ADR-0020), `SqlDelight*Store`s, Inbox, `SyncCoordinator`, `RestChangeFeedSource`, `FcmHintSource`, `ReceiptService`; call sites #1 and #17 retired | RQ-03, RQ-05 tests |
| S4 | 0.5 | use cases; call sites #2–#13 migrated; view-once queueable; inline reply on the outbox | `engine.enabled` builds pass the suite |
| S5 | 0.5 | screens observe DB flows; `verifyUi` ratchet to 0 | ratchet 0 |
| S6 | 0.5.1 → 0.5.2 | 0.5.0 ships both paths, engine on for internal testers; 0.5.1 flips the default after emulator parity (owner, P13); 0.5.2+ deletes legacy queue, dedup, encrypt hook, `PendingMessageCache`, `PendingAckQueue`, `pendingMessage`/`cachedMessage`/`cachedConversation` | owner's emulator note; no legacy references |
| — | 0.7 | call sites #14–#16 via `LiveSessionCoordinator` | 0.7 exit |

Kill switch: `engine.enabled` remote flag for one release (ADR-0012). The legacy path is not
modified beyond S0 while both coexist (R3).

---

## 17. Evidence

| Finding | How this spec answers it |
|---|---|
| MOB-03, MISS-01 (mobile), MOB-08 | Inbox persist-then-ack (I6), call sites #1/#17 retired, tick function (§5) |
| MOB-04, MOB-05, MOB-13 | Lossless full-envelope outbox, no attempt-cap drop (I2), `editedAt` in dedup, single writer |
| MOB-01, MISS-02 (mobile) | `SessionTeardown` and I17 |
| MOB-10 | `SyncCoordinator` by seq |
| MOB-11, MOB-12 | Ports, strangler map (§16), harness (§14) |
| BE-03, BE-06, BE-07, BE-18, BE-12 | Server preconditions the engine relies on (§7, §11.3) |
| C9 | Seal failure never falls back to plaintext (T08, T13) |
| GEM-01/02/03/04/16/22, GEM MISS-01, MISS-03 (Gemini) | Lanes + biased select, per-link senders, no global Mutex, acks and reassembly, value-class ids, structured concurrency, frame headers via ICD |
| PROX-01, MISS-03 (proximity) | BLE goodput expectations; MTU read at runtime |
| QUIC-1 | Continuity by ids and acks, not migration |

## 18. Verification

| Claim | Method | Evidence |
|---|---|---|
| I1–I17 hold | Test (property suite, 10 000 seeds; TestClock) | CI report (0.5 exit) |
| Every transition row implemented | Test (one test per T/S/L/C/K row) + Inspection | CI report |
| `:mobile:messaging` free of Compose/Koin/`WsMessage` | Build-enforced dependency rules | CI |
| `ui/` free of wire types | `verifyUi` ratchet = 0 | CI |
| Engine on, emulator parity, airplane-mode recovery, notification with app killed | Demonstration | **Owner only** (P13, ADR-0032) |

## 19. Reviewer notes

> **Reviewer note (open) R-1:** the brief does not say whether LOCAL_ONLY messages relayed through
> the server mailbox receive a `seq`. ADR-0014 says LOCAL_ONLY has causal order only, while ADR-0021
> makes the mailbox a projection of the seq cursor. This spec therefore treats the server's ack for a
> LOCAL_ONLY message as a custody ack (T18, `HOP_ACKED(SERVER_MAILBOX, serverOwed=false)`) and purges
> on E2E delivery. Owner/brief to confirm before LOCAL_ONLY ships (it is already gated on Q-O3).

> **Reviewer note (open) R-2:** brief §6.1 says "any non-terminal → EXPIRED on lifetime". Read
> literally, that would mark a server-accepted message as expired. This spec excludes
> `SERVER_ACCEPTED` and purges it instead (T27/T28), because the server already has custody.

> **Reviewer note (open) R-3:** with per-device fan-out (1.0), a recipient may add a device after the
> sender sealed. The brief has no rejection path for a stale recipient-device list. This spec proposes
> `DEVICE_LIST_STALE` → re-seal for the missing devices; the ICD and ADR-0018 should define it.

> **Reviewer note (open) R-4:** DELIVERED regeneration needs `conversation.delivered_reported_seq`,
> which the brief's §11.2 schema does not list (it elides columns with "…"). `data-model.md` should
> add it, or the server's `heads()` response should return the device's last acked seq instead.

> **Reviewer note (open) R-5:** the brief gives `ServerLink` a single production implementation and
> says no second server link is planned. Today two paths deliberately avoid the socket because they
> run in short-lived Android components: the notification inline reply (`NotificationReplyReceiver`
> → REST `POST …/messages`, #510) and the push-handler DELIVERED ack (#596). With a durable outbox the
> reply can be enqueued and flushed by an expedited background job, and the ack moves behind the Inbox
> commit; but if the owner wants to keep a socket-free submit, that is a `RestServerLink`, a second
> `ServerLink` implementation the brief should name.

> **Reviewer note (open) R-6:** ADR-0011 counts "17 `wsClient.send` call sites". The grep that
> produces 17 includes 2 `sendAck` calls and 1 comment; executable sending sites are 16 (14 `send`, 2
> `sendAck`), plus 4 `incoming` collectors, 2 `connectionState` collectors, 2 focus calls and the
> READ path in `ReadReceipts.kt`, all of which the 0.5 ratchet must also remove. The inventory above is
> complete; only the label "17 call sites" is loose.

> **Reviewer note (open) R-7:** `CONVERSATION_INFLIGHT_WINDOW = 1` is a spec choice to keep strict
> per-conversation order under retries. The brief requires FIFO but does not fix a window; the owner
> may prefer pipelining and accept occasional re-sort.
