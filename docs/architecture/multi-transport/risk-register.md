# Muhabbet multi-transport risk register

> **Status: Approved by delegated owner review (2026-11-12), per scope:** Phase 0, `process`, 0.4–0.6
> approved; 0.7 approved except the ADR 0009 hosting/budget choice; 1.0 approved in principle and gated;
> 1.1–1.4 Proposed (gated). The binding record is [`APPROVAL.md`](APPROVAL.md) (one milestone open at a
> time, D1a). No code may be written for any mitigation listed
> here until approval for the relevant scope is recorded there (owner decisions 3 and 4). The
> security emergencies behind R1–R2 are the exception the brief recommends the owner authorise now,
> under scope `phase-0`.

| | |
|---|---|
| **Date** | 2026-11-12 (living after approval; reviewed at every milestone entry and exit) |
| **Base** | `dev` @ 2434324, branch `claude/muhabbet-p2p-architecture-wdan5r` |
| **Source of truth** | [decision brief](decision-brief-2026-11-12.md) §15 (R1–R22). Rows R1–R22 keep the brief's wording, ratings, mitigations, owners and triggers; this register adds status, linkage and review points. **R23–R43 are added by this document** from the findings register, the threat model and the reviewer notes of the package; their ratings are proposals for the owner. |
| **Companions** | [`roadmap.md`](roadmap.md) (gates and kill criteria), [`vv-plan.md`](vv-plan.md) (RQ IDs), [`threat-model.md`](threat-model.md) (T-* IDs, RN-1…RN-6) |

---

## 1. Scales and fields

| Field | Values |
|---|---|
| **L** (likelihood, before mitigation) | **L** unlikely within the planning horizon (through 1.4) · **M** plausible · **H** expected, or already true |
| **I** (impact) | **L** inconvenience · **M** milestone slip or degraded guarantee · **H** user harm, data exposure, legal exposure or lost work · **Critical** compromise of production, keys or every user |
| **Owner** | Owner (the project owner) · Counsel · Agents (implementation) · Reviewer (external crypto reviewer) |
| **Trigger** | The observable event that means the risk is materialising or must be re-rated |
| **Status** | **Open** (no mitigation in effect) · **Planned** (mitigation scheduled in an approved scope) · **Mitigating** (mitigation partly in effect) · **Accepted** (owner accepted the residual, dated) · **Closed** (mitigation verified, evidence linked) |
| **Review point** | The milestone gate at which the row must be re-rated |

Since the delegated approval of 2026-11-12, a row whose mitigation sits inside an approved scope
(Phase 0, `process`, 0.4–0.6, 0.7 except ADR 0009 hosting) is **Planned**; rows mitigated only in
gated or open scopes stay **Open** unless evidence on `dev` shows a mitigation partly in effect.
Accepted residuals cite the owner decision (D1–D6).

## 2. Heat summary

| | I = L | I = M | I = H | I = Critical |
|---|---|---|---|---|
| **L = H** | R34 | R9, R19, R36 | R4, R27, R38 | R1 |
| **L = M** | R16, R22, R26, R33 | R10, R11, R13, R17, R18, R21, R31, R35, R40, R42 | R3, R5, R7, R8, R20, R24, R25, R28, R37, R39, R41 | R2 |
| **L = L–M** | | R32 | R6, R30, R43 | R23 |
| **L = L** | | R12, R15 | | R14, R29 |

## 3. Register

### 3.1 Brief risks (R1–R22)

| ID | Risk | L | I | Owner | Trigger | Mitigation | Status | Links | Review point |
|---|---|---|---|---|---|---|---|---|---|
| R1 | Leaked prod secrets (public history) still live | H | Critical | Owner | Now | Rotate all; secret scanning; commit-scoped allowlist | **Open** — rotation unverified (CICD-06 "unverifiable") | CICD-06; T-SRV-04, T-HST-02; RQ-76; roadmap P0-1/P0-2 | Phase 0 exit |
| R2 | Fork or branch PR code executes on the prod host (docker group = root) | M | Critical | Owner | Any PR job on cx43 | ADR-0031; move CI; no keys on host | **Open** | CICD-01, CICD-03, CICD-04, MISS-01 (CI/CD); T-HST-01; RQ-76 | Phase 0 exit; 1.2/1.3 entry (G-CI) |
| R3 | Engine refactor regresses the live send path | M | H | Owner/agents | Any loss or duplicate in the suite or on the emulator | `engine.enabled` overlap; keep `WsClient` loop and its tests; SimulatedLink suite; emulator script before default flip | **Open** | MOB-04, MOB-11, #342; RQ-01, RQ-02, RQ-29; roadmap 0.5 rollback criterion | 0.5.0 exit; 0.5.1 flip; 0.5.2 deletion |
| R4 | Solo bandwidth: 0.4–1.0 backlog plus new paths | H | H | Owner | Milestone slip > 1 release | Post-1.0 paths gated and droppable; the engine fixes existing bugs | **Open** — aggravated by the two 0.5.0 scopes (roadmap RN-R2) | GOV-05, FT-02; R36 | every milestone exit |
| R5 | Legal classification as *işletmeci* (5809 Art. 9(14)) imposes retention or interception duties that conflict with E2E | M | H | Counsel/Owner | Opinion received | Counsel before 1.0; server keeps metadata; plan B documented | **Open** | LEG-01, MISS-01 (legal); Q-L1, Q-L9 | 1.0 entry |
| R6 | Art. 39 read to cover E2E over BLE/Wi-Fi, blocking 1.2 in TR | L–M | H | Counsel | Opinion | Opinion request names BLE/Wi-Fi; 1.2 gate | **Open** | LEG-03; Q-L2; T-RF-01 | 1.2 entry |
| R7 | Self-declared age gate judged insufficient (*yaş doğrulama*) | M | H | Counsel/Owner | Regulator guidance or opinion | `AgeAssurancePort`; do not claim compliance; e-Devlet/NFC path | **Open** | LEG-02, MISS-04 (legal); Q-L3; T-REG-03; RQ-10 | 0.4 exit; on opinion |
| R8 | AGPL on the App Store or Play deemed incompatible | M | H | Counsel/Owner | Opinion | D1: no relicensing now; `mobile/` stays MIT until 1.0 entry; relicence vs convey-under-AGPL decided after the written Q-L7 opinion; vodozemac fallback if negative | **Planned** (decision deferred to 1.0 entry, [OI-29](open-items.md#oi-29)) | KMP-13; Q-L7 (sharpened per ADR-0017 reviewer note); RQ-40, RQ-44 | 1.0 entry |
| R9 | iOS still cannot launch (KMP-01), so cross-platform claims are hypothetical | H | M | Owner | 0.6 entry | D2: blocking 0.6 gate; if KMP-01 is not fixed, 1.0 ships Android-only and the UI and store text say so | **Planned** | KMP-01, KMP-02, KMP-09, FT-04, #79, #729, #735; RQ-42 | 0.6 exit |
| R10 | libsignal iOS bridge (Swift-only, `@objc`) harder than planned | M | M | Owner | Spike fails | Pre-1.0 spike; D2: Android-only 1.0 stated in the UI and store text | **Planned** | ADR-0017 pre-1.0 spike; MISS-3 (networking) by analogy (LiveKit bridge), KMP-13; RQ-41, RQ-42 | 1.0 entry |
| R11 | Wake-only push delays notifications on aggressive OEMs | M | M | Owner | Measured p90 latency above the agreed bound | High-priority data messages, foreground pull, WorkManager, battery-exemption prompt; measure on Xiaomi/Oppo | **Planned** — bound not yet agreed by the owner (0.5 Condition, [OI-47](open-items.md#oi-47)) | BE-15, #502; T-PSH-04; QS-17; RQ-49 | 0.5 exit |
| R12 | Hot `last_seq` row in large groups or channels | L | M | Owner | p99 send latency regression | Measure; per-channel sequence sharding later | **Open** | BE-06; ADR-0014 | 0.4 exit (baseline), then on trigger |
| R13 | Nearby iOS interop fails or HMS share is high | M | M | Owner | Spike result | LanLink as the cross-platform floor; Ditto fallback; publish the matrix | **Open** | PROX-06, PROX-10; capability matrix SP-2; RQ-63 | 1.2 entry |
| R14 | Device-CA or uplink key compromise on the single host | L | Critical | Owner | Host incident | Offline root, short intermediate, not on a PR-job host, rotation runbook | **Open** (no key exists yet) | T-PKI-01, T-UPL-07; Q-O7; RQ-47 | 1.2 / 1.3 entry |
| R15 | Offline revocation window (≤ 7 d) abused by a banned or under-15 user over proximity | L | M | Owner | Abuse report | Disclose in the Nearby-mode and transparency copy (RQ-18); 1:1 verified contacts only; block lists inbound | **Accepted** (D4, delegated 2026-11-12) | P11; T-PRX-02; D4; RQ-18, RQ-72 | 1.2 exit |
| R16 | Provisional off-server ordering visibly reorders | M | L | Owner | UX feedback | "Not yet synced" marker; 1:1 only | **Open** | ADR-0014; RQ-22, RQ-33 | 1.2 exit |
| R17 | 1.0 cutover surprises users (search loss, history deletion) | M | M | Owner | 1.0 RC | D3 mechanics: download into the encrypted local DB → notices → no deletion before T+30 d → server deletion of content, media and backups; opt-in backup under a user-held key | **Open** (1.0 gated) | ADR-0021; D3; RQ-13, RQ-91…RQ-94 | 1.0 entry and RC |
| R18 | Moderation under E2E (only reporter-supplied content) conflicts with 5651 | M | M | Counsel | Takedown request on E2E content | Franking research; counsel | **Open** | LEG-02; Q-L4; research "message franking" | 1.0 entry |
| R19 | Host disk at ~92% and a single runner | H | M | Owner | `df` > 95% | No linuxX64/heavy targets; LiveKit and signer off-host | **Open** — #459 reports 95% disk and 100% swap | CICD-04, #459, #722; TC-2 | every milestone entry |
| R20 | Agents report device criteria as passed | M | H | Owner | Any device-criterion claim without an owner note | P13/ADR-0032 "observed by" field | **Open** | GOV-06; RQ-77; vv-plan rule zero | every milestone exit |
| R21 | KVKK: relaying devices hold third-party data | M | M | Counsel/Owner | 1.3 entry | Contacts-only, sealed opaque blobs, quotas, DPIA | **Open** | LEG-04; Q-L5; T-UPL-01; RQ-67, RQ-68 | 1.3 entry |
| R22 | detekt is fixed on `dev` (db9e59f) but `main` still runs the dead config, so the gate is only real after the dev→main merge | M | L | Owner | dev→main merge | ADR-0030 merge; keep the frozen baseline ratcheting down | **Mitigating** — fixed on `dev` only | CICD-11; R28 | process scope (dev→main) |

### 3.2 Added risks (R23–R44)

| ID | Risk | L | I | Owner | Trigger | Mitigation (proposed) | Status | Links | Review point |
|---|---|---|---|---|---|---|---|---|---|
| R23 | **Co-tenant data stores.** `shared-postgres`, `shared-redis`, `shared-minio` serve other projects on the same host; compromise of a neighbouring project reaches Muhabbet metadata, keys directory, pre-1.0 content and media without crossing Muhabbet's own boundary | L–M | Critical | Owner | Any incident in a co-tenant project; any new co-tenant | Record current isolation (DB roles, buckets, Redis ACLs/DB numbers) in the 1.0 security review; least-privilege roles; any move to dedicated stores is an owner decision | **Open** | threat-model RN-3; SAD TC-9; T-HST-03 | 1.0 entry |
| R24 | **Build supply-chain integrity.** No Gradle dependency verification, no wrapper checksum, JitPack in global resolution; the Signal repository becomes the sole source of libsignal > 0.86.5 | M | H | Owner | libsignal restored without verification metadata; an unexpected artefact checksum | `verification-metadata.xml`, `distributionSha256Sum`, wrapper validation, `exclusiveContent` for the Signal repo, before 1.0 (proposed for ADR-0031) | **Open** | CICD-13, KMP-15, CICD-05; threat-model RN-5 | 1.0 entry |
| R25 | **Account takeover via SIM swap or OTP interception.** Phone-OTP is the account identity; the only control is the safety-number change at 1.0; it is undecided whether a new-handset OTP revokes existing devices or whether a registration lock exists | M | H | Owner | Takeover report; 1.0 design review | Owner question at 1.0 (registration lock, new-device notification to existing devices); `IdentityKeyChanged` interstitial for verified contacts | **Open** | T-REG-01; threat-model RN-4; ADR-0018 | 1.0 entry |
| R26 | **Test-number OTPs logged in full in production.** Anyone with production log access can sign in as `+905000000001..5`, which are the accounts used for owner verification | M | L | Owner | Log access by anyone other than the owner; log shipping to a third party | Keep test numbers out of any real conversation; restrict log access; reconsider when logs are shipped off-host | **Open** | T-REG-04; CLAUDE.md deployment notes | 0.4 exit |
| R27 | **Status drift.** "Closed as completed" means merged into `dev`; DONE claims and stale P0s mislead planning, so a milestone may be scheduled or exited on false status | H | H | Owner | An issue closed without device or release evidence; a "DONE" contradicted by code | ADR-0030 status axis; ADR-0032 observed-by; one-time tracker cleanup before filing (roadmap §17); claims register at 1.0 | **Open** | GOV-02, GOV-03, SSOT-01, SSOT-03, SSOT-11, BEV-M3 | Phase 0 exit (process) |
| R28 | **Big-bang promotion.** `dev` is ≥ 100 commits and ~12 weeks ahead of `main`; deploy is wired to `main` and has not run since 2026-08-17; the ADR-0030 merge ships all of it at once | M | H | Owner | The dev→main merge PR | Merge only with Docker-run backend suite on both refs, the N-1 schema job, and an emulator launch of a build from the merged trunk; rollback image tagged; release notes list known issues | **Open** | BE-01, CICD-08, SSOT-01, MISS-01 (product); RQ-80 | process scope |
| R29 | **Release keystore single copy.** The release keystore exists in one place with no backup; losing it means the app can never be updated on Play | L | Critical | Owner | Owner machine loss or failure | Backed-up, encrypted escrow decided with ADR-0031's `release` environment; documented in ROADMAP "Where the keys live" | **Open** | #422; CICD-02 | Phase 0 / process |
| R30 | **No production backup while the server becomes the recovery source.** There is no backup of the production DB or media (#289); from 0.5 clients rebuild their encrypted DB by resync from the server, and the server is the sole ordering authority — server data loss becomes history loss for every user | L–M | H | Owner | Disk or volume failure; failed migration | Backup and restore drill before 0.5 ships the fresh-DB migration; delete the old local file only after resync reaches the server head (ordering detail within ADR-0020); **a verified backup and a restore drill are a 0.5 entry Condition** (brief erratum E-37) | **Planned** | #289; ADR-0010, ADR-0020; RQ-51 | 0.5 entry |
| R31 | **Broken features remain visible.** Calls (FT-01), server-only features (FT-02) and the sticker picker (FT-03) are shown but do not work, contradicting the 0.4/0.5 questions and the "honest UI" premise of the delivery work | M | M | Owner | Any release that ships them visible | Hide behind a build flag until they work (FT-01/FT-03 recommendations); 0.7 deferral keeps calls hidden (roadmap §9) | **Open** | FT-01, FT-02, FT-03, #367–#373, #732 | 0.4 exit |
| R32 | **Wake-hint linkability.** The wake payload carries `conversationId` in clear to the push provider, revealing who is woken for the same conversation at the same moment | L–M | M | Owner/Counsel | Q-L6 answer; DPIA for the push processor | Accepted residual or a per-device opaque hint, decided before 0.5 ships (ADR-0019 reviewer note) | **Open** | T-PSH-02; ADR-0019 RN; Q-L6 | 0.5 entry |
| R33 | **App-presence disclosure.** A fixed mDNS service type and Nearby service identifier reveal "a Muhabbet user is here" to anyone on the LAN or in BLE range | M | L | Owner | 1.2 spike measurement; DPIA | Measure in the spike; disclose in the 1.2 DPIA; reword P9/RQ-17 to "no stable per-user or per-device identifier" or specify a mitigation | **Open** | T-PRX-06; threat-model RN-2; vv-plan RN-V3 | 1.2 entry |
| R34 | **Client-isolating Wi-Fi defeats `LanLink`.** Many public and guest access points drop multicast and peer traffic | H | L | Owner | Spike row on an isolated AP | Matrix row marked expected failure; UI does not imply LAN works on every Wi-Fi; Nearby remains the in-range path on Android | **Open** | ADR-0024 reviewer note; LAN-1; CH-18 | 1.2 entry |
| R35 | **Approval gate ambiguity.** The design gate cannot tell which scope an edit belongs to; `docs/**` is always writable, so an agent could edit `APPROVAL.md`; spikes need code before their scope can be approved | M | M | Owner | First agent edit refused or allowed wrongly; a spike started without a recorded condition | D6: G3 (`APPROVAL.md` never agent-editable) and G5 (declared active scope) accepted; G2 narrowed (worktrees); D1a one open milestone; CODEOWNERS ([OI-45](open-items.md#oi-45)); spike approval as an owner scope condition ([OI-18](open-items.md#oi-18)) | **Planned** | ADR-0033; APPROVAL.md reviewer notes; capability matrix SP-1 | process scope |
| R36 | **Owner observation is the bottleneck.** P13 makes the owner the only signer for every device criterion; each milestone exit needs several emulator and physical-device sessions | H | M | Owner | A milestone whose only open items are [O] criteria for more than one release | Batch device sessions per milestone (ROADMAP "batch is the unit of verification"); agents prepare scripts and logs; possible CI emulator pre-check (vv-plan RN-V1) to filter failures before the owner's session | **Open** | P13, ADR-0032; R4, R20 | every milestone exit |
| R37 | **PR CI disabled.** ADR-0031 removes PR jobs from cx43; if the GitHub-hosted billing lock persists, PR workflows are disabled and changes merge without any automated gate | M | H | Owner | ADR-0031 applied while CICD-04 is unresolved | Resolve the billing lock first (free for public repos) or provision a separate non-production runner; until then run the gates on E-LOCAL and attach reports to every PR | **Open** | CICD-04, #108, CICD-16; vv-plan §5 | Phase 0 exit |
| R38 | **Age rule may already apply while the gate is unbuilt.** The brief records the under-15 rule as in force on 2026-11-01; the age gate is a 0.4 build item, so registration currently has no gate | H | H | Owner/Counsel | Counsel confirms the in-force date → re-rate; a regulator request | Age gate built in 0.4 under RN-R11 option (a) (implementation may start now; merge, deploy and release after P0-1…P0-4, P0-7 and P0-8), minimum age 18 by configuration; confirm the date with counsel (SAD reviewer note); any interim measure (e.g. pausing open registration) is an owner decision, not proposed here | **Open** — rating conditional on the unverified in-force date (sad.md reviewer note; Q-L3) | LEG-02, MISS-04 (legal); SAD reviewer note on the date; dpia-screening note on age data; RQ-10 | 0.4 entry |
| R39 | **Erasure gap behind public claims.** Account deletion leaves media and content (#327); public documents claim KVKK compliance; disappearing messages do not disappear (#328) | M | H | Owner | Erasure request; complaint; 1.0 claims register | Keep #327 open and correct the claims (SSOT-09); claims register at 1.0 (RQ-20); media erasure is a backend item outside this design | **Open** | MISS-02 (governance), SSOT-09, MISS-03/MISS-04 (product), #314, #327, #328 | 0.4 exit; 1.0 exit |
| R40 | **Bought-component dependence.** Nearby Connections is closed, Play-services-only, with an unofficial iOS package and unknown data flows to Google; libsignal's distribution moved to Signal's own repository and its API changed incompatibly across versions | M | M | Owner | Spike finds data sent to Google; a libsignal release breaks the adapter; the unofficial iOS package is abandoned | 1.2 spike answers Nearby data flows from documentation and a traffic capture (DPIA processor question); `LanLink` as floor; libsignal behind `SessionCryptoPort` with a contract suite; pinned version with verification metadata (R24) | **Open** | PROX-06, KMP-13, KMP-M3, C1; dpia-screening reviewer note; ADR-0024 | 1.0 entry; 1.2 entry |
| R41 | **Verification hardware unavailable.** The plan's exits assume two Android phones on two operators (one TT Mobil), a Xiaomi/Oppo-class device, Pixel, Samsung A, iPhone 12, iPhone 15+, a recommended Huawei, and a macOS host for 06-3 "iOS launches on a simulator" and RQ-42; no iOS CI exists (KMP-02). Nothing records that the owner has these | M | H | Owner | 0.6 G-iOS "fixed" branch chosen; 1.2 spike planned; any [O] row whose device is missing | Device inventory recorded as an `APPROVAL.md` Condition of `0.6`, `0.7` and `1.2` (paste-ready in `open-items.md` §2); Android-only per D2 if there is no macOS host | **Planned** | KMP-02, FT-04; capability matrix SP-2, SP-3; vv-plan E-PHYS; RQ-14, RQ-42, RQ-49 | 0.6 entry; 0.7 entry; 1.2 entry |
| R42 | **LiveKit host and TURN egress cost.** The SFU and TURN must live off cx43 (0.7) and TURN capacity grows with 1.1 P2P relaying; neither host nor budget is decided | M | M | Owner | Q-O8 answer; first week of RQ-61 telemetry | Owner decides host and budget (Q-O8) before 0.7 entry; size 1.1 TURN from 0.7 data; 1.1 kill criterion if cost exceeds benefit | **Open** | Q-O8; NAT-3; RQ-61; roadmap §9, §11 | 0.7 entry/exit; 1.1 entry |
| R43 | **Meshtastic module present in a TR build.** A Gradle or flavour mistake ships `:mobile:transport-meshtastic` in a TR release before counsel signs off | L–M | H | Owner | A TR release classpath containing the module; flavour change | Gradle exclusion with a classpath check in CI; `RegulatoryRule` denies `EXTERNAL_RF` in TR | **Open** | LEG-03, RF-07; RQ-37; ADR-0026 | 1.4 entry; every 1.4+ release |
| R44 | **D3 deletion causes history loss** for users inactive or offline for the whole grace period, or reinstalling after deletion without the opt-in backup | M | H | Owner | 1.0 RC | Notices at the start and before the end of the grace period; no deletion before each active device confirms the download up to the server head or the grace period ends; opt-in backup prompt; disclosure in the cutover copy | **Open** (1.0 gated) | ADR-0021 §4; D3; RQ-91, RQ-92 | 1.0 RC |

---

## 4. Risks by gate

| Gate | Risks that must be re-rated, with evidence, before the gate passes |
|---|---|
| Phase 0 exit | R1, R2, R27, R29, R37 |
| process scope (ADR-0030…0033 applied) | R22, R28, R35 |
| 0.4 entry / exit | R7, R12, R26, R31, R38, R39 |
| 0.5 entry / exit | R3, R11, R30, R32 |
| 0.6 entry / exit | R9, R41 |
| 0.7 entry / exit | R2 (LiveKit host off cx43), R19, R41, R42 |
| 1.0 entry / exit | R5, R8, R10, R17, R18, R23, R24, R25, R39, R40 |
| 1.1 entry | R42 |
| 1.2 entry / exit | R2, R6, R13, R14, R15, R16, R33, R34, R40, R41 |
| 1.3 entry | R2, R14, R21 |
| 1.4 entry | R6, R43 |
| every milestone | R4, R19, R20, R36 |

## 5. Maintenance rules

1. A rating changes only with a dated note citing evidence (finding ID, issue, test report, opinion).
2. A risk is **Closed** only when its mitigation is verified by the RQ named in *Links*; the closing
   note links the evidence.
3. **Accepted** requires the owner's name and date and, for IL 4 consequences (vv-plan §2), a
   disclosed statement in the transparency or release text.
4. New risks take the next free number; numbers are never reused. Rows R1–R22 keep the brief's text;
   changes to them are brief amendments.
5. The register is reviewed at every milestone entry and exit (§4) and whenever a trigger fires.

## 6. Evidence

| Risk(s) | Evidence |
|---|---|
| R1, R2, R19, R22, R24, R28, R29, R37 | Findings CICD-01…CICD-13, BE-01, CICD-08; issues #108, #422, #459, #722; ROADMAP "Where the keys live" |
| R5, R6, R7, R18, R38 | LEG-01…LEG-03, MISS-01/MISS-02/MISS-04 (legal); Law 5651 consolidated text <https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5651.pdf>; Law 5809 consolidated text as cited in the legal fact-check digest |
| R8, R10, R24, R40 | KMP-13, KMP-M3, KMP-15, C1; PQXDH specification <https://signal.org/docs/specifications/pqxdh/> |
| R11, R32 | BE-15; ADR-0019 reviewer note; threat model T-PSH-02/04 |
| R13, R33, R34, R40 | PROX-02, PROX-06, PROX-10, LAN-1; Nearby Connections overview <https://developers.google.com/nearby/connections/overview> |
| R14, R23, R25, R26 | Threat model T-PKI-01, T-HST-03, T-REG-01, T-REG-04; reviewer notes RN-3, RN-4 |
| R21, R39 | LEG-04, MISS-02 (governance), SSOT-09; KVKK text <https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf> |
| R27, R35, R36 | GOV-02, GOV-03, GOV-06, SSOT-01, SSOT-03; APPROVAL.md reviewer notes; capability matrix SP-1 |
| R30, R31 | #289; FT-01, FT-02, FT-03 |
| R41, R42, R43 | KMP-02, FT-04, capability matrix SP-2/SP-3; Q-O8, NAT-3; LEG-03, RF-07 |

## 7. Verification of this register

| Check | Method |
|---|---|
| R1–R22 match brief §15 in risk, L, I, mitigation, owner and trigger | Inspection, row by row |
| Every added risk cites a finding, issue, threat or reviewer note | Inspection of *Links* |
| Every risk has a review point mapped to a roadmap gate | Inspection of §4 against `roadmap.md` |

---

## 8. Reviewer notes

> **Note:** **RN-K1 — status columns.** The brief's risk table lists trigger and owner but not
> status or review point; this register adds both. Since the delegated approval (2026-11-12), risks
> whose mitigation sits in an approved scope (Phase 0, `process`, 0.4–0.6, 0.7 except ADR 0009
> hosting) are **Planned**; R15 is **Accepted** (D4); R22 is **Mitigating** because the fix is on
> `dev`.

> **Resolved (2026-11-12):** **RN-K2 — risks the brief did not carry.** R23, R24, R25, R29, R30, R37
> and R38 (and the new R44) are adopted into brief §15 (erratum E-37), with gates: R30 — 0.5 entry
> requires a verified production Postgres backup plus a restore test; R24 — 1.0 entry requires Gradle
> dependency verification, wrapper SHA and `exclusiveContent` for the Signal repository (ADR-0031
> amendment); R37 — handled by the ADR-0030 transition order; R38 — the 0.4 age gate; R25 — owner
> question at 1.0 ([OI-22](open-items.md#oi-22)).
