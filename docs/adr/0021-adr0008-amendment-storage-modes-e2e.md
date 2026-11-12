# ADR 0021 — Amendment to ADR 0008: storage modes in an E2E world; one cursor model; mailbox as a projection

**Status:** Accepted (2026-11-12, delegated; amended by D3) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); code against this ADR is written only while the matching scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits. The §3 mode redefinitions are a product change covered by owner decision D3; the 1.0 cutover is decided by owner decision D3 (2026-11-12; §4), and LOCAL_ONLY availability depends on Q-O3 · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0021; §9; §11.1 V34; §13 (1.0.0); §17 Q-O3, Q-O4, Q-L9](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Amends** [ADR 0008](0008-per-conversation-storage-mode.md) (strictest-wins rule kept; mode definitions changed) · Depends on ADR-0010, ADR-0013, ADR-0014, ADR-0016, ADR-0017, ADR-0019, ADR-0020. Related: ADR-0025 (custody hierarchy), [T3 design](../design/T3-conversation-storage-modes.md).

## Context

ADR 0008 (2026-08-12) made storage a per-conversation choice with three modes — CLOUD (our servers,
searchable), BRING_YOUR_OWN, LOCAL_ONLY — and the rule that the stricter side wins. It already
stated that LOCAL_ONLY cannot be offered until E2E is genuinely on, and the T3 design recommended
"encrypted store-and-forward, and say so plainly".

Three things now conflict with ADR 0008 as written:

1. **"CLOUD = server-held, searchable" cannot coexist with E2E.** From 1.0 the server holds no
   readable content for E2E conversations (ADR-0019), so it cannot search it.
2. **The judges required one SSOT and one cursor model across eras** (pre-1.0 plaintext, post-1.0
   ciphertext), not a second per-device mailbox cursor beside the conversation `seq`. The
   backend/target-state proposals had a separate per-device mailbox or server outbox; the brief
   rejected that (judge disagreement, "No server outbox table").
3. **Existing plaintext history** on the server at the 1.0 cutover has no defined fate, and a UI
   that labels a conversation "E2E" while the server still holds a readable copy of its history
   would repeat the false-padlock defect ADR 0008 itself cites.

## Decision

### 1. One cursor model in every era

The only client cursor is a **per-(device, conversation) `seq` watermark** over
`GET /api/v1/conversations/{id}/changes?afterSeq=&limit=` (ADR-0014).

| Era / conversation | `/changes` item carries | Source table(s) |
|---|---|---|
| Before 1.0, all conversations | Plaintext content (as today, TLS only, honest "not E2E" UI) | `messages` |
| From 1.0, E2E conversations | The **envelope addressed to the requesting device** | `messages` (metadata) ⋈ `message_envelopes` |
| From 1.0, channels and communities | Plaintext content, labelled server-readable by design (ADR-0016) | `messages` |

- **`messages` stays the authoritative metadata row** for every conversation: `id`,
  `conversation_id`, `seq`, `change_seq`, `sender_device_id`, timestamps, `encryption_state`.
  `content` is **NULL** for E2E conversations.
- **The mailbox is a projection, not a second cursor:**
  `message_envelopes(message_id, recipient_device_id, protocol, ciphertext BYTEA, expires_at, PK(message_id, recipient_device_id))` (V34, 1.0).
- **Deletion:** a device's `ack{conversationId, uptoSeq, DELIVERED}` (ADR-0013) deletes that
  device's envelope rows with `seq ≤ uptoSeq`; a scheduled purge job removes rows past `expires_at`.
  Default TTL **30 days**, configured in `application.yml` (`muhabbet.mailbox.ttl`).
- A device that pulls after its envelopes expired receives the metadata row with an
  `envelopeExpired` marker and shows an honest "message expired before this device received it"
  placeholder; it never sees a silent gap.

```kotlin
// backend out-port (brief §5)
interface MailboxPort {
    fun store(msg: UUID, envs: List<DeviceEnvelope>, exp: Instant)
    fun deleteUpTo(d: DeviceId, conv: UUID, seq: Long): Int
    fun purgeExpired(now: Instant): Int
}
```

### 2. Strictest-wins is unchanged

Strictness order `LOCAL_ONLY` > `BRING_YOUR_OWN` > `CLOUD`, negotiated at creation and
re-negotiated with an explicit prompt, with "whose choice it was" shown — exactly as ADR 0008.

### 3. Mode definitions from 1.0 (owner decision required)

| Mode | ADR 0008 (before) | From 1.0 (this amendment) | Server holds | Off-server custodians (ADR-0025) |
|---|---|---|---|---|
| **CLOUD** | Server-held, searchable | **E2E relay** plus an **optional encrypted history backup under a user-held key** (T3 step 5). The server is **no longer searchable** | Metadata, envelopes until ack/TTL, optional opaque backup blob | Always ends at custodian 1 (server); `serverOwed` until the server acks |
| **BYO** | User's own server | The **same backend self-hosted**, or the user's bucket as the backup destination | Same as CLOUD, on the user's server | Same as CLOUD |
| **LOCAL_ONLY** | Two devices only; gated on E2E | **No backup anywhere.** Envelopes deleted on delivery. Offerable only when E2E is real **and** Q-O3 (receipt-metadata retention) is decided | Metadata + envelopes until delivery or TTL, then nothing but what Q-O3 allows | Custodian 1 with delete-on-delivery; custodians 2–3 only after Q-O3 |

**Constraints on the optional encrypted backup (D3; required before 1.0 entry).**

- The backup key is **client-generated and high-entropy** — for example a displayed recovery code of
  at least 128 bits.
- A user-chosen low-entropy passphrase alone is **forbidden**: with a plain KDF the server, which
  stores the blob, could brute-force it offline (AT-4/AT-5). A passphrase is allowed only if it is
  protected by a hardened, rate-limited scheme approved in its own ADR.
- The encryption construction comes from a vetted library (P12) and is versioned and
  protocol-tagged.
- The backup format and key handling get **their own ADR before 1.0 entry**
  ([OI-38](../architecture/multi-transport/open-items.md#oi-38)) and are in scope of the external
  crypto review (1.0 gate).
- Losing the key means losing the backup, and the opt-in screen says so.

LOCAL_ONLY keeps causal, not total, order (ADR-0014) and is the only mode whose off-server
deliveries are final without a server `seq` (ADR-0010 rule 5).

### 4. Existing plaintext history at the 1.0 cutover (decided: owner decision D3, 2026-11-12)

| Step | Action |
|---|---|
| 1 | From the cutover release, **new** messages in 1:1 and small-group conversations are E2E (protocol v2, ADR-0022) |
| 2 | Each client downloads that conversation's existing history into its encrypted local DB (ADR-0020) by `seq` |
| 3 | Users are notified at the start of the grace period and again before deletion. After a **30-day grace period** the server's readable copy of the conversation is deleted: `messages.content`, server search index rows, cached previews, **the conversation's MinIO media objects and thumbnails**, and **every server-built backup archive containing it** (`BackupService` / `MinioBackupArchiveAdapter` / `message_backups`). Nothing is deleted for a user before T+30 days or before each of their active devices has confirmed the download up to the server head seq (RQ-91, RQ-92). The deletion schedule is also gated on counsel Q-L9 (retention or production duties). If the user opted in to backup, their client, which already holds the history from step 2, encrypts it under the user-held backup key and uploads the opaque blob before the deletion date. The server never encrypts history on the user's behalf |
| 4 | A conversation is **labelled E2E only once the server holds no readable copy of it** — no `content`, no search row, no cached preview, no MinIO media object or thumbnail and no backup archive holding its plaintext — i.e. after step 3 for that conversation (RQ-94) |

Until step 4 completes, the conversation UI says "new messages are end-to-end encrypted; earlier
history is still on the server until <date>".

## Why

- One cursor (conversation `seq`) and one metadata row means every path — WS hint, FCM wake,
  REST pull, proximity (`serverOwed` upload) — converges on the same SSOT (ADR-0011) with one
  gap-detection rule. A second per-device cursor would be a second source of truth for "what has
  this device seen".
- The envelope projection is exactly what a per-device mailbox needs (BE-18) without a server
  outbox table: rows exist only until the device acks or the TTL fires.
- CLOUD as "server-readable" would put two privacy classes inside one 1:1 product and invite
  dishonest UI; redefining it as E2E relay + optional user-keyed backup keeps the convenience
  (survives a lost phone, multi-device via backup) without readable server storage.
- The labelling rule in §4.4 is ADR 0008's own honesty argument applied to the cutover.

## Consequences

- **Server search for 1:1 and small groups ends at 1.0**, so on-device FTS (ADR-0020) must ship
  first; the CLOUD mode description in the UI changes accordingly.
- Multi-device history for E2E conversations comes from device-to-device transfer (post-1.0) or the
  encrypted backup, never from the server (ADR-0018 consequences).
- **5651 reporting relies on reporter-supplied content** for E2E conversations. Message franking is a
  post-1.0 research item that needs legal input (R18, Q-L4).
- Receipt metadata for LOCAL_ONLY has an open retention question that may also carry a legal
  retention duty (Q-O3, Q-L9); LOCAL_ONLY does not ship until both are answered.
- `/messages/since` stays one release as a deprecated shim (ADR-0014); after that `/changes` is the
  only history read.
- V34 adds `message_envelopes` and `messages.encryption_state`, and makes `content` nullable;
  additive first, destructive step (dropping plaintext) one release later (CICD-09).
- R17 (cutover surprises users) is mitigated by notice, grace period and backup opt-in; R44 (history
  loss for users inactive through the whole grace period, or reinstalling without the backup) by the
  download confirmation, repeated notices, the backup prompt and disclosure.
- **Server-side `BackupService` is retired for E2E conversations at 1.0.** Existing archive objects
  are purged; today no code deletes an archive object at `expiresAt` (`EXPIRY_SECONDS` = 7 days is
  only a URL lifetime), which is a **pre-1.0 KVKK storage-limitation defect to file now**
  ([OI-37](../architecture/multi-transport/open-items.md#oi-37); threat-model T-HST-09, DD-07).
- The KVKK **data export** returns metadata only for E2E conversations, and the UI says so (the
  content is on the user's devices).
- Media erasure on the server stays tracked by #327; the cutover deletion covers media objects and
  thumbnails.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| CLOUD as opt-in server plaintext | Two privacy classes in one 1:1 product; dishonest UI risk |
| Per-chat opt-in E2E (Telegram secret chats) | Same objection; users cannot tell which chats are protected |
| Per-device mailbox cursor separate from conversation `seq` | Two SSOTs for "what has this device seen" |
| Backend transactional outbox table as mailbox | Redundant with pull-by-seq (brief §3 judge disagreement) |
| Label conversations E2E at cutover while old history stays readable on the server | False claim |
| Silently delete all server history at cutover | Data loss without notice; R17 |

## Evidence

- Findings: BE-15, BE-18, BE-05 (rejected server outbox alternative), C9, SSOT-09, GOV-08, LEG-02,
  LEG-04, MISS-01 (legal verifier, Law 5809 Art. 9(14) duties).
- Documents: [ADR 0008](0008-per-conversation-storage-mode.md),
  [T3 design](../design/T3-conversation-storage-modes.md) ("Shape of the work" step 5, open
  questions on receipts and retention).
- External (from the legal digest): https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5651.pdf,
  https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf.

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| RQ-13 | No plaintext `content` for E2E conversations after the grace period | SQL assertion + DB dump inspection | 1.0 exit and cutover + 30 d |
| — | `/changes` for device A never returns device B's envelope | Integration test (Testcontainers) | 1.0 |
| — | Device ack `uptoSeq` deletes exactly that device's envelopes `≤ seq`; TTL purge removes expired rows | Integration test with `TestClock` | 1.0 |
| RQ-05 | Lost hints converge through the same `/changes` cursor before and after 1.0 | `SimulatedLink` test | 0.5 / 1.0 |
| — | A conversation is never labelled E2E while any readable server copy exists | UI test against fake server state + claims-register inspection | 1.0 exit |
| — | LOCAL_ONLY is not offered while Q-O3 is undecided | Inspection (feature flag absent from release) | 1.0 |
| — | Backup blob is produced client-side; no server code path reads `content` to build it | Inspection + test | 1.0 |
| RQ-94 | No MinIO object or backup archive holds plaintext of a conversation labelled E2E | MinIO listing + SQL assertion | 1.0 exit and cutover + 30 d |
| — | Server-built backup archives are deleted at `expiresAt` | Integration test with `TestClock` + MinIO listing | as soon as the purge job ships (OI-37) |
| RQ-91/RQ-92/RQ-93 | Download completeness before deletion; notices and no deletion before T+30 d; opt-in backup round-trip under a user-held key the server cannot read | see vv-plan §6.2 | 1.0 |
