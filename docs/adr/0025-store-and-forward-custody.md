# ADR 0025 — Store-and-forward and indirect mode: custody hierarchy, contacts-first bundles, single-hop sealed uplink; multi-hop mules are research

**Status:** Proposed (gated) — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). Custodian 1 is delivered by ADR-0014/0021; custodians 2–3 start only when the 1.3 entry gate (brief §13) is met, and custodian 3 is droppable · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0025; §6.1, §6.2, §8.2, §9, §10 (UplinkBundle), §11.1 (V36), §13 (1.3)](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Milestones:** 0.5/1.0 (custodian 1), 1.3 "Relay" (custodians 2–3), research R-DTN (custodian 4) · Depends on ADR-0010, ADR-0013, ADR-0014, ADR-0016, ADR-0018, ADR-0021, ADR-0022, ADR-0024, ADR-0029, ADR-0031. Relates to [ADR 0008](0008-per-conversation-storage-mode.md) (as amended by ADR-0021).

## Context

The owner wants an INDIRECT mode: a message reaches its recipient even when sender and recipient are
never online at the same time, "via other devices" if needed.

- **INDIRECT already exists** in its strongest form: the server stores messages and the recipient
  pulls them (`/messages/since` today, `/changes` by seq from 0.5, ciphertext `message_envelopes`
  from 1.0 — ADR-0014, ADR-0021). It has legal clarity: one controller, one takedown path (LEG-02).
- **Gemini's DTN layer is not usable.** `sprayToNearbyCustodyPeers` is not Spray-and-Wait: duplicate
  peers, full copies, no custody handshake, no dedup, no quota, no metadata protection (GEM-09). The
  wire frame has no addressing, TTL or authenticated header (GEM-08). Custody bundles carry the
  recipient tag in clear, so every mule can link sender to recipient (C7). Custody semantics
  misstate RFC 9171, which moved custody out of the core protocol (C6). PoW admission punishes
  phones (C6, brief §12 #52).
- **Stranger custody fails both law and security.** Relaying users hold third parties' personal
  data (recipient identifiers, sizes, timing) even when the payload is E2E. On the reviewer's reading
  (LEG-04, partially confirmed), Muhabbet would be the controller of relaying and relaying users
  would be neither household-exempt nor contracted processors; Q-L5 asks counsel to confirm. App-level stranger meshes were broken cryptographically twice (PROX-03).
- **Today's E2E is NoOp with a plaintext fallback** (C9). Any custodian other than the server would
  hold plaintext until 1.0 and ADR-0016.

## Decision

### 1. Bundles (concepts borrowed from BPv7, codec not)

An INDIRECT delivery is custody of a **bundle**: the normal per-device E2E envelope(s) of one
message plus a lifetime.

| Concept | Muhabbet meaning |
|---|---|
| Lifetime | Bounded by the outbox lifetime (30 d default) and the custodian's own TTL (uplink cache 24 h) |
| Bundle age | Elapsed time carried by custodians, so clockless or skewed nodes still expire bundles |
| Status reports | `RECEIVED`, `CUSTODY_ACCEPTED`, `DELIVERED`, `DELETED_EXPIRED` |
| Custody release | Only on an **authenticated** `DELIVERED`, on **authenticated transfer to a higher-ranked custodian** (server 2xx over TLS = `CUSTODY_ACCEPTED` by custodian 1), or on expiry |

No BPv7 CBOR codec and no BPSec are adopted (ADR-0022).

> **Reviewer note (open):** brief §6.2 says custody is released "only on authenticated DELIVERED or
> expiry", which is narrower than its own uplink rule (the mule deletes on the server's 2xx) and the
> custodian-2 release on `ServerAccepted`. This ADR states the release rule that matches both; brief
> §6.2 should be amended to name handover to a higher-ranked custodian.

### 2. Custodian hierarchy (offered strictly in this order)

| # | Custodian | Milestone | Holder | What the holder can see | Release | Gate |
|---|---|---|---|---|---|---|
| 1 | **Server mailbox** (`messages` + `/changes`; `message_envelopes` from 1.0) | exists; ciphertext from 1.0 | Muhabbet | Metadata only from 1.0 (ADR-0019) | Device `ack uptoSeq`, or TTL (30 d) | none |
| 2 | **Own linked devices** (e.g. the user's tablet on LAN/Nearby) | 1.3 | The same user | Their own ciphertext; no third-party data | Recipient-device DELIVERED, or upload to server and `ServerAccepted` | 1.3 entry |
| 3 | **Single-hop sealed uplink** through a **verified contact's** online phone | 1.3, gated, droppable | A verified contact (the "mule") | Sender identity (from link authentication), size bucket and time; never the recipient or the content | Mule: on HTTP 2xx from `/api/v1/uplink`, or after 24 h | 1.3 entry + kill criteria |
| 4 | **Multi-hop contact mules** | research (R-DTN), not scheduled | Mutual contacts only, never strangers | Rotating tags, Padmé-padded blobs | Sealed "vaccination" receipts | New ADR |

Groups above the off-server cap (in 1.2/1.3: every group), channels and communities **never** use
custodians 2–4 (ADR-0029 rule 2).

### 3. Port and policy

```kotlin
// :mobile:messaging (ADR-0012); second real implementation arrives at 1.3 (P7)
interface CustodyTarget {
    val kind: CustodianKind                         // SERVER_MAILBOX | OWN_DEVICE | CONTACT_UPLINK
    suspend fun offer(b: Bundle): CustodyResult     // Accepted(custodian, expiresAt) | Declined(reason) | Retry(after)
}
```

- `CustodianKind` has four values: `SERVER_MAILBOX | PEER_DEVICE | OWN_DEVICE | CONTACT_UPLINK`
  (= delivery-engine-spec §2). `PEER_DEVICE` is a **hop custodian** recorded only by
  `OutboxState.HopAcked` when a direct peer acknowledges a hop; it is **not** a `CustodyTarget`
  implementation and is never offered a bundle by `CustodyPolicy`.

- `ServerMailboxCustody` (via `ServerLink`) is the only implementation before 1.3. **Before 1.3 the
  Outbox does not route through `CustodyTarget` at all** (brief §5, P7 check).
- `CustodyPolicy` (1.3) offers custodians in hierarchy order and applies `QuotaRule` and the
  admission rules of ADR-0015. A custodian is offered only when no higher-ranked custodian and no
  direct path to the recipient device is available; "reachable but slow" is a deferral, not a
  custody trigger (MISS-02, Gemini verifier).

### 4. Bundle custody state machine (brief §6.2)

| From | Event | To |
|---|---|---|
| CREATED | `offer` to next custodian | OFFERED(custodian) |
| OFFERED | `Accepted(expiresAt)` | IN_CUSTODY(custodian, expiresAt) |
| OFFERED | `Declined` | CREATED (next custodian in order) |
| IN_CUSTODY | authenticated `DELIVERED` | DELIVERED_RECEIPT |
| IN_CUSTODY | `CUSTODY_ACCEPTED` by custodian 1 (server 2xx over TLS) | RELEASED |
| DELIVERED_RECEIPT | custodian deletes | RELEASED |
| IN_CUSTODY | lifetime or custodian TTL exceeded | EXPIRED |

On the sender, a successful handover is `HOP_ACKED(custodian, serverOwed)` in the outbox (ADR-0013):
the bubble keeps the **clock** with the secondary label "handed to ‹device/contact›". There is never
a "relayed" tick.

### 5. Custodian 2: own linked devices (1.3)

1. Holds bundles addressed to that user's **other** devices, received over `LanLink`/`NearbyLink`
   (ADR-0024).
2. Delivers them to the target device when it appears, or **uploads them to the server** when the
   holder has internet (the bundle is the sender's per-device envelope set; the holder adds nothing).
3. Uses the separate encrypted custody store (§7). No new KVKK controller question: the data
   belongs to the same user.
4. An own-device custodian hands the bundle up through the **same ingress as custodian 3**: the
   sender builds an `UplinkBundle` (sealed, sender-device signature, §6 step 1) and the holder POSTs
   it to `/api/v1/uplink` under its own auth. The server checks the signature and that holder and
   sender belong to the same user, then ingests idempotently (§6 step 5). There is no second
   on-behalf submit path.

> **Reviewer note (open):** the brief leaves open how the server authorises and attributes a
> submission by device B for an envelope set produced by device A (`SendMessageUseCase` takes the
> authenticated device as sender; the idempotency key is `(sender_device_id, client_message_id)`).
> Rule 4 closes it by reusing the uplink ingress; it shares the signature-key question raised below
> for custodian 3.

### 6. Custodian 3: single-hop sealed uplink (1.3, gated, droppable)

| Step | Actor | Action |
|---|---|---|
| 1 | Sender (no connectivity) | Builds `UplinkBundle = HPKE-Seal(serverUplinkPk, info="mbt-uplink-v1", aad=bucketSize, pt = senderDeviceSignature ‖ routing ‖ DeviceEnvelopes)` (ADR-0022, brief §10); pads to a bucket |
| 2 | Sender → mule | Hands the blob over an authenticated proximity link (sender–mule hop: ADR-0024 E3 (verified safety number with the mule), E4 (certificates), E5 (consent, Nearby mode), E6; the bundled message itself must pass E1 and E7 for its own conversation) |
| 3 | Mule | Stores the opaque blob in its custody store, within its **per-mule quota** (default ≤ 50 bundles / 10 MB per 24 h, `application.yml`/client config); cannot read or route it |
| 4 | Mule → server | `POST /api/v1/uplink` under the **mule's own auth**; deletes the blob on 2xx, or after 24 h |
| 5 | Server | Unseals with the uplink private key; verifies the sender-device signature (preserves 5651 attribution); enforces per-sender and per-mule quotas (V36 `uplink_quota`); ingests through the normal **idempotent** `SendMessageUseCase` (`UNIQUE(sender_device_id, client_message_id)`, ADR-0013) |
| 6 | Sender (later online) | Its `HOP_ACKED(serverOwed=true)` entry re-queues to the server; the server replays the original `ServerAck(seq, replay=true)` |

Rules:

1. The mule never delivers directly to the recipient (that would reintroduce offline ordering and
   recipient-visible routing).
2. No stable recipient identifier exists anywhere in the blob or in mule-side storage (C7).
3. **Uplink key:** a long-lived server secret, created only once ADR-0031 is in force, **never** on
   the CI/production host (Q-O7). Published on a rotation schedule (V36 `uplink_keys`); clients pin
   the current and next key.
4. Abuse controls: per-mule daily quota and per-bundle size cap enforced by **both** the mule and the
   server; per-sender upload quota on `/uplink`; no Proof-of-Work.

### 7. Storage and erasure

| Store | Owner | Protection | Eviction / erasure |
|---|---|---|---|
| Sender outbox | sender | SQLCipher (ADR-0020) | terminal state or 30 d |
| Server mailbox | server | ciphertext from 1.0 | ack or TTL |
| `custody.db` (own-device custody and mule cache) | holder device | **separate** SQLCipher store, own key, own quota | FIFO-by-expiry; wiped immediately on leaving relay mode; wiped by `SessionTeardown` |

### 8. Relation to ADR 0008 (as amended by ADR-0021)

| Mode | Custodian 1 | Custodians 2–3 | Final when |
|---|---|---|---|
| CLOUD | always (end point) | allowed in 1.3 | server assigns seq (`serverOwed` until then) |
| BYO | always (self-hosted backend) | allowed in 1.3 | same |
| LOCAL_ONLY | delete-on-delivery mailbox | **only once Q-O3 (receipt-metadata retention) is decided** | recipient E2E DELIVERED |

### 9. Research only: R-DTN multi-hop contact mules (not scheduled)

Recorded so the parameters are not re-litigated; building any of it needs a new ADR, a DPIA, a
counsel opinion (Q-L5) and an external crypto review:

- Binary Spray-and-Wait with an L-copy budget **≤ 4**; rotating recipient tags; Padmé padding.
- Mule store quotas ≤ 20 MB / ≤ 200 bundles total, ≤ 10 per origin token.
- Admission by **server-issued anonymous rate tokens** (Privacy-Pass-style, N per account per day);
  the token issuer is built only if R-DTN or sealed sender is scheduled.
- Sealed "vaccination" receipts. Mutual contacts only. Strangers never.

## Why

- The mailbox already is INDIRECT mode, with one accountable controller (LEG-02, MISS-01 legal).
- Own-device custody is the concrete second `CustodyTarget` with no third-party data (P7).
- The sealed uplink is the smallest honest "via other devices" path: the mule sees an opaque blob,
  so C7 and GEM-08 are removed by construction, and ingestion reuses server idempotency and ordering
  instead of inventing offline ordering.
- Stranger custody fails KVKK (LEG-04) and abuse resistance (PROX-03, C6, GEM-09).

## Consequences

- **Kill criteria (1.3):** drop the uplink if 1.2 telemetry shows fewer than an owner-set share of
  Nearby sessions in which one side lacked internet (Q-O5), or if counsel or the crypto review
  objects. Collecting that telemetry is itself a DPIA trigger (ADR-0029 rule 6).
- New backend surface `/api/v1/uplink` (`UplinkIngress`, V36) and an operational runbook for the
  uplink key.
- A user acting as a mule spends storage and data for contacts; quotas and a visible "relay mode"
  switch bound it.
- Entry to 1.3 requires: 1.2 shipped; telemetry above threshold; external crypto review of the
  uplink sealing; written KVKK opinion on contacts transiently holding ciphertext; DPIA.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Gemini epidemic routing / `sprayToNearbyCustodyPeers` | Not Spray-and-Wait; storage DoS; no quotas or metadata protection (GEM-09, LEG-04) |
| Custody table with a clear `target_peer_id_hash` | Mules link sender to recipient (C7) |
| Proof-of-Work admission | Punishes phones; replaced by anonymous rate tokens if ever needed (C6) |
| Mule delivers directly to the recipient | Reintroduces offline ordering and recipient-visible routing |
| Reed-Solomon / FEC on bundles | Wrong layer; packets are lost whole (GEM-07, C12) |
| Stranger custodians | KVKK controller/processor gap; Bridgefy-class attacks (LEG-04, PROX-03) |

## Evidence

- Findings: GEM-03, GEM-07, GEM-08, GEM-09, MISS-02 (Gemini verifier), C6, C7, C9, C12, PROX-03,
  PROX-10, LEG-02, LEG-04, MISS-01 (legal verifier), BE-05 (backend outbox not adopted; brief §3
  judge disagreements), BE-07, BE-18.
- External sources (from the crypto-sync and proximity digests):
  - https://www.rfc-editor.org/rfc/rfc9171.html
  - https://signal.org/blog/sealed-sender/
  - https://eprint.iacr.org/2021/214
  - https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf (KVKK)

## Verification

| Req | Check | Method | Signed off by | When |
|---|---|---|---|---|
| RQ-01 | No bundle is lost between sender outbox and server ingest across crash points | `SimulatedSealedLink` multi-node property test (sender, own device, mule, server fake) | CI | 1.3 |
| — | Custody is released only on authenticated delivery or expiry | Invariant in the simulation harness (brief §16.2) | CI | 1.3 |
| — | Phone offline, tablet online: a message reaches the phone through the tablet over LAN and is E2E-acked | Demonstration | **Owner** | 1.3 exit |
| — | A phone with no connectivity delivers through a contact's online phone; the server attributes it to the true sender device | Demonstration + server test | Owner + CI | 1.3 exit |
| — | The mule store holds only opaque blobs; no stable recipient identifier | Test (inspect `custody.db` contents) | CI | 1.3 exit |
| — | Quota abuse stays bounded (mule and server sides) | Test | CI | 1.3 exit |
| RQ-02 | Uplinked and later directly uploaded copies of one message produce one row | Idempotency test (`replay = true`) | CI | 1.3 |

> **Reviewer note (open):** the uplink step "the server verifies the sender-device signature" does
> not say **which key** signs or over **what bytes**. The brief elsewhere forbids long-term
> signatures on messages for deniability (§8.3) and reserves the link key for link-layer
> authentication only (ADR-0018 §4). A signature by the device identity key over the routing plus
> ciphertext envelopes is server-visible only and does not sign plaintext, so it is compatible in
> spirit, but it is a new use of a long-term key. Recommend: a domain-separated signature
> (`"mbt-uplink-sig-v1"` prefix, length-prefixed encoding, GEM-13) whose key and scope are fixed by an
> ADR-0018 amendment and approved by the 1.3 crypto review.

> **Reviewer note (open):** the brief names HPKE for the uplink seal but no implementation. P12
> forbids hand-written crypto primitives, so the HPKE library (and its availability on Android, iOS
> and the backend JVM) is a 1.3 selection item for the crypto review, not an implementation detail.

> **Reviewer note (open):** the mule receives the blob over an authenticated proximity link from a
> verified contact, so it learns the sender's identity and device and that this contact sent
> something at time T (§2 row 3). Brief §8.2 understates this. `threat-model.md` (LINDDUN) should
> record it as a residual linkability/identifiability risk of custodian 3.
