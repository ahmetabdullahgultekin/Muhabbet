# Muhabbet multi-transport delivery — DPIA screening (KVKK)

> **Status: Proposed — pending owner approval.** Nothing in this document is accepted until the
> owner records it in [`APPROVAL.md`](APPROVAL.md). No code may be written for any scope until
> approval for that scope is recorded there. A signed DPIA for each applicable trigger is required
> **before the change ships** (ADR-0029 rule 6) and, for 1.2 and 1.3, is a **milestone entry
> artefact** (brief P8, §13); it is listed under `Conditions` of the scope it gates.

| | |
|---|---|
| **Date** | 2026-11-12 |
| **Base** | `dev` @ 2434324, branch `claude/muhabbet-p2p-architecture-wdan5r` |
| **Source of truth** | [decision brief](decision-brief-2026-11-12.md) — ADR-0029 rule 6 and §13 gates |
| **Trigger list** | [ADR-0029](../../adr/0029-compliance-boundary.md) rule 6 |
| **Threats referenced** | [`threat-model.md`](threat-model.md) (`T-*`, `L-*`, `RR-*`) |
| **Counsel questions** | [`docs/legal/counsel-questions-2026-11.md`](../../legal/counsel-questions-2026-11.md) (Q-L1…Q-L9) |
| **Evidence** | [findings register](../../reviews/2026-11-12-findings-register.md) — LEG-04 in particular |

> This document is an engineering process, not legal advice, and claims no compliance. Every
> statement about what KVKK requires is subject to counsel (Q-L5, Q-L6, Q-L8, Q-L9).

---

## 1. Legal footing (what this is and is not)

| Point | Text | Source |
|---|---|---|
| KVKK has **no statutory DPIA article** | The verified text of Law 6698 contains no *etki değerlendirme* provision | LEG-04 (verifier), <https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf> |
| The duty a DPIA serves | Art. 12(1): the controller must take "uygun güvenlik düzeyini temin etmeye yönelik gerekli her türlü teknik ve idari tedbirleri" to prevent unlawful processing and access and to safeguard data | KVKK Art. 12(1)(a)–(c) |
| Joint responsibility with processors | Art. 12(2): the controller is jointly responsible with whoever processes on its behalf | KVKK Art. 12(2) |
| Principles a DPIA tests | Art. 4(2): lawful and fair; accurate; specific, explicit, legitimate purposes; *bağlantılı, sınırlı ve ölçülü*; retained only as long as necessary | KVKK Art. 4(2)(a)–(d) |
| Lawful bases | Art. 5(1) explicit consent; Art. 5(2) bases without consent (law, contract, legal obligation, …) | KVKK Art. 5 |
| Transfers abroad | Art. 9 (amended by Law 7499, 2024): adequacy decision (9(1)); otherwise appropriate safeguards incl. a Board-announced **standard contract** (9(4)(c)), notified to the Authority **within five business days** of signing (9(5)); explicit consent only as an **incidental (*arızi*)** exception when no safeguard is possible (9(6)(a)) | KVKK Art. 9 |
| Controller | Art. 3(1)(ı): the person who determines the purposes and means of processing | KVKK Art. 3 |
| Household exemption | Art. 28(1)(a): processing by natural persons solely for activities about themselves or family members in the same household, not given to third parties | KVKK Art. 28 |
| Breach notification | Art. 12(5): notify the data subject and the Board "en kısa sürede" | KVKK Art. 12(5) |

Therefore: a DPIA here is **best practice in service of Art. 12**, used as a release gate by our own
rule (ADR-0029 rule 6, brief P8). It is not described as a statutory filing in any document or UI.

---

## 2. How screening works

| Step | Who | When | Output |
|---|---|---|---|
| 1. Screen | Author of the change (agent or owner) | When an ADR, migration, new adapter, processor, host or telemetry field is proposed | A **screening record** (§5) in the PR description or the milestone issue |
| 2. Decide | Owner | Before the milestone's entry gate | "DPIA required" if any §3 trigger fires or any §4 question is *Yes*; otherwise "screened out" with reasons |
| 3. Assess | Owner, with counsel where a Q-L is linked | Before the change ships; for 1.2 and 1.3, part of the entry gate (brief §13) | A **full DPIA** from the template (§6) |
| 4. Sign | Owner (and counsel's opinion reference) | Before the change ships | Signed DPIA referenced in `APPROVAL.md` → scope → `Conditions` |
| 5. Verify | CI + owner observation (P13) | Milestone exit | Evidence column of the DPIA filled; threat-model rows updated |
| 6. Review | Owner | On any change to the processing, a counsel opinion, an incident, or at the next milestone | Revised DPIA (versioned) |

Storage convention (proposed): `docs/architecture/multi-transport/dpia/DPIA-<trigger-id>-<milestone>.md`,
one file per trigger per milestone. Screening records for changes that screen out stay in the PR.

---

## 3. Trigger list (ADR-0029 rule 6)

Any one of these makes a full DPIA **mandatory** before the change ships.

| ID | Trigger (ADR-0029 rule 6) | First milestone | Gate it belongs to | Threats | Counsel | Template |
|---|---|---|---|---|---|---|
| DT-1 | **Proximity scanning** (BLE / Wi-Fi / mDNS discovery) | 1.2 | G-PROXIMITY ("DPIA for proximity scanning signed") | T-PRX-05/06/07/13, L-02, D-02, I-03 | Q-L2 | §6.1 |
| DT-2 | **Uplink relay** (contacts transiently holding ciphertext); also own-device custody | 1.3 | 1.3 entry ("DPIA") | T-UPL-*, T-OWN-*, D-03, U-04, Nc-06, Nr-01 | Q-L5 | §6.2 |
| DT-3 | **TURN/ICE logging or telemetry** (candidate type, IP family, ASN) | 0.7 | Before the change ships (0.7 exit) | T-CAL-01/04/05, I-02, DD-05 | Q-L8, Q-L6, Q-L9 | §6.3 |
| DT-4 | **Contact-discovery changes** | 0.4 (cap) / 0.6 (discoverability) | Before the change ships (0.4 and 0.6 exit) | T-DIR-01/02/03, L-03 | — | §6.4 |
| DT-5 | **A new processor, or a new foreign host** | any | Before the change ships (exit of the milestone that adds it) | T-PSH-02, RR-3, Nc-01 | Q-L6 | §6.5 |
| DT-6 | **Telemetry used for kill criteria** (e.g. share of Nearby sessions where one side lacked internet) | 1.2 | G-PROXIMITY (collection starts in 1.2 to decide 1.3) | — | Q-L8 by analogy | §6.6 |

> **Reviewer note (open):** the brief's ADR-0029 item 6 lists **five** triggers (proximity scanning,
> uplink relay, TURN/ICE logging, contact-discovery changes, new processor or foreign host). The
> ADR-0029 file adds a sixth, DT-6 (kill-criteria telemetry). DT-6 is kept here because brief
> ADR-0025 and §13 (1.3 entry) make that telemetry a gate input, and SAD §8.5 already treats every
> new telemetry field as a DPIA input. The owner should confirm the sixth trigger when dispositioning
> ADR-0029.

### 3.1 Trigger × roadmap (pre-filled screening)

| Milestone | Change | Trigger / §4 question | Result |
|---|---|---|---|
| Phase 0 | Secret rotation, CI trust boundary | none | Screened out (security fix, no new processing) |
| 0.4 | Age gate: `birth_date`, `age_assurance_level`, `age_assured_at` (V30), possible e-Devlet/NFC-ID later | §4 S-01 (new data category), S-07 (children) | **DPIA recommended** — not on the trigger list; see reviewer note in §8 |
| 0.4 | Contact-lookup daily cap, block filter (V31) | DT-4 | **DPIA required** (§6.4) |
| 0.4 | Device-scoped idempotency, `sender_device_id` (V29) | S-04 (new attribution data) | Screening record; screened out pending owner-recorded reason (§4) — candidate reason: attribution data required by 5651, unless counsel links it to Q-L9 |
| 0.5 | Wake-only push | S-03/S-06 (reduction of what FCM receives; DT-5 does not fire — no new processor or host) | Screening record; screened out pending owner-recorded reason (§4); update the baseline FCM DPIA and the aydınlatma metni (ADR-0019) |
| 0.5 | SQLCipher, `SessionTeardown` | S-12 (security measure change) | Screening record (risk reduction) |
| 0.6 | Discoverable-by-phone setting with reader (V31.2); per-device delivery rows (V32); per-device keys (V33) | DT-4; S-04 | **DPIA required** for DT-4 (revision of the 0.4 DPIA) |
| 0.6 / iOS | APNs | DT-5 | **DPIA required** when iOS ships |
| 0.7 | LiveKit host (placement open, Q-O8), TURN credentials, ICE telemetry | DT-3, DT-5 (if the host is new or foreign) | **DPIA required** |
| 1.0 | E2E cutover: server copy deleted after grace or encrypted to user backup (Q-O4); envelopes with TTL; on-device FTS | S-05 (retention), S-10 (rights) | Screening record + DPIA revision for the server store; claims register (RQ-20) |
| 1.0+ | LOCAL_ONLY | S-05 (retention of receipt metadata, Q-O3/Q-L9) | **DPIA required** before LOCAL_ONLY is offered |
| 1.1 | P2P 1:1 calls (`IP_DIRECT` between mutual contacts) | DT-3 (revision), S-08 | DPIA revision |
| 1.2 | LAN + Nearby, device certificates, CRL, kill-criteria telemetry | DT-1, DT-6, S-08 (co-location) | **DPIA required** (G-PROXIMITY) |
| 1.3 | Own-device custody, sealed uplink | DT-2 | **DPIA required** (1.3 entry) |
| 1.4 | Meshtastic | DT-1 (radio emission), S-03 (hardware vendor/app) | **DPIA required** with the Q-L2 opinion |

---

## 4. Screening checklist

Answer each question **Yes / No / Unknown** for the change. Any *Yes* or *Unknown* means a full
DPIA (§6) unless the owner records why not.

| # | Question | Why it matters | KVKK anchor |
|---|---|---|---|
| S-01 | Does the change collect a **new category** of personal data, or a new field about an existing data subject? | Minimisation | Art. 4(2)(ç) |
| S-02 | Does it involve a **new class of data subject** (non-users in address books, bystanders in radio range, contacts acting as mules)? | Their lawful basis and notice differ | Art. 5, Art. 10 |
| S-03 | Does any data reach a **new processor**, a new **foreign host**, or a new jurisdiction? | Transfer mechanism; joint responsibility | Art. 9, Art. 12(2) |
| S-04 | Does it create a new **identifier** or link two existing ones (device ↔ radio tag, phone ↔ account, IP ↔ user)? | Linking/identifying | Art. 4(2)(ç) |
| S-05 | Does it change a **retention period**, add a store, or keep something until an ack that may never come? | Storage limitation | Art. 4(2)(d), Art. 7 |
| S-06 | Does it make **content or metadata** visible to a party that could not see it before (server, processor, mule, peer, bystander)? | Confidentiality | Art. 12(1) |
| S-07 | Can it affect **children** (age data, under-15 exposure)? | 5651 Ek Md. 4(7) interaction | Art. 4, Law 5651 |
| S-08 | Does it reveal **location or co-location** (radio proximity, IP address, ASN)? | Sensitive in practice; profiling | Art. 4(2)(ç) |
| S-09 | Does it add **telemetry** leaving the device, even aggregate? | SAD §8.5 rule: every field is a DPIA input | Art. 4(2)(c) |
| S-10 | Does it change how users exercise **rights** (access, erasure, objection) or make erasure harder (copies on other people's phones, backups)? | Art. 11 rights; erasure | Art. 7, Art. 11 |
| S-11 | Does it require a change to the **aydınlatma metni** or consent texts? | Notice | Art. 10 |
| S-12 | Does it change a **security measure** (encryption, keys, authentication, revocation window)? | Art. 12 duty | Art. 12(1) |
| S-13 | Could a failure **expose data at scale** (breach-notification path)? | Notification | Art. 12(5) |
| S-14 | Does it rely on **explicit consent** for something systematic? | Consent is not the systematic Art. 9 basis; LEG-04 | Art. 5(1), Art. 9(6) |
| S-15 | Does it place users or the owner under a **non-KVKK duty** that conflicts with minimisation (5651 traffic data, 5809 *işletmeci* duties)? | Retention vs. LOCAL_ONLY | Q-L1, Q-L9 |
| S-16 | Is any **claim** about privacy in UI or docs introduced or changed? | Unawareness (LINDDUN U-01); claims register RQ-20 | Art. 4(2)(a) |

---

## 5. Screening record template

```text
Screening record — <change title>
Date: YYYY-MM-DD        Author: <name / agent identity>        Milestone: <0.x / 1.x>
Linked ADR(s): ADR-00xx        Linked issue/PR: #nnn
Triggers fired (DT-1..DT-6): <list or "none">
Checklist S-01..S-16: <Y/N/U per question, one line each with a reason for every Y or U>
Result: DPIA REQUIRED | SCREENED OUT
If screened out, reason: <one paragraph>
Owner decision: <Approved / Rejected>  Name:  Date:
```

---

## 6. DPIA templates per trigger

### 6.0 Common skeleton (every DPIA uses these sections)

| § | Section | Content required |
|---|---|---|
| 1 | Identification | Trigger ID, milestone, version, author, owner sign-off, counsel opinion reference |
| 2 | Description of the processing | Purpose; data subjects; data items (table: item → source → recipient → store → retention); flows referencing `threat-model.md` §3 IDs; processors and hosts with country |
| 3 | Lawful basis | Art. 5 basis per data item; Art. 9 mechanism per foreign recipient (adequacy / standard contract + 5-business-day notification / other safeguard); explicit consent only where incidental |
| 4 | Necessity and proportionality | Why each item is needed; what was minimised; alternatives rejected (cite ADR) |
| 5 | Risks to data subjects | Table: risk → likelihood → impact → linked `T-*`/`L-*` IDs |
| 6 | Measures | Table: measure → ADR → milestone → verification (RQ-xx / test / owner observation) |
| 7 | Residual risk | Linked `RR-*`; owner acceptance; disclosure text location |
| 8 | Data-subject rights | How access, erasure and objection work for this processing, including copies on other devices |
| 9 | Notice | Exact aydınlatma metni / consent-text changes (TR + EN) |
| 10 | Breach handling | What is exposed if this component is breached; Art. 12(5) notification path |
| 11 | Evidence at exit | Filled at milestone exit: test reports, captures, owner observations (P13) |
| 12 | Review | Next review trigger and date |

### 6.1 DT-1 — Proximity scanning (1.2, `LAN` / `PROXIMITY`; 1.4 `EXTERNAL_RF` reuses it)

| Section | Pre-filled content (to be confirmed by the 1.2 spike) |
|---|---|
| Purpose | Deliver E2E messages between **verified 1:1 contacts** without internet while both have Nearby mode open (ADR-0024) |
| Data subjects | Users in Nearby mode; **bystanders** whose devices advertise or scan in range (their tags are seen and discarded) |
| Data items | Rotating pairwise tag (8 bytes, 15-min epoch); random mDNS instance / Nearby endpoint names; app-level service type / service id (fixed — RN-2); OS-level radio identifiers (randomisation is the OS's); `TransportFrame` sizes (padded) and timing; co-location of two contacts; device certificate (`userId`, `deviceId`, `linkKey`), revealed only after mutual proof of the pairwise secret (threat model T-PRX-14) |
| Processors | Google Play services (Nearby Connections) on Android; the unofficial Nearby Swift package on iOS only if the spike passes. **Whether Nearby Connections sends any data to Google is unknown** and must be established by the spike (see §8) |
| Stores | `peer_link`, `crl_entry` in the encrypted mobile DB; no explicit server store of proximity events; the server can infer co-located delivery from receipt and seq timing (threat model L-06) |
| Retention | Tags: never stored beyond the epoch; peer links: until certificate expiry or contact removal; kill-criteria counters: see DT-6 |
| Lawful basis | To be stated per item by counsel; the feature is user-started and opt-in on both sides (ADR-0024) |
| OS consents | Consent matrix (MISS-01 proximity): Bluetooth, Local Network (iOS), Location (where required), Nearby devices (Android), hotspot join |
| Key risks | Tracking (T-PRX-05, L-02), app-presence disclosure (T-PRX-06, RN-2), co-location (T-PRX-13), crafted-frame DoS (T-PRX-08), revoked device accepted (T-PRX-02, RR-4) |
| Key measures | Rotating tags, random names, padding, no identifiers in advertisements (brief §8.5); foreground only, 15-min auto-off; verified contacts only; certificate + CRL; `SealedFrame` only |
| Verification | RQ-16 (device matrix), RQ-17 (OTA capture by the owner), RQ-18, fuzz targets |
| Counsel | Q-L2 (Art. 39 over BLE/Wi-Fi), Q-L5 if custody rides this path |

### 6.2 DT-2 — Uplink relay and own-device custody (1.3)

| Section | Pre-filled content |
|---|---|
| Purpose | Get a message from a sender with no connectivity to the server through (a) the user's own linked device, then (b) a verified contact's online phone (ADR-0025) |
| Data subjects | Sender; recipient(s); **the mule** (a contact who carries data that is not theirs) |
| Data items | `UplinkBundle` = HPKE-sealed blob containing the sender-device signature, routing and per-device E2E envelopes; visible to the mule: bucket size, time of hand-over, the fact that the sender lacked connectivity; per-mule quota counters (`uplink_quota`) server-side; server-side: mule account ↔ sender device linkage per bundle (threat model L-05) |
| Stores | Mule: separate encrypted `custody.db`, ≤ 50 bundles / 10 MB, deleted on 2xx or after 24 h; server: normal message ingest |
| Roles | **Open — Q-L5.** LEG-04: Muhabbet sets purposes and means of relaying (controller, Art. 3(1)(ı)); relaying users fall outside the household exemption (Art. 28(1)(a)) and are not contracted processors (Art. 12(2)) |
| Key risks | Mule learns metadata (T-UPL-08, D-03); mule unaware of its role (U-04); non-repudiable signature retained (RN-1, Nr-01); quota abuse (T-UPL-05); uplink key compromise (T-UPL-07) |
| Key measures | HPKE to the server around E2E; bucket padding; contacts only; relay mode entered and left by the user, leaving wipes the store; quotas on mule and server; key off the CI/prod host |
| Rights | Erasure: bundles expire in 24 h and are opaque; `SessionTeardown` wipes `custody.db` |
| Verification | 1.3 exit tests (opaque blobs only; true sender attribution; bounded quota abuse) |
| Gate | Written KVKK opinion (Q-L5) and external crypto review of the uplink sealing are 1.3 entry criteria (brief §13) |

### 6.3 DT-3 — TURN/ICE logging and telemetry (0.7; revised at 1.1)

| Section | Pre-filled content |
|---|---|
| Purpose | Relay and connect calls; measure relayed share and setup success per ASN (ADR-0023) |
| Data items | At the TURN/SFU: **participants' IP addresses and ports** (inherent to relaying), session timing and volume; ICE telemetry leaving the device: candidate type, IP family, ASN — **never IPs**; credentials (≤ 10 min) |
| Host | LiveKit host, placement open (Q-O8); if outside Turkey or a new provider, DT-5 also fires |
| Retention | TURN/SFU logs: **to be set** — the 5651 *trafik bilgisi* definition includes IP address, ports, start/end time and data amount (Law 5651 Art. 2(1)(j)); whether a retention duty applies to Muhabbet is Q-L9 |
| Key risks | IP exposure to peers (T-CAL-01, I-02); SFU media access (T-CAL-02); telemetry re-identification (T-CAL-04) |
| Key measures | Relay-only for non-contacts and an "always relay" setting; SFU disclosure; telemetry schema without IPs; KVKK notice |
| Verification | RQ-14, RQ-15; telemetry-schema inspection; one-week egress measurement |
| Counsel | Q-L8 (is the telemetry personal data requiring notice), Q-L6 (hosting basis), Q-L9 (retention) |

### 6.4 DT-4 — Contact discovery (0.4 cap; 0.6 discoverability)

| Section | Pre-filled content |
|---|---|
| Purpose | Tell a user which of their address-book contacts use Muhabbet |
| Data subjects | Users; **non-users** whose numbers sit in users' address books |
| Data items | Unsalted SHA-256 of E.164 numbers (reversible by brute force over the Turkish mobile space — C8; the server also stores users' numbers in plaintext); per-account quota counters (V31, 0.4); discoverability setting (`users.discoverable_by_phone`, V31.2, 0.6); response: display name and avatar of matches |
| Key risks | Enumeration (T-DIR-01), blocked-user leakage (T-DIR-02), false privacy claim (T-DIR-03, U-01) |
| Key measures | Per-account daily cap on new numbers; block filter; discoverable-by-phone setting **with a server-side reader**; stop calling the hash a privacy measure (`SECURITY.md`, KVKK texts); OPRF/PSI is research |
| Notice | Correct the aydınlatma metni: hashes are not a privacy protection; matching is server-side |
| Verification | RQ-12 |

### 6.5 DT-5 — New processor or foreign host (any milestone)

| Section | Pre-filled content |
|---|---|
| Inventory today | Hetzner (hosting, Germany), Google FCM (push), Twilio Verify (OTP in production), Google Firebase Authentication (phone number, verification SMS, device signals; client-side, before the backend-OTP fallback — `FirebasePhoneAuth`), Sentry (crash and error reports from the backend and Android app — `backend/build.gradle.kts:80`, `mobile/composeApp/build.gradle.kts:112`; content depends on scrubbing; active only if `SENTRY_DSN` is set; the owner confirms which), GitHub (CI/CD; build data, not user data); later: Apple APNs (iOS), LiveKit host (0.7), signer host or hardware token (1.2/1.3, Q-O7) |
| Per recipient | Country; Art. 9 mechanism: adequacy decision (9(1)) or a standard contract (9(4)(c)) **notified within five business days** (9(5)), or another 9(4) safeguard; **not** explicit consent as the systematic basis (LEG-04; `docs/legal/03-acik-riza-metni.md` currently does this) |
| Data per recipient | FCM: device token, wake hint `{conversationId, seq}`, timing (after 0.5); Twilio: phone number, OTP verification; Firebase Authentication: phone number, verification SMS, device signals; Sentry: crash/error reports (stack traces, device and request context, possibly identifiers unless scrubbed); Hetzner: all server data |
| Key risks | Content to processor (T-PSH-01, fixed at 0.5), wake metadata (T-PSH-02, RR-3), foreign compulsion (§6.17 of the threat model) |
| Verification | RQ-07 payload capture; contract and notification records listed in the DPIA |
| Counsel | Q-L6 |

### 6.6 DT-6 — Telemetry used for kill criteria (1.2 → 1.3 decision)

| Section | Pre-filled content |
|---|---|
| Purpose | Decide whether 1.3 Relay is built: share of Nearby sessions where one side lacked internet (ADR-0025, Q-O5) |
| Data items | Aggregate counters only (SAD §8.5: "aggregate and counted, not per-user traces"); no peer identity, no tags, no location |
| Retention | Proposed: until the 1.3 entry decision is recorded, then deleted or kept aggregate only |
| Key risks | Re-identification by joining with server logs |
| Measures | Counters computed on-device; upload over an unauthenticated endpoint, or aggregate server-side with the per-request identity discarded at ingest (an upload on the authenticated channel is account-linked even without a device id in the payload); to be specified by the 1.2 telemetry design |
| Verification | Payload capture by the owner; schema inspection |

---

## 7. Sign-off record

| DPIA | Version | Owner sign-off (name, date) | Counsel opinion ref. | Listed in `APPROVAL.md` scope |
|---|---|---|---|---|
| DT-4 contact discovery | — | — | — | 0.4 / 0.6 |
| Age gate (recommended, §8) | — | — | Q-L3 | 0.4 |
| DT-5 FCM / Hetzner / Twilio / Firebase Auth / Sentry (baseline) | — | — | Q-L6 | 0.5 |
| DT-3 TURN/ICE | — | — | Q-L8, Q-L9 | 0.7 |
| DT-1 proximity, DT-6 telemetry | — | — | Q-L2 | 1.2 |
| DT-2 relay | — | — | Q-L5 | 1.3 |
| DT-1 (RF) | — | — | Q-L2 | 1.4 |

All rows are **Not started** as of 2026-11-12.

---

## 8. Reviewer notes

> **Reviewer note (open):** the brief says "a DPIA (KVKK Art. 12)". KVKK has no statutory DPIA
> article (LEG-04 verifier). This document frames the DPIA as best practice serving the Art. 12
> security duty and as a self-imposed release gate, matching the wording of ADR-0029. No document
> should describe it as a statutory requirement.

> **Reviewer note (open):** the 0.4 **age gate** collects date of birth for every user
> (`users.birth_date`, V30) and may later involve e-Devlet or NFC-ID evidence, but it is not on the
> ADR-0029 trigger list. It screens positive on S-01 and S-07. Recommend adding "age assurance" as a
> trigger (or simply running the DPIA) before 0.4 ships, together with Q-L3.

> **Reviewer note (open):** the brief treats Google Nearby Connections as "buy" (ADR-0024) but no
> source in the review establishes **what data, if any, Play services sends to Google** during
> discovery and connection. DT-1 cannot be completed without it; the 1.2 spike should answer it
> from Google's documentation and a traffic capture, and Google becomes a DT-5 processor for this
> feature if it does.

> **Reviewer note (open):** ADR-0029 rule 6 requires a DPIA "before the change ships"; brief §13
> makes one an **entry** criterion only for 1.2 (G-PROXIMITY) and 1.3. DT-3, DT-4 and DT-5 are
> therefore placed at milestone exit here. If the owner wants DPIAs as 0.x **entry** gates, that is a
> new decision to record in ADR-0029 and §13, not something this document asserts.

> **Reviewer note (open):** VERBIS registration (KVKK Art. 16, Veri Sorumluları Sicili) is not
> covered by any brief artefact; it is put to counsel as Q-L6(f).
