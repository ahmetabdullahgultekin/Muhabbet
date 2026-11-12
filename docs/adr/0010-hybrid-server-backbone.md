# ADR 0010 — Hybrid backbone: the server is the authority, every other path is an additional pipe

**Status:** Accepted (2026-11-12, delegated) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); code against this ADR is written only while the matching scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0010](../architecture/multi-transport/decision-brief-2026-11-12.md) · Sets the boundary for the [ARCHITECTURE.md §8](../ARCHITECTURE.md) seams added by ADR-0012. Supersedes nothing.

## Context

The owner's goal is "communicate always and everywhere, somehow". The Gemini proposal answered it
with a serverless design: PeerID = Blake3(Ed25519), Kademlia DHT for prekeys and lookup, volunteer
supernodes, Merkle-CRDT history and multi-hop meshes. The review rejected that shape on verified
evidence (findings register 2026-11-12):

- A DHT cannot hand out a one-time prekey exactly once and leaks who looks up whom (C2, P2P-3).
- MLS needs a Delivery Service to order Commits; offline concurrent Commits fork the group (C3).
- Self-certifying PeerIDs conflict with the phone-OTP account model and give no recovery or
  multi-device story (C11).
- Law 5809 Art. 9(14) can deem an OTT provider an *işletmeci* (operator) for duties BTK assigns
  (MISS-01, legal verifier; LEG-01). Laws 5651 and 7578 require a reachable controller for takedown
  and for the under-15 ban (LEG-02). A mesh mode does not change the classification (LEG-01).
- Stranger relays make other users' phones hold third parties' data (LEG-04, PROX-03).

Owner decision 1 fixed the base: the server is the backbone, and other paths are additional pipes.
This ADR states what that means precisely enough to be tested, because every later ADR (0011–0029)
relies on it.

## Decision

### 1. Authority split

| Concern | Authority | Notes |
|---|---|---|
| Account identity (E.164 + OTP) and the age gate | Server | Age gate per ADR-0029 |
| Device registry, per-device key directory | Server | ADR 0007, ADR-0018 |
| Device-certificate issuance and revocation | Server | Built at 1.2 (ADR-0018) |
| Per-conversation order (`seq`, `change_seq`) | Server | ADR-0014 |
| Ciphertext mailbox (custodian 1) | Server | ADR-0021, ADR-0025 |
| Push wake-up, TURN credentials | Server | ADR-0019, ADR-0023 |
| Moderation, legal duties, block enforcement | Server, plus inbound filter on every path | ADR-0029 |
| Moving bytes | Any adapter | Adapters never mint identity, order, membership or moderation state |

### 2. Rules for non-server paths

1. P2P, LAN, proximity, uplink-relay and RF paths are adapters that move bytes only (`SealedLink`,
   ADR-0012). They hold no identity, ordering or membership authority.
2. Off-server traffic is allowed only between **registered devices** of users who **already share a
   conversation** and an **E2E session established through the server**. No first contact,
   conversation creation, membership change or key bootstrap happens off-server.
3. Only E2E ciphertext crosses a non-server path (ADR-0016, by type).
4. In a CLOUD or BYO conversation (ADR 0008), a message delivered off-server is **not final** until
   the server assigns it a `seq`. Until then it carries a `synced = false` marker in the local
   store and a "not yet synced" label in the UI, and the sender's outbox entry keeps a
   `serverOwed` obligation (ADR-0013, §6.1 of the brief).
5. LOCAL_ONLY (ADR 0008 Mode 3) is the only exception to rule 4: the server holds TTL ciphertext and
   delivery metadata only, and the conversation has causal, not total, order (ADR-0014).

### 3. Seams within this boundary (added to ARCHITECTURE.md §8 by ADR-0012)

| Seam | Side | Purpose | First real implementation |
|---|---|---|---|
| `ServerLink` / `SealedLink` | mobile | Outbound pipes, split by what they may carry | `WsServerLink` (0.5); `LanLink`, `NearbyLink` (1.2) |
| `EnvelopeSealer` | mobile | The only producer of `SealedFrame` | libsignal adapters (1.0) |
| `MailboxPort` | backend | Per-device ciphertext projection | `JpaMailboxAdapter` (1.0) |

### 4. Degraded-mode semantics

Offline operation is **tolerant, not authoritative**. With the server unreachable, users can
deliver to verified contacts over gated proximity paths (1.2+) and queue everything else in the
durable outbox. Nothing created off-server is authoritative history until the server has sequenced
it.

## Why

- **Owner decision 1.**
- **Cryptographic correctness needs a directory and a sequencer.** One-time prekeys must be claimed
  exactly once (C2, C10); group membership changes must be totally ordered (C3). Both are trivially
  correct with one server and hard or impossible without one.
- **Legal reachability.** The operator deeming clause (5809 Art. 9(14)), 5651 takedown duties and
  the 7578 under-15 ban all presuppose a controller that can act. Offline modes must not be where
  those duties evaporate (ADR-0029).
- **One ordering authority removes CRDT merge logic.** A per-conversation server `seq` plus a
  client causal header (ADR-0014) replaces Merkle-DAG anti-entropy, which also conflicts with
  deniability and KVKK erasure (C5).

## Consequences

- The server stays in the identity TCB. Key transparency can reduce that trust later; it cannot
  remove it, and it is research (ADR-0018 item 7).
- Messages exchanged off-server in CLOUD/BYO conversations may re-sort once when their `seq` arrives
  (ADR-0014, risk R16).
- A user who has never been online with a contact cannot reach them off-server. This is a stated
  non-promise (brief §1.2) and must appear in product copy.
- When the server is down, there is no new contact, no group change, no key bootstrap and no
  moderation action. Revocation reaches offline peers only within the certificate window
  (≤ 7 days, ADR-0018/0029).
- ADR-0012 adds the seams in Decision §3 to ARCHITECTURE.md §8 (banner added; text change on approval).

> **Resolved (2026-11-12):** approving scopes 0.4–0.6 by delegated owner review accepts ADR-0010,
> 0011, 0012, 0013, 0014, 0016, 0018 items (1)–(3) and (6), 0019, 0020, 0022 planes A–B, 0028 and 0029,
> so the 0.4 slices of 0010/0013/0014/0028/0029 are accepted before 0.4 starts. ADR-0017 is accepted
> as amended by D1. Brief §13 0.5 Entry now lists ADR-0010 (brief erratum E-02).

> **Note:** ADR-0010 sets the boundary; ADR-0012 makes the ARCHITECTURE.md §8 amendment
> (three seams: `ServerLink`/`SealedLink`, `EnvelopeSealer`, `MailboxPort`). The brief's ADR-0010
> Consequences were corrected accordingly (brief erratum E-01).

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Gemini fully decentralised design (PeerID, Kademlia prekeys, supernodes, Merkle-CRDT) | C2, C3, C5, C11, P2P-1, P2P-3; legal anchor lost (LEG-01, MISS-01) |
| Peer-equal hybrid where nearby-delivered messages are authoritative | Two ordering authorities need CRDT merge; moderation and revocation bypassed |
| Federation as the base | Out of scope; BYO stays a per-conversation storage mode (ADR 0008) |
| Server-only forever | Fails the product goal; the engine (ADR-0012) keeps extra pipes cheap to add later |

## Evidence

- Findings: C2, C3, C5, C10, C11, P2P-1, P2P-3, LEG-01, LEG-02, LEG-04, MISS-01 (legal verifier),
  PROX-03, BE-06, C13.
- External sources (from the fact-check digests): https://signal.org/docs/specifications/x3dh/,
  https://signal.org/docs/specifications/pqxdh/, https://www.rfc-editor.org/rfc/rfc9750.html,
  https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5651.pdf.

## Verification

| Check | Method | When |
|---|---|---|
| No transport adapter depends on an identity, ordering or membership port | ArchUnit (backend) / Konsist (mobile) rule | From 0.5, when `:mobile:messaging` exists |
| An off-server-delivered CLOUD message shows "not yet synced" and its outbox entry keeps `serverOwed = true` until `ServerAccepted(seq)` | `SimulatedLink` multi-node test with `TestClock` | 1.2 |
| No off-server delivery is possible for a pair without a server-established session and shared conversation | Admission-rule unit tests (ADR-0015 `RelationshipRule`) | 1.2 |
| Boundary review | Inspection of each new port against the table in Decision §1 | Every ADR touching transports |
