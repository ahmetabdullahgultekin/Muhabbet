# Multi-transport delivery — open items

| | |
|---|---|
| **Date** | 2026-11-12 (delegated review) |
| **Role** | The **single source of truth** for every item the delegated review could not close. A document that still depends on one of these items carries an "Open item OI-NN" callout that links here; nothing else in the package is open. |
| **Decisions applied** | Owner decisions D1–D6 and the scope outcomes are recorded in the [decision brief](decision-brief-2026-11-12.md) header; the errata it adopted are in brief §18. |
| **Who closes an item** | `owner` items: the owner, in `APPROVAL.md` (§4 or a scope's `Conditions:`) or in the tracker. `counsel` items: a written opinion filed with the counsel question named. `milestone` items: the work named, at the milestone named. Closing an item means updating its row here and the callouts that link to it. |

Types: **owner** — a decision only the owner can take; **counsel** — needs a written legal opinion;
**milestone** — deliberately deferred to a named gate or piece of work.

## 1. Register

| ID | Summary | Type | Blocks | Source file(s) | Proposed default | Owner |
|---|---|---|---|---|---|---|
| <a id="oi-01"></a>OI-01 | Per-user safety-number QR over several identity keys, versus one fingerprint per device (`safetyNumber(peer: DeviceAddress)`) | milestone | 1.0 entry (external crypto review) | `docs/adr/0018-identity-keys-device-certificates.md` | Per-device fingerprints (libsignal primitive); no hand-designed multi-key fingerprint (P12) | Crypto reviewer / owner |
| <a id="oi-02"></a>OI-02 | FTS5 `contentless_delete=1` availability in the SQLite bundled by the chosen SQLCipher build | milestone | 1.0 entry spike (SQLCipher/FTS) | `docs/adr/0020-data-at-rest-and-erasure.md` | If unavailable, external-content FTS5 table inside the same encrypted DB | Implementer |
| <a id="oi-03"></a>OI-03 | Uplink signing key and scheme for `UplinkBundle`/`SenderProof` (dedicated non-persisted signature key, identity key under `mbt-uplink-sig-v1`, or deniable device–server MAC) | milestone | 1.3 entry (crypto review; ADR-0018 amendment) | ADR-0022, ADR-0025 (two notes), `icd-wire-protocol.md` §8.4 and index row 5, `threat-model.md` RN-1 | Deniable device–server MAC (keeps brief §8.3 deniability on every path) | Crypto reviewer / owner |
| <a id="oi-04"></a>OI-04 | HPKE (RFC 9180) library available on Android, iOS and the backend JVM | milestone | 1.3 entry (crypto review) | `docs/adr/0025-store-and-forward-custody.md` | Selected by the crypto review; never hand-written | Crypto reviewer |
| <a id="oi-05"></a>OI-05 | In-force KET duty-cycle limits versus the May 2026 *TASLAĞI* table | counsel | 1.4 entry | `docs/adr/0026-meshtastic-legal-gate.md`; counsel Q-L2(e) | Limits stay configuration; no document presents 1%/10% as in force | Counsel |
| <a id="oi-06"></a>OI-06 | How a non-TR Meshtastic build reaches users, and what happens when a Turkish user installs it | owner | 1.4 entry | `docs/adr/0026-meshtastic-legal-gate.md` | No non-TR build until counsel clears 1.4; if one ships, a runtime region check disables RF | Owner |
| <a id="oi-07"></a>OI-07 | Meshtastic node-firmware channel encryption (AES) over *telsiz*, separate from Muhabbet's E2E payload | counsel | 1.4 entry | `docs/adr/0026-meshtastic-legal-gate.md`; counsel Q-L2(a)(4) | Covered in the Q-L2 opinion before any RF build | Counsel |
| <a id="oi-08"></a>OI-08 | `[AGE-POLICY DECISION]`: minimum age 18, 16 with parental consent, or 15 | owner | 0.4 release | `docs/adr/0029-compliance-boundary.md`; `docs/legal/04-kullanim-kosullari.md:34`; counsel Q-L3(d)/(f)/(h) | `muhabbet.age.minimum-years = 18` (the published Terms) until decided | Owner |
| <a id="oi-09"></a>OI-09 | On `JWT_SECRET` rotation, keep sessions or revoke all refresh tokens | owner | Phase 0 (incident action A4) | `docs/adr/0031-ci-trust-boundary.md`; `docs/process/security-incident-2026-11-env-prod.md` | Revoke all refresh tokens (fail closed; one re-login per user) | Owner |
| <a id="oi-10"></a>OI-10 | Message identity: keep `messages.id = client_message_id` or adopt the brief-literal pair reference with a server-minted id (removes `MSG_ID_SQUATTED`) | milestone | 1.0 entry (before ICD §6.2 freezes; 1.2 design-review input) | `data-model.md` §3.2 and index row 10; `icd-wire-protocol.md` §3.6 and index row 11 | Pair reference `(sender_device_id, client_message_id)` | Owner / design review |
| <a id="oi-11"></a>OI-11 | Keep or drop per-message `message_device_delivery` rows (V32) and the mobile `receipt` table | milestone | 0.6 (when V32 is written) | `data-model.md` §3.6 | Drop if `GET /messages/{id}/info` can live with cursor-derived status | Implementer / owner |
| <a id="oi-12"></a>OI-12 | Scheduled send in E2E conversations: (a) server-held envelopes, (b) unavailable, (c) client-side scheduling | owner | 1.0 entry (ICD freeze) | `icd-wire-protocol.md` §3.5 and index row 12; `data-model.md` index row 11 | ICD assumes (b); note that under D3 (b) removes scheduled send from the whole product | Owner |
| <a id="oi-13"></a>OI-13 | Q-O3 LOCAL_ONLY receipt-metadata retention; makes the spec's custody-ack reading normative | owner | 1.0 (LOCAL_ONLY), 1.3 | `delivery-engine-spec.md` R-1; counsel Q-L9(c) | LOCAL_ONLY not shipped until answered; spec reading provisional | Owner (with counsel) |
| <a id="oi-14"></a>OI-14 | Re-run the `wsClient`/`WsMessage` call-site inventory at stage S5, including `App.kt` | milestone | 0.5 (stage S5) | `delivery-engine-spec.md` R-6, §15 | Inventory is the S5 entry check | Implementer |
| <a id="oi-15"></a>OI-15 | `Control.decryptFailed`, per-device re-seal path distinct from replay, tick label "could not be delivered securely" | milestone | 1.0 entry (ICD + ADR-0013 amendment) | `delivery-engine-spec.md` R-9, T33 | Defined in ICD §6.2/§3 and ADR-0013 before 1.0 entry | Implementer / owner |
| <a id="oi-16"></a>OI-16 | What Play services sends to Google during Nearby discovery and connection | milestone | 1.2 entry (G-PROXIMITY spike) | `dpia-screening.md` | If any data flows, Google is a DT-5 processor in the 1.2 DPIA and notice | Spike owner |
| <a id="oi-17"></a>OI-17 | VERBIS registration (KVKK Art. 16) | counsel | none directly (compliance hygiene); before 1.0 | `dpia-screening.md`; counsel Q-L6(f) | Register if counsel says the threshold or exemption does not apply | Counsel / owner |
| <a id="oi-18"></a>OI-18 | Authorising spikes (1.0 iOS bridge, 1.2 device matrix/handshake) as `Conditions:` lines; precondition SP-P6 (Q-L2 (a)(1)–(3) answered, or counsel confirms test traffic between owner-held devices is acceptable) | owner | 1.0 entry (iOS bridge spike), 1.2 entry | `roadmap.md` RN-R4; `transport-capability-matrix.md` SP-1, §13.2 | "Harness code only, outside product modules, never distributed, deleted after the report"; only the owner writes it (G3) | Owner |
| <a id="oi-19"></a>OI-19 | Data source and method for the HMS share in Turkey | milestone | 1.2 entry (G-PROXIMITY) | `roadmap.md` RN-R6 | Own install base by device vendor plus one public market-share source | Owner |
| <a id="oi-20"></a>OI-20 | 1.2 scope and DPIA include the telemetry "Nearby sessions where one side lacked internet" that 1.3 entry needs | milestone | 1.2 scope approval | `roadmap.md` RN-R7; brief erratum E-43 | Included when 1.2 is approved | Owner |
| <a id="oi-21"></a>OI-21 | ROADMAP "Patch releases fix defects only" versus the 0.5.1 flag flip and 0.5.2 deletion | owner | 0.5 exit (0.5 Condition) | `roadmap.md` RN-R9 | Amend the rule to allow flipping an already-shipped flag's default and removing dead code behind it | Owner |
| <a id="oi-22"></a>OI-22 | Account takeover: does a new-handset OTP revoke or add; registration lock; companion-link phishing (T-REG-06) | owner | 1.0 entry | `threat-model.md` RN-4; `risk-register.md` R25 | Add, not revoke, plus a time-delayed re-registration lock — owner to confirm | Owner |
| <a id="oi-23"></a>OI-23 | Freeze the spike pass thresholds (§13.5) before the first run | milestone | 1.2 spike approval | `transport-capability-matrix.md` SP-4 | Recorded with the spike approval, dated | Owner |
| <a id="oi-24"></a>OI-24 | Optional iOS↔iOS Wi-Fi Aware (prior DeviceDiscoveryUI pairing, background) cell in the device-matrix spike | milestone | 1.2 spike approval | `docs/reviews/2026-11-12-gemini-proposal-review.md`; `transport-capability-matrix.md` §13.3 | Include as optional cell | Owner |
| <a id="oi-25"></a>OI-25 | Commit the Gemini chat transcript, or record that `G:n` references cannot be checked | owner | none (package completeness) | `docs/reviews/2026-11-12-gemini-proposal-review.md` | Commit as `docs/reviews/2026-11-12-gemini-chat-transcript.md`, marked third-party LLM output | Owner |
| <a id="oi-26"></a>OI-26 | FT-02 server-only features (chat folders, pinned-in-chat, backup, bots): wire under a named milestone or delete the endpoints | owner | none directly; must not be claimed as built (brief E-34) | `docs/reviews/2026-11-12-project-review.md` | Delete (YAGNI) unless a milestone names them | Owner |
| <a id="oi-27"></a>OI-27 | In-force date of the 7578 amendment (5651 Ek md. 4/5, 1/11/2026) | counsel | Any user-facing or transparency text quoting the date | `sad.md` §2.3; `docs/legal/counsel-questions-2026-11.md`; ADR-0029 §1 | Text says "according to the consolidated text; legal confirmation pending" | Counsel |
| <a id="oi-28"></a>OI-28 | Counsel quotes Law 5809 from the current mevzuat.gov.tr text only (post Law 7590) | counsel | 1.0 entry (Q-L1) | `docs/legal/counsel-questions-2026-11.md` Q-L1(f) | — | Counsel |
| <a id="oi-29"></a>OI-29 | D1 licence step: relicense `mobile/` to AGPL-3.0-or-later or convey MIT sources in an AGPL combined work; App Store/Play terms vs AGPL §10 and §6 | counsel | 1.0 entry | ADR-0017; counsel Q-L7(g)/(h); brief Q-O1 | Owner chooses after the written Q-L7 opinion; not blocking 0.4–0.7 | Counsel, then owner |
| <a id="oi-30"></a>OI-30 | ADR 0009 LiveKit host placement and budget; TURN egress budget (Q-O8) | owner | 0.7 entry (and therefore 1.1) | `roadmap.md` RN-R5; `APPROVAL.md` scope 0.7; ADR 0009 | — | Owner |
| <a id="oi-31"></a>OI-31 | New `:mobile:*` modules: inside the `SKIP_MOBILE` guard with `jvmTest` in the mobile CI job, or keep `:mobile:crypto`/`:mobile:messaging` free of Android plugins so they sit outside the guard | milestone | 0.5 (before the modules are created) | `docs/adr/0028-kmp-module-structure.md` §3 | Inside the guard; `jvmTest` runs in the mobile CI job | Implementer / owner |
| <a id="oi-32"></a>OI-32 | Can the Claude Code harness push under a separate GitHub App identity (ADR-0032 Decision 1)? | owner | `process`: ruleset step (github-governance §12.1 step 4) | ADR-0032; `docs/process/agent-workflow.md` §2 | Until shown: required approvals = 0 plus CODEOWNERS owner review; interim agent conditions apply | Owner |
| <a id="oi-33"></a>OI-33 | Worked byte budget for a ≈ 200 B RF frame (header, link AEAD tag, SignalMessage overhead, padding bucket, Meshtastic payload limit) | milestone | 1.4 entry | `docs/adr/0026-meshtastic-legal-gate.md` §2 | Budget written before 1.4 entry; per-preset golden vectors | Implementer |
| <a id="oi-34"></a>OI-34 | Custodian 2 inbound role: how a holder obtains another device's per-device envelope and how the server authorises it — or drop the inbound case | milestone | 1.3 entry | `docs/adr/0025-store-and-forward-custody.md` §5 | Specify before 1.3 entry, else drop | Owner / implementer |
| <a id="oi-35"></a>OI-35 | Grace period before an unattested pre-0.4 account stops being served | counsel | 0.4 or 0.5 (re-attestation item) | `docs/adr/0029-compliance-boundary.md` Rule 1; counsel Q-L3(c) | 30 days | Counsel |
| <a id="oi-36"></a>OI-36 | Age-gate DPIA (trigger DT-7) | milestone | 0.4 exit (before 0.4 ships) | `dpia-screening.md` §3.1, §7; ADR-0029 Rule 6; counsel Q-L3(g) | DPIA signed before the 0.4 release | Owner |
| <a id="oi-37"></a>OI-37 | Purge job for server-built plaintext backup archives that outlive `expiresAt` (KVKK storage limitation; pre-1.0 defect) | owner | none directly (schedule it); 1.0 for E2E conversations | ADR-0021 §4; `threat-model.md` T-HST-09 / DD-07 | File a defect now; purge at `expiresAt` | Owner |
| <a id="oi-38"></a>OI-38 | Backup format and key-handling ADR for the D3 opt-in encrypted backup | milestone | 1.0 entry (in scope of the external crypto review) | ADR-0021 §3 | Client-generated ≥ 128-bit recovery code; vetted library (P12) | Owner / crypto reviewer |
| <a id="oi-39"></a>OI-39 | Kill thresholds for 1.1, 1.3 and 1.4 (Q-O5) | owner | 1.1, 1.3, 1.4 entry | brief §17 | — | Owner |
| <a id="oi-40"></a>OI-40 | Where the device-CA intermediate and the uplink key live (Q-O7) | owner | 1.2, 1.3 entry | brief §17 | A second small host or a hardware token; never cx43 | Owner |
| <a id="oi-41"></a>OI-41 | Turkish UI copy for offline labels (Q-O11) | owner | 0.5 (copy), 1.2 | brief §17 | "Henüz eşitlenmedi"; "Yakındakiler" at 1.2 | Owner |
| <a id="oi-42"></a>OI-42 | Residual of the opaque wake payload (device token plus timing visible to the push provider) | counsel | 0.5 release (notice text) | ADR-0019; counsel Q-L6(b) | Disclosed in the privacy notice | Counsel |
| <a id="oi-43"></a>OI-43 | Written counsel opinions Q-L1, Q-L4, Q-L7 and Q-L9 (Q-L9 also gates the D3 deletion schedule) | counsel | 1.0 entry | `roadmap.md` §4 G-LEGAL; `vv-plan.md` RQ-40 | — | Counsel |
| <a id="oi-44"></a>OI-44 | External crypto review engaged and its findings closed | milestone | 1.0 entry / exit | brief §13 1.0; ADR-0017; ADR-0021 | — | Owner |
| <a id="oi-45"></a>OI-45 | CODEOWNERS assigning `APPROVAL.md`, `.claude/**` and `.github/workflows/**` to the owner | milestone | `process` (before the design-gate hook is armed) | ADR-0032/0033; `APPROVAL.md` | Created in the first `process` PR | Owner |
| <a id="oi-46"></a>OI-46 | Transcribe the delegated approval into `APPROVAL.md` (paste-ready text in §2 below) | owner | Every scope (the hook reads only `APPROVAL.md`) | `APPROVAL.md`; ADR-0033 G3 | Owner pastes §2, or merges a PR carrying it (CODEOWNERS) | Owner |
| <a id="oi-47"></a>OI-47 | p90 wake-to-sync bound for R11, as a 0.5 Condition | owner | 0.5 exit | `risk-register.md` R11; `vv-plan.md` | Owner sets the number before 0.5 exit | Owner |

## 2. Paste-ready text for `APPROVAL.md` (OI-46)

`APPROVAL.md` is never agent-editable (ADR-0033 G3, accepted under D6), so the delegated review does
**not** edit it. The text below is what the review proposes the owner (or the recorder acting on the
owner's merge) enters. It keeps ADR-0033 D1a: only one milestone scope reads `Status: Approved`.

**§1, new item 7 — Delegated approval (one-time, 2026-11-12).** "On 2026-11-12 the owner delegated the
review and approval of this package: *'Kendin hepsini bağımsız review et ve onayla ve devam'* (review
everything independently yourself, approve and continue), and took decisions D1–D6 (decision brief
header). The scope blocks below record that delegation. It is the single sanctioned agent edit of this
file, made before the G3 hook exists, and it is ratified only when the owner merges the PR that carries
it (CODEOWNERS on this file, ADR-0032 D3). From then on G3 applies with no exception. `Approved-by`
reads `<owner> (by delegation, see §1.7)`, never an agent name."

**§1, new item 8 — One open milestone (ADR-0033 D1a).** "At most one milestone scope (0.4–1.4) reads
`Status: Approved` at a time. Milestones approved in advance are recorded as `Deferred` with the
condition 'pre-approved by delegation 2026-11-12; set to Approved by the owner at exit of the previous
milestone'. Any change to this file runs check H11."

**Scope blocks:**

| Scope | Status | Conditions |
|---|---|---|
| `phase-0` | Approved | Removing the two required checks from classic protection together with the runner change is part of this scope. The containment changes of commit 235c63dd (made by an agent before approval) are ratified. |
| `process` | Approved | Amendments accepted: ADR-0030 (R-8a tag split; normative ruleset order; bundletool universal APK; burned-version rollback), ADR-0031 (runner off cx43; rotate after the runner change; JWT rotation invalidates access tokens only; variant conditions), ADR-0032 (fallback as default), ADR-0033 (G3, G5, narrowed G2, `.gitignore` re-include). Interim agent conditions until OI-32 is closed: every commit carries the Claude-Session trailer; every agent PR or issue is labelled `source:agent`; agents never push changes under `.github/workflows/`; agents never merge, tag or dispatch. Expiry: the ADR-0032 fallback decision is due before github-governance §12.1 step 4. Issue filing: ADR-0030 D8 option (b) — 0.4 implementation issues may be filed before tracker cleanup TC-11 if labelled `status:needs-evidence`, milestone 0.4, and linked from one Phase-0/0.4 epic; no other multi-transport epics before TC-11. |
| `0.4` | Approved | Implementation may start on branches now (P0-5 met by the delegated dispositions). Nothing is merged to `main`, deployed or released until P0-1…P0-4, P0-7 (protected `release`/`production` environments) and P0-8 (SHA-pinned actions) are met. P0-6 (dev→main merge, rulesets) must be complete before the 0.4 release tag. Minimum age = 18 until OI-08. Age-gate DPIA before release (OI-36). MOB-15 added. |
| `0.5` | Deferred | Pre-approved by delegation 2026-11-12; set to Approved by the owner at 0.4 exit. Entry also needs a verified production backup and restore test (R30). Conditions to record before 0.5 exit: OI-21, OI-47. |
| `0.6` | Deferred | Pre-approved by delegation 2026-11-12; set to Approved by the owner at 0.5 exit. iOS gate per D2. Device inventory recorded (R41). |
| `0.7` | Deferred | Pre-approved by delegation 2026-11-12 except ADR 0009 hosting/budget (Q-O8, owner only, OI-30); set to Approved by the owner at 0.6 exit and after Q-O8. Device inventory recorded (R41). |
| `1.0` | Deferred | Approved in principle 2026-11-12. Opens only after the written counsel opinions Q-L1, Q-L4, Q-L7, Q-L9 (OI-43), the external crypto review (OI-44), the licence step per D1 (OI-29) and the backup-key ADR (OI-38). |
| `1.1`–`1.4` | Pending | Proposed (gated) behind their own entry gates. |

**§3 disposition rows (all dated 2026-11-12, by delegation):** 0010–0016, 0019, 0020, 0022–0025,
0027, 0029 *Accepted* (0015, 0018 items 4–5, 0022 plane C/UplinkBundle, 0023 1.1 part, 0024, 0025 and
0026 stay *Proposed (gated)* for their gated parts); 0017 *Amended* (D1); 0018 *Amended* (relationship
predicate, device-set freshness, IdentityKeyChanged wording); 0021 *Amended* (D3, readable-copy
definition, backup-key constraints); 0028 *Amended* (targets, SKIP_MOBILE, licence column, D2);
0030–0033 *Amended* (as listed under `process`). ADR-0027 attaches to scope `phase-0`.

**§4 answers (2026-11-12, by delegation):** Q-O1 ← D1 (deferred to 1.0 entry after Q-L7);
Q-O2 ← D2; Q-O4 ← D3; Q-O6 ← D4; Q-O9 ← Approved; Q-O8 stays with the owner.

**The two remaining reviewer-note callouts in `APPROVAL.md`** (§1.1) are resolved as follows and should
be replaced by the owner:

- First callout → "> **Resolved (2026-11-12):** G3 and G5 accepted under D6 (§3 row 0033). The G2
  always-allowed set is narrowed to `.claude/settings.json`, `.claude/hooks/**` and
  `.claude/active-scope` (ADR-0033). CODEOWNERS for this file and `.claude/**` is [OI-45]."
- Second callout → "> **Note:** until the hook is installed, the gate holds by convention."
