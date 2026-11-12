# ADR 0014 — Ordering: per-conversation server seq plus change feed; client causal header; no HLC authority, no Merkle-DAG

**Status:** Proposed — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0014; §10; §11.1](../architecture/multi-transport/decision-brief-2026-11-12.md) · Depends on ADR-0010 (single sequencer). Supersedes nothing.

## Context

The server orders messages by `serverTimestamp` alone (BE-06, C13). Verified consequences:

- `val now = Instant.now()` is taken before the insert, so a slow transaction commits a row older
  than one already synced and `since` sync misses it permanently.
- History uses strict `<` and since-sync strict `>` with no tie-break column, so equal timestamps
  at a page boundary are skipped.
- `findUndeliveredForUser` caps at 500 rows; a burst above 500 at one instant is lost.
- An unparseable cursor becomes `null` (duplicates instead of a 400); `getMessages` ignores its
  `direction` parameter (BEV-M2).
- Scheduled messages are visible to recipients before they are due (BE-02).
- Background sync uses the **device clock** as its watermark (MOB-10).

There is no monotonic, gap-detectable order, so no client can know whether it has missed anything.

Gemini proposed a Hybrid Logical Clock as the ordering authority plus a signed, hash-chained
Merkle-DAG. The review found the HLC has no drift bound — one peer with a far-future clock poisons
every clock it touches (C4, GEM-10) — and its compact encoding is not sortable (GEM-12); and that a
signed hash-chained history destroys deniability and conflicts with KVKK erasure and disappearing
messages (C5, GEM-13).

## Decision

### 1. Server sequence (V28–V29, 0.4, additive)

| Column | Meaning |
|---|---|
| `conversations.last_seq BIGINT NOT NULL DEFAULT 0` | Allocation counter |
| `messages.seq BIGINT` | Position of the message; `UNIQUE (conversation_id, seq)`; `NULL` for unreleased scheduled messages (V29) |
| `messages.change_seq BIGINT` | Last change to the row (insert, edit, delete, reaction, expiry) |

- Allocation happens inside the send transaction:
  `UPDATE conversations SET last_seq = last_seq + 1 WHERE id = ? RETURNING last_seq`
  (`ConversationSequencePort.next(conversationId)`, called inside the caller's transaction).
- An insert sets `seq = change_seq = allocated`. Edits, deletes, reactions and expiries allocate a
  new value and stamp only `change_seq`.
- **Invariant:** the row lock on `conversations` is held until commit, so values become visible in
  allocation order per conversation; a reader can never see `n + 1` committed while `n` is still
  pending.
- Backfill of existing rows orders by `(server_timestamp, id)` and sets `seq = change_seq`.

### 2. Change feed — the authoritative read

- `GET /api/v1/conversations/{id}/changes?afterSeq=&limit=` → `{items, nextAfterSeq, lastSeq, hasMore}`,
  ordered by `change_seq`, membership-checked, hiding unreleased scheduled messages and any other
  per-requester-hidden change.
- The cursor is **server-driven**. `nextAfterSeq` is the highest `change_seq` the server scanned for
  this request, visible to the requester or not. `lastSeq` (in `/changes`, the conversation list and
  hints) is the conversation's allocation high-water (`conversations.last_seq`).
- The conversation list returns `lastSeq` per conversation.
- WS `message.new` and FCM are **hints** carrying `(conversationId, seq)` (`sync.hint` frame,
  ADR-0022). Pull is truth, push is a hint (P6).
- The client watermark is over `change_seq` and is set to `nextAfterSeq`, **never** to
  max(`item.change_seq`): changes the requester cannot see (an edit or reaction to an unreleased
  scheduled message, BE-02) still advance `last_seq`, so an item-derived watermark could never reach
  it and the client would pull forever.
- A gap is `watermark < hinted seq` or `watermark < listed lastSeq`, **not** a hole in contiguity:
  superseded edits and hidden changes leave holes that are not losses. The gap is closed when the
  server answers with `nextAfterSeq ≥` that value.
- Scheduled messages get their `seq` when released, each in its own transaction (BEV-M1). They are
  invisible before release, which closes BE-02 structurally.

### 3. Client causal header (inside E2E plaintext from 1.0)

```kotlin
data class CausalHeader(
    val senderDeviceId: DeviceId,
    val deviceCounter: ULong,       // monotonic per (device, conversation)
    val clientMessageId: Uuid,      // UUIDv7; never on the outer wire (ADR-0022)
    val senderTimeMs: Long,         // advisory only
)
```

- `deviceCounter` is assigned at **seal time** (QUEUED → IN_FLIGHT), never at enqueue, so a
  CANCELLED or EXPIRED outbox entry (ADR-0013) leaves no counter gap.
- A gap in `deviceCounter` from a sender device triggers **at most one** pull per (sender device,
  gap) and never blocks display. A replayed counter is dropped.

### 4. Display order

1. Rows with a `seq` sort by `seq`.
2. Rows without a `seq` (off-server, 1.2+) sort by
   `(lastKnownSeq, senderDeviceId-local receive order, deviceCounter)`.
3. Sender time is clamped to `[receivedAt − MAX_SKEW, receivedAt]` with `MAX_SKEW = 5 min`
   (companion-object constant), for display only.
4. Rows re-sort stably once their `seq` arrives.
5. LOCAL_ONLY conversations have per-sender causal order only, and the UI says so.

## Why

- One sequencer (ADR-0010) gives total order per conversation with one SQL statement; gap detection
  becomes a comparison of two integers.
- An HLC on top of a single sequencer adds complexity without adding a guarantee. Unbounded it is
  poisonable (C4, GEM-10); bounded it still needs the server to settle order. Target-state's bounded
  HLC is therefore not adopted.
- `deviceCounter` gives the causal and replay check that off-server paths need without signatures
  (C5).
- Pull-by-seq makes every lost WS frame or push harmless, which is why no backend transactional
  outbox table is added (brief §3 judge disagreement; BE-05 is answered by this, not by an outbox).

## Consequences

- Cursor pagination moves from `serverTimestamp` to `seq` (also BEV-M2). A malformed cursor returns
  400.
- `/messages/since` stays one release as a deprecated shim, then is removed.
- Background sync uses the server `seq`, not the device clock (MOB-10).
- Hot-row contention on `conversations.last_seq` is accepted at current scale (risk R12). Measure p99
  send latency before considering per-channel sharding.
- Off-server messages may visibly re-sort once (risk R16).
- ADR-0021's mailbox is a projection of this cursor, not a second cursor.

> **Reviewer note (open):** The brief speaks of "seq-gap pulls" without defining a gap. Because edits,
> deletes and reactions consume `last_seq` values and overwrite a row's `change_seq`, the stream a
> client sees has holes that are not losses. This ADR therefore defines gap detection as
> *watermark < hinted seq or listed `lastSeq`* (Decision §2), not as contiguity, and makes the
> cursor server-driven (`nextAfterSeq`), because changes hidden from a requester still advance
> `last_seq`. Owner/brief to confirm; the ICD must state the same rule, including the
> `nextAfterSeq` field and "watermark := `nextAfterSeq`".

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Gemini Merkle-CRDT with HLC | C4, C5, GEM-10, GEM-12, GEM-13 |
| Bounded HLC in every message (target-state) | No guarantee beyond seq + counter; extra state on every path |
| Vector clocks | Grows with devices; unnecessary with one sequencer |
| Global per-user inbox sequence | Revisit only if conversation-list fan-in becomes a measured cost |
| Backend transactional outbox table for fan-out (BE-05) | Pull-by-seq makes push loss harmless at lower cost; revisit if hint latency is measured unacceptable |

## Evidence

- Findings: BE-02, BE-05, BE-06, BEV-M1, BEV-M2, C4, C5, C13, GEM-10, GEM-12, GEM-13, MOB-10.
- External sources (from the crypto/sync digest): https://cse.buffalo.edu/tech-reports/2014-04.pdf
  (HLC paper), https://www.usenix.org/conference/hotcloud15/workshop-program/presentation/demirbas.

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| RQ-04 | 50 concurrent senders produce gapless, unique `seq` per conversation | Testcontainers (Postgres), Docker running | 0.4 exit |
| RQ-05 | Dropping 100% of hints still converges via pull on reconnect, foreground or WorkManager | `SimulatedLink` test | 0.5 exit |
| — | An unreleased scheduled message never appears in `/changes` | Integration test | 0.4 |
| — | An edited unreleased scheduled message does not cause repeated pulls by recipients (`nextAfterSeq` reaches `lastSeq`) | Testcontainers integration test | 0.5 |
| — | A cancelled outbox entry causes no counter gap and no repeated pulls | `SimulatedLink` test | 0.5 |
| — | A far-future `senderTimeMs` cannot move a row out of `[receivedAt − 5 min, receivedAt]` | Unit test with `TestClock` | 0.5 (logic) / 1.2 (off-server rows) |
| — | Watermark never derives from the device clock | Code inspection + unit test | 0.5 |
