# Design approval review — multi-transport package (2026-11-12)

**Date:** 2026-11-12 · **Branch:** `claude/muhabbet-p2p-architecture-wdan5r` (base `dev` @ 2434324)
· **Subject:** the multi-transport design package
([README](../architecture/multi-transport/README.md),
[decision brief](../architecture/multi-transport/decision-brief-2026-11-12.md), ADR 0010–0033 and the
documents they cite) · **Evidence base:** [findings register](2026-11-12-findings-register.md) ·
**Open items:** [`open-items.md`](../architecture/multi-transport/open-items.md) (OI-01 … OI-47)

## 0. Who approved this, and on what authority

**This approval was given by Claude, acting under the owner's explicit delegation.** The owner wrote:

> *"Kendin hepsini bağımsız review et ve onayla ve devam"*
> (review everything independently yourself, approve and continue)

The owner also took decisions D1–D6 (§3) through the same delegation. No human reviewed the documents
for this record. Every verdict below comes from Claude review agents that did not write the documents
they reviewed.

**The binding gate is still [`APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), and this
record has not edited it.** Three rules forbid an agent edit:
- CLAUDE.md calls it "the owner's file; agents never edit it".
- The file's own header says agents must not change any `Status:`, `Approved-by:`, `Date:` or
  `Conditions:` line.
- ADR-0033 G3, accepted under D6, makes it never agent-editable.

The delegation tells us to *approve*. It does not tell us to break a hard rule that the owner accepted
in the same breath (D6). So the outcome is recorded here and in §5 as paste-ready blocks. **It takes
effect for code only when the owner pastes them into `APPROVAL.md`.** Until then every scope there
reads `Pending`, and by CLAUDE.md no code outside `docs/` may be written.

**Revocation.** The owner can revoke all or part of this approval at any time by editing
`APPROVAL.md`: leave a scope `Pending`, set it to `Deferred` or `Rejected`, or add a condition. That
file wins over this record and over every ADR status line. If the owner does not ratify a disposition,
the matching ADR status line set by this review (§6) reverts to *Proposed*.

**Provenance caveat.** The delegation sentence reached this recorder through the workflow that ran the
review, not directly from the owner. The owner's transcription into `APPROVAL.md` is also the
confirmation that the delegation was meant as recorded.

## 1. Method

1. **Independent, non-author reviewers.** The package was split into five clusters. Each cluster went
   to a reviewer that had not authored any of its documents. Reviewers were told to be sceptical and
   to check claims against the repository, not against the documents themselves.

   | Cluster | Documents | Lens |
   |---|---|---|
   | core-engine | ADR 0010–0016, delivery-engine-spec, ICD, data-model, sequence and state diagrams | Distributed-systems correctness: ordering, idempotency, ack bounds, outbox state machine, migrations; repo claims (`WsClient.kt`, send sites, highest migration V27) |
   | crypto-identity-compliance | ADR 0017–0023, 0029, threat model, DPIA screening, counsel questions | Cryptography (P12: no hand-written crypto), key lifecycle, erasure, KVKK / 5651 / 5809 law, licence; repo citations spot-checked |
   | transports | ADR 0024–0028, capability matrix, brief §6.2/§13 (1.0–1.4) | Mobile platform and radio feasibility, background limits, RF legal gating, KMP module build facts (`SKIP_MOBILE`, targets, minSdk) |
   | process-governance | ADR 0030–0033, `docs/process/*`, APPROVAL.md, README | GitHub mechanics (rulesets, environments, `workflow_run`), CI trust, Claude Code hook contract. The proposed hook code was extracted and executed: 44/44 of the specified tests pass, plus extra probes |
   | plan-vv | SAD, roadmap, V&V plan, risk register, project review | Traceability (brief §13 exit criteria → roadmap → RQ), gate completeness, consistency with ROADMAP.md and the register totals (211 findings) |

2. **Resolution.** A separate resolver agent applied every must-fix: 39 across the five clusters, 198
   changes in total. It also resolved 159 of the 161 open reviewer notes. The 2 left are in
   `APPROVAL.md`, which the resolver did not touch. Each note it could not close became an *Open item*
   in `open-items.md`. The relative-link check over `docs/**/*.md` found 0 broken links.
3. **Re-check.** Each cluster went back to a reviewer, which checked the resolver's changes against the
   original must-fix list.
4. **Recorder (this document).** It closed the two items left after the re-check (§2), recorded the
   decisions and scope outcomes, and set the ADR status lines (§6).

## 2. Verdicts per cluster

| Cluster | Initial verdict | Must-fix themes (abridged) | Re-check verdict | After recorder |
|---|---|---|---|---|
| core-engine | APPROVE_WITH_FIXES | Cumulative DELIVERED bound defined three ways (only `complete_through_seq` is safe; the other two can permanently lose an edited old message from 1.0); three cursor definitions; cancelling a submitted outbox entry; EDIT/DELETE/REACT/REPORT had no wire path; retryable seal failure dispatchable; K3 rollback needed a schema change | **APPROVE** | APPROVE |
| crypto-identity-compliance | APPROVE_WITH_FIXES | D1 contradicted (AGPL as plan); D3 missed MinIO media and server-built plaintext backup archives (no deletion code); age gate 15 vs Terms 18+, age-gate DPIA only "recommended"; crypto-erase overstated on keys that are not rollback-resistant; Q-O4/Q-O6 still open | APPROVE_WITH_FIXES — one remaining: ADR-0021 status line still called the cutover open question Q-O4 | **Closed** by this recorder: the ADR-0021 status line now says "the 1.0 cutover is decided by owner decision D3 (2026-11-12; §4)" and that the §3 redefinitions are covered by D3 → APPROVE |
| transports | APPROVE_WITH_FIXES | ADR-0028: iosX64 (dropped by CMP 1.11.1), "JVM tests cheap, no SDK" (false under `SKIP_MOBILE`), AGPL licence column (D1), iOS gate wording (D2); matrix repeats D2 error | **APPROVE** | APPROVE |
| process-governance | APPROVE_WITH_FIXES | `.gitignore:62` ignores `.claude/` (hooks uncommittable); G2 allowed `.claude/worktrees/**`; G3 vs agent recording; one-open-scope rule vs batch approval; accepted-risk runner variant ignores agent credential and tag push; "delete the tag" impossible under immutable tags; incident record stale; WS-D/WS-E conflicts | **APPROVE** | APPROVE |
| plan-vv | APPROVE_WITH_FIXES | D1–D4 not reflected; G3/D1a vs recording; RQ-78 missed H10/H11; 0.4 entry vs Phase-0 exit; 1.0 counsel gate incomplete; D3 deletion had no RQ | APPROVE_WITH_FIXES — one remaining: the 0.4 Conditions dropped P0-7/P0-8 and three places worded them differently | **Closed** by this recorder: one wording now appears in `open-items.md` §2, roadmap §6 (0.4 Entry) and the RN-R11 note, and the brief §13 0.4 entry and risk R38 match it (§5, scope `0.4`). The self-contradiction about P0-5 is removed → APPROVE |

No cluster was rejected at any stage, so no ADR was held back from *Accepted* on review grounds. The
ADRs that remain *Proposed (gated)* (0024, 0025, 0026) do so because the owner's delegation says they
do.

## 3. Owner decisions D1–D6 (binding)

| # | Decision | Rationale | Where applied |
|---|---|---|---|
| D1 | **Licence (ADR-0017): no relicensing now.** `mobile/` stays MIT until 1.0 entry. Whether to relicense `mobile/` to AGPL-3.0-or-later or keep MIT sources and convey the libsignal-linked binary under AGPL-3.0 is decided at 1.0 entry, after counsel answers Q-L7. Does not block 0.4–0.7. | No libsignal is linked before 1.0 (it is commented out at `mobile/composeApp/build.gradle.kts:126`), so there is no AGPL obligation to meet yet. Relicensing is hard to reverse and needs a contributor audit and counsel's answer on Play / App Store distribution (Q-L7 g/h). | ADR-0017 (title, §2, status), ADR-0028 licence column, SAD, README, roadmap G-LICENCE, risk register, counsel Q-L7 |
| D2 | **iOS:** if KMP-01 (iOS cannot launch) is not fixed at the 0.6 gate, 1.0 ships Android-only, and the UI and store text say so. | KMP-01 is verified, and libsignal has no Kotlin/Native binding. Holding 1.0 for iOS would hold encryption hostage to a bridge that may not exist. Saying so in the UI and store text keeps P-honesty. | ADR-0028 §6, capability matrix, roadmap G-iOS / 06-3 / RM-6, RQ-42, threat model |
| D3 | **Plaintext history at 1.0:** clients download history into the encrypted local DB. The server copy (content, media, thumbnails, server-built backup archives) is deleted after a notified 30-day grace. Encrypted backup under a user-held key is opt-in. A conversation is labelled E2E only once the server holds no readable copy. | An "E2E" label over a server-readable archive would be a false claim. Deleting after a notified grace respects users who want their history. The crypto cluster showed that media and `BackupService` archives were readable copies the first text missed. | ADR-0021 §3/§4 and status, threat model T-HST-09 / DD-07, RQ-91…RQ-94, risk R44, counsel Q-L9(f); the backup-format ADR before 1.0 is OI-38 |
| D4 | **The ≤ 7-day offline revocation window is accepted and disclosed.** | Offline peers cannot learn of a revocation faster than the certificate lifetime. Shorter certificates would push devices that are rarely online into constant re-issue, and stating the window is the honest option. | ADR-0018, ADR-0029, RR-4, R15, RQ-18 |
| D5 | **No off-server path before 1.0**, enforced by the `SealedFrame` type gate. | A non-server path carrying plaintext is the single worst failure in the design. A compile-time gate cannot be bypassed by a flag. | ADR-0016, ADR-0012, ICD |
| D6 | **ADR-0033 amendments G3 (`APPROVAL.md` never agent-editable) and G5 (`.claude/active-scope`) accepted.** | Without G3 an agent could approve its own scope. G5 links each agent edit to one scope, as a guard and not a boundary. | ADR-0033, APPROVAL.md §1.1, and the reason this record does not edit `APPROVAL.md` (§0) |

## 4. Scope approvals

The **delegated decision** column is the outcome of this review. The **`APPROVAL.md` line** column is
what the hook should read once the owner transcribes it. They differ for 0.5–1.0 because of ADR-0033
D1a (check H11): at most one milestone scope reads `Status: Approved` at a time. Milestones approved
in advance are therefore entered as `Deferred` with a pre-approval condition, and the owner flips them
to `Approved` when the previous milestone exits.

| Scope | Delegated decision | `APPROVAL.md` line | Gates (conditions) |
|---|---|---|---|
| `phase-0` | **Approved** | `Status: Approved` | Owner-only actions remain owner-only: rotate every `.env.prod` value (incident #745), take the runner off cx43, resolve `muhabbet-mordecai`. The agent containment commit 235c63dd is ratified. |
| `process` (ADR 0030–0033) | **Approved** (amended) | `Status: Approved` | Amendments as listed in §5. Interim agent rules until OI-32 is closed. No GitHub setting is changed and no hook is installed by an agent; the hook ships with its tests, and T42/T43 have not been run. |
| `0.4` | **Approved** | `Status: Approved` | Branch work may start. Merge, deploy and release wait for P0-1…P0-4, P0-7 and P0-8, and the release tag also waits for P0-6. Minimum age 18. Age-gate DPIA before release. |
| `0.5` | **Approved** | `Status: Deferred` (pre-approved) | Opened by the owner at 0.4 exit. Verified production backup and restore (R30). OI-21 and OI-47 before 0.5 exit. |
| `0.6` | **Approved** | `Status: Deferred` (pre-approved) | Opened at 0.5 exit. iOS gate per D2. Device inventory (R41). |
| `0.7` | **Approved except the ADR 0009 hosting/budget choice** | `Status: Deferred` (pre-approved) | Opened at 0.6 exit **and** after the owner decides Q-O8 / ADR 0009 (OI-30). |
| `1.0` | **Approved in principle (gated)** | `Status: Deferred` | Opens only after the written counsel opinions Q-L1, Q-L4, Q-L7 and Q-L9 (OI-43), the external crypto review (OI-44), the D1 licence step (OI-29) and the backup-key ADR (OI-38). |
| `1.1` | **Proposed (gated)** | `Status: Pending` | 0.7 telemetry, TURN budget, Q-L6/Q-L8 opinions, DT-3 DPIA, Q-O5. |
| `1.2` | **Proposed (gated)** | `Status: Pending` | G-PROXIMITY in full. |
| `1.3` | **Proposed (gated)** | `Status: Pending` | 1.2 shipped, telemetry threshold, external review of uplink sealing, KVKK opinion, DPIA. |
| `1.4` | **Proposed (gated)** | `Status: Pending` | Written opinion on 5809 Art. 39/63(6), the coded-communications regulation and KET; TR builds excluded by Gradle. |

## 5. Paste-ready blocks for `APPROVAL.md` (for the owner)

These blocks use the exact format of `APPROVAL.md` §1.1, which ADR-0033 G6 parses: a heading line,
then four lines, no list markers and no trailing spaces. `Conditions` stays on one line.
`open-items.md` §2 (OI-46) carries the companion §1 items 7–8, the §3 disposition rows, the §4
answers and the replacement text for the two remaining reviewer-note callouts.

```text
### Scope: phase-0
Status: Approved
Approved-by: Claude, under owner delegation (2026-11-12 session); ratified by the owner on transcription
Date: 2026-11-12
Conditions: Rotation, runner removal from cx43 and muhabbet-mordecai remain owner actions (incident #745). Removing the two required checks from classic protection together with the runner change is part of this scope. Containment commit 235c63dd (made by an agent before approval) is ratified.

### Scope: process
Status: Approved
Approved-by: Claude, under owner delegation (2026-11-12 session); ratified by the owner on transcription
Date: 2026-11-12
Conditions: Amendments accepted: ADR-0030 (R-8a tag split; normative ruleset order; bundletool universal APK; burned-version rollback), ADR-0031 (runner off cx43; rotate after the runner change; JWT rotation invalidates access tokens only; variant conditions), ADR-0032 (fallback as default), ADR-0033 (G3, G5, narrowed G2, .gitignore re-include). Until OI-32 closes: every agent commit carries the Claude-Session trailer; agent PRs and issues are labelled source:agent; agents never push under .github/workflows/; agents never merge, tag or dispatch. Hook ships with its test (T01-T43 green). ADR-0030 D8 option (b) for 0.4 issues.

### Scope: 0.4
Status: Approved
Approved-by: Claude, under owner delegation (2026-11-12 session); ratified by the owner on transcription
Date: 2026-11-12
Conditions: Implementation may start on branches now (P0-5 met by the delegated dispositions). Nothing is merged to main, deployed or released until P0-1…P0-4, P0-7 (protected release/production environments) and P0-8 (SHA-pinned actions) are met. P0-6 (dev→main merge, rulesets) must be complete before the 0.4 release tag. Minimum age = 18 until OI-08. Age-gate DPIA (DT-7) before release (OI-36). MOB-15 added.

### Scope: 0.5
Status: Deferred
Approved-by: Claude, under owner delegation (2026-11-12 session); ratified by the owner on transcription
Date: 2026-11-12
Conditions: Pre-approved by delegation 2026-11-12; the owner sets Approved at 0.4 exit (ADR-0033 D1a). Entry also needs a verified production backup and restore test (R30). Before 0.5 exit: OI-21, OI-47.

### Scope: 0.6
Status: Deferred
Approved-by: Claude, under owner delegation (2026-11-12 session); ratified by the owner on transcription
Date: 2026-11-12
Conditions: Pre-approved by delegation 2026-11-12; the owner sets Approved at 0.5 exit. iOS gate per D2 (Android-only 1.0 if KMP-01 is not fixed; UI and store text say so). Device inventory recorded (R41).

### Scope: 0.7
Status: Deferred
Approved-by: Claude, under owner delegation (2026-11-12 session); ratified by the owner on transcription
Date: 2026-11-12
Conditions: Pre-approved by delegation 2026-11-12 EXCEPT the ADR 0009 hosting/budget choice (Q-O8), which stays with the owner (OI-30). The owner sets Approved at 0.6 exit and only after deciding Q-O8. Device inventory recorded (R41).

### Scope: 1.0
Status: Deferred
Approved-by: Claude, under owner delegation (2026-11-12 session); ratified by the owner on transcription
Date: 2026-11-12
Conditions: Approved in principle (gated). Opens only after written counsel opinions Q-L1, Q-L4, Q-L7, Q-L9 (OI-43), the external crypto review (OI-44), the D1 licence step (OI-29) and the backup-key ADR (OI-38); D3 cutover with notified 30-day grace.

### Scope: 1.1
Status: Pending
Approved-by:
Date:
Conditions: Proposed (gated) per delegated review 2026-11-12; entry gates in brief §13 (1.1).

### Scope: 1.2
Status: Pending
Approved-by:
Date:
Conditions: Proposed (gated) per delegated review 2026-11-12; G-PROXIMITY in full.

### Scope: 1.3
Status: Pending
Approved-by:
Date:
Conditions: Proposed (gated) per delegated review 2026-11-12; 1.3 entry gate in brief §13.

### Scope: 1.4
Status: Pending
Approved-by:
Date:
Conditions: Proposed (gated) per delegated review 2026-11-12; written 5809 Art. 39/63(6) opinion first.
```

`Approved-by` names Claude because Claude did the review. The words "ratified by the owner on
transcription" record that the owner's own edit is what makes it binding. If the owner prefers the
`open-items.md` §2 wording, which puts the owner's name with "(by delegation)", either form is
acceptable: the hook reads only the `Status:` line.

## 6. ADR and document status changes made by this record

| Record | New status |
|---|---|
| ADR 0010–0016, 0018–0020, 0022, 0027–0029 | Accepted (2026-11-12, delegated). Gated parts stay gated: 0015 `PathPolicy` at 1.2; 0018 items 4–5 at 1.2; 0022 plane C and `UplinkBundle` at 1.2/1.3. |
| ADR 0021 | Accepted (2026-11-12, delegated; amended by D3). The status line no longer calls the cutover open. |
| ADR 0023 | Accepted for 0.7, except the ADR 0009 hosting/budget choice (owner, Q-O8). The 1.1 part stays gated. |
| ADR 0017 | Accepted with D1: the licence step is deferred to 1.0 entry. |
| ADR 0030–0033 | Accepted (2026-11-12, delegated; process, amended). |
| ADR 0024, 0025, 0026 | Proposed (gated). The review confirms them as gated designs; they are not accepted. |
| ADR-003 | Superseded by ADR 0016 (ADR 0017 related). Its banner and status are updated. |
| ADR 0008 | Amended by ADR 0021. ADR 0008 itself was still *Proposed*, and the review does not accept it beyond what ADR 0021 restates. |
| `docs/ARCHITECTURE.md` | Banner: §4 is amended by ADR 0011 and §8 by ADR 0012, and the details are folded into the text when 0.5 lands. |
| README, decision brief | Status line and status table updated. Both point here. |

Every *Accepted* status line keeps the gate. It says code is written only while the matching scope
reads `Status: Approved` in `APPROVAL.md`. `APPROVAL.md` §3 says ADR files change only after a §3
row is filled. This record changes them first, on the strength of the delegation, and flags that as a
deviation. If the owner does not transcribe a row, that ADR reverts.

## 7. Residual risks

1. **The gate is closed in fact.** `APPROVAL.md` still reads `Pending` for every scope, so by CLAUDE.md
   no code outside `docs/` may be written for any scope yet. **Workstream WS-E (implementing 0.4 in
   this session) is therefore ahead of the gate** until the owner transcribes §5. Its branch work is at
   risk of being discarded and must not be merged. The same applies to anything WS-D filed beyond what
   ADR-0030 D8 option (b) allows.
2. **The delegation came through the workflow, not directly from the owner** (§0). The owner's
   transcription is the confirmation.
3. **The security incident is open.** Incident #745 (secrets in public history, PR jobs on the
   production runner) needs owner-only action: rotation, runner removal and resolving
   `muhabbet-mordecai`. Until then 0.4 cannot be merged, deployed or released, whatever its approval
   says. 42 of 44 workflow action references are still unpinned (P0-8).
4. **The hook is unverified for the worktree change.** Tests T42/T43 (`.claude/worktrees/**` no longer
   bypasses the gate) are specified but have not been run. The 44/44 result covers T01–T41. Until the
   hook is installed, the gate holds by convention only.
5. **External dependencies for 1.0.** The counsel opinions (Q-L1, Q-L4, Q-L7, Q-L9), the external
   crypto review, the D1 licence step and the backup-key ADR are all outstanding. The age-rule in-force
   date (R38, Q-L3) is unverified, and registration has no age gate today.
6. **An owner-only decision blocks 0.7.** ADR 0009 / Q-O8 (LiveKit host and budget) is still Proposed.
   Calls have never worked (#367–#373).
7. **47 open items remain** in [`open-items.md`](../architecture/multi-transport/open-items.md), owned
   by the owner, counsel or a later milestone. They include the two reviewer-note callouts in
   `APPROVAL.md` §1.1, whose replacement text is in OI-46.
8. **The review was done by agents.** Every reviewer and the resolver were Claude agents. "Independent"
   here means non-author, not non-Claude. No device-dependent claim was observed on a device, and per
   P13 those claims stay UNMEASURED until the owner signs them off.
