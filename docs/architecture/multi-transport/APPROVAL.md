# Multi-transport delivery — approval record

> **Status: Proposed — pending owner approval.** This file is the only place where approval of the
> multi-transport design is recorded. No code may be written for a scope until that scope's block
> below reads `Status: Approved`, entered by the owner. Every scope is **Pending** as of 2026-11-12.

| | |
|---|---|
| **Source of truth** | [decision brief](decision-brief-2026-11-12.md) |
| **Package index** | [README.md](README.md) |
| **Read by** | The `PreToolUse` design-gate hook of [ADR-0033](../../adr/0033-claude-code-hooks.md), once that ADR is approved and the hook is installed |
| **Who may edit** | The owner only. Agents must not change any `Status:`, `Approved-by:`, `Date:` or `Conditions:` line, nor any row of the disposition tables. |

## 1. How this file works

1. **One block per scope.** Scopes are: `phase-0`, `0.4`, `0.5`, `0.6`, `0.7`, `1.0`, `1.1`, `1.2`,
   `1.3`, `1.4`, `process`. Approving one scope approves nothing else; later milestones re-arm the
   gate (ADR-0033).
2. **To approve**, the owner edits the scope's block: changes `Status: Pending` to
   `Status: Approved`, fills `Approved-by` and `Date` (ISO `YYYY-MM-DD`), and writes any binding
   conditions. Other allowed values are `Rejected` and `Deferred`; the hook treats every value other
   than exactly `Approved` as closed.
3. **Conditions are binding** but not machine-checked. Typical conditions: an ADR amendment, an
   answered owner question (Q-O*n*), or a signed gate artefact (opinion, DPIA, spike result) with its
   location.
4. **Approval authorises work; it does not waive entry criteria.** A milestone still starts only when
   its entry criteria in brief §13 are met. For gated scopes (1.1–1.4) list the signed gate artefacts
   under `Conditions`.
5. **Device-dependent exit criteria** are signed off by the owner's observation only (P13,
   ADR-0032). That sign-off is recorded in the milestone's tracker issue, not here.
6. **Revoking approval**: set the scope back to `Pending` (or `Deferred`) with a dated condition
   explaining why. The hook re-arms immediately.

### 1.1 Format the hook relies on

Each scope block is a level-3 heading followed by four lines, exactly:

```text
### Scope: <scope-id>
Status: <Pending|Approved|Rejected|Deferred>
Approved-by: <name or empty>
Date: <YYYY-MM-DD or empty>
Conditions: <free text or empty>
```

Parsing rule for the hook: find the line `### Scope: <id>`; within that block (up to the next line
starting with `### `), the scope is open iff a line, after stripping a trailing CR, equals exactly `Status: Approved`
(ADR-0033 G6). CRLF line endings are tolerated; trailing spaces and list markers are not. Text in the rest of
this file is never parsed. Do not reformat the blocks into a table, and do not add trailing
spaces or list markers to these lines. (Rendered Markdown joins the four lines into one paragraph;
edit the raw file.)

While the gate is armed, agent edits are always allowed under `docs/**` except this file,
`.github/ISSUE_TEMPLATE/**`, `.github/pull_request_template.md`, `CODEOWNERS` (root or `.github/`)
and `.claude/**` (ADR-0033 G2). This file is never agent-editable (G3). Any other path requires the
scope named in `.claude/active-scope` to be open here (G4–G5); a missing or invalid declaration is
denied.

> **Reviewer note (open):** ADR-0033 resolves scope attribution with `.claude/active-scope` (G5) and
> self-approval with G3. Both go beyond the brief's ADR-0033 text and need the owner to accept them as
> an amendment in the §3 disposition row for 0033. `.claude/active-scope` sits under the
> always-allowed `.claude/**`, so an agent can select any already-approved scope; that is accepted as
> 'a guard, not a boundary'. No CODEOWNERS file exists yet; ADR-0032/0033 must create it, with this
> file and `.claude/**` owned by the owner.

> **Reviewer note (open):** until ADR-0033 is approved (scope `process`) and the hook exists, nothing
> enforces this file mechanically. The no-code rule holds by convention (owner decision 4) during
> that window.

## 2. Scope approvals

### Scope: phase-0
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* security emergencies of brief §14 items 1–6 (rotate every value ever in `.env.prod` —
CICD-06; stop `pull_request` jobs on `muhabbet-cx43` — CICD-01 — per ADR-0031 D2: the runner on cx43 stopped and
deregistered, or the accepted-risk variant recorded under Conditions with an expiry; identify or deregister
`muhabbet-mordecai` — CICD-03; protected `release`/`production` environments — CICD-02; SHA-pin
actions and remove `trivy-action@master` — CICD-05; correct the SECURITY.md phone-hash claim —
GOV-08); engaging counsel with the question list; threat model and DPIA screening; opening the
licence decision. *Note:* the brief recommends the owner authorise the emergencies now, independent
of design approval. *Exit (brief §13):* old JWT fails after rotation; no PR job runs on cx43; ADR
dispositions recorded in §3 below.

### Scope: 0.4
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* "the app tells the truth" (trimmed). Backend: BE-03, BE-02 (+ one transaction per
release), BE-07, BE-12 (+ JWT out of query string), BE-13, atomic prekey claim and fetch rate limit
(BE-14 part 1), contact-lookup cap and block filter, age gate (V30), V28/V29 additive seq and
device-scoped idempotency, BE-08 ArchUnit rules. Mobile: MOB-01 `SessionTeardown`, MOB-03
persist-before-ack, MOB-05, MOB-04 stopgap (delete `PendingMessage` only on `ServerAck`), group
receipt aggregation, version catalog, one convention plugin, `.sqm` migrations + `verifyMigrations`,
`:shared:jvmTest` in CI. *ADRs:* 0013, 0014, 0018 (part), 0020 (part), 0022 (plane A auth), 0028 (prerequisites), 0029.
*Entry:* phase-0 exit; ROADMAP's existing 0.4 conditions.

### Scope: 0.5
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* 0.5.0 Honest. `:mobile:crypto` (type gate, no producer), `:mobile:messaging`,
`:mobile:crypto-testkit`; `WsClient` strangled; `RestChangeFeedSource`, `FcmHintSource`,
`SyncCoordinator`; UI on DB flows (`verifyUi` ratchet to 0); SQLCipher via fresh DB + resync;
backend `/changes`, seq/changeSeq on frames, `hello`/`Unknown`, KMP-07, BE-11, wake-only FCM; plaintext
fallback deleted; `engine.enabled` staged rollout. *ADRs that must be Accepted first (brief §13):*
0011, 0012, 0013, 0014, 0016, 0019, 0020, 0022, 0028. *Entry:* 0.4 shipped; Docker available for
Spring-config changes.

### Scope: 0.6
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* 0.6.0 Reachable. Invite flow; contact discovery with a discoverability setting and reader;
background sync on seq; per-device delivery rows (V32); per-device key schema (V33) and
`IdentityKeyChanged`; **blocking iOS decision** (KMP-01 fixed, or an ADR scoping iOS out of 1.0).
*Owner question:* Q-O2. *Entry:* 0.5 exit.

### Scope: 0.7
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* 0.7.0 Live. ADR 0009 resolved with LiveKit on a separate host, embedded TURN/TLS 443,
`LIVEKIT_*` in `.env.prod`; client sends `call.initiate` and publishes the mic track (#367–#373);
`LiveSessionPort`/`LiveKitSession`; relay-only for non-contacts, "always relay", SFU disclosure;
ICE telemetry without IPs. *ADRs:* 0009, 0023. *Owner question:* Q-O8. *Entry:* ADR 0009 accepted;
host provisioned; 0.6 exit.

### Scope: 1.0
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* 1.0.0 Encrypted. libsignal adapters behind `SessionCryptoPort`; per-device fan-out;
sender-key groups; media E2E; safety-number QR; `message_envelopes` mailbox (V34); protocol v2;
on-device FTS; sender-side link previews; ADR 0007 `DeviceLinkCrypto`; plaintext-history cutover;
external crypto review; LOCAL_ONLY only if Q-O3 is decided. *ADRs:* 0016, 0017, 0018, 0019, 0021,
0022. *Preconditions to list under Conditions:* ADR-0017 (a) licence approval, (b) contributor audit,
(c) written counsel opinion on AGPL via Play and App Store; iOS bridge spike result or Android-only
decision. *Owner questions:* Q-O1, Q-O2, Q-O3, Q-O4. *Entry:* 0.7 shipped or explicitly deferred;
crypto reviewer engaged; two-device Android round trip on real builds.

### Scope: 1.1
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* 1.1 Direct (gated). `WebRtcP2PSession` for 1:1 calls (DTLS-SRTP E2E); relay-only for
non-contacts; TURN sized from 0.7 data. *ADR:* 0023 (1.1 part). *Gate artefacts:* 0.7 telemetry
showing benefit; TURN egress budget; iOS WebRTC bridge spike if iOS is in scope; written counsel
opinions Q-L6 (if the TURN host is abroad) and Q-L8 (ADR-0029); DT-3 DPIA revision. *Owner question:*
Q-O5 (kill threshold).

### Scope: 1.2
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* 1.2 Nearby (gated). Device certificates and CRL (V35) with the signer off PR-job hosts;
link keys and vetted `LinkAuthenticator`; rotating tags; `LanLink`, `NearbyLink`, `PathPolicy`;
Nearby-mode screen; 1:1, verified, E2E only; `serverOwed`. *ADRs:* 0012 (`PathPolicy`/second link), 0015, 0018 (items 4–5), 0022 (plane C), 0024, 0029.
*Gate artefacts (G-PROXIMITY, all required):* 1.0 exit; 0.6 iOS decision honoured; published 2-week
device-matrix spike; handshake library chosen; signed proximity DPIA; counsel opinion covering E2E
over BLE/Wi-Fi; ADR-0031 in force; HMS share in Turkey measured. *Owner questions:* Q-O6, Q-O7.

### Scope: 1.3
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* 1.3 Relay (gated, droppable). `OwnDeviceCustody`; then single-hop sealed uplink
(`UplinkBundle`, `/api/v1/uplink`, quotas, uplink key on a non-CI host). *ADRs:* 0018, 0022 (`UplinkBundle`), 0025, 0029. *Gate
artefacts:* 1.2 shipped; telemetry above the owner's threshold; external crypto review of uplink
sealing; written KVKK opinion on contacts transiently holding ciphertext; DPIA. *Owner questions:*
Q-O5, Q-O7.

### Scope: 1.4
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* 1.4 Off-grid hardware (gated, droppable, TR-excluded until cleared). `MeshtasticLink`,
`AirtimeLedger`, constrained policy, certified hardware list. *ADR:* 0026. *Gate artefacts:* written
opinion on Law 5809 Art. 39/63(6), the 2010 coded/encrypted communications regulation and KET
conformity; G-PROXIMITY in place. *Owner question:* Q-O5.

### Scope: process
Status: Pending
Approved-by:
Date:
Conditions:

*Covers:* process ADRs 0030 (trunk-based `main`, build-once release), 0031 (CI trust boundary),
0032 (agent identity and verification gate), 0033 (Claude Code hooks: design gate and cloud
environment bootstrap); the GitHub restructuring they describe; `.claude/settings.json` and
`.claude/hooks/*.sh` under CODEOWNERS. *Owner question:* Q-O9. *Note:* the design-gate hook is
installed only after this scope is approved, and must ship with its test (an edit to `backend/**` is
refused while this file is unsigned).

## 3. ADR disposition (Phase 0 exit; not read by the hook)

Owner records one of *Accepted*, *Amended* (state the amendment), *Rejected*. ADR files are updated
to match only after a row here is filled.

| ADR | Title | Proposed status | Disposition | Amendment / notes | Date |
|---|---|---|---|---|---|
| 0010 | Hybrid backbone | Proposed | | | |
| 0011 | Local DB is the SSOT | Proposed | | | |
| 0012 | Delivery engine and link ports | Proposed | | | |
| 0013 | Delivery semantics and ticks | Proposed | | | |
| 0014 | Ordering | Proposed | | | |
| 0015 | Path scopes and policy | Proposed (gated: `PathPolicy` built at 1.2) | | | |
| 0016 | E2E fail-closed type gate | Proposed | | | |
| 0017 | E2E library and licence | Proposed | | | |
| 0018 | Identity, keys, certificates | Proposed (items 4–5 gated, 1.2) | | | |
| 0019 | Wake-only push | Proposed | | | |
| 0020 | Data at rest and erasure | Proposed | | | |
| 0021 | Amendment to ADR 0008 | Proposed | | | |
| 0022 | Wire protocol | Proposed (plane C + UplinkBundle gated, 1.2/1.3) | | | |
| 0023 | Internet-direct | Proposed (1.1 part gated) | | | |
| 0024 | Proximity | Proposed (gated) | | | |
| 0025 | Store-and-forward | Proposed (gated) | | | |
| 0026 | Meshtastic | Proposed (gated) | | | |
| 0027 | Rejected transports | Proposed | | | |
| 0028 | KMP module structure | Proposed | | | |
| 0029 | Compliance boundary | Proposed | | | |
| 0030 | Trunk and release flow | Proposed (process) | | | |
| 0031 | CI trust boundary | Proposed (process) | | | |
| 0032 | Agent identity and verification gate | Proposed (process) | | | |
| 0033 | Claude Code hooks | Proposed (process) | | | |

Existing records changed by the package (disposition follows the ADR that changes them): ADR-003
→ *Superseded by 0016* (0017 related); ADR 0008 → *Amended by 0021*; ADR 0007 → crypto realised by 0018;
ARCHITECTURE.md §4/§5/§8 → rewritten per 0010/0011/0012.

## 4. Owner questions (brief §17)

Answers here are binding inputs to the scopes named. Not read by the hook.

| ID | Question (short) | Blocks scope | Answer | Date |
|---|---|---|---|---|
| Q-O1 | Relicense `mobile/` to AGPL-3.0-or-later (`shared/`, `backend/` stay MIT), subject to counsel? | 1.0 | | |
| Q-O2 | iOS in 1.0, or Android-only if KMP-01 / the bridge is not ready by the 0.6 gate? | 0.6, 1.0 | | |
| Q-O3 | LOCAL_ONLY receipt-metadata retention | 1.0 (LOCAL_ONLY), 1.3 | | |
| Q-O4 | 1.0 cutover: delete server plaintext history after grace, or encrypt to a user-held backup by default? | 1.0 | | |
| Q-O5 | Kill thresholds for 1.1, 1.3, 1.4 | 1.1, 1.3, 1.4 | | |
| Q-O6 | Accept and disclose the ≤ 7-day offline revocation window? | 1.2 | | |
| Q-O7 | Where the device-CA intermediate and uplink key live (second host or hardware token) | 1.2, 1.3 | | |
| Q-O8 | LiveKit host placement and budget; TURN egress budget for 30–50% relayed calls | 0.7 | | |
| Q-O9 | Approve governance changes (trunk `main`, agent identity, rulesets) | process | | |
| Q-O10 | Separate screen-state ADR (MOB-02) wanted? | none (optional) | | |
| Q-O11 | Turkish UI copy for offline labels ("Yakındakiler", "Henüz eşitlenmedi") | 0.5, 1.2 | | |

## 5. Change log

| Date | Change | By |
|---|---|---|
| 2026-11-12 | Template created; every scope Pending | Authoring workflow (agent) |
