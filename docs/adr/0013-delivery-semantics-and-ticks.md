# ADR 0013 — Delivery semantics: durable outbox, persist-then-ack inbox, idempotency, honest ticks

**Status:** Accepted (2026-11-12, delegated) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); code against this ADR is written only while the matching scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0013; §6.1, §6.4](../architecture/multi-transport/decision-brief-2026-11-12.md) · Depends on ADR-0011, ADR-0012, ADR-0014. Supersedes nothing.

## Context

Milestone 0.4 is "the app tells the truth": a tick must mean what it says. Today it does not.

| Finding | Defect |
|---|---|
| MOB-04 | `PendingMessage` is deleted on socket write, not on `ServerAck`; a null session is a silent no-op that still deletes the row; exhausted retries vanish; the rebuilt `SendMessage` drops `viewOnce`, `thumbnailUrl`, `forwardedFrom` (`WsClient.kt:534-569`, `drainPendingMessages`) |
| MOB-03 | DELIVERED is acked when a coroutine sees the frame in RAM, before any durable write |
| MOB-05 | Dedup key drops every edit after the first |
| MISS-01 (mobile verifier) | In a group, one member's READ turns all own messages blue |
| BE-03 | Acks accepted for any `messageId` from any authenticated user; DELIVERED after READ is still broadcast, so ticks regress |
| BE-07 | Idempotency is check-then-insert on a client-chosen *global* PK; a retried send gets an ERROR ack |
| BE-18 | Delivery status is per user, not per device |

With more than one path, at-least-once delivery plus idempotent receivers is the only contract that
survives. An explicit model of hop acks is also needed: the earlier `SubmitResult.Accepted(seq?)`
sketch conflated server commit with a peer hop ack (architecture judge).

## Decision

### 1. Outbox (sender)

- `outbox_entry` holds the full, versioned `OutgoingEnvelope` (no field loss). It is written **in
  the same transaction** as the optimistic `message` row.
- Transitions are compare-and-set (`OutboxStore.cas(id, from, to)`). A row is removed only at a
  terminal state, or after `SERVER_ACCEPTED` once receipts settle.
- States and transitions (brief §6.1):

| From | Event | To | Notes |
|---|---|---|---|
| (new) | Send (text) | QUEUED | Optimistic row + entry in one transaction |
| (new) | Send (media) | PREPARING | Separate media worker; does not block the conversation FIFO |
| PREPARING | UploadDone(blobRef) | QUEUED / PENDING_SEAL | Resumable upload, 1 MiB ranges |
| PREPARING | UploadFailed(retryable) | PREPARING (backoff) | |
| PENDING_SEAL | SealFailed(NoSession) | PENDING_SEAL | Never a plaintext fallback (ADR-0016); triggers prekey fetch |
| PENDING_SEAL | SealFailed(UntrustedIdentity) | PENDING_SEAL(reason = UNTRUSTED_IDENTITY) | Left only by a user decision event (ADR-0016) |
| PENDING_SEAL | SealFailed(Failed(code)), retryable | PENDING_SEAL(reason = SEAL_RETRY, backoff) | Never `RETRY_WAIT`: an unsealed E2E entry must not become dispatchable or hold the head (spec T12, I4) |
| PENDING_SEAL | SealFailed(Failed(code)), permanent | FAILED_PERMANENT(code) | Decided by the code (ADR-0016) |
| IN_FLIGHT | Rejected(`MSG_DEVICE_SET_STALE`) | PENDING_SEAL | Re-seal for the new device set (spec T25b) |
| PENDING_SEAL | SessionReady | QUEUED | |
| QUEUED | Dispatch(link) | IN_FLIGHT(link, attempt, deadline) | Direct call ≤ 1.1, `PathPolicy` ≥ 1.2 |
| IN_FLIGHT | ServerAccepted(seq) | SERVER_ACCEPTED(seq) | Message row gets its `seq` |
| IN_FLIGHT | HopAcked(peer / own device / mule) | HOP_ACKED(custodian, serverOwed = mode ≠ LOCAL_ONLY) | |
| HOP_ACKED(serverOwed = true) | ServerLinkUp | QUEUED (server only) | Upload obligation; recipient dedups |
| HOP_ACKED(serverOwed = false) | E2EDelivered | purge | LOCAL_ONLY |
| IN_FLIGHT | Timeout / LinkLost / Retry | RETRY_WAIT(nextAttemptAt) | 1 s → 30 s, full jitter, injected `Clock`; reset on an eligible LinkUp |
| IN_FLIGHT | Rejected(code) | FAILED_PERMANENT(code) | Bubble shows "tap to retry"; `RetryDelivery` → QUEUED (only from FAILED_PERMANENT; for EXPIRED the UI offers "resend as new") |
| EXPIRED / FAILED_PERMANENT | SeqObserved(seq) or late ServerAccepted(seq) | SERVER_ACCEPTED(seq) | A lost ack followed by expiry must not leave a message the server holds labelled "not sent"; same tick rule as spec T16 |
| any non-terminal except SERVER_ACCEPTED | lifetime exceeded | EXPIRED | Per-command lifetime (spec §4.5): default 30 d, call signalling 60 s; SERVER_ACCEPTED is purged at lifetime instead |
| QUEUED / PENDING_SEAL, never submitted (`attempt = 0`, not HOP_ACKED) | user deletes | CANCELLED | Row and entry deleted |
| QUEUED / RETRY_WAIT / IN_FLIGHT, submitted before (`attempt > 0`) | user deletes | same state, `cancel_requested = 1` | Spec T32: resolved by a server probe or the next ServerAccepted/SeqObserved (then a DELETE is enqueued, withdrawing the message from everyone), or by a definitive non-acceptance (then CANCELLED). An earlier submit may have been committed with its ack lost, so deleting the row would leave a message the recipient has and the sender cannot see |
| IN_FLIGHT at process start | Recover | QUEUED | Safe because the server is idempotent |

DELIVERED and READ are **not** outbox states. They are derived from receipt rows.

**0.4 stopgap** (ships before the engine): `PendingMessage` is deleted only on
`ServerAck(requestId)`. The `MAX_RETRY_COUNT = 5` drop at `WsClient.kt:540-542` is removed in the same
change: an exhausted row is marked FAILED and shown with "tap to retry", never deleted, so 0.4 no
longer loses a message silently after five retries. Nothing else in the old queue is redesigned.

### 2. Inbox (receiver)

One transaction per inbound event:

1. Upsert the row: insert, or update only if the incoming `change_seq` is newer than the stored one
   (edits, deletes, reactions are last-writer-wins by `change_seq`, which also fixes MOB-05).
2. Advance the per-conversation change-feed watermark `w` (ADR-0014) and, where rule R-ACK allows,
   the **complete-through point** `C = conversation.complete_through_seq` in the same transaction.
   `C` advances only when a pull ends with `hasMore = false` (`C = lastSeq`) or when a contiguous
   `message.new` extends `w` from `C` (ICD §3.8 R-ACK).
3. Commit.
4. **Only after the commit**, enqueue the DELIVERED receipt (CONTROL class) with `uptoSeq = C`.

DELIVERED and READ `uptoSeq` are **always** `C` — never `w`, never `max(seq ≤ w)`, never a pushed or
mid-pull value. An edited old message has a low `seq` and a high `change_seq` and arrives on a later
page; an ack bounded by `w` would cover it unstored, and from 1.0 that ack deletes the device's mailbox
envelope (ADR-0021), a permanent loss. On every Ready/Authenticated transition the client sends
`message.ack{conversationId, uptoSeq = C, DELIVERED}` for each conversation whose `C` exceeds the
last reported value (`conversation.delivered_up_to_seq`, data-model §5). A crash between step 3 and
step 4 therefore delays the tick but never loses it.

Off-server frames (1.2+) without a `seq` are deduplicated by `inbound_dedup(src_device,
frame_or_message_id, seen_at)` with a TTL at least the maximum message lifetime.

### 3. Server idempotency

- `UNIQUE (sender_device_id, client_message_id)` (V29) is added **beside** the kept identity rule
  `messages.id = client_message_id` (global PK); the PK check is not replaced (ICD §3.6 A-2/A-2b;
  the alternative is [OI-10](../architecture/multi-transport/open-items.md#oi-10)).
- Order of operations, in one transaction (ICD §3.6 A-1): **select** by
  `(sender_device_id, client_message_id)` → found: replay; not found: **allocate** the seq, then
  **insert** with no `ON CONFLICT`; on a unique violation roll the whole transaction back (the
  allocation with it, so no seq value is spent) and reselect. `INSERT … ON CONFLICT DO NOTHING` is
  not used, because it spends a seq value on every replay.
- A duplicate submit **replays** the original `ServerAck(seq, serverTs, replay = true)`; it is never
  `MSG_DUPLICATE` and never echoes exception text (BE-07).

### 4. Acks

- Wire: `message.ack{conversationId, uptoSeq, kind}` — cumulative, per device.
- Accepted only for `seq` values that exist (BE-03, BE-18), and split by kind (ICD §3.8(b)):
  - **tick-affecting** DELIVERED/READ — only from a registered, non-revoked device of a member who is
    not the sender;
  - **watermark/custody** acks (`ack uptoSeq`) — from any registered, non-revoked device of any
    member, the sender's own companion devices included; from 1.0 they drive mailbox deletion
    (ADR-0021) and they are excluded from tick aggregation.
- Monotonicity is enforced in one conditional UPDATE; a status change is broadcast only when a row
  actually changed.
- Levels:

| Level | Meaning | Source |
|---|---|---|
| L1 hop ack | A hop took custody | `ServerAck(seq)` from the server, or `HopAck{frameId}` from a peer device or custodian |
| L2 E2E DELIVERED | The recipient device committed the message | `ack{…, DELIVERED}` after inbox commit; off-server, a sealed receipt frame, also uploaded to the server when reachable |
| L3 READ | The chat was visible | `ack{…, READ}`; server applies the `readReceiptsEnabled` downgrade (#377) at publish time |

- The sender's status is computed from the server's per-device rows. The client never promotes to
  READ because of one member's status update (group fix).

### 5. Ticks — one meaning on every path

| Indicator | Meaning |
|---|---|
| Clock | Not in server custody and not delivered to any recipient device |
| One tick | Server custody (`seq` assigned) |
| Two ticks | Every recipient user has at least one device that committed (L2) |
| Read | L3, subject to the server-enforced `readReceiptsEnabled` downgrade |
| Two ticks + "not yet synced" | Delivered off-server directly to the recipient device; no `seq` yet |
| Clock + "handed to ‹device/contact›" | Handed to an own device or uplink mule (custody, not delivery) |
| — | There is never a "relayed to mesh" tick |

## Why

- At-least-once with idempotent receivers is the only contract that survives multiple paths
  (principle P3); exactly-once transport does not exist.
- Persist-then-ack (P4) is what makes two ticks true (MOB-03).
- Replaying the ack turns the standard lost-ack retry into a no-op instead of a false failure or a
  duplicate (BE-07).
- Modelling `HopAcked` separately from `ServerAccepted` keeps custody and delivery distinct, which
  is what stops a mule hand-over from being shown as delivery.

## Consequences

- The UI gets a real FAILED state with "tap to retry".
- Receipts remain **server-visible metadata** at 1.0. The server needs them to delete mailbox rows
  anyway, so #377 read-receipt enforcement keeps working server-side and is not silently weakened.
  This trade is written down in ADR-0019.
- `PendingAckQueue` regenerates read receipts on chat open; read receipts are not persisted on the
  client.
- Backend: V29 adds `messages.sender_device_id` and the device-scoped unique key; V32 (0.6) adds
  `message_device_delivery(message_id, device_id, status, updated_at)`.
- Mobile 0.5 schema: `outbox_entry`, `inbound_dedup`, `receipt` tables (brief §11.2).

> **Resolved (2026-11-12):** the split is adopted in Decision §4 above and matches ICD §3.8:
> tick-affecting receipts only from non-sender members' devices; watermark/custody acks from any
> registered, non-revoked device of any member, the sender's own included, excluded from ticks
> (brief erratum E-39). At 0.4 (one device per user) only the first rule is exercised.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Patch `PendingMessage` | Loses fields, no FAILED state, no CAS transitions (MOB-04) |
| Exactly-once transport | Not achievable across independent links; idempotency gives effectively-once to the user |
| Long-term signatures on receipts | Non-repudiable; conflicts with deniability |
| Gemini `Relayed_To_Mesh` tick | Claims delivery that did not happen |
| Persist read receipts on the client | Unnecessary; regenerated on chat open |

## Evidence

- Findings: MOB-03, MOB-04, MOB-05, MISS-01 (mobile verifier), BE-03, BE-07, BE-18, GEM-03.
- Code: `mobile/.../data/remote/WsClient.kt:534-569` (`drainPendingMessages`), `mobile/.../App.kt:135-156` (global DELIVERED pump),
  `backend/.../MessagePersistenceAdapter.kt:53-63` (per BE-03 verification); issues #299 (BE-03, ack authorisation; per backend digest) and #669 (post-commit fan-out).

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| RQ-01 | No message lost after send | `SimulatedLink` property suite: 10k seeded runs with loss, duplication, reorder, partition, process death, 24 h offline, token expiry | 0.5 exit |
| RQ-02 | Each message appears exactly once in sender and receiver stores | Same suite | 0.5 exit |
| RQ-03 | Double tick only after the recipient DB holds the row | Crash-point injection between commit and ack; owner on emulator with two test numbers | 0.4 exit |
| RQ-03 | Kill after inbox commit, before the DELIVERED ack: the ack is sent after relaunch | Crash-point test | 0.5 |
| — | Kill mid-send gives no loss or duplicate after relaunch | Owner on emulator-5554 (P13) | 0.4 exit |
| — | Duplicate submit returns the original `seq` with `replay = true` | Testcontainers integration test | 0.4 |
| — | An ack from a non-member or for an unknown `messageId` is rejected and not broadcast; DELIVERED after READ is not broadcast (BE-03) | Integration test | 0.4 |
| — | `uptoSeq` greater than the conversation's `last_seq` is rejected | Integration test | 0.5 |
