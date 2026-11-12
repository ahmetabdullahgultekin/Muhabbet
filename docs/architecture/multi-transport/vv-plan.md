# Muhabbet multi-transport V&V plan

> **Status: Proposed — pending owner approval.** This plan is not in force until the owner records
> approval in [`APPROVAL.md`](APPROVAL.md). No code may be written — including test code, harnesses
> and CI jobs described here — for any scope until approval for that scope is recorded there (owner
> decisions 3 and 4).

| | |
|---|---|
| **Date** | 2026-11-12 |
| **Base** | `dev` @ 2434324, branch `claude/muhabbet-p2p-architecture-wdan5r` |
| **Source of truth** | [decision brief](decision-brief-2026-11-12.md) §2 (principles P1–P14), §13 (exit criteria), §16 (V&V strategy, RQ-01…RQ-20). Where this plan and the brief disagree, the brief wins; disagreements are `> **Reviewer note (open):**` callouts (§21). |
| **Standard** | IEEE 1012 (system, software and hardware V&V) **in spirit**, tailored for one owner, agent implementers and one CI host: integrity levels, V&V activities per milestone, traceability, independence, anomaly reporting. Not a conformance claim. |
| **Companions** | [`roadmap.md`](roadmap.md) (milestone exits), [`delivery-engine-spec.md`](delivery-engine-spec.md) §3 invariants I1–I17 and §14 harness, [`icd-wire-protocol.md`](icd-wire-protocol.md) §11 golden vectors G-1…G-7 and §12 fuzzing F-1…F-7, [`threat-model.md`](threat-model.md) (T-*), [`sad.md`](sad.md) quality scenarios QS-01…QS-20, [`risk-register.md`](risk-register.md) |

---

## 1. Purpose and scope

1. **Verification** asks "was it built right?" against the ADRs, the ICD and the engine spec.
   **Validation** asks "is it the right thing?" against the product promise of brief §1 — including
   the things the product must *not* claim (brief §1.2).
2. In scope: the delivery engine, wire protocol, backend seq/change feed/acks/keys/mailbox,
   E2E gate, data at rest, push, calls, proximity, custody, Meshtastic, compliance boundary, and the
   process ADRs that make verification trustworthy (ADR-0030…0033).
3. Out of scope: UI visual design, call media internals beyond ADR 0009, web/desktop.
4. **Rule zero (P13, ADR-0032):** an agent may execute any verification step and attach output, but
   may never record a device-dependent criterion as passed. "Not observed" is reported as such.

---

## 2. Integrity levels

Assigned per requirement (§6). The level sets the minimum method, independence and the waiver
policy (§17).

| Level | Consequence if the requirement fails | Minimum verification | Waiver |
|---|---|---|---|
| **4** | Content or keys exposed; plaintext where E2E is claimed; legal exposure (age, RF, 5651/5809, KVKK); production compromise | Test **and** a second method (inspection, analysis or owner demonstration); independent review where named (crypto reviewer, counsel) | Never; the milestone does not exit |
| **3** | Message lost, duplicated, mis-ordered, or a tick/label that lies | Automated test in CI plus owner demonstration where device-dependent | Owner only, dated, with a follow-up issue in the next milestone |
| **2** | Degraded performance, UX friction, operational burden | Test or analysis | Owner |
| **1** | Hygiene, maintainability | Inspection or automated guardrail | Reviewer |

---

## 3. Verification levels

| Level | Name | What is verified | Tooling | Environment (§5) |
|---|---|---|---|---|
| V1 | Unit | Pure functions: transitions, rules, codecs, ledger, tick function | JUnit 5 + MockK (backend), `kotlin.test` (KMP) | E-LOCAL, E-CI |
| V2 | Component | Engine with fakes: `SimulatedLink`, `SimulatedSealedLink`, `FakeServer`, in-memory stores, `TestClock` | `:mobile:messaging:jvmTest` | E-LOCAL, E-CI |
| V3 | Integration | Backend with real Postgres + Redis: seq, idempotency, acks, prekey claims, change feed, mailbox; contract tests against shared DTOs; golden vectors across backend and `:shared` | Testcontainers (**Docker running**), ArchUnit/Konsist | E-LOCAL (Docker), E-CI |
| V4 | System | Release APK against the production backend with test numbers; two-number flows | `adb`, emulator, test numbers `+905000000001..5` | E-EMU |
| V5 | Acceptance | Owner observation of device-dependent exit criteria; physical phones, operators, OEMs, OTA captures | Physical devices, capture tools | E-PHYS |
| V6 | Field | Radio duty cycle over one hour; Nearby in real environments | Certified Meshtastic nodes; spike harness apps | E-FIELD |
| VA | Analysis / audit | Telemetry analysis, legal opinions, crypto review, DPIA, licence audit | Documents | n/a |

---

## 4. Methods

| Code | Method | Accepted evidence | Rule |
|---|---|---|---|
| **T** | Test | JUnit XML / CI report naming the test, seed and commit | A test that did not execute is not a pass (ROADMAP DoD 2). Aggregate counts from `build/test-results` XML; compare base vs branch. |
| **A** | Analysis | A dated note with data source, query and result | Thresholds are fixed **before** data is seen (capability matrix SP-4). |
| **I** | Inspection | Reviewer note naming the artefact and revision; for compile-time guarantees, the build output | Inspection of a "should not compile" property requires a negative test fixture that is shown to fail compilation. |
| **D** | Demonstration | Owner note: observed by / where / date / build, plus screenshot, logcat excerpt or capture file | Only the owner signs a device demonstration (P13). |

---

## 5. Environments

| ID | Environment | Runs | Constraints | Signs |
|---|---|---|---|---|
| E-LOCAL | Agent or owner workstation (cloud session or Windows dev host); JDK 21; Docker for Testcontainers | V1–V3 | Without Docker the `@Testcontainers` classes fail at class-init and **do not run** (#598); Redis is required as well as Postgres | nobody (pre-check only) |
| E-CI | Today: one self-hosted runner on the production host `muhabbet-cx43`, one job at a time, ~92% disk, **no KVM**. After ADR-0031: PR jobs never on cx43; GitHub-hosted runners once the billing lock is fixed (CICD-04), otherwise PR workflows disabled | V1–V3, golden vectors, short fuzz runs, 1 000-seed property subset per PR | Heavy suites (10 000 seeds, long fuzzing) only on merge/nightly and only once CI is off the production host (R19, R37) | CI report |
| E-EMU | `emulator-5554` on the owner's Windows PC | V4: release-artefact launch, two-number flows, airplane-mode script, killed-app notification | Points at **production** (hard-coded `BASE_URL`, MOB-15); backend changes must be deployed, or the temporary `BASE_URL` build change used and reverted (CLAUDE.md) | Owner |
| E-PHYS | Owner's physical phones: two Android on two operators (one TT Mobil); one aggressive-OEM device (Xiaomi/Oppo class); spike set (Pixel, Samsung A, Xiaomi, iPhone 12, iPhone 15+; one Huawei/HMS recommended, capability matrix SP-2) | V5 | Availability of the owner is the bottleneck (R36) | Owner |
| E-PROD | Production backend with test numbers | V4 read-only checks | No fault injection on production: it is a shared host with other projects (TC-9, R23) | Owner |
| E-FIELD | Two certified Meshtastic nodes, outdoor | V6 | 1.4 only, after the written opinion | Owner |

---

## 6. Requirements traceability matrix

Columns: **IL** integrity level · **Lvl** verification level · **M** method · **Env** environment ·
**MS** milestone where it must first pass (later milestones re-run it) · **Links** ADR, findings,
threats (T-*), quality scenarios (QS-*), engine invariants (I*).

### 6.1 Brief requirements (RQ-01…RQ-20, verbatim from brief §16.1)

| Req | Requirement | IL | Lvl | M | Env | MS | Evidence | Links |
|---|---|---|---|---|---|---|---|---|
| RQ-01 | No message lost after the user taps send | 3 | V2, V4 | T, D | CI, EMU | 0.4 (demo), 0.5 (suite) | Property report (10k seeds); owner note "kill mid-send" | ADR-0013; MOB-04; I1, I2, I12; QS-01, QS-03 |
| RQ-02 | Each message shown exactly once | 3 | V2, V3, V4 | T, D | CI, EMU | 0.4 / 0.5 | Property report; replay test (`replay = true`) | ADR-0013; BE-07; I8; T-SRV-06 |
| RQ-03 | Double tick only after the recipient's durable store | 3 | V2, V4 | T, D | CI, EMU | 0.4 | Crash-point test `AFTER_INBOX_COMMIT_BEFORE_ACK`; owner note | P4; MOB-03; I6; QS-06 |
| RQ-04 | Total order per conversation | 3 | V3 | T | CI | 0.4 | Testcontainers: 50 concurrent senders, gapless unique seq | ADR-0014; BE-06, C13; I9; QS-07; T-SRV-07 |
| RQ-05 | Lost hints are self-healing | 3 | V2 | T | CI | 0.5 | Hint-loss test: drop 100% of WS hints and FCM wakes | P6; ADR-0014/0019/0021; QS-02; T-SRV-14 |
| RQ-06 | No plaintext on non-server paths | 4 | V1, V2, V3 | I, T | CI | 0.5 (gate), 1.0, 1.2, 1.4 | Negative compile fixture; arch test; release-classpath check; `SealedLink` contract suite | P2; ADR-0016; C9; I5; QS-09; T-PRX-12, T-DEV-05 |
| RQ-07 | Push contains no content | 4 | V1, V5 | T, I | CI, PHYS | 0.5 | Adapter test on the built message; on-device payload capture | ADR-0019; BE-15; QS-10; T-PSH-01 |
| RQ-08 | Logout or deletion leaves no readable data | 4 | V2, V4 | T, D | CI, EMU | 0.4 (logical), 0.5 (forensic) | Teardown test at every crash point (I17); owner forensic note | ADR-0020; MOB-01, MISS-02 (mobile); QS-15; T-DEV-01/03 |
| RQ-09 | Sessions die on revoke within 5 s | 4 | V3, V4 | T, D | CI, EMU | 0.4 | Integration test; owner note; access-log inspection shows no token in URLs | ADR-0022, ADR-0029; BE-12, BE-16; T-SRV-01/02 |
| RQ-10 | Under-15 cannot register | 4 | V3, V4, VA | T, A | CI, EMU | 0.4 | Backend test; owner check; counsel opinion Q-L3 before any compliance claim | ADR-0029; LEG-02, MISS-04 (legal); T-REG-02/03 |
| RQ-11 | Prekeys claimed at most once; no silent identity swap | 4 | V3 | T | CI | 0.4 (claim), 0.6 (swap) | Concurrency test; identity-replacement rejection test | ADR-0018; BE-14, C10, M2; QS-12; T-KEY-01/03 |
| RQ-12 | Contact enumeration throttled | 4 | V3 | T | CI | 0.4 (cap), 0.6 (reader) | Integration test | §8.6; C8, M3; T-DIR-01/02 |
| RQ-13 | Server holds no E2E plaintext | 4 | V3, VA | T, I | CI | 1.0 (and cutover + 30 d) | SQL assertion; DB dump inspection | ADR-0019, ADR-0021; BE-15; T-SRV-09 |
| RQ-14 | Calls connect with two-way audio | 3 | V5 | D | PHYS | 0.7, 1.1 | Owner call log: two devices, two operators incl. TT Mobil | ADR-0023; FT-01, #367–#373 |
| RQ-15 | Non-contacts never see IP | 4 | V1, V5 | T, I | CI, PHYS | 0.7, 1.1 | Policy unit test; ICE stats capture | ADR-0023; MISS-2 (networking); T-CAL-01 |
| RQ-16 | Proximity works without internet between verified contacts | 3 | V5 | D | PHYS | 1.2 | Owner note + published device matrix | ADR-0024; PROX-06 |
| RQ-17 | No stable identifiers on air | 4 | V1, V5 | I, T | CI, PHYS | 1.2 | Golden vectors (tags rotate per 15-min epoch, random names); OTA capture | P9; §8.5; C7; QS-14; T-PRX-05/06 (RN-2) |
| RQ-18 | Revoked devices refused off-server within ≤ 7 d | 4 | V2, V5 | T, D | CI, PHYS | 1.2 | `TestClock` expiry/CRL test; owner note | ADR-0018, ADR-0029; P11; QS-11; T-PRX-02, T-PKI-02 |
| RQ-19 | Duty cycle respected | 4 | V1, V6 | T, D | CI, FIELD | 1.4 | Ledger tests with time-on-air golden vectors; one-hour field log | ADR-0026; RF-07, GEM-20; T-RF-02 |
| RQ-20 | Every privacy claim true | 4 | all | I + all | all | 1.0 (register), every release after | Claims register: each UI/doc claim → test or observation | SSOT-09; ADR-0017, ADR-0021; DPIA S-16 |

### 6.2 Extended requirements (RQ-21…RQ-81, derived from ADR verification sections and principles)

Every row cites the ADR verification item it comes from. Requirement text is the testable form of
that item; nothing here adds scope beyond the brief and the ADRs.

| Req | Requirement | IL | Lvl | M | Env | MS | Source | Links |
|---|---|---|---|---|---|---|---|---|
| RQ-21 | No transport adapter depends on an identity, ordering or membership port | 3 | V1 | T (arch rule) | CI | 0.5 | ADR-0010, ADR-0012; P1 | BE-08 |
| RQ-22 | An off-server-delivered CLOUD/BYO message shows "not yet synced" and keeps `serverOwed = true` until `ServerAccepted(seq)`, then re-queues on server link-up | 3 | V2 | T | CI | 1.2 | ADR-0010, ADR-0015 | I12; QS-05; T-PRX-11 |
| RQ-23 | Off-server delivery only between parties with a server-established session and a shared conversation | 4 | V1 | T | CI | 1.2 | ADR-0010 (`RelationshipRule`) | T-PRX-10 |
| RQ-24 | UI never speaks wire types: `WsMessage`/`wsClient` uses in `ui/` and `App.kt` = 0 | 2 | V1 | T (`verifyUi`) | CI | 0.5 | ADR-0011; P5 | MOB-08, MOB-11 |
| RQ-25 | `:mobile:messaging` has no Compose, Koin-annotation or `WsMessage` dependency | 2 | V1 | T, I | CI | 0.5 | ADR-0011/0012/0028 | QS-18 |
| RQ-26 | A message received while the app is killed is in the DB before the notification is tapped | 3 | V4 | D | EMU | 0.6 | ADR-0011 | MOB-03 |
| RQ-27 | SQLDelight migrations verified in CI (`verifyMigrations`) | 3 | V1 | T | CI | 0.4 | ADR-0011, ADR-0028 | GEM-15 |
| RQ-28 | A slow, stalled or backpressured link, lane or conversation never delays another | 2 | V2 | T | CI | 0.5 | ADR-0012; brief §6.6 | I4, I14, I15; QS-16; GEM-02, MISS-01 (Gemini) |
| RQ-29 | Engine path reaches parity with the legacy path before the default flips | 3 | V4 | D | EMU | 0.5.1 | ADR-0012 | R3 |
| RQ-30 | `:shared:jvmTest` and `:mobile:messaging:jvmTest` execute in CI | 2 | V1 | I, T | CI | 0.4 / 0.5 | ADR-0012, ADR-0028 | KMP-03, #324 |
| RQ-31 | Acks accepted only from a non-sender member device, only for existing seqs, monotonic; status broadcast only on a real change; aggregate from per-device rows | 4 | V3 | T | CI | 0.4 (auth), 0.6 (per device) | ADR-0013 | BE-03, BE-18, MISS-01 (mobile); T-SRV-05 |
| RQ-32 | An unreleased scheduled message never appears to recipients (history, sync, `/changes`) | 4 | V3 | T | CI | 0.4 | ADR-0014 | BE-02, BEV-M1; T-SRV-08 |
| RQ-33 | Advisory sender time is clamped to `[receivedAt − 5 min, receivedAt]` for display | 2 | V1 | T | CI | 0.5 / 1.2 | ADR-0014 | C4, GEM-10 |
| RQ-34 | Domain time and ids come only from injected `Clock`/`IdGenerator`; the sync watermark is server-derived, never the device clock | 2 | V1 | T, I | CI | 0.5 | ADR-0014; P10 | MOB-10; I10; QS-19 |
| RQ-35 | Each admission rule (incl. ADR-0024 E1–E7) denies its own case and admits otherwise | 4 | V1 | T | CI | 1.2 | ADR-0015, ADR-0024 | T-PRX-10 |
| RQ-36 | Channels, communities and groups above the cap never get an off-server candidate | 4 | V1 | T | CI | 1.2 | ADR-0015, ADR-0029 (`ScopeRule`) | LEG-02 |
| RQ-37 | `:mobile:transport-meshtastic` absent from every TR release runtime classpath; `RegulatoryRule` denies `EXTERNAL_RF` in TR | 4 | V1 | T, I | CI | from module creation / 1.4 | ADR-0015, ADR-0026, ADR-0028 | LEG-03, RF-08; T-RF-01 |
| RQ-38 | `goodputBps` counts acked application bytes only | 1 | V2 | T | CI | 1.2 | ADR-0015 | GEM-05 |
| RQ-39 | A seal failure leaves the entry in `PENDING_SEAL`/`FAILED`, never plaintext; no fallback code path remains | 4 | V2, V3 | T, I | CI | 0.5 (fake), 1.0 (libsignal) | ADR-0016 | C9; QS-08 |
| RQ-40 | Gate artefacts are filed before gated work starts or ships: licence (a)–(c), counsel opinions, DPIAs per trigger, spike reports | 4 | VA | I | — | per milestone | ADR-0017, 0024, 0025, 0026, 0029; P8 | LEG-01…04 |
| RQ-41 | Two-device E2E round trip on real builds; crypto contract suite passes on Android, iOS (if in scope) and fake adapters | 4 | V2, V5 | T, D | CI, PHYS | 1.0 entry / exit | ADR-0017 | C1, KMP-13 |
| RQ-42 | The iOS decision is honoured: iOS launches on a simulator and the bridge round-trips a golden vector, or the Android-only decision is recorded | 3 | V4, VA | D, I | owner Mac or CI | 0.6 / 1.0 | ADR-0017, ADR-0028 | KMP-01, KMP-02, FT-04 |
| RQ-43 | No libsignal type importable from commonMain, `:mobile:messaging` or `:shared`; no `javax.crypto`/`CryptoKit` primitive outside `:mobile:crypto` adapters and the existing media AEAD | 4 | V1 | T, I | CI | 0.5 (rule), 1.0 | ADR-0017; P12 | C1 |
| RQ-44 | Every release artefact has a resolvable source tag and an in-app source link (AGPL) | 4 | VA | I | — | 1.0 onward | ADR-0017 | KMP-13 |
| RQ-45 | Prekey directory abuse controls: per-requester and per-pair limits, relationship check (own devices allowed), last-resort Kyber, never a bundle without a PQ key | 4 | V3 | T | CI | 0.4 (limits), 0.6 | ADR-0018 | M1, M2; T-KEY-02 |
| RQ-46 | Device-certificate issuance refused without a valid age attestation or with an active ban | 4 | V1, V3 | T | CI | 1.2 | ADR-0018; P11 | T-PKI-03 |
| RQ-47 | Device-CA intermediate and uplink private keys are absent from every host that runs PR jobs | 4 | VA | I | — | 1.2 / 1.3 entry | ADR-0018, ADR-0025, ADR-0031 | R14; T-PKI-01, T-UPL-07 |
| RQ-48 | Golden vectors decode identically on JVM, backend and (when present) iOS; signed/hashed inputs are length-prefixed and domain-separated | 3 | V1, V3 | T | CI | 0.5 (A), 1.0 (B), 1.2 (C) | ADR-0018, ADR-0022; ICD G-1…G-7 | GEM-13, GEM-16; #351 |
| RQ-49 | A notification arrives with the app killed, composed from the local DB; p90 wake-to-notification within the owner-agreed bound on an aggressive OEM | 3 | V4, V5 | D | EMU, PHYS | 0.5 | ADR-0019 | QS-17; R11; T-PSH-04 |
| RQ-50 | #377 enforcement preserved: READ downgraded to DELIVERED for a reader with `readReceiptsEnabled = false` over WS and REST, including E2E conversations | 4 | V3 | T | CI | 0.5, 1.0 | ADR-0019 | #377 |
| RQ-51 | The 0.5 encrypted-DB migration keeps every unsent message; the DB key is available on a killed-app wake | 3 | V2, V4 | T, D | CI, EMU | 0.5 | ADR-0020 | MOB-01, MOB-04 |
| RQ-52 | No plaintext message data at rest on the device: SQLCipher DB, FTS only inside it, separate encrypted custody store | 4 | V2, V4 | T, I, D | CI, EMU | 0.5, 1.0 (FTS), 1.3 (custody) | ADR-0020 | GEM-14; T-DEV-02, T-OWN-01 |
| RQ-53 | `/changes` for device A never returns device B's envelope; ack `uptoSeq` deletes exactly that device's envelopes ≤ seq; TTL purge removes expired rows | 4 | V3 | T | CI | 1.0 | ADR-0021 | BE-18 |
| RQ-54 | Undecided product modes are not offered: LOCAL_ONLY absent while Q-O3 is open | 3 | VA | I | — | 1.0 | ADR-0021 | #423 |
| RQ-55 | Protocol negotiation: no frame before `hello`; unknown client frame → `UNSUPPORTED_FRAME`; unknown server frame ignored by the client; below `minSupported` → `PROTOCOL_TOO_OLD` | 3 | V1, V3 | T | CI | 0.5 | ADR-0022 | KMP-11; T-SRV-10 |
| RQ-56 | Every backend controller response decodes with the shared DTO | 2 | V3 | T | CI | 0.5 | ADR-0022 | KMP-07, #330 |
| RQ-57 | A tampered `TransportFrame` header (fields 1–6) fails AEAD; a replayed `frameId` is dropped | 4 | V1 | T | CI | 1.2 | ADR-0022 | GEM-08; T-PRX-03/04 |
| RQ-58 | Every parser survives fuzzing without crash, hang or unbounded allocation | 3 | V1 | T | CI | 0.5 onward | ADR-0022; ICD F-1…F-7 | T-PRX-08, T-MED-04 |
| RQ-59 | TURN credentials expire ≤ 10 min after minting and are per device and session | 4 | V3 | T | CI | 0.7 | ADR-0023 | T-CAL-03 |
| RQ-60 | Stored ICE telemetry contains no IP address | 4 | V3 | T, I | CI | 0.7 | ADR-0023 | NAT-3; T-CAL-04 |
| RQ-61 | TURN capacity and P2P benefit are decided from measured data: one week of relayed share and egress (0.7); per-ASN setup success and latency A/B (1.1) | 2 | VA | A | — | 0.7, 1.1 | ADR-0023 | P2P-2; T-CAL-05 |
| RQ-62 | Proximity discovery runs only in foreground Nearby mode, which turns off after 15 min | 3 | V1, V5 | T, D | CI, PHYS | 1.2 | ADR-0024 | PROX-02; T-PRX-09 |
| RQ-63 | The device matrix is published before any proximity claim; no unmeasured cell is claimed in UI | 3 | VA | I | — | 1.2 entry | ADR-0024 | PROX-08 |
| RQ-64 | No bundle lost between sender outbox and server ingest across crash points; custody released only on authenticated delivery, server ack or expiry | 3 | V2 | T | CI | 1.3 | ADR-0025 | I16 |
| RQ-65 | Own-device custody delivers: phone offline, tablet online, message reaches the phone over LAN and is E2E-acked | 3 | V5 | D | PHYS | 1.3 | ADR-0025 | — |
| RQ-66 | A phone with no connectivity delivers through a contact's online phone; the server attributes the message to the true sender device | 4 | V3, V5 | T, D | CI, PHYS | 1.3 | ADR-0025 | QS-13; T-UPL-02 |
| RQ-67 | The mule store holds only opaque blobs; no stable recipient identifier | 4 | V2 | T | CI | 1.3 | ADR-0025 | C7; T-UPL-01 |
| RQ-68 | Relay quotas bound abuse on the mule and the server | 3 | V2, V3 | T | CI | 1.3 | ADR-0025 | T-UPL-05 |
| RQ-69 | Text and receipts are delivered between two certified Meshtastic nodes | 2 | V6 | D | FIELD | 1.4 | ADR-0026 | RF-07 |
| RQ-70 | No rejected transport type, module or flag exists in the code | 1 | V1 | T (grep guardrail), I | CI | 0.5 onward | ADR-0027 | GEM-19, GEM-21 |
| RQ-71 | Version catalog and convention plugin in use; no hard-coded versions in module build files | 1 | V1 | I, T | CI | 0.4 | ADR-0028 | KMP-05 |
| RQ-72 | Block lists are applied before any row is written, on every `InboundSource` | 4 | V2 | T | CI | 0.5 (server sources), 1.2+ | ADR-0029 | I13; M3 |
| RQ-73 | A report created offline reaches the server after reconnect (durable CONTROL outbox entry) | 3 | V2 | T | CI | 0.5 | ADR-0029 | LEG-02 |
| RQ-74 | Trunk and ruleset enforced: `dev` gone, squash-only, required checks, bypass cannot skip checks, merged ≠ verified status axis | 2 | VA | I | — | process | ADR-0030 V1–V4, V8 | GOV-01, GOV-02, GOV-07 |
| RQ-75 | Released bytes are verified bytes: built once, the exact signed APK/AAB launched on `emulator-5554`, the same artefact promoted; backend deployed by artefact | 4 | V4 | I, D | EMU, CI | process; every release | ADR-0030 V5–V7 | CICD-07, CICD-10 |
| RQ-76 | CI trust boundary in force (ADR-0031 F1–F8) | 4 | VA | I | — | Phase 0 / process; re-checked at 1.2, 1.3 entry | ADR-0031 | CICD-01…06; T-HST-01/02 |
| RQ-77 | Agents act under their own identity; the owner approves agent PRs; only the owner ticks device criteria (ADR-0032 A1–A6) | 3 | VA | I | — | process | ADR-0032; P13 | GOV-06; R20 |
| RQ-78 | Design-gate and bootstrap hooks behave as specified (ADR-0033 H1–H9) | 2 | V1 | T | E-LOCAL | process | ADR-0033 | — |
| RQ-79 | ADR status hygiene: product ADRs for 1.1+ stay *Proposed (gated)* until spikes and opinions exist; process ADRs are grouped separately | 1 | VA | I (status lint) | — | every ADR change | P14 | SSOT-08 |
| RQ-80 | Migrations are rollback-compatible: additive first, destructive one release later; the previous release starts against the new schema | 3 | V3 | T | CI | 0.4 onward | brief §11.1 | CICD-09, #295 |
| RQ-81 | Every port names its second real implementation, already scheduled; a port with only a speculative second implementation is rejected | 1 | VA | I | — | every port PR | P7 | — |

### 6.3 ADR verification-item index

Each ADR's own Verification table is reproduced here only as a pointer to its RQ, so no item is
untraced.

| ADR | Items → RQ |
|---|---|
| 0010 | boundary rule → RQ-21 · `serverOwed`/"not yet synced" → RQ-22 · no off-server without session + conversation → RQ-23 · boundary review → RQ-81 |
| 0011 | `ui/` count 0 → RQ-24 · module purity → RQ-25 · DB before DELIVERED → RQ-03 · killed-app receive → RQ-26 · `verifyMigrations` → RQ-27 |
| 0012 | `SealedFrame` only → RQ-06 · purity → RQ-25 · no HOL across links → RQ-28 · adapter boundary → RQ-21 · parity → RQ-29 · jvmTest in CI → RQ-30 |
| 0013 | RQ-01, RQ-02, RQ-03 · kill mid-send → RQ-01/RQ-02 (D) · replay seq → RQ-02 · ack authorisation → RQ-31 |
| 0014 | RQ-04, RQ-05 · scheduled → RQ-32 · clamp → RQ-33 · watermark → RQ-34 |
| 0015 | rules → RQ-35 · CLOUD over LAN → RQ-22 · scope → RQ-36 · TR RF → RQ-37 · goodput → RQ-38 |
| 0016 | RQ-06 (×3) · fallback removed / injected failure → RQ-39 · RQ-13 |
| 0017 | preconditions → RQ-40 · round trip, contract suite → RQ-41 · iOS → RQ-42 · confinement, P12 → RQ-43 · AGPL obligations → RQ-44 · claims → RQ-20 |
| 0018 | RQ-11 (×2) · limits, relationship, Kyber → RQ-45 · issuance → RQ-46 · RQ-18 · key placement → RQ-47 · domain separation → RQ-48 |
| 0019 | RQ-07 · RQ-05 · killed-app notification and p90 → RQ-49 · RQ-13 · #377 → RQ-50 |
| 0020 | RQ-08 (×3) · migration lossless, key on wake → RQ-51 · FTS inside → RQ-52 |
| 0021 | RQ-13 · per-device `/changes`, ack-delete, TTL → RQ-53 · RQ-05 · E2E label honesty → RQ-20 · LOCAL_ONLY absent → RQ-54 |
| 0022 | golden vectors → RQ-48 · unknown frames, hello, too old → RQ-55 · RQ-09 · shared DTOs → RQ-56 · RQ-17 · AEAD header → RQ-57 · fuzzing → RQ-58 |
| 0023 | RQ-14, RQ-15 · TURN TTL → RQ-59 · telemetry no IP → RQ-60 · egress and A/B → RQ-61 · P2P two-operator call → RQ-14 |
| 0024 | RQ-06 · RQ-16 · RQ-02/RQ-04 on reconnect · RQ-17 · RQ-18 · E1–E7 → RQ-35 · auto-off → RQ-62 · matrix → RQ-63 |
| 0025 | RQ-01 (bundles) and custody release → RQ-64 · tablet → RQ-65 · uplink attribution → RQ-66 · opaque blobs → RQ-67 · quotas → RQ-68 · RQ-02 |
| 0026 | opinion → RQ-40 · RQ-19 (×2) · RQ-06 · TR exclusion and `RegulatoryRule` → RQ-37 · two nodes → RQ-69 |
| 0027 | no rejected identifiers → RQ-70 · matrix cites ADR → inspection in doc review · watch-list re-check → RQ-79 (dated note) |
| 0028 | catalog → RQ-71 · `verifyMigrations` → RQ-27 · jvmTest → RQ-30 · purity → RQ-25 · `SealedFrame` → RQ-06 · testkit/Meshtastic classpath → RQ-06/RQ-37 · `ui/` count → RQ-24 · iOS → RQ-42 |
| 0029 | RQ-10 (×2) · RQ-09 · RQ-18 · block lists → RQ-72 · `ScopeRule` → RQ-36 · offline report → RQ-73 · DPIA per trigger → RQ-40 · RQ-20 |
| 0030 | V1–V4, V8 → RQ-74 · V5–V7 → RQ-75 |
| 0031 | F1–F8 → RQ-76 |
| 0032 | A1–A6 → RQ-77 |
| 0033 | H1–H9 → RQ-78 |

### 6.4 Principles → requirements

| Principle | RQ |
|---|---|
| P1 server authority | RQ-21, RQ-22, RQ-23 |
| P2 no plaintext on non-server paths, by type | RQ-06, RQ-39 |
| P3 at-least-once, idempotent, effectively-once | RQ-01, RQ-02 |
| P4 persist before acknowledge | RQ-03, RQ-64 |
| P5 DB is SSOT; UI observes DB only | RQ-24, RQ-25, RQ-26 |
| P6 pull is truth | RQ-05 |
| P7 second implementation named | RQ-81 |
| P8 written admission gate per path | RQ-40, RQ-63 |
| P9 no stable identifiers outside TLS | RQ-17, RQ-48, RQ-57 |
| P10 deterministic time and ids | RQ-34 |
| P11 compliance survives offline | RQ-18, RQ-46, RQ-72 |
| P12 buy radios and crypto | RQ-43 |
| P13 owner-only device sign-off | RQ-77 and every D row |
| P14 process/product ADR separation | RQ-79 |

---

## 7. Per-milestone V&V entry and exit

V&V **entry** = what must exist before implementation of the milestone starts (test readiness).
V&V **exit** = which RQs must pass, with which evidence, for the milestone to exit (brief §13).

| Milestone | V&V entry (test readiness) | RQs that must pass at exit | Owner-observed (D) | V&V report |
|---|---|---|---|---|
| Phase 0 | None | RQ-76 (F1–F4, F7 at minimum), RQ-74 after ADR-0030 is applied | rotation login check | Phase 0 epic comment |
| 0.4 | Docker-capable E-LOCAL; base-commit backend suite measured; crash-point test scaffold; Testcontainers concurrency harness | RQ-01 (D), RQ-02, RQ-03, RQ-04, RQ-08 (logical), RQ-09, RQ-10, RQ-11 (claim), RQ-12 (cap), RQ-27, RQ-30 (`:shared`), RQ-31 (auth), RQ-32, RQ-45 (limits), RQ-71, RQ-75, RQ-80 | RQ-01, RQ-03, RQ-08, RQ-09, RQ-10, ROADMAP four conditions, release launch | Milestone tracking issue |
| 0.5.0 | `SimulatedLink` harness, `TestClock`, `CrashPoints`, `FakeServer` (spec §14) merged **before** the engine; seed policy (§9); plane-A golden vectors; fuzz targets scaffolded | RQ-01, RQ-02, RQ-05, RQ-06, RQ-07, RQ-08 (forensic), RQ-21, RQ-24, RQ-25, RQ-28, RQ-29 (0.5.1), RQ-30, RQ-33, RQ-34, RQ-39 (fake), RQ-43 (rule), RQ-48 (A), RQ-49, RQ-50, RQ-51, RQ-52, RQ-55, RQ-56, RQ-58, RQ-70, RQ-72, RQ-73, RQ-75 | RQ-07 capture, RQ-08, RQ-29, RQ-49, RQ-51, release launch | Same |
| 0.6.0 | Per-device fixtures; iOS decision path chosen | RQ-11 (swap), RQ-12 (reader), RQ-26, RQ-31 (per device), RQ-40 (contact DPIA), RQ-42, RQ-45, RQ-75 | RQ-26, RQ-42 (if iOS kept), release launch | Same |
| 0.7.0 | Separate LiveKit host reachable from a test environment; telemetry schema reviewed against DPIA | RQ-14, RQ-15, RQ-40 (TURN/ICE DPIA), RQ-59, RQ-60, RQ-61, RQ-75 | RQ-14, RQ-15 capture, release launch | Same |
| 1.0.0 | Crypto contract suite against `FakeSealer`; plane-B golden vectors; external reviewer engaged; claims register drafted | RQ-06, RQ-08, RQ-13, RQ-20, RQ-39, RQ-40, RQ-41, RQ-42, RQ-43, RQ-44, RQ-48 (B), RQ-50, RQ-52 (FTS), RQ-53, RQ-54, RQ-75 | RQ-08, RQ-41, RQ-20 sign-off, release launch | Same + external review report |
| 1.1 | 0.7 telemetry analysed (RQ-61 part 1) | RQ-14, RQ-15, RQ-61 (A/B), RQ-75 | RQ-14 (P2P), candidate capture | Same |
| 1.2 | G-PROXIMITY artefacts filed (RQ-40, RQ-63); `SimulatedSealedLink` multi-node scenarios; plane-C golden vectors; OTA capture procedure written | RQ-02, RQ-04, RQ-06, RQ-16, RQ-17, RQ-18, RQ-22, RQ-23, RQ-35, RQ-36, RQ-38, RQ-46, RQ-47, RQ-57, RQ-62, RQ-72, RQ-75 | RQ-16, RQ-17, RQ-18, RQ-62, release launch | Same + spike report |
| 1.3 | Custody scenarios in harness; HPKE vectors; opinion and review filed | RQ-01, RQ-02, RQ-47, RQ-52 (custody), RQ-64, RQ-65, RQ-66, RQ-67, RQ-68, RQ-75 | RQ-65, RQ-66, release launch | Same |
| 1.4 | Written opinion filed (RQ-40); time-on-air golden vectors | RQ-06, RQ-19, RQ-37, RQ-69, RQ-75 | RQ-19 field log, RQ-69 | Same |

**Regression rule.** Every RQ that passed in an earlier milestone is re-run (automated ones in CI;
device ones by spot-check, at least RQ-01/03/08 on every release) at every later milestone exit.

---

## 8. Simulated-transport harness

The harness is specified in [`delivery-engine-spec.md`](delivery-engine-spec.md) §14 and must exist
before engine code (V&V entry 0.5). Requirements on it:

| # | Requirement | Why |
|---|---|---|
| H-1 | Every source of nondeterminism is injected: `TestClock` (virtual time shared with the coroutine scheduler), `SequentialIdGenerator`, seeded `Random` per link | Replay from a seed (P10, QS-19) |
| H-2 | `LinkProfile` covers latency distribution, loss, duplication, reorder window, MTU/size cap, scripted partitions, flapping and token expiry | brief §16.2 |
| H-3 | `CrashPoint`s at every commit/ack boundary (spec enum), including mid-reassembly and mid-teardown; a crash discards all in-memory engine objects and rebuilds from surviving stores | P4, RQ-01, RQ-03, RQ-08 |
| H-4 | Multi-node scenarios: sender, `FakeServer` on `InMemory*` ports, recipient, recipient companion; from 1.2 an own device and a mule | RQ-02, RQ-22, RQ-64 |
| H-5 | Invariants I1–I17 checked **after every simulated step**, not only at the end | spec §3 |
| H-6 | Global end conditions: no acked-but-unpersisted row; no lost outbound after enqueue; per-conversation seq strictly increasing; exactly-once display; no non-sealed frame on sealed links; custody released only on authenticated delivery | brief §16.2 |
| H-7 | `SimulatedSealedLink` rejects any frame that is not a testkit-produced `SealedFrame` | RQ-06, I5 |
| H-8 | `FakeServer` implements idempotent replay, per-device ack policy, change feed with `change_seq`, hints, and (1.0) a mailbox; it is itself contract-tested against the backend's behaviour through shared golden vectors | Prevents the fake drifting from the real server |
| H-9 | The harness lives in a test-only module; it never appears on a release classpath (same check as the crypto testkit) | ADR-0016 |
| H-10 | Every adapter (`WsServerLink`, `LanLink`, `NearbyLink`, `MeshtasticLink`) passes the same `ServerLink`/`SealedLink`/`InboundSource` contract suite as the simulated links | CICD-17 conformance suite |

---

## 9. Property tests and seeds

| Rule | Content |
|---|---|
| S-1 Seed shape | A run has one 64-bit master seed, printed at start and on failure. Per-scenario seeds derive deterministically from `(masterSeed, scenarioName, index)`. |
| S-2 PR budget | PRs run a fixed 1 000-seed subset (spec §14.2), identical on every run, so a PR failure is reproducible locally. |
| S-3 Full budget | 10 000 seeds on merge to the trunk and nightly, with a fresh master seed each night — only once CI is off the production host (ADR-0031, R19). Until then the full run is executed on E-LOCAL and its report attached to the milestone exit. |
| S-4 Regression seeds | Every failing seed, once fixed, is added to a checked-in regression-seed list that runs on every PR, forever (spec §14.2). |
| S-5 Reproduction | One command reruns a single seed with full trace (system property or test filter; exact form fixed at implementation). |
| S-6 Shrinking | Failing scenarios are minimised (fewer messages, shorter partitions) before the issue is filed; the minimal scenario is the regression test. |
| S-7 Exit evidence | 0.5 exit attaches: master seeds used, run count, pass count (must be 10 000/10 000), regression-seed list revision. |
| S-8 Flakiness | A property test that fails non-reproducibly from its seed is a harness defect (IL 3), not a flaky test; it is never retried into green. |

Scenario families (each crossed with the link profiles): send burst · edit/delete/react interleaved
with sends · media in PREPARING while text flows · logout mid-flight · token expiry mid-session ·
24 h offline then reconnect · two devices of one user · group of N with mixed online state · (1.2)
LAN and Nearby racing with server reconnect · (1.3) custody chain with mule loss.

---

## 10. Golden vectors

Governed by ICD §11 (G-1…G-7). Plan-level additions:

| # | Rule |
|---|---|
| GV-1 | Plane A (JSON `WsMessage`, `MessageChange`, wake data map) vectors exist at 0.5 exit; plane B (`Content` protobuf) at 1.0; plane C (`TransportFrame`, tags, certificates, CRL, `UplinkBundle`) at 1.2/1.3 |
| GV-2 | The same vector files run in `:shared:jvmTest`, the backend suite and (when it exists) the iOS native test target (KMP-03, KMP-14) |
| GV-3 | Crypto vectors (AEAD, HPKE, signatures, Padmé, proximity tags) use fixed keys from `:mobile:crypto-testkit` and run against every adapter through the commonTest contract suite |
| GV-4 | Time-on-air vectors for the `AirtimeLedger` (e.g. 200 B at SF12/125 kHz) are fixed before 1.4 code and checked against Meshtastic's calculator by analysis |
| GV-5 | A released vector's bytes never change without an ADR (ICD G-6) |

## 11. Fuzzing

Governed by ICD §12 (F-1…F-7). Plan-level additions:

| # | Rule |
|---|---|
| FZ-1 | Targets exist for every parser listed in F-1 before the parser is reachable from a network input |
| FZ-2 | PR runs are short and time-boxed; long campaigns run only off the production host (F-6, R19) |
| FZ-3 | Corpus seeded from golden vectors; every crash becomes a negative vector (F-7) and an IL 3 anomaly |
| FZ-4 | Bounds (`MAX_*`) are verified by a test that feeds a just-over-limit input and asserts rejection before allocation |

---

## 12. Chaos scenarios

"Chaos" here is deliberate fault injection with a stated hypothesis and a pass condition. Production
is **never** a chaos target (shared host, TC-9).

| ID | Fault | Where | Hypothesis / pass condition | RQ | MS |
|---|---|---|---|---|---|
| CH-01 | Process killed at every `CrashPoint` | V2 harness | No loss, no duplicate, no ack before commit | RQ-01/02/03 | 0.5 |
| CH-02 | App killed mid-send on device | E-EMU | Same, after relaunch | RQ-01/02 | 0.4 |
| CH-03 | Airplane-mode toggling script during a conversation | E-EMU | Converges with no manual refresh; no stuck clocks after link up | RQ-05, RQ-29 | 0.5 |
| CH-04 | 100% of WS hints and FCM wakes dropped | V2 | Convergence via pull on reconnect, foreground, WorkManager | RQ-05 | 0.5 |
| CH-05 | JWT expires mid-session; refresh rejected | V2 + V3 | Session closes at `exp`; client refreshes or tears down; no data survives a rejected refresh | RQ-08, RQ-09 | 0.4 / 0.5 |
| CH-06 | Device revoked while connected | V3 + E-EMU | Socket closed ≤ 5 s on every instance | RQ-09 | 0.4 |
| CH-07 | Backend restarted mid-traffic; previous release started against the new schema | V3 (N-1 job) | Outbox resubmits; idempotent replay; old image boots | RQ-02, RQ-80 | 0.4 |
| CH-08 | Redis unavailable | V3 | WS connect and list degrade as documented (#309), messages are not lost; recovery by pull | RQ-05 | 0.5 |
| CH-09 | Device clock set far into the future / past | V1/V2 | Display clamped; watermark unaffected | RQ-33, RQ-34 | 0.5 |
| CH-10 | MinIO unavailable during media upload | V2 | Text and receipts unaffected; media stays PREPARING and resumes | RQ-28 | 0.5 |
| CH-11 | Slow consumer / backpressure on one link | V2 | Other links and conversations unaffected | RQ-28 | 0.5 |
| CH-12 | Seal failure / untrusted identity injected | V2 | `PENDING_SEAL`/park and ask; no plaintext | RQ-39 | 0.5 / 1.0 |
| CH-13 | Killed app receives a wake before first unlock vs after | E-EMU / E-PHYS | DB key availability behaves as ADR-0020 states; no crash | RQ-49, RQ-51 | 0.5 |
| CH-14 | Reinstall / fresh encrypted DB | E-EMU | Full resync by seq; no duplicates | RQ-02, RQ-51 | 0.5 |
| CH-15 | Concurrent prekey claims and identity-key replacement attempts | V3 | One claim per key; replacement rejected | RQ-11 | 0.4 / 0.6 |
| CH-16 | Nearby link flaps during BULK; Wi-Fi lost mid-transfer | V2 + E-PHYS | Resume by offset/ids; no duplicate; text not blocked | RQ-02, RQ-28 | 1.2 |
| CH-17 | Peer certificate expires or CRL delta arrives mid-session | V2 (`TestClock`) + E-PHYS | Link torn down, new sessions refused | RQ-18 | 1.2 |
| CH-18 | Client-isolating Wi-Fi access point | E-PHYS | `LanLink` fails visibly; UI does not claim LAN | RQ-63 | 1.2 |
| CH-19 | Mule goes offline for 24 h; mule quota exhausted | V2 | Sender keeps `serverOwed`; bundle expires on the mule; quota refusal is honest | RQ-64, RQ-68 | 1.3 |
| CH-20 | Uplink key rotates while bundles are in flight | V2/V3 | Current and next keys accepted; no loss | RQ-66 | 1.3 |
| CH-21 | Airtime budget exhausted on a sub-band | V1 | Ledger refuses; other links unaffected | RQ-19, RQ-28 | 1.4 |

---

## 13. Backend integration verification

1. **Docker first.** Every backend verification that counts runs with Docker (Postgres **and**
   Redis). A run without Docker is reported as "N classes not executed", never as "N failures,
   all Testcontainers" (#598).
2. **Base vs branch.** The suite runs on the unmodified base commit and on the branch; the comparison,
   not an absolute count, is the evidence (counts move daily; do not quote them in documents).
3. **Concurrency tests** use real Postgres: seq allocation (RQ-04), prekey claim (RQ-11), ack CAS
   (RQ-31), idempotent send (RQ-02).
4. **N-1 schema job** (RQ-80, CICD-09): apply the branch's migrations to a DB built by the previous
   release's migrations, start the previous release's image against it, run a smoke suite.
5. **Contract tests** (RQ-56): every controller response decoded with the shared DTO and the shared
   `Json` configuration.
6. **ArchUnit/Konsist** rules (BE-08, RQ-21) are frozen-baseline ratchets: the violation count may
   only go down.

---

## 14. Device verification protocol (owner)

| Step | Content |
|---|---|
| D-1 | Artefact: the release-signed APK from the GitHub release, never a debug build (ROADMAP release step 6). Uninstall first if a differently signed build is installed. |
| D-2 | Launch: `adb install`, `am start`, `logcat -d` grep for `FATAL`, screenshot (exact commands in CLAUDE.md). |
| D-3 | Accounts: test numbers `+905000000001..5`; OTP read from the production log for test numbers only; 60 s cooldown per number. |
| D-4 | Record per criterion: RQ ID · observed by · where (emulator-5554 / device model + Android version + operator) · date · build/tag · result · attachment (screenshot, logcat excerpt, capture file). |
| D-5 | State what was **not** seen (colour on a real panel, haptics, background behaviour on a given OEM). |
| D-6 | Captures (FCM payload, ICE stats, OTA) are stored outside the public repo if they contain identifiers; the note records where. |
| D-7 | A failed observation reopens the criterion; the agent files the anomaly (§17). |

---

## 15. What CI cannot verify

| Item | Why CI cannot | Who / where | Earliest |
|---|---|---|---|
| The release artefact launches | No KVM on the CI host; emulator only on the owner's PC (TC-3) | Owner, E-EMU | every release |
| Notification with the app killed; aggressive-OEM latency | Needs real FCM delivery and OEM power management | Owner, E-EMU + E-PHYS | 0.5 |
| Forensic absence of data after logout | Needs device storage access | Owner, E-EMU | 0.5 |
| Calls: audio both ways, operators, CGNAT behaviour | Physical radios and operators | Owner, E-PHYS | 0.7 |
| Biometric and Keystore/StrongBox behaviour | Hardware-backed | Owner, E-PHYS | 0.5 |
| iOS launch and iOS prompts | No macOS runner (KMP-02) | Owner (Mac) or none | 0.6 |
| Nearby/LAN device matrix, consent prompts, background behaviour | Radios, OS versions, OEMs | Owner, E-PHYS | 1.2 entry |
| Over-the-air captures | Radio capture equipment | Owner | 1.2 |
| Battery impact | Physical devices over time | Owner | 1.2 |
| Meshtastic field log | RF hardware, outdoor | Owner, E-FIELD | 1.4 |
| Legal sufficiency (age assurance, Art. 39, KVKK roles, AGPL on stores) | Not a technical property | Counsel | per gate |
| Cryptographic soundness of the integration | Needs expert review | External reviewer | 1.0, 1.3 |
| Telemetry thresholds for kill criteria | Needs production data and owner thresholds (Q-O5) | Owner, analysis | 0.7, 1.2 |

---

## 16. Independence

IEEE 1012 asks for technical, managerial and financial independence proportional to integrity
level. Tailored:

| Integrity level | Independence achieved by |
|---|---|
| 4 | Implementer (agent) ≠ approver (owner) ≠ external party where named: crypto reviewer (1.0, 1.3), counsel (legal RQs), and the owner's own device observation. Agent PRs need the owner's approval (ADR-0032). |
| 3 | Automated tests written against the ADR/spec, reviewed by the owner; adversarial agent review allowed as an input, never as sign-off. |
| 1–2 | Automated guardrails and normal review. |

An agent that implements a change does not also write the only test that verifies it at IL 4: the
contract/property suite is merged before the implementation (V&V entry, §7).

---

## 17. Anomaly management

| Rule | Content |
|---|---|
| AN-1 | Every failed check is an issue with: RQ ID, IL, environment, seed or build, evidence, and "observed by" for device anomalies. |
| AN-2 | IL 4 anomaly: blocks the milestone exit; no waiver (§2). |
| AN-3 | IL 3 anomaly: blocks exit unless the owner records a dated waiver with a follow-up issue in the next milestone. |
| AN-4 | Quarantining or `@Disabled` on an IL 3–4 test is prohibited; `continue-on-error` on a gate job is prohibited (CICD-11). |
| AN-5 | A gate that did not run is reported as not run (ROADMAP DoD 2; the detekt precedent, #279). |
| AN-6 | Environmental CI failures (e.g. 429 from GitHub, #563) are confirmed from the log before a branch is blamed; local gates are the signal that day. |

## 18. Reporting

At each milestone exit, the tracking issue receives a V&V summary:

```text
V&V summary — <milestone> — <tag>
Base vs branch backend suite (Docker on): <link to both reports>
Property suite: <masterSeeds>, <passed>/<run>, regression-seed rev <sha>
RQ status: <RQ-id: PASS (T|A|I|D, evidence link) | FAIL | NOT RUN | WAIVED (by, date, issue)>
Owner observations: <RQ-id, observed by, where, date>
Not observed: <list>
Open anomalies: <issues>
```

---

## 19. Evidence

| Basis | Source |
|---|---|
| RQ-01…RQ-20, harness, owner-only items | brief §16.1–§16.3 |
| RQ-21…RQ-81 | Verification sections of ADR-0010…0033; brief §2 principles, §11.1 |
| Harness components and suites | `delivery-engine-spec.md` §3, §14 |
| Golden vectors and fuzzing | `icd-wire-protocol.md` §11, §12 |
| CI host limits | CLAUDE.md build section; CICD-01, CICD-04, CICD-16, CICD-17; R19 |
| Docker rule | CLAUDE.md; #598 |
| Conformance suite for every adapter | CICD-17 |

## 20. Verification of this plan

| Check | Method |
|---|---|
| Every brief §13 exit criterion maps to an RQ or a gate | Inspection against `roadmap.md` exit tables |
| Every ADR verification item appears in §6.3 | Inspection, ADR by ADR |
| Every RQ has an IL, method, environment, milestone and evidence | Inspection of §6 |
| Every D method names the owner as signer | Inspection |

---

## 21. Reviewer notes

> **Reviewer note (open) RN-V1 — "CI cannot verify any of these" is true only for today's CI host.**
> Brief §16.3 says no device item is CI-verifiable "because there is no KVM on the Hetzner host".
> CICD-16 recommends an emulator smoke test (install, `am start`, no `FATAL`, screenshot) on a
> GitHub-hosted Linux runner with KVM once CI moves off cx43 (ADR-0031), with KVM availability on the
> chosen runner size still to be verified. If that becomes available it should be **added** as an
> automated pre-check on every release candidate. It would not replace the owner's observation: P13
> still applies, and the launch-on-the-promoted-bytes check (ADR-0030 V6) stays owner-signed.

> **Reviewer note (open) RN-V2 — device verification of backend changes happens against
> production.** The app's `BASE_URL` is hard-coded (MOB-15, TC-11), so every V4 demonstration of a
> backend change (0.4 acks, sessions, age gate) needs either a production deploy first or a temporary
> build change that must be reverted. A build-type-scoped base URL (the MOB-15 recommendation) would
> let the owner verify against a staging backend before production. Not in the brief's scope; flagged
> as a test enabler for 0.4/0.5.

> **Reviewer note (open) RN-V3 — RQ-17 wording.** Brief RQ-17 says "no stable identifiers on air".
> The fixed mDNS service type and Nearby service identifier make "a Muhabbet user is here"
> observable by construction (threat-model RN-2, ADR-0024 reviewer note). This plan verifies "no
> stable **per-user or per-device** identifier" and lists the app-level fingerprint as a disclosed
> residual, pending the owner's decision on P9's wording.

> **Reviewer note (open) RN-V4 — the 10 000-seed exit run has no CI home yet.** The brief makes 10k
> seeded runs a 0.5 exit criterion; on the single production-host runner a full run competes with the
> live backend (R19) and ADR-0031 forbids PR jobs there. Until CI moves, the full run is executed on
> E-LOCAL and attached as evidence (S-3). The owner should accept that, or make "CI off the
> production host" an explicit 0.5 entry criterion.
