# Multi-transport delivery — design package

> **Status: Approved by delegated owner review (2026-11-12), with amendments.** The owner delegated
> the review and approval ("Kendin hepsini bağımsız review et ve onayla ve devam") and took decisions
> D1–D6; the review, verdicts and scope outcomes are recorded in
> [`docs/reviews/2026-11-12-design-approval-review.md`](../../reviews/2026-11-12-design-approval-review.md).
> The binding per-scope gate is still [`APPROVAL.md`](APPROVAL.md), which only the owner edits (G3,
> accepted under D6): no code may be written, and no GitHub restructuring or hooks applied, for a
> scope until its block there reads `Status: Approved` (owner decisions 3 and 4).

| | |
|---|---|
| **Date** | 2026-11-12 |
| **Base** | `dev` @ 2434324, branch `claude/muhabbet-p2p-architecture-wdan5r` |
| **Single source of truth** | [`decision-brief-2026-11-12.md`](decision-brief-2026-11-12.md). Every document here must agree with it; a disagreement is a defect in the derived document, recorded as a reviewer-note callout until resolved; resolved notes become plain notes citing the decision (D1–D6 or the 2026-11-12 errata, brief §18), and unresolved ones become open items in [`open-items.md`](open-items.md). |
| **Evidence** | [`docs/reviews/2026-11-12-findings-register.md`](../../reviews/2026-11-12-findings-register.md) — 211 verified findings (IDs such as `BE-06`, `MOB-03`, `CICD-01`, `PROX-02`) |

## What this package decides, in one paragraph

Muhabbet keeps the **server as the authority** for identity, devices, keys, order, mailbox, wake-up
and compliance (ADR-0010), and first makes the one path it already has **reliable and honest**: a
durable outbox, persist-then-ack, per-conversation server sequence with pull-by-seq, wake-only push
and an encrypted local store (0.4–0.5). Real end-to-end encryption arrives at 1.0 as a **type-level,
fail-closed gate**. Only after that do additional pipes appear, each behind a written admission gate
and each droppable: P2P 1:1 calls (1.1), LAN + Google Nearby Connections between verified contacts
(1.2), own-device custody and a single-hop sealed uplink through a contact (1.3), and Meshtastic LoRa
compiled out of Turkish builds until counsel clears it (1.4). The rejected list (DHT, libp2p, CRDT
history, BLE mesh, amateur radio, broadcast, satellite-as-transport, ultrasonic …) is recorded so it
is not re-litigated (ADR-0027).

## Reading order

| # | Document | Read it for | Audience |
|---|---|---|---|
| 1 | [Decision brief](decision-brief-2026-11-12.md) | Every decision, principle, port, state machine, roadmap, risk and open question. The source of truth. | Owner (approver) |
| 2 | [`sad.md`](sad.md) | arc42 Software Architecture Document: goals, constraints, context, building blocks, runtime, deployment, cross-cutting concepts, quality scenarios, glossary | Owner, implementers |
| 3 | ADRs 0010–0029 (product), 0030–0033 (process) — table below | One decision per record, with evidence and verification | Owner |
| 4 | [`threat-model.md`](threat-model.md) | STRIDE + LINDDUN per path and trust boundary | Owner, crypto reviewer, counsel |
| 5 | [`dpia-screening.md`](dpia-screening.md) | KVKK Art. 12 DPIA screening and per-trigger templates | Owner, counsel |
| 6 | [`icd-wire-protocol.md`](icd-wire-protocol.md) | Interface control: WS control plane, change feed, wake hints, inner `Content`, outer `TransportFrame`, `UplinkBundle` | Implementers |
| 7 | [`delivery-engine-spec.md`](delivery-engine-spec.md) | Engine state machines, lanes, backpressure, chunking, racing | Implementers |
| 8 | [`data-model.md`](data-model.md) | Flyway V28–V36 and the mobile SQLDelight SSOT schema | Implementers |
| 9 | [`transport-capability-matrix.md`](transport-capability-matrix.md) | Every path scope and adapter; the data source for `PathPolicy` rules | Owner, implementers |
| 10 | [`roadmap.md`](roadmap.md) | Phase 0 → 1.4 with entry/exit criteria and kill criteria | Owner |
| 11 | [`vv-plan.md`](vv-plan.md) | Requirement traceability (RQ-01…RQ-20 from brief §16.1 plus derived RQ-21…RQ-94), simulated-transport harness, owner-only verification | Owner, implementers |
| 12 | [`risk-register.md`](risk-register.md) | R1–R22 from brief §15 plus R23–R43, living | Owner |
| 13 | [`APPROVAL.md`](APPROVAL.md) | Where the owner approves each scope; read by the design-gate hook (ADR-0033) | Owner, agents, hook |

Supporting documents outside this folder:

| Document | Purpose |
|---|---|
| [`docs/reviews/2026-11-12-findings-register.md`](../../reviews/2026-11-12-findings-register.md) | Verified evidence |
| [`docs/reviews/2026-11-12-gemini-proposal-review.md`](../../reviews/2026-11-12-gemini-proposal-review.md) | Record of the Gemini proposal, fact-check outcomes and the three-proposal / three-judge synthesis |
| [`docs/legal/counsel-questions-2026-11.md`](../../legal/counsel-questions-2026-11.md) | Questions Q-L1…Q-L9 for Turkish telecom/KVKK counsel |
| [`docs/process/branching-and-release.md`](../../process/branching-and-release.md) | Operational detail for ADR-0030 |
| [`docs/process/ci-trust-boundary.md`](../../process/ci-trust-boundary.md) | Operational detail for ADR-0031 |
| [`docs/process/agent-workflow.md`](../../process/agent-workflow.md) | Operational detail for ADR-0032 |
| [`docs/process/claude-code-hooks.md`](../../process/claude-code-hooks.md) | Specification and test plan for ADR-0033 (design gate, environment bootstrap) |
| [`docs/process/security-incident-2026-11-env-prod.md`](../../process/security-incident-2026-11-env-prod.md) | Incident record for CICD-06 (names of exposed values only, never values) |
| [`docs/diagrams/`](../../diagrams/) | Mermaid: `c4-context`, `c4-container`, `c4-component-backend`, `c4-component-mobile`, `seq-send-receive`, `seq-nearby-reconcile`, `seq-wake-push-sync`, `outbox-state-machine`, `link-state-machine`, `custody-state-machine`, `path-scopes` |

Existing documents this package amends **after** approval (not before): `docs/ARCHITECTURE.md` §4
(SSOT, ADR-0011), §5 (message travel), §8 (seams `ServerLink`/`SealedLink`, `EnvelopeSealer`,
`MailboxPort`); ADR-003 (superseded); ADR 0008 (amended); `SECURITY.md` phone-hash claim (GOV-08);
`docs/legal/*` corrections (brief §14 item 8).

## Status of each document

Status as set by the delegated review of 2026-11-12 ([record](../../reviews/2026-11-12-design-approval-review.md)).
"Gated" means the document is approved as a design, or kept Proposed, but its milestone cannot start
until its admission gate (brief §13) is met. "Accepted (delegated)" never opens code by itself: code
needs the scope's `Status: Approved` line in `APPROVAL.md`.

| Document | Status | Gate |
|---|---|---|
| Decision brief | Approved (delegated, 2026-11-12) with D1–D6 and errata §18 | — |
| `sad.md` | Approved (delegated, 2026-11-12) | — |
| ADR-0010 … ADR-0014, ADR-0016, ADR-0019 … ADR-0021, ADR-0027 … ADR-0029 | Accepted (2026-11-12, delegated); 0021 amended by D3, 0028 amended | Code per scope in `APPROVAL.md` |
| ADR-0017 | Accepted with D1 (licence step deferred to 1.0 entry, after counsel Q-L7) | 1.0 entry |
| ADR-0015 | Accepted (delegated); `PathPolicy` built only at 1.2 | G-PROXIMITY (1.2) |
| ADR-0018 | Accepted (delegated); items 4–5 gated, 1.2 | G-PROXIMITY (1.2) |
| ADR-0022 | Accepted (delegated); plane C + UplinkBundle gated, 1.2/1.3 | G-PROXIMITY (1.2); 1.3 entry |
| ADR-0023 | Accepted (delegated) for 0.7 except the ADR 0009 hosting/budget choice (owner, Q-O8); 1.1 part gated | Q-O8; 0.7 telemetry |
| ADR-0024, ADR-0025, ADR-0026 | Proposed (gated) — confirmed by the delegated review, not accepted | G-PROXIMITY (1.2); 1.3 entry; 1.4 counsel opinion |
| ADR-0030 … ADR-0033 | Accepted (delegated; process, amended) | Applied only after scope `process` reads Approved in `APPROVAL.md` (owner decision 3) |
| `threat-model.md`, `dpia-screening.md` | Approved (delegated) | Gating deliverables for 0.4 age gate (DT-7), 0.5 wire changes, 1.0, 1.2, 1.3 |
| `icd-wire-protocol.md`, `delivery-engine-spec.md`, `data-model.md` | Approved (delegated) | Per milestone |
| `transport-capability-matrix.md` | Approved (delegated) | Device-dependent rows remain "unmeasured" until the owner observes them (P13) |
| `roadmap.md`, `vv-plan.md`, `risk-register.md` | Approved (delegated); living | — |
| ADR-003 | Superseded by ADR-0016 (ADR-0017 related) | — |
| ADR 0008 | Amended by ADR-0021 | — |
| `APPROVAL.md` | Template; still reads **Pending** until the owner transcribes the 2026-11-12 delegated approval (phase-0, `process`, 0.4 Approved; 0.5–0.7 and 1.0 Deferred with a pre-approval condition; 1.1–1.4 Pending). Paste-ready text: [`open-items.md` §2](open-items.md#2-paste-ready-text-for-approvalmd-oi-46) | Owner edits only (G3) |
| [`open-items.md`](open-items.md) | Living | The single source of truth for every item the delegated review left open (owner, counsel, milestone) |

## ADR index

Product ADRs (P14: 1.1+ product ADRs remain *Proposed (gated)* until their spikes and opinions exist):

| ADR | Title | One-line summary | Milestone |
|---|---|---|---|
| [0010](../../adr/0010-hybrid-server-backbone.md) | Hybrid backbone | The server is the authority for identity, devices, keys, order, mailbox, push, TURN and moderation; every other path only moves bytes and is final only once the server assigns a seq (LOCAL_ONLY excepted). | all |
| [0011](../../adr/0011-local-db-ssot-no-viewmodels.md) | Local DB is the SSOT | SQLDelight becomes the mobile source of truth, superseding ARCHITECTURE.md §4; screens observe DB flows; commands go through `MessagingEngine`; the no-ViewModel decision stays. | 0.5 |
| [0012](../../adr/0012-delivery-engine-and-link-ports.md) | Delivery engine and link ports | New `:mobile:messaging` engine with ISP-split `ServerLink` / `SealedLink` / `InboundSource`; `WsClient` strangled; no `LinkSelector` until a second outbound link exists (1.2). | 0.5 |
| [0013](../../adr/0013-delivery-semantics-and-ticks.md) | Delivery semantics and ticks | Durable CAS outbox, persist-then-ack inbox, server idempotency with replayed ack, per-device acks, hop ack modelled; one tick meaning on every path; no "relayed" tick. | 0.4 / 0.5 |
| [0014](../../adr/0014-ordering-seq-change-feed.md) | Ordering | Per-conversation `seq` + `change_seq` and a pull-by-seq change feed; client `CausalHeader` inside E2E; no HLC authority, no Merkle-DAG. | 0.4 / 0.5 |
| [0015](../../adr/0015-path-scopes-and-policy.md) | Path scopes and policy | Paths classified by scope, not medium; rule chain (Admission, CloudPersistence, Preference) instead of a fitness score. *Proposed (gated: `PathPolicy` built at 1.2)*. | 1.2 |
| [0016](../../adr/0016-e2e-fail-closed-type-gate.md) | E2E fail-closed type gate | Non-server ports accept only `SealedFrame` (internal constructor); plaintext fallback deleted; test fakes kept off release classpaths; supersedes ADR-003. | 0.5 / 1.0 |
| [0017](../../adr/0017-e2e-library-and-licence.md) | E2E library and licence | libsignal at the platform edge; licence decided after counsel Q-L7 — `mobile/` stays MIT until 1.0 entry (D1), `shared/` and `backend/` stay MIT; Android-only 1.0 if KMP-01 is not fixed at the 0.6 gate (D2). | 1.0 |
| [0018](../../adr/0018-identity-keys-device-certificates.md) | Identity, keys, certificates | Per-device keys with atomic prekey claim and no silent replacement; separate signed link key; 7-day device certificates under an offline root, built with their first consumer at 1.2. *Proposed (items 4–5 gated, 1.2)*. | 0.4 / 0.6 / 1.2 |
| [0019](../../adr/0019-wake-only-push-and-server-plaintext.md) | Wake-only push | Data-only push from 0.5; no server plaintext, search, previews or media reads for E2E conversations at 1.0; receipts stay server-visible so #377 holds. | 0.5 / 1.0 |
| [0020](../../adr/0020-data-at-rest-and-erasure.md) | Data at rest and erasure | SQLCipher from 0.5 via a fresh DB and resync from the server; `SessionTeardown` is ordered crypto-erase. | 0.4 / 0.5 |
| [0021](../../adr/0021-adr0008-amendment-storage-modes-e2e.md) | Amendment to ADR 0008 | One cursor model in every era; mailbox as a projection; CLOUD/BYO/LOCAL_ONLY redefined for E2E; plaintext-history cutover per D3 (download → notified 30-day grace → server deletion of content, media and backups; opt-in user-keyed backup). | 1.0 |
| [0022](../../adr/0022-wire-protocol-envelope.md) | Wire protocol | JSON control plane with `hello`/`welcome` and `Unknown`; protobuf inner `Content`; AEAD-authenticated outer `TransportFrame` with random ids and padding. *Proposed (plane C + UplinkBundle gated, 1.2/1.3)*. | 0.4 / 0.5 / 1.0 / 1.2 |
| [0023](../../adr/0023-internet-direct-ice-turn.md) | Internet-direct | WebRTC ICE plus our own TURN for live sessions only (LiveKit at 0.7, P2P 1:1 at 1.1); relay-only for non-contacts; no messaging over data channels. | 0.7 / 1.1 |
| [0024](../../adr/0024-proximity-lan-and-nearby.md) | Proximity | Build `LanLink`, buy Nearby Connections; foreground Nearby mode; verified 1:1 E2E only; rotating tags. *Proposed (gated)*. | 1.2 |
| [0025](../../adr/0025-store-and-forward-custody.md) | Store-and-forward | Custody hierarchy: server mailbox → own devices → single-hop sealed uplink via a verified contact; multi-hop mules are research. *Proposed (gated)*, droppable. | 1.0 / 1.3 |
| [0026](../../adr/0026-meshtastic-legal-gate.md) | Meshtastic | Optional adapter to certified nodes with an `AirtimeLedger`; built only after a written Art. 39/63(6) opinion; excluded from TR flavours by Gradle. *Proposed (gated)*. | 1.4 |
| [0027](../../adr/0027-rejected-transports.md) | Rejected transports | Permanent and ordinary rejections with evidence, plus a watch-list; reopened only by a new ADR. | — |
| [0028](../../adr/0028-kmp-module-structure.md) | KMP module structure | `:mobile:crypto`, `:mobile:crypto-testkit`, `:mobile:messaging` at 0.5, transports at 1.2/1.4; version catalog, one convention plugin, `.sqm` migrations and JVM tests in CI first; iOS decision blocking at 0.6. | 0.4 → 1.4 |
| [0029](../../adr/0029-compliance-boundary.md) | Compliance boundary | Age gate in 0.4 (no compliance claim until counsel confirms); broadcast surfaces server-only; block lists on every inbound path; ≤ 7-day offline revocation; DPIA triggers; counsel request names BLE/Wi-Fi. | 0.4 → 1.4 |

Process ADRs (kept separate from product, P14):

| ADR | Title | One-line summary |
|---|---|---|
| [0030](../../adr/0030-trunk-and-release-flow.md) | Trunk and release flow | Merge `dev` into `main` once and delete `dev`; squash-only; two rulesets (`main-checks` with no bypass, `main-review`), required checks added once PR CI runs off cx43; build once, launch on `emulator-5554`, promote the same artefact; deploy by artefact. |
| [0031](../../adr/0031-ci-trust-boundary.md) | CI trust boundary | No PR job on the production host; `muhabbet-mordecai` resolved; signing in a protected `release` environment; SHA-pinned actions; rotate every `.env.prod` value; no long-lived key before this is in force. |
| [0032](../../adr/0032-agent-identity-verification-gate.md) | Agent identity and verification gate | Agents act under their own GitHub identity; the owner approves agent PRs; device criteria record "observed by / where" and only the owner ticks them. |
| [0033](../../adr/0033-claude-code-hooks.md) | Claude Code hooks | A `PreToolUse` design gate that blocks agent edits outside docs/governance paths until `APPROVAL.md` approves the scope, plus a cloud-only `SessionStart` environment bootstrap. |

Existing ADRs affected: [003](../../adr/003-defer-e2e-encryption.md) superseded by 0016 (0017 related);
[0007](../../adr/0007-companion-device-trust.md) crypto realised by 0018;
[0008](../../adr/0008-per-conversation-storage-mode.md) amended by 0021;
[0009](../../adr/0009-call-media-infrastructure.md) unchanged, 0023 builds on it.

> **Resolved (2026-11-12):** brief erratum E-06 replaces "§17 doc plan" with "README.md package index"
> (ADR-0015 Consequences and §4.1).

## How approval works

1. **The owner reads** the brief, then this package, and answers the open owner questions Q-O1…Q-O11
   (brief §17) that the scope being approved depends on.
2. **ADR disposition.** For each ADR the owner records *Accepted*, *Amended* (with the amendment) or
   *Rejected* in the ADR disposition table of [`APPROVAL.md`](APPROVAL.md). On 2026-11-12 the
   delegated review recorded the dispositions in
   [its record](../../reviews/2026-11-12-design-approval-review.md) and the ADR status lines were set
   to match ("Accepted (2026-11-12, delegated)"); the owner transcribes them into `APPROVAL.md` §3.
   If the owner does not ratify a disposition, the ADR's status line reverts.
3. **Scope approval.** Approval is per scope, not for the whole package: `phase-0`, `0.4`, `0.5`,
   `0.6`, `0.7`, `1.0`, `1.1`, `1.2`, `1.3`, `1.4`, `process` (exactly as the `APPROVAL.md` headings). The owner sets `Status: Approved` in
   that scope's block of `APPROVAL.md`, with name, date and any binding conditions. Approving `0.5`
   does not approve `1.2`; each later milestone re-arms the gate.
   At most one milestone scope reads `Status: Approved` at a time (ADR-0033 D1a); milestones
   approved in advance are recorded as `Deferred` with a pre-approval condition. On 2026-11-12 the owner
   delegated the review and approval of this package and took decisions D1–D6 (brief header); the
   owner transcribes that outcome into `APPROVAL.md` ([OI-46](open-items.md#oi-46)).
4. **Gates still apply.** Approval of a scope authorises work on it; it does not waive that
   milestone's entry criteria (brief §13). Gated scopes (1.1–1.4) additionally need their spikes,
   opinions and DPIAs as signed artefacts, listed in the scope's conditions.
5. **Mechanism.** Until ADR-0033 is approved and its hook is installed, the rule "no code before
   approval" holds by convention. Once installed, the `PreToolUse` design-gate hook reads
   `APPROVAL.md` and refuses agent edits outside `docs/**`, `.github/ISSUE_TEMPLATE/**`,
   `.github/pull_request_template.md`, `CODEOWNERS` and the governance files `.claude/settings.json`,
   `.claude/hooks/**`, `.claude/active-scope` unless the relevant scope is approved (paths inside
   `.claude/worktrees/<wt>/` are evaluated against that worktree). The scope an edit belongs to is the one named in `.claude/active-scope` (ADR-0033 G5;
   missing ⇒ denied). `APPROVAL.md` itself is never agent-editable (G3), although it lies under
   `docs/**`. The hook is a guard for agents, not a security boundary.
6. **Security emergencies are separate.** Brief §14 items 1–6 (secret rotation, PR jobs off the
   production host, `muhabbet-mordecai`, protected environments, SHA pinning, SECURITY.md) are
   incident response. They are approved through the `phase-0` scope and the brief recommends the owner
   authorise them now, independent of design approval.
7. **After approval**, the GitHub restructuring of ADR-0030/0031/0032 is applied, milestones 1.1–1.4
   are created, and the amendments to existing documents listed above are made.
