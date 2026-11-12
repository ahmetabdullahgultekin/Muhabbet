# Muhabbet multi-transport delivery — threat model (STRIDE + LINDDUN)

> **Status: Proposed — pending owner approval.** Nothing in this document is accepted until the
> owner records it in [`APPROVAL.md`](APPROVAL.md). No code may be written for any scope until
> approval for that scope is recorded there. This threat model is a **gating deliverable** (brief
> §13 Phase 0): it must be re-reviewed before the 0.5 wire changes, before 1.0, and as part of the
> G-PROXIMITY (1.2) and 1.3 entry gates.

| | |
|---|---|
| **Date** | 2026-11-12 |
| **Base** | `dev` @ 2434324, branch `claude/muhabbet-p2p-architecture-wdan5r` |
| **Source of truth** | [decision brief](decision-brief-2026-11-12.md). A disagreement with the brief is a defect in this document and is recorded as a `> **Reviewer note (open):**` callout (§12) |
| **Evidence** | [findings register](../../reviews/2026-11-12-findings-register.md) (IDs such as `BE-12`, `C9`, `CICD-01`, `PROX-03`) |
| **Related** | [`sad.md`](sad.md) §7.2 trust boundaries, §8; [`dpia-screening.md`](dpia-screening.md); [`../../legal/counsel-questions-2026-11.md`](../../legal/counsel-questions-2026-11.md); ADR-0010 … ADR-0033 |
| **Diagrams** | [`c4-context.mmd`](../../diagrams/c4-context.mmd), [`c4-container.mmd`](../../diagrams/c4-container.mmd) — the data-flow model in §3 is derived from them |

---

## 1. Method

| Step | What | Where |
|---|---|---|
| 1 | Data-flow model from the C4 container view (brief §4.1, SAD §5.1): external entities, processes, data stores, data flows, trust boundaries | §3 |
| 2 | Coverage of **every path scope** of ADR-0015 (plus own-device custody, which rides LAN/PROXIMITY) | §4 |
| 3 | Attacker catalogue: capabilities, goals, reach per scope | §5 |
| 4 | **STRIDE per element** (external entity: S, R; process: S, T, R, I, D, E; data store: T, R, I, D; data flow: T, I, D) | §6 |
| 5 | **LINDDUN** privacy threats (Linking, Identifying, Non-repudiation, Detecting, Data disclosure, Unawareness, Non-compliance) | §7 |
| 6 | Each threat → mitigation → ADR → milestone → verification (RQ-xx of brief §16.1, a named test, or an owner observation per P13) | §6, §7, §8 |
| 7 | Residual risks the owner must accept explicitly | §9 |

Conventions:

- **Threat IDs** are `T-<area>-<nn>` (STRIDE) and `L-<nn>` (LINDDUN). They are stable; a retired
  threat keeps its ID with status *Retired*.
- **Today** = state on `dev` @ 2434324, with the finding that proves it. *n/a* = the path does not
  exist yet. *Open by design* = an accepted pre-1.0 property that the UI must state honestly.
- **Verification** names the brief §16.1 requirement (RQ-01 … RQ-20) where one exists. Anything that
  needs a device is signed off only by the owner's own observation (P13, ADR-0032). An agent may not
  mark it passed.
- Nothing here is legal advice. Legal threats name the counsel question (Q-L*n*) that settles them.

### 1.1 Security assumptions (if one fails, the model must be redone)

| ID | Assumption | Consequence if false |
|---|---|---|
| A-1 | libsignal (PQXDH, Double Ratchet, sender keys) is correctly implemented by its authors (P12) | Content confidentiality on every path fails; external crypto review at 1.0 checks our *use* of it, not the library |
| A-2 | Android Keystore / iOS Keychain protect wrapped keys against extraction on a locked, non-rooted device | At-rest protection (ADR-0020) reduces to file-system encryption |
| A-3 | TLS to the server (Traefik) is correctly configured; no TLS-intercepting CA on user devices | Pre-1.0 content and all metadata exposed on the network |
| A-4 | The server is in the **identity TCB** (ADR-0010 consequences): it can lie about device lists and keys; this is detected only by safety-number changes, not prevented | See T-KEY-04, RR-1 |
| A-5 | Google Play services (FCM, Nearby Connections) and OS radio stacks behave as documented | Wake and proximity threats re-evaluated |
| A-6 | The owner's emulator and devices used for P13 observations are not compromised | Device-dependent verification evidence is void |

### 1.2 Out of scope

UI visual design; call-media internals beyond ADR 0009; group E2E beyond sender keys (MLS is
research); web/desktop clients; rooted or jailbroken devices against their own owner; side channels
on the handset (power, EM); denial of service by the radio environment itself (jamming).

---

## 2. Assets

| ID | Asset | Where it lives | Highest-value attacker |
|---|---|---|---|
| AS-1 | Message content (text, media, edits, reactions) | Server (pre-1.0, and channels/communities by design), mobile DB, in transit, custody stores (1.3) | AT-4, AT-5, AT-7 |
| AS-2 | Communication metadata (who↔whom, when, size, seq, receipts) | Server (all eras), FCM/APNs (wake timing), TURN, mules | AT-4, AT-5, AT-3 |
| AS-3 | Identity: phone number (E.164, plaintext server-side, C8), account, device registry | Postgres | AT-8, AT-4 |
| AS-4 | Cryptographic keys: libsignal identity and ratchet state, link key, DB wrapping key | Mobile key store | AT-7 |
| AS-5 | Server trust keys: `JWT_SECRET` (HS256), device-CA intermediate, offline root, uplink unseal key, signing/Play credentials | `.env.prod`, signer host, GitHub secrets | AT-6, AT-1 (CICD-06) |
| AS-6 | Contact graph and address-book numbers (including non-users) | Contact lookup requests, server | AT-8 |
| AS-7 | Location and co-location (proximity, IP addresses) | Radio air, ICE candidates, TURN | AT-3, AT-2, AT-8 |
| AS-8 | Integrity of order and delivery state (ticks) | Server seq, mobile outbox/inbox | AT-1, AT-8 |
| AS-9 | Availability of delivery | All paths | AT-3, AT-8 |
| AS-10 | Compliance capability (takedown, age gate, attribution, lawful-request handling) | Server | AT-5 (as a duty), AT-8 (evasion) |
| AS-11 | Build and release integrity (what users install) | CI, runners, Play | AT-6 |

---

## 3. Data-flow model (from the C4 container view)

### 3.1 Elements

| ID | Type | Element | C4 container (brief §4.1) | Milestone |
|---|---|---|---|---|
| EE-1 | External entity | User (human at the UI) | — | existing |
| EE-2 | External entity | Contact's device (Nearby/LAN peer; mule at 1.3) | Mobile app (another instance) | 1.2 / 1.3 |
| EE-3 | External entity | FCM / APNs | FCM / APNs | 0.5 / iOS |
| EE-4 | External entity | Twilio Verify (OTP SMS) | — (SAD §3.1) | existing |
| EE-5 | External entity | Meshtastic node | Meshtastic node | 1.4 (gated) |
| EE-6 | External entity | GitHub (repo, Actions, secrets) and the runners it dispatches to | — (SAD §7.1) | existing |
| EE-7 | External entity | Regulators, prosecutors, courts | — (SAD §3.1) | existing |
| PR-1 | Process | Mobile UI + `:mobile:messaging` engine (`Outbox`, `Inbox`, `SyncCoordinator`, `PathPolicy` 1.2, `CustodyPolicy` 1.3) | Mobile app | 0.5 |
| PR-2 | Process | `:mobile:crypto` edge (`SealedFrame`, `EnvelopeSealer`, libsignal adapters 1.0, `DeviceCertificateVerifier` and `LinkAuthenticator` 1.2) | Mobile app | 0.5 / 1.0 / 1.2 |
| PR-3 | Process | Transport adapters (`WsServerLink`, `RestChangeFeedSource`, `FcmHintSource`, `LanLink`, `NearbyLink`, `UplinkHandover`, `MeshtasticLink`) | Mobile app | 0.5 → 1.4 |
| PR-4 | Process | Backend modular monolith (auth, messaging, keys, notification, compliance, contacts, calls, WS gateway) | Backend | existing |
| PR-5 | Process | LiveKit SFU + embedded TURN/TLS | LiveKit host | 0.7 |
| PR-6 | Process | Device-CA intermediate / uplink-key signer | Device-CA / uplink-key service | 1.2 / 1.3 |
| PR-7 | Process | CI runners (`muhabbet-cx43` on the production host, unknown `muhabbet-mordecai`, future hosted) | — | existing |
| DS-1 | Data store | Mobile DB (SSOT, outbox, inbound dedup, receipts, FTS); SQLCipher from 0.5 | Mobile app | existing → 0.5 |
| DS-2 | Data store | Mobile key store (Keystore/StrongBox, Keychain): DB wrapping key, libsignal store, link key | Mobile app | 0.5 / 1.0 / 1.2 |
| DS-3 | Data store | `custody.db` (own-device custody, mule cache), separate key and quota | Mobile app | 1.3 |
| DS-4 | Data store | PostgreSQL (`shared-postgres`): users, phone numbers, messages, keys, envelopes (1.0), certificates (1.2) | PostgreSQL 16 | existing |
| DS-5 | Data store | Redis (`shared-redis`): fan-out, rate limits | Redis 7 | existing |
| DS-6 | Data store | MinIO (`shared-minio`): media blobs (ciphertext from 1.0) | MinIO | existing |
| DS-7 | Data store | Production secrets (`.env.prod`, Firebase credential, signing key, Play credential) | Backend host, GitHub | existing |
| DS-8 | Data store | Offline root key | Offline | 1.2 |
| DS-9 | Data store | Server logs (container logs, including the documented test-number OTP log line) | Backend host | existing |

### 3.2 Data flows

| ID | Flow | From → To | Scope (ADR-0015) | Trust boundary | Milestone |
|---|---|---|---|---|---|
| DF-1 | WS control plane (`hello`/`welcome`, `message.*`, `sync.hint`, `crl.delta`, call signalling) | PR-3 ↔ PR-4 | `SERVER_RELAY` | TB-1 | existing / 0.5 |
| DF-2 | REST: `/changes`, contact lookup, prekey claim, uplink, reports | PR-3 ↔ PR-4 | `SERVER_RELAY` | TB-1 | existing / 0.5 |
| DF-3 | Wake push `{type:"wake", conversationId, seq}` | PR-4 → EE-3 → PR-3 | (hint only) | TB-2 | 0.5 |
| DF-4 | Media upload/download via presigned URLs | PR-3 ↔ DS-6 | `SERVER_RELAY` | TB-1 | existing |
| DF-5 | Call media and ICE | PR-3 ↔ PR-5 / PR-3 ↔ EE-2 | `IP_RELAYED` / `IP_DIRECT` | TB-7, TB-3 | 0.7 / 1.1 |
| DF-6 | LAN link (mDNS + TCP + link handshake, `TransportFrame`) | PR-3 ↔ EE-2 | `LAN` | TB-3 | 1.2 |
| DF-7 | Nearby link (BLE discovery → Wi-Fi upgrade, `TransportFrame`) | PR-3 ↔ EE-2 | `PROXIMITY` | TB-3 | 1.2 |
| DF-8 | Sealed uplink: sender → mule (LAN/Nearby), mule → `POST /api/v1/uplink` | PR-3 → EE-2 → PR-4 | `UPLINK_RELAY` | TB-3, TB-4 | 1.3 |
| DF-9 | Own-device custody hop | PR-3 ↔ user's other device | `LAN` / `PROXIMITY` | TB-3 | 1.3 |
| DF-10 | Meshtastic BLE/serial → LoRa air | PR-3 ↔ EE-5 ↔ air | `EXTERNAL_RF` | TB-3 | 1.4 |
| DF-11 | Certificate issuance/renewal, CRL deltas | PR-6 → PR-4 → PR-3 | `SERVER_RELAY` | TB-6, TB-1 | 1.2 |
| DF-12 | OTP request/verify | PR-4 ↔ EE-4 ↔ user's SIM | — | TB-2-like (processor) | existing |
| DF-13 | Build, sign, release, deploy | EE-6 → PR-7 → host / Play | — | TB-5 | existing |
| DF-14 | Room creation, TURN credentials | PR-4 → PR-5 | — | TB-7 | 0.7 |

### 3.3 Trust boundaries

TB-1 … TB-7 are those of SAD §7.2 (device↔backend; backend↔push processors; device↔device;
mule↔backend; CI↔production host and secrets; signer↔everything else; backend↔LiveKit). This model
adds two that the brief's controls depend on:

| ID | Boundary | Why it is separate |
|---|---|---|
| TB-8 | **Physical possession** of the handset (lost, stolen, seized) | ADR-0020 controls; AT-7 |
| TB-9 | **Co-tenancy on the production host**: `shared-postgres`, `shared-redis`, `shared-minio` serve other projects; docker-group membership equals root (CICD-01, SAD TC-9) | Compromise of a neighbour project or of a runner job crosses into Muhabbet data without crossing TB-1 |

---

## 4. Path-scope coverage

Every scope of ADR-0015, the elements it touches and the threats that apply. A scope may not ship
until every threat listed for it is *Mitigated* or an accepted residual (§9).

| Scope | Flows | Trust boundaries | Milestone | Attackers in reach | STRIDE threats | LINDDUN |
|---|---|---|---|---|---|---|
| `SERVER_RELAY` pre-1.0 (TLS only, honest "not E2E") | DF-1, DF-2, DF-3, DF-4, DF-12 | TB-1, TB-2, TB-9 | existing → 0.6 | AT-1, AT-4, AT-5, AT-6, AT-7, AT-8 | T-SRV-*, T-PSH-*, T-MED-*, T-DIR-*, T-KEY-01/02, T-REG-*, T-DEV-*, T-HST-*, T-SCM-* | L-01, L-03, I-01, D-01, D-04, DD-01…03, U-01, Nc-01…04 |
| `SERVER_RELAY` 1.0+ (libsignal per device) | as above + `message_envelopes` | TB-1, TB-2, TB-9 | 1.0 | AT-4 (metadata only), AT-5, AT-8 | T-SRV-09/10, T-KEY-03/04, T-E2E-*, T-MED-01/02 | DD-04, D-04, Nr-03, Nc-05 |
| `IP_RELAYED` (LiveKit SFU/TURN) | DF-1 (signalling), DF-5, DF-14 | TB-7 | 0.7 | AT-1, AT-4, AT-5, AT-8 | T-CAL-02…06 | I-02, DD-05, U-03 |
| `IP_DIRECT` (ICE host/srflx, 1:1 calls) | DF-5 | TB-3 (IP between peers) | 1.1 | AT-2, AT-8 | T-CAL-01, T-CAL-03 | I-02 |
| `LAN` | DF-6 | TB-3 | 1.2 | AT-2, AT-3 | T-PRX-*, T-PKI-* | L-02, D-02, U-03, Nc-06 |
| `PROXIMITY` | DF-7 | TB-3 | 1.2 | AT-2, AT-3 | T-PRX-*, T-PKI-* | L-02, D-02, I-03, U-03 |
| Own-device custody (custodian 2) | DF-9 | TB-3, TB-8 | 1.3 | AT-3, AT-7 | T-OWN-* | — |
| `UPLINK_RELAY` (custodian 3) | DF-8 | TB-3, TB-4, TB-6 | 1.3 | AT-2 (as mule), AT-4, AT-8 | T-UPL-* | D-03, Nr-01, U-04, Nc-06 |
| `EXTERNAL_RF` | DF-10 | TB-3 | 1.4 (TR-excluded) | AT-3, AT-5 (as legal exposure) | T-RF-* | D-05 |
| Cross-cutting: certificates/CRL | DF-11 | TB-6 | 1.2 | AT-4, AT-6 | T-PKI-* | — |
| Cross-cutting: build and release | DF-13 | TB-5 | Phase 0 | AT-6 | T-SCM-*, T-HST-01/02 | — |

---

## 5. Attacker catalogue

| ID | Attacker | Capabilities | Goals | Cannot (by design, once the cited control exists) | First exposure | Principal threats |
|---|---|---|---|---|---|---|
| AT-1 | **Network attacker** (on-path or passive: hostile Wi-Fi, carrier/CGNAT operator, ISP, anyone reading public git history) | Observe/modify IP traffic; read logs at intermediaries; read the public repo history | Read content, hijack sessions, correlate users, forge tokens | Read TLS content (A-3); read E2E content (1.0); forge a JWT after rotation (Phase 0) | today | T-SRV-01, T-SRV-04, T-HST-02, T-REG-01 |
| AT-2 | **Malicious contact** (registered, shares a conversation, may hold a *verified* safety number, may act as a mule) | Everything a legitimate peer can do; sends crafted frames over LAN/Nearby; holds bundles as a mule; sees the other party's IP if direct | Track, impersonate, read others' traffic, disrupt delivery, deny having said something | Read bundles it carries (HPKE + E2E); forge another sender (sender-device signature, libsignal); reach groups/channels off-server (`ScopeRule`) | today (server); 1.2/1.3 (off-server) | T-SRV-05, T-PRX-02/10/11/13, T-UPL-*, T-CAL-01 |
| AT-3 | **Malicious stranger in radio range** (no relationship with the victim; may own a registered account) | Scan BLE/Wi-Fi/mDNS; advertise; connect; inject, replay or flood frames; capture airtime | Track (social graph, presence), impersonate a contact, crash or drain the app, learn content | Pass the link handshake without a certified link key (1.2); be admitted without verified-contact status; read `SealedFrame` content | 1.2 | T-PRX-01/03/04/05/06/07/08/09/12, T-RF-03/04, T-OWN-02 |
| AT-4 | **Compromised server / insider** (operator with DB, log or host access; a breached backend; a co-tenant project on the host, TB-9) | Read and modify DS-4…DS-9; change key directory and device registry; issue certificates if the signer is reachable | Read content and metadata, MITM via keys, add ghost devices, silently downgrade | Read E2E content without a client-visible key change (1.0); sign certificates if the signer is off-host (ADR-0031); silently replace an identity key (ADR-0018) | today | T-SRV-09, T-KEY-03/04, T-HST-03/04, T-PKI-01/04, T-MED-01, T-REG-04 |
| AT-5 | **Legal compulsion** (lawful order to the operator; device seizure; regulatory demand: retention, interception cooperation, key deposit) | Compel the operator to disclose what it holds, retain more, or alter the service; seize devices | Obtain content/metadata; identify users; enforce takedown | Obtain E2E content from the server after 1.0 cutover (only ciphertext, deleted on ack/TTL); obtain what is not retained | today | §6.17, T-DEV-04, T-RF-01, L-* (DD, Nc) |
| AT-6 | **Malicious CI contributor** (fork PR author, compromised action or dependency, compromised agent identity) | Run code on self-hosted runners via `pull_request`; poison dependencies/actions; read repo-level secrets from branch PRs | Root on the production host, steal signing/Play/JWT secrets, ship a backdoored build | Run on the production host (ADR-0031); reach `release` secrets without a tag on `main` and a reviewer; change what ships after the emulator launch (ADR-0030) | today | T-SCM-*, T-HST-01/02, T-PKI-01, T-UPL-07 |
| AT-7 | **Lost/stolen device** (finder, thief, forensic examiner) | Physical access; locked or unlocked; forensic extraction | Read history, keys; keep receiving messages; impersonate the user | Read the DB after logout or teardown (ADR-0020); keep receiving after remote revoke (≤ 5 s on server paths; ≤ 7 d off-server) | today | T-DEV-*, T-SRV-02/03, T-OWN-01, T-PRX-02 |
| AT-8 | **Remote malicious registered user** (no relationship; scales by scripting the API) — added because contact enumeration and prekey drain need no radio | Authenticated API and WS calls at scale | Enumerate users, drain prekeys, forge acks, spam, evade age gate | Exceed per-account caps; ack messages it is not a recipient of; claim prekeys outside a relationship | today | T-DIR-01, T-KEY-01/02, T-SRV-05/11, T-REG-02/05, T-CAL-03 |

---

## 6. STRIDE per element

Column key: **S/T/R/I/D/E** category · **AT** attacker · **Today** state on `dev` with evidence ·
**ADR** decision · **MS** milestone in which the mitigation lands · **Verification** how it is proven.

### 6.1 Server control plane and REST — PR-4, DF-1, DF-2 (`SERVER_RELAY`)

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-SRV-01 | S | JWT carried in the WS query string is captured in proxy/access logs and replayed | AT-1, AT-4 | Open (BE-12) | First-frame or header auth, never the query string | 0022 | 0.4 | Test: connect with `?token=` is refused; log inspection shows no token |
| T-SRV-02 | S, E | WS session outlives token expiry, logout, device revoke and account deletion | AT-7, AT-2 | Open (BE-12) | `WsSessionRegistry` bound to `(deviceId, jti, exp)`; `SessionRevocationPort` closes sessions on expiry/logout/revoke/delete, cross-instance via Redis | 0010, 0029 R5 | 0.4 | **RQ-09** (≤ 5 s): test + owner emulator observation |
| T-SRV-03 | S | Refresh-token rotation is racy and has no reuse detection, so a thief and the owner both hold valid sessions | AT-7, AT-1 | Open (BE-13) | Conditional revoke (one row affected or fail); reuse of a revoked token revokes all of that device's tokens | 0010 (auth, brief §4.2) | 0.4 | Test: replayed refresh token revokes the device |
| T-SRV-04 | S | `JWT_SECRET` (HS256) was committed in `.env.prod` (commit 3925f708) to a now-public repo: anyone can mint a token for any user | AT-1, AT-6 | Open (CICD-06) | Rotate **every** value ever in `.env.prod`; secret scanning + push protection; commit-scoped allowlist recording the rotation | 0031 | Phase 0 | Phase 0 exit: login with a token signed by the old secret fails |
| T-SRV-05 | T | Delivery acks accepted for any `messageId` from any user; status broadcast to the sender regardless → forged ticks and READ | AT-8, AT-2 | Open (BE-03) | `ack{conversationId, uptoSeq, kind}` per device, only from a non-sender member's device, only for existing seqs; monotonic conditional update | 0013 | 0.4 | Testcontainers: non-member ack changes 0 rows and broadcasts nothing |
| T-SRV-06 | T | Check-then-insert idempotency on a client-chosen global id; a retried send gets an error, a race yields duplicates | AT-1 (loss), benign retry | Partial (BE-07) | `UNIQUE(sender_device_id, client_message_id)`; duplicate submit replays `ServerAck(seq, serverTs, replay=true)` | 0013 | 0.4 (V29) | **RQ-02** |
| T-SRV-07 | T | Timestamp-only order with no tie-break and no gap detection: reorders and silent loss | AT-1 | Open (BE-06, C13) | Per-conversation `seq` + `change_seq`; pull-by-seq; WS/FCM are hints | 0014 | 0.4 (V28) / 0.5 | **RQ-04**, **RQ-05** |
| T-SRV-08 | I | Scheduled messages readable by recipients (history, background sync) before they are due | AT-2 | Open (BE-02) | Seq assigned at release; `ChangeFeedService` hides unreleased rows; one transaction per release | 0014 | 0.4 | Test: recipient history, `/changes`, search and media exclude unreleased messages |
| T-SRV-09 | I | Server stores, searches, previews and pushes plaintext content | AT-4, AT-5 | **Open by design until 1.0** (BE-15) | Pre-1.0: honest "not E2E" UI. 1.0: no server plaintext, search, link previews or media reads for E2E conversations | 0019, 0021 | 1.0 | **RQ-13** (SQL assertion + DB-dump inspection) |
| T-SRV-10 | T, I | Downgrade: an old or modified client keeps sending `protocolVersion 1` (plaintext body) into an E2E conversation; unknown frame types silently dropped | AT-4, AT-8 | Open (KMP-11) | `hello`/`welcome` negotiation with `minSupported`; server-side minimum protocol version for E2E conversations (`PROTOCOL_TOO_OLD`); `Unknown` → `UNSUPPORTED_FRAME` | 0022, 0016 | 0.5 / 1.0 | Golden vectors; test: a v1 plaintext send into an E2E conversation is rejected |
| T-SRV-11 | D | Message flood or slow consumer exhausts the instance; limiter is in-process per instance | AT-8 | Partial (BE-16 fixed on dev; BE-17) | `ConcurrentWebSocketSessionDecorator` bounds; `AbuseRateLimiter` (Redis token buckets); bounded lanes and `Backpressure` | 0012, brief §4.2, §6.9 | 0.5 / scale-out | Load test against a non-production environment; lane-bound unit tests |
| T-SRV-12 | E | Inbound adapters bypass use cases; ArchUnit does not enforce module boundaries, so new ports can skip authorisation | AT-8 | Open (BE-08, BE-09) | ArchUnit/Konsist boundary rules **before** new ports; `adapter.in` may not depend on out-ports | 0010 (P1), brief §14 item 10 | 0.4 | Architecture tests in CI |
| T-SRV-13 | R | Server cannot attribute a message to a device (5651 duty), or a sender denies a message the server attributes | AT-2, AT-5 | Partial: attribution per user, not per device | `messages.sender_device_id` on every row (V29); attribution is server-side only; no long-term content signatures (deliberate, Nr-01) | 0013, 0014, 0029 | 0.4 | Inspection: every message row carries `sender_device_id` |
| T-SRV-14 | D | A lost WS frame or push leaves the recipient stale indefinitely | AT-1 | Open | Pull-by-seq on reconnect, wake, foreground and WorkManager (P6) | 0014 | 0.5 | **RQ-05** (drop 100% of hints, still converges) |

### 6.2 Wake push — DF-3, EE-3

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-PSH-01 | I | Message content in the FCM payload: content to a foreign processor (KVKK Art. 9 transfer) | AT-4 (processor), AT-5 (foreign compulsion) | Open (BE-15) | Data-only `{type:"wake", conversationId, seq}`, high priority, collapse key; client fetches over TLS and renders locally | 0019 | 0.5 | **RQ-07** (payload capture) |
| T-PSH-02 | I | Residual metadata at the processor: device token, wake timing, internal `conversationId` | AT-4, AT-5 | Residual by design | No sender, no content; aydınlatma update; lawful basis confirmed by counsel (Q-L6) | 0019, 0029 | 0.5 | Payload inspection; counsel opinion Q-L6 |
| T-PSH-03 | S | Forged or replayed wake (holder of the Firebase credential) | AT-6, AT-4 | — | A wake carries no authority: it only triggers a TLS pull (P6); worst case is spurious pulls | 0019, 0014 | 0.5 | Test: a wake for an unknown seq results in one pull and no state change |
| T-PSH-04 | D | Push suppressed or delayed (OEM throttling, processor outage) | environment | — | Foreground pull, seq-driven WorkManager sync, battery-exemption notice on known OEMs | 0019 | 0.5 | R11: p90 latency measured on emulator and a Xiaomi/Oppo-class device (owner) |

### 6.3 Media — DF-4, DS-6

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-MED-01 | I | Media stored in plaintext; a leaked presigned URL exposes it until expiry | AT-4, AT-1 | Open by design until 1.0 | From 1.0 media bytes are AES-256-GCM ciphertext; key and IV travel only inside the sealed body | 0016, 0017 (brief §8.3) | 1.0 | Inspection: a downloaded blob is not decodable without the in-message key |
| T-MED-02 | T | Blob substitution in storage or in transit | AT-4 | Open | `MediaPointer{blobId, size, sha256, key, iv}` sealed; SHA-256 verified before the message row completes | 0022 (brief §6.7) | 1.0 | Test: tampered blob is rejected |
| T-MED-03 | I | Media survives account erasure | AT-4, AT-5 | Open (MISS-02 governance, #327) | Outside this design; #327 stays open | 0020 consequences | — | Tracked in #327 |
| T-MED-04 | D | Reassembly memory exhaustion off-server | AT-2, AT-3 | n/a | `MAX_REASSEMBLY_BYTES`, TTL, SHA-256 verify | brief §6.7 | 1.2 | Limit test; fuzz |

### 6.4 Contact discovery — DF-2 (`/contacts`), PR-4 contacts

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-DIR-01 | I | Enumeration of the Turkish mobile space: unsalted SHA-256 of E.164, 1000 hashes per request, only a per-IP edge limit; response returns display name and avatar | AT-8 | Open (C8, M3) | Per-account daily cap on *new* numbers (0.4); server-enforced "discoverable by phone" setting **with a reader** (0.6); OPRF/PSI is research | brief §8.6, 0029 R4 | 0.4 / 0.6 | **RQ-12** (integration test proves throttling) |
| T-DIR-02 | I | A blocked user still learns the blocker's name and avatar through sync | AT-2, AT-8 | Open (M3) | Block-list filter in lookup | brief §8.6, 0029 R4 | 0.4 | Test |
| T-DIR-03 | R (honesty) | Public documents present unsalted hashes as a privacy measure | — | Partial (GOV-08, MISS-03 governance) | Correct `SECURITY.md` and KVKK texts | brief §14 item 6, §8.6 | Phase 0 | Inspection of both branches |

### 6.5 Key directory and identity — PR-4 keys, DS-4

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-KEY-01 | T | Non-atomic one-time-prekey consumption hands the same prekey to two requesters | AT-8 | Open (BE-14, C10) | `DELETE … WHERE id = (SELECT … FOR UPDATE SKIP LOCKED) RETURNING`; last-resort Kyber fallback | 0018 | 0.4 | **RQ-11** (concurrent claims never return the same key) |
| T-KEY-02 | D | Any authenticated user drains a victim's prekeys | AT-8 | Open (M1) | Per-requester and per-pair rate limit; relationship check (contact or shared conversation); low-water replenishment signal | 0018 | 0.4 | Test |
| T-KEY-03 | S | Silent identity-key replacement for an existing device enables an undetected server-side MITM | AT-4, AT-5 | Open (BE-14) | Changed key for an existing `device_id` rejected; a new key is a new device → `IdentityKeyChanged` → safety-number-changed message; **blocking interstitial for verified contacts** | 0018 | 0.6 / 1.0 | 0.6 exit: replacement attempt returns an error |
| T-KEY-04 | S | Server adds a ghost device to a user's device list, so fan-out encrypts to an attacker device | AT-4, AT-5 | n/a until 1.0 | Every new device emits `IdentityKeyChanged` to contacts and to the user's own devices; verified contacts see a blocking interstitial. **Residual**: the server is in the identity TCB (A-4); key transparency is research | 0018, 0010 | 1.0 | Test: device addition produces the system message on all peers. Residual RR-1 |
| T-KEY-05 | I | Per-user key schema would force private-key export for multi-device | — | Open (M2) | Per-device key tables | 0018 | 0.6 (V33) | Migration test |

### 6.6 Registration, OTP and age — DF-12, PR-4 auth

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-REG-01 | S | Account takeover by SIM swap or SMS interception (phone-OTP identity) | AT-1 (carrier-level), AT-5 | Residual, inherent to phone-OTP | The thief's handset is a **new device**: contacts get a safety-number change (1.0); verified contacts get a blocking interstitial | 0018 | 1.0 | See reviewer note RN-4 |
| T-REG-02 | E | A user under the statutory minimum age registers and is served on every path | AT-8 | Open (MISS-04 legal) | Age gate at registration behind `AgeAssurancePort`; from 1.2 a valid attestation is a precondition for a device certificate | 0029 R1, 0018 | 0.4 / 1.2 | **RQ-10** |
| T-REG-03 | S | Self-declared date of birth is false | AT-8 | Residual (R7) | No compliance claim until counsel answers Q-L3; e-Devlet/NFC-ID adapter behind the same port if required | 0029 R1 | 0.4 | Counsel opinion Q-L3 |
| T-REG-04 | I | The documented test-number path logs OTP codes in full in production logs; anyone with log access can sign in as `+905000000001…5` | AT-4 | Open (documented practice) | Residual: limited to unallocated test numbers; test accounts must hold no real-user conversations | — | — | Residual RR-8 |
| T-REG-05 | D | OTP pumping (SMS cost, provider throttling) | AT-8 | Partial (per-number cooldown exists) | `AbuseRateLimiter` on OTP requests | brief §4.2 | 0.4 | Rate-limit test |

### 6.7 Device at rest — DS-1, DS-2, DS-3, TB-8

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-DEV-01 | I | Logout and account deletion leave the plaintext DB and outbox on the device | AT-7 | Open (MOB-01) | `SessionTeardown` (0.4: wipe tables, queues, WorkManager); 0.5: ordered crypto-erase (destroy wrapped key, then delete) | 0020 | 0.4 / 0.5 | **RQ-08** (forensic check after logout; owner) |
| T-DEV-02 | I | Unencrypted DB at rest; a plaintext FTS index would sit beside ciphertext | AT-7 | Open (repo DB unencrypted; GEM-14 pattern) | SQLCipher with a random 256-bit key wrapped by Keystore (StrongBox when present) / Keychain; FTS only inside the encrypted DB | 0020 | 0.5 / 1.0 | Forensic check; inspection |
| T-DEV-03 | I | A background sync job re-creates data after logout | AT-7 | Partial (MISS-02 mobile) | Teardown step 2 cancels WorkManager/BGTask | 0020 | 0.4 | Test |
| T-DEV-04 | I | Device seized unlocked, or after first unlock: the DB key is available (needed for background delivery) | AT-7, AT-5 | Residual by design | Trade recorded in ADR-0020 (passphrase key rejected); App Lock #378 is a separate UI gate | 0020 | — | Residual RR-6 |
| T-DEV-05 | I | A test fake that reports E2E reaches a release build, so the UI claims E2E over plaintext | AT-6 (or mistake) | n/a | `:mobile:crypto-testkit` allowed only on test configurations; build check fails if on any release runtime classpath | 0016 | 0.5 | **RQ-06** (classpath check) |

### 6.8 Crypto edge — PR-2

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-E2E-01 | I | Silent plaintext fallback on seal failure (normal offline) ships plaintext under an E2E label | AT-2, AT-3, AT-4 | Open (C9) | Fallback **deleted**; `SealResult{Sealed, NoSession, UntrustedIdentity, Failed}`; entry stays `PENDING_SEAL` with honest UI; non-server ports accept only `SealedFrame` (internal constructor) | 0016 | 0.5 (deletion) / 1.0 | **RQ-06**; 1.0 exit: injected seal failure ends in `PENDING_SEAL`/`FAILED`, never plaintext |
| T-E2E-02 | T, S | Hand-written primitives, or keys derived from ratchet internals | — | — | libsignal only; no `javax.crypto`/`CryptoKit` primitive outside `:mobile:crypto` adapters and the existing media AEAD; link key is separate (domain separation) | 0017, 0018 (P12) | 1.0 / 1.2 | Dependency rule in CI; external crypto review |
| T-E2E-03 | S | An untrusted (changed) identity is accepted silently | AT-4 | n/a | `UntrustedIdentity` parks the entry and asks the user | brief §8.7 | 1.0 | Test |
| T-E2E-04 | I | A conversation is labelled E2E while the server still holds a readable copy of its history | AT-4 | n/a | Label E2E only once the server holds no readable copy (cutover per Q-O4) | 0021 | 1.0 | **RQ-20** (claims register) |
| T-E2E-05 | I | iOS (NoOp crypto) joins a cross-platform E2E conversation | — | Open (KMP-01; iOS NoOp) | iOS has no `SealedFrame` producer, so its entries stay `PENDING_SEAL`; 1.0 may ship Android-only | 0016, 0017, 0028 | 0.6 gate / 1.0 | Contract suite against the iOS adapter when it exists |

### 6.9 Calls — DF-5, DF-14, PR-5 (`IP_RELAYED`, `IP_DIRECT`)

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-CAL-01 | I | ICE host/srflx candidates reveal a user's IP (an IPv6 /64 may pin a household) to the peer | AT-8, AT-2 | n/a (calls do not work, FT-01) | `iceTransportPolicy = relay` unless mutual contacts with an existing conversation; "always relay" setting | 0023 | 0.7 / 1.1 | **RQ-15** (ICE-stats inspection) |
| T-CAL-02 | I | An SFU terminates DTLS-SRTP and can access media | AT-4, AT-5 | n/a | UI discloses SFU media access; 1.1 P2P makes 1:1 calls end-to-end | 0023 | 0.7 / 1.1 | UI inspection; **RQ-14** |
| T-CAL-03 | S, E | TURN abused as an open relay or with replayed credentials | AT-8 | n/a | Backend-minted credentials per device and session, TTL ≤ 10 min | 0023 | 0.7 | Test: expired credential refused |
| T-CAL-04 | I | ICE telemetry records IP addresses | AT-4, AT-5 | n/a | Candidate type, IP family and ASN only; KVKK notice; DPIA trigger | 0023, 0029 R6 | 0.7 | Telemetry-schema inspection; Q-L8 |
| T-CAL-05 | D | TURN egress exhaustion or cost blow-up | AT-8 | n/a | Separate host; budget for 30–50% relayed calls; measure | 0023 | 0.7 | One week of relayed share and egress measured (0.7 exit) |
| T-CAL-06 | S | Spoofed call signalling | AT-8 | n/a | Signalling only as authenticated CONTROL frames over the server | 0023, 0012 | 0.7 | Test |

### 6.10 LAN and Nearby — DF-6, DF-7, PR-3 (`LAN`, `PROXIMITY`)

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-PRX-01 | S | A stranger impersonates a contact's device on the link | AT-3 | n/a | Device certificate + CRL + vetted handshake (Noise or TLS 1.3 pinned, chosen by spike) against the certified link key; Nearby UKEY2 is hygiene only; eligibility requires a verified safety number | 0018, 0024 | 1.2 | Test: handshake with an uncertified or mismatched key fails; external crypto review |
| T-PRX-02 | S | A revoked, banned or under-age device is still accepted off-server | AT-2, AT-3 | n/a | 7-day certificates, non-renewal, signed CRL delta over WS; age attestation and no active ban are issuance preconditions | 0018, 0029 R5 | 1.2 | **RQ-18** (TestClock + owner observation) |
| T-PRX-03 | T | Frame injection or modification on the air | AT-3 | n/a | Link AEAD with `TransportFrame` fields 1–6 as associated data; libsignal envelope inside | 0022 | 1.2 | Golden vectors; fuzz |
| T-PRX-04 | T | Replay of captured frames | AT-3 | n/a | Random 16-byte `frameId` dedup with TTL ≥ max lifetime; `deviceCounter` replay drop; `ttlSec` | 0014, 0022 | 1.2 | Engine test |
| T-PRX-05 | I | Tracking via stable identifiers in advertisements (Bridgefy-class social-graph attack, PROX-03) | AT-3 | n/a | Rotating pairwise tags `HMAC-SHA256(pairKey, "mbt-prox-v1" ‖ epoch15m)[0..8]`; random mDNS instance and Nearby endpoint names per session; no account/phone/device identifiers; random outer ids (P9) | brief §8.5, 0022 | 1.2 | **RQ-17** (OTA capture by owner; golden vectors) |
| T-PRX-06 | I | App-presence disclosure: a fixed mDNS service type or Nearby service identifier tells anyone in range "a Muhabbet user is here" | AT-3 | n/a | **Not addressed by the brief** — see RN-2; the 1.2 DPIA must record the disposition | brief §8.5 | 1.2 | OTA capture |
| T-PRX-07 | I | Traffic analysis by size and timing | AT-3 | n/a | Padmé bucket padding; timing is residual | 0022 | 1.2 | Golden vectors for bucket sizes; residual RR-5 |
| T-PRX-08 | D | A crafted frame crashes the parser or the engine ("single malicious message" class) | AT-3 | n/a | Parse application frames only after the handshake; fuzz targets for every parser; bounded buffers; credit window; `MAX_REASSEMBLY_BYTES` | 0022, brief §6.9 | 1.2 | Fuzz targets in CI |
| T-PRX-09 | D | Connection flooding, battery drain | AT-3 | n/a | Foreground Nearby mode only, 15-min auto-off; only verified contacts admitted after the handshake | 0024 | 1.2 | Device-matrix battery measurement (owner) |
| T-PRX-10 | E | An off-server path is used for a group, channel, community or stranger | AT-2, AT-3 | n/a | `ScopeRule` (1:1 only), `RelationshipRule`, `ConsentRule`, `CertificateRule` | 0015, 0029 R2 | 1.2 | `ScopeRule` unit test |
| T-PRX-11 | R | An off-server message escapes attribution, moderation or blocking | AT-2 | n/a | CLOUD/BYO final only after server seq (`serverOwed`); reports are durable outbox commands; block list in the inbound pipeline for every source | 0010, 0025, 0029 R3–R4 | 1.2 | Engine test; per-source block-list test |
| T-PRX-12 | I | Plaintext on a proximity path | AT-3 | n/a | `SealedLink.send` accepts only `SealedFrame` | 0016 | 1.2 | **RQ-06** |
| T-PRX-13 | I | Co-location disclosed to the contact | AT-2 | n/a | Inherent; both opted in; foreground mode; consent matrix | 0024 | 1.2 | DPIA (proximity trigger) |

### 6.11 Sealed uplink — DF-8 (`UPLINK_RELAY`), AT-2 as mule

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-UPL-01 | I | The mule reads content or learns the recipient | AT-2 | n/a | Per-device E2E envelope inside `HPKE-Seal(serverUplinkPk, …)`; bucket padding; no stable recipient hash anywhere (C7) | 0025, 0022 | 1.3 | 1.3 exit test: the mule store holds only opaque blobs |
| T-UPL-02 | S | The mule forges a bundle as the sender | AT-2 | n/a | Sender-device signature inside the HPKE plaintext, verified by the server before idempotent ingest | 0025 | 1.3 | 1.3 exit: server attributes to the true sender device; see RN-1 |
| T-UPL-03 | T | The mule modifies a bundle | AT-2 | n/a | HPKE AEAD (`aad = bucketSize`) | 0022 | 1.3 | Golden vector |
| T-UPL-04 | D | The mule drops or delays a bundle | AT-2 | n/a | Sender keeps the outbox entry with `serverOwed`; UI shows clock + "handed to <contact>"; no delivered tick until the server/recipient acks | 0013, 0025 | 1.3 | Engine test |
| T-UPL-05 | D | Abuse of mule storage or `/uplink` | AT-2, AT-8 | n/a | Per-mule daily quota and size cap (enforced by mule and server); per-sender upload quota; ≤ 50 bundles / 10 MB per mule, 24 h; FIFO-by-expiry | 0025 | 1.3 | 1.3 exit quota test |
| T-UPL-06 | T | The mule replays a bundle | AT-2 | n/a | Server idempotency on `(sender_device_id, client_message_id)` | 0013 | 1.3 | Test |
| T-UPL-07 | I | Uplink private key compromised | AT-4, AT-6 | n/a | Not on the CI/production host; ADR-0031 in force first; published rotation; clients pin current and next keys (content stays E2E inside) | 0025, 0031 | 1.3 | Runbook inspection; R14 |
| T-UPL-08 | I | The mule learns that the sender is offline, when, and the bucket size | AT-2 | n/a | Inherent; contacts only; disclosed; DPIA | 0025 | 1.3 | DPIA (uplink trigger); residual RR-5 |

### 6.12 Own-device custody — DF-9

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-OWN-01 | I | A lost tablet holds bundles for the user's other devices | AT-7 | n/a | Separate SQLCipher `custody.db` with its own key and quota; contents are per-device E2E envelopes; expiry; teardown and device revocation wipe it | 0020, 0025 | 1.3 | Teardown test |
| T-OWN-02 | S | An attacker device poses as one of the user's own devices | AT-3 | n/a | Certificate binds `userId`/`deviceId`/`linkKey`; same handshake as T-PRX-01 | 0018 | 1.3 | Handshake test |

### 6.13 External RF — DF-10 (`EXTERNAL_RF`)

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-RF-01 | E (legal) | Encrypted traffic over *telsiz* exposes users and the owner to Law 5809 Art. 39 / 63(6) | AT-5 | n/a | Module excluded from TR release flavours **by Gradle** until a written opinion (Q-L2) | 0026 | 1.4 | Build inspection: the TR artefact does not contain the module |
| T-RF-02 | D (legal) | Duty-cycle breach (Gemini's `delay(200)` ≈ 97% at SF12) | — | n/a | `AirtimeLedger`: sliding hour per sub-band (1% 868.0–868.6 MHz; 10% 869.4–869.65 MHz) | 0026 | 1.4 | **RQ-19** (ledger tests + one-hour field log) |
| T-RF-03 | I | Airtime and frame timing visible to any receiver | AT-3 | n/a | Fixed-size frames; rotating tags; residual | 0026, 0022 | 1.4 | Field log; residual RR-5 |
| T-RF-04 | S | Peer impersonation | AT-3 | n/a | As T-PRX-01 | 0018 | 1.4 | As T-PRX-01 |

### 6.14 Certificates and CRL — PR-6, DS-8, DF-11

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-PKI-01 | S, E | Theft of the online intermediate lets an attacker mint certificates for arbitrary devices | AT-4, AT-6 | n/a | Offline root pinned with a rotation list; 90-day intermediate; signer never on a host that runs PR jobs; ADR-0031 in force first; runbook | 0018, 0031 | 1.2 | Inspection; R14 |
| T-PKI-02 | D | CRL deltas suppressed for an offline victim | AT-3 | n/a | Bounded by the 7-day certificate expiry | 0018 | 1.2 | **RQ-18** |
| T-PKI-03 | E | A certificate is issued to a banned or under-age account | AT-8 | n/a | Issuance requires valid age attestation and no active ban | 0018, 0029 | 1.2 | Test |
| T-PKI-04 | R | Issuance by an insider leaves no externally checkable trace | AT-4 | n/a | Residual; key transparency is research | 0018 (7) | — | Residual RR-1 |

### 6.15 Production host and server data stores — DS-4…DS-9, TB-9

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-HST-01 | E | Fork/branch PR code runs on the production host; docker-group membership equals root | AT-6 | Open (CICD-01) | No `pull_request` job on the production host; PR workflows only on a non-production runner or disabled | 0031 | Phase 0 | Phase 0 exit: workflow inspection + a test PR |
| T-HST-02 | I | Every `.env.prod` value is in public history | AT-1 | Open (CICD-06) | Rotate all; secret scanning and push protection | 0031 | Phase 0 | As T-SRV-04 |
| T-HST-03 | I | A DB dump yields plaintext phone numbers, the contact graph and (pre-1.0) content; post-1.0 metadata | AT-4, AT-5 | Open by design until 1.0 | 1.0 removes content for E2E conversations; metadata is a written residual (ADR-0019) | 0019, 0021 | 1.0 | **RQ-13** |
| T-HST-04 | E, I | A co-tenant project on the same host reaches `shared-postgres`/`shared-redis`/`shared-minio` | AT-4 | Open (SAD TC-9) | **Not addressed by the brief** — see RN-3 | — | — | — |
| T-HST-05 | R | Legal requests and privileged access leave no record | AT-4, AT-5 | Open | Legal-request log and transparency counters in the compliance module | brief §4.2 | 0.4+ | Inspection |
| T-HST-06 | I | Tokens, OTPs or content in logs | AT-4 | Partial | JWT out of query strings; observability rules (SAD §8.5) | 0022 | 0.4 | Log inspection |
| T-HST-07 | D | One host at ~92% disk carries production and CI | environment | Open (R19) | CI, LiveKit and signer off the host | 0031, 0023 | Phase 0 / 0.7 | `df` trigger in R19 |

### 6.16 CI/CD and supply chain — PR-7, DF-13, TB-5

| ID | Cat | Threat | AT | Today | Mitigation | ADR | MS | Verification |
|---|---|---|---|---|---|---|---|---|
| T-SCM-01 | I | Signing key and Play credential are repo-level secrets, readable by any same-repo branch PR; `DEBUG_KEYSTORE` written into the persistent runner home | AT-6 | Open (CICD-02, MISS-03 CI/CD) | `release` environment restricted to tags on `main` with a required reviewer; production environment verified | 0031 | Phase 0 | Environment-settings inspection |
| T-SCM-02 | T | No action SHA-pinned; `trivy-action@master` ran on the production-host runner during the March 2026 compromise window | AT-6 | Open (CICD-05) | SHA-pin every action; remove `@master` | 0031 | Phase 0 | Workflow inspection |
| T-SCM-03 | S | Unknown runner `muhabbet-mordecai` with identical labels receives arbitrary jobs | AT-6 | Open (CICD-03) | Identify or deregister | 0031 | Phase 0 | Runner list inspection |
| T-SCM-04 | E | Deploy `workflow_run` fires for a PR-triggered run whose head branch is named `main` (fork) | AT-6 | Open (MISS-01 CI/CD) | Deploy only the CI-built artefact/SHA of a push on `main`; PR jobs off the host | 0030, 0031 | Phase 0 / process | Workflow inspection |
| T-SCM-05 | T | What ships is not what was tested: deploy rebuilds from `git pull`; mobile release builds twice | AT-6 | Open (CICD-07, CICD-10) | Build once → launch that exact APK/AAB on `emulator-5554` → promote the same artefact; deploy the CI-built image | 0030 | process | Release checklist with artefact hash |
| T-SCM-06 | T | Dependency confusion or tampered dependency (JitPack global, no verification metadata) | AT-6 | Open (CICD-13, KMP-15) | **No ADR decides this** — see RN-5 | — | — | — |
| T-SCM-07 | I | New secrets committed; Gitleaks never runs on the integration branch | AT-6 | Open (MISS-01 governance) | Gitleaks on every PR without a paths filter; push protection | 0031 | Phase 0 | A seeded test secret is blocked |
| T-SCM-08 | R | Agents commit as the owner; zero reviews on 231 merged PRs | AT-6 | Open (GOV-06) | Agent identity; owner approves agent PRs | 0032 | process | Ruleset inspection |
| T-SCM-09 | T | An agent writes code before design approval | agent | Open | Design-gate hook (a guard for agents, **not** a security boundary) | 0033 | process | Hook test: edit to `backend/**` refused while `APPROVAL.md` is unsigned |

### 6.17 Legal compulsion (AT-5) — what can be produced, per era

| Demand | Pre-1.0 (today) | 1.0+ E2E conversations | Channels/communities | Stance / control | Counsel |
|---|---|---|---|---|---|
| Content disclosure | All server-stored content and media | None readable: envelopes are ciphertext, deleted on device ack or TTL (30 d default); history cutover per Q-O4 | Plaintext by design, labelled so | Do not claim more than the era delivers (claims register, RQ-20) | Q-L1, Q-L4 |
| Metadata / traffic data | Sender, recipients, timestamps, sizes, receipts, IP in logs | Same metadata (ADR-0019); receipts stay server-visible | Same | Retention to be set by counsel; LOCAL_ONLY blocked until Q-O3/Q-L9 | Q-L9 |
| Interception cooperation / *işletmeci* duties | Technically possible (plaintext) | Conflicts with E2E (R5) | Possible | Opinion before 1.0; never weaken E2E silently | Q-L1 |
| Key deposit (2010 regulation) | — | Would defeat E2E | — | Never accepted silently; product-level decision (LEG-03) | Q-L2 |
| Takedown | Server removal | Reporter-supplied content only (R18) | Central (ADR-0029 R2) | Franking is research | Q-L4 |
| Identify a user | Phone number, device registry | Same | Same | Phone-OTP identity is the subscriber identity (ADR-0018) | Q-L1, Q-L4 |
| Ghost device / key swap | n/a | Detectable by contacts (T-KEY-04) | n/a | Visible by design | — |
| Device seizure | DB readable if unlocked | Same after first unlock (T-DEV-04) | — | Residual RR-6 | — |
| Hosting in Turkey | Hetzner, Germany | Same | Same | Planned before the 1 M daily-access threshold | Q-L4, Q-L6 |

---

## 7. LINDDUN privacy threats

| ID | Category | Threat | Flows / scope | Mitigation | ADR | MS | Verification | Residual |
|---|---|---|---|---|---|---|---|---|
| L-01 | Linking | Outer frame ids that are stable or time-bearing (UUIDv7) link frames across carriers | DF-6…DF-10 | 16 random bytes on the outer wire; UUIDv7 only inside E2E | 0022 (P9) | 1.2 | Golden vectors; **RQ-17** | — |
| L-02 | Linking | Rotating tags are linkable within one 15-min epoch | DF-6, DF-7 | Epoch rotation; random per-session names | brief §8.5 | 1.2 | OTA capture | Within-epoch linkability (RR-5) |
| L-03 | Linking | Contact discovery links phone numbers to accounts | DF-2 | Caps, block filter, discoverability reader | brief §8.6 | 0.4 / 0.6 | **RQ-12** | Matching remains server-side; OPRF research |
| L-04 | Linking | The same device is linkable between server and radio identities | DF-6, DF-7 | Separate link key (domain separation); in-session rendezvous secret preferred so the server never learns the pair key | 0018, brief §8.5 | 1.2 | Inspection | — |
| I-01 | Identifying | Phone number is the account identity, stored in plaintext server-side | DS-4 | Disclosed honestly (GOV-08 fix); access control (TB-9) | brief §8.6 | Phase 0 | Inspection | Inherent |
| I-02 | Identifying | IP address identifies a subscriber to a call peer or telemetry | DF-5 | Relay-only for non-contacts; telemetry without IPs | 0023 | 0.7 / 1.1 | **RQ-15** | IPs visible to our TURN (DPIA) |
| I-03 | Identifying | Advertisements carry account/phone/device identifiers | DF-7 | None carried | brief §8.5 | 1.2 | **RQ-17** | — |
| Nr-01 | Non-repudiation | Signed messages, receipts or hash chains create durable proof of what a user said | all | No long-term signatures on messages or receipts; Merkle-DAG rejected (C5) | 0013, 0014 | — | Inspection | **Uplink sender-device signature — see RN-1** |
| Nr-02 | Non-repudiation | Server-side device attribution (5651) | DF-1 | Deliberate: attribution is a server record, not a cryptographic proof a third party can verify | 0029 | 0.4 | Inspection | Accepted (legal duty) |
| D-01 | Detecting | Wake timing reveals activity to Google/Apple | DF-3 | Content-free hint; collapse key | 0019 | 0.5 | **RQ-07** | Timing (RR-3) |
| D-02 | Detecting | Proximity advertising reveals presence of a user / of the app | DF-6, DF-7 | Foreground only; rotating tags | 0024 | 1.2 | OTA capture | App presence (RN-2) |
| D-03 | Detecting | A mule learns the sender lacks connectivity | DF-8 | Contacts only; disclosed | 0025 | 1.3 | DPIA | Accepted |
| D-04 | Detecting | Read receipts are server-visible | DF-1 | `readReceiptsEnabled` enforced server-side (#377) | 0019 | 1.0 | Test of the downgrade | Accepted trade |
| D-05 | Detecting | LoRa airtime reveals activity | DF-10 | Fixed-size frames | 0026 | 1.4 | Field log | Accepted |
| DD-01 | Data disclosure | Server plaintext (pre-1.0) | DF-1, DF-2, DS-4 | 1.0 E2E | 0019, 0021 | 1.0 | **RQ-13** | Channels/communities plaintext by design |
| DD-02 | Data disclosure | Content in push | DF-3 | Wake-only | 0019 | 0.5 | **RQ-07** | — |
| DD-03 | Data disclosure | Device at rest | DS-1 | SQLCipher, teardown | 0020 | 0.4 / 0.5 | **RQ-08** | RR-6 |
| DD-04 | Data disclosure | Server metadata after 1.0 | DS-4 | Written down as a trade (ADR-0019) | 0019 | 1.0 | Inspection | RR-2 |
| DD-05 | Data disclosure | ICE telemetry | DF-5 | No IPs | 0023 | 0.7 | Schema inspection | — |
| DD-06 | Data disclosure | Receipt metadata retained for LOCAL_ONLY | DS-4 | LOCAL_ONLY blocked until Q-O3 and Q-L9 | 0021, 0025 | 1.0+ | Owner decision | Open |
| U-01 | Unawareness | False claims (padlock without E2E; "KVKK-compliant"; phone hash as privacy) | UI, docs | Claims register; honest states; `SECURITY.md` fix | 0016, 0029 | Phase 0 / 1.0 | **RQ-20** | — |
| U-02 | Unawareness | User believes an off-server message is final | UI | "Not yet synced" marker; "handed to …" label; no relayed tick | 0013 | 1.2 / 1.3 | UI inspection | — |
| U-03 | Unawareness | Users unaware of SFU media access, ≤ 7-day revocation window, OS prompts in Nearby mode | UI | SFU disclosure; transparency text (Q-O6); consent matrix | 0023, 0029, 0024 | 0.7 / 1.2 | Inspection | — |
| U-04 | Unawareness | A contact carries others' data without realising it | DF-8 | Relay participation is a mode the user enters and leaves (brief §9 "leaving relay mode wipes the relay store"); DPIA confirms explicit opt-in and disclosure | 0025 | 1.3 | DPIA | — |
| Nc-01 | Non-compliance | Consent used as the systematic KVKK Art. 9 transfer basis (`docs/legal/03`) | DS-4 abroad | Legal-doc corrections | 0029 | Phase 0 / 0.4 | Inspection; Q-L6 | — |
| Nc-02 | Non-compliance | Soft delete presented as erasure; media survives deletion | DS-4, DS-6 | `SessionTeardown` on device; #327 open server-side | 0020 | 0.4 / open | **RQ-08**; #327 | #327 |
| Nc-03 | Non-compliance | Under-15 users served | all | Age gate; certificate precondition | 0029 | 0.4 / 1.2 | **RQ-10** | R7 |
| Nc-04 | Non-compliance | Public "KVKK-compliant" claims while erasure is open | docs | Remove claims (SSOT-09) | 0029 | Phase 0 | Inspection | — |
| Nc-05 | Non-compliance | Retention duties conflict with LOCAL_ONLY delete-on-delivery | DS-4 | Q-L9 before LOCAL_ONLY | 0021 | 1.0+ | Opinion | Open |
| Nc-06 | Non-compliance | Relaying users hold third-party data with no defined KVKK role | DF-8, DF-9 | Contacts-only, sealed opaque blobs, quotas, DPIA, opinion Q-L5 | 0025, 0029 | 1.3 | Opinion + DPIA | Open |

---

## 8. Verification index

| Requirement (brief §16.1) | Threats it closes |
|---|---|
| RQ-02 exactly-once | T-SRV-06, T-UPL-06 |
| RQ-04 total order | T-SRV-07 |
| RQ-05 hint loss self-heals | T-SRV-07, T-SRV-14, T-PSH-04 |
| RQ-06 no plaintext on non-server paths | T-E2E-01, T-DEV-05, T-PRX-12 |
| RQ-07 push without content | T-PSH-01, DD-02, D-01 |
| RQ-08 logout/deletion leaves no readable data | T-DEV-01, T-DEV-02, T-DEV-03, DD-03, Nc-02 |
| RQ-09 sessions die on revoke ≤ 5 s | T-SRV-02 |
| RQ-10 under-15 cannot register | T-REG-02, Nc-03 |
| RQ-11 prekeys claimed at most once; no silent identity swap | T-KEY-01, T-KEY-03 |
| RQ-12 enumeration throttled | T-DIR-01, L-03 |
| RQ-13 server holds no E2E plaintext | T-SRV-09, T-HST-03, DD-01 |
| RQ-14 calls connect | T-CAL-02 (disclosure path) |
| RQ-15 non-contacts never see IP | T-CAL-01, I-02 |
| RQ-17 no stable identifiers on air | T-PRX-05, L-01, I-03 |
| RQ-18 revoked devices refused off-server ≤ 7 d | T-PRX-02, T-PKI-02 |
| RQ-19 duty cycle respected | T-RF-02 |
| RQ-20 every privacy claim true | T-E2E-04, U-01, Nc-04 |
| Phase 0 exit (old JWT fails; no PR job on cx43) | T-SRV-04, T-HST-01, T-HST-02 |

Threats verified by a named test or inspection rather than an RQ are listed in their own rows in §6.

### 8.1 Threats that must be closed per milestone exit

| Milestone | Must be *Mitigated* (not merely planned) |
|---|---|
| Phase 0 | T-SRV-04, T-HST-01, T-HST-02, T-SCM-01, T-SCM-02, T-SCM-03, T-SCM-07, T-DIR-03 |
| 0.4 | T-SRV-01, T-SRV-02, T-SRV-03, T-SRV-05, T-SRV-06, T-SRV-08, T-SRV-12, T-KEY-01, T-KEY-02, T-DIR-01 (cap), T-DIR-02, T-REG-02, T-DEV-01, T-DEV-03 |
| 0.5 | T-SRV-07, T-SRV-10 (hello), T-SRV-14, T-PSH-01, T-DEV-02, T-DEV-05, T-E2E-01 (fallback deleted) |
| 0.6 | T-KEY-03, T-KEY-05, T-DIR-01 (discoverability reader) |
| 0.7 | T-CAL-01 (relay policy), T-CAL-02 (disclosure), T-CAL-03, T-CAL-04, T-CAL-06 |
| 1.0 | T-SRV-09, T-SRV-10 (minimum version), T-KEY-04 (visibility), T-E2E-*, T-MED-01, T-MED-02, T-HST-03 (content) |
| 1.2 | T-PRX-*, T-PKI-01…03, T-MED-04 |
| 1.3 | T-UPL-*, T-OWN-* |
| 1.4 | T-RF-* |

---

## 9. Residual risks requiring explicit owner acceptance

Each is recorded as a condition in the relevant scope block of [`APPROVAL.md`](APPROVAL.md) or as a
row in the risk register. None may be claimed as "solved" in user-facing text.

| ID | Residual | Why it remains | Disclosure | Linked |
|---|---|---|---|---|
| RR-1 | The server is in the identity TCB: it can add a ghost device or issue a certificate; contacts detect it only through a safety-number change | Owner decision 1; key transparency without independent auditors would be false assurance | Safety-number UX; transparency text | A-4, T-KEY-04, T-PKI-04 |
| RR-2 | Server-visible metadata after 1.0 (sender/recipient devices, seq, sizes, timestamps, receipts) | Needed for mailbox deletion and #377 enforcement | Aydınlatma metni | ADR-0019, DD-04 |
| RR-3 | Wake timing visible to Google/Apple | Push is the only wake path on mobile OSes | Aydınlatma metni | D-01 |
| RR-4 | ≤ 7-day offline revocation window | Offline peers cannot learn of revocation sooner | Transparency text (Q-O6) | R15, T-PRX-02 |
| RR-5 | Size-bucket and timing analysis on radio and uplink paths | Padding hides size, not timing | DPIA | T-PRX-07, T-UPL-08, T-RF-03 |
| RR-6 | DB key available after first unlock | Background delivery | Settings help text | T-DEV-04 |
| RR-7 | SIM-swap account takeover | Phone-OTP identity | Safety-number change | T-REG-01, RN-4 |
| RR-8 | Test-number OTPs in production logs | Documented operational practice | Internal only | T-REG-04 |

---

## 10. Maintenance

This model is updated (and re-signed by the owner) when any of the following happens:

1. A new path scope, adapter or custodian is introduced (ADR-0015 / ADR-0025).
2. A new processor, foreign host or telemetry field (also a DPIA trigger, ADR-0029 rule 6).
3. A wire-protocol version bump (ADR-0022).
4. A change to key management, certificates or the signer placement (ADR-0018, Q-O7).
5. A counsel opinion that changes a legal threat's disposition.
6. Before each milestone's entry gate in brief §13 that lists this document.

---

## 11. Evidence

- Findings: BE-02, BE-03, BE-06, BE-07, BE-08, BE-09, BE-12, BE-13, BE-14, BE-15, BE-16, BE-17;
  MOB-01, MISS-02 (mobile); KMP-01, KMP-11, KMP-15; C1, C5, C7, C8, C9, C10, C13, M1, M2, M3;
  PROX-02, PROX-03, PROX-06, MISS-01 (proximity); MISS-2 (networking), NAT-4; RF-07, RF-08, LEG-01,
  LEG-02, LEG-03, LEG-04, MISS-01/MISS-04 (legal); CICD-01, CICD-02, CICD-03, CICD-05, CICD-06,
  CICD-07, CICD-10, CICD-13, MISS-01 and MISS-03 (CI/CD); GOV-06, GOV-08, MISS-01 and MISS-02
  (governance), MISS-03 (governance); SSOT-09, FT-01, GEM-08, GEM-14.
- External sources (all from the fact-check digests):
  - Bridgefy attacks (tracking, impersonation, single-message DoS): <https://eprint.iacr.org/2021/214>,
    <https://www.usenix.org/conference/usenixsecurity22/presentation/albrecht>
  - Contact-discovery enumeration: <https://eprint.iacr.org/2020/1119>
  - Sealed sender: <https://signal.org/blog/sealed-sender/>
  - PQXDH: <https://signal.org/docs/specifications/pqxdh/>
  - Safety numbers: <https://signal.org/blog/safety-number-updates/>
  - Nearby Connections: <https://developers.google.com/nearby/connections/overview>
  - NAT behaviour: <https://www.rfc-editor.org/rfc/rfc4787.html>
  - Actions hardening: <https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions>
  - Trivy action compromise: <https://www.aquasec.com/blog/trivy-supply-chain-attack-what-you-need-to-know/>
  - Laws 5651, 5809, 6698: <https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5651.pdf>,
    <https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5809.pdf>,
    <https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf>

---

## 12. Reviewer notes

> **Reviewer note (open) RN-1 — uplink signature vs. deniability.** Brief §8.3 says "no long-term
> signatures on messages (deniability, erasure)", while ADR-0025 / brief §10 put a
> `senderDeviceSignature` inside every `UplinkBundle` so the server can attribute it (5651). The
> brief does not say which key signs, over what, or whether the server keeps the signature. If it is
> the device identity key and the server stores it, the server holds a third-party-verifiable proof
> of authorship for uplinked messages only — a non-repudiation asymmetry between paths (LINDDUN
> Nr-01). Proposed for the 1.3 ADR revision: sign with a key whose only purpose is uplink
> authentication, verify at ingest, **do not persist** the signature, and record attribution as for
> any server-path send. Needs crypto review at 1.3 entry.

> **Reviewer note (open) RN-2 — app-presence disclosure on LAN/Nearby.** Brief §8.5 randomises
> instance and endpoint names and rotates tags, but a DNS-SD service type and a Nearby service
> identifier are fixed per app by construction. Anyone on the same LAN or in BLE range can learn
> "a Muhabbet user is here", which P9 ("no stable identifiers outside TLS-to-server") does not
> allow on its face. Proposed: the 1.2 spike measures what each OS exposes, the 1.2 DPIA records the
> disposition, and P9 is either reworded ("no stable *user or device* identifiers") or a mitigation
> is specified. The brief should decide which.

> **Reviewer note (open) RN-3 — co-tenant data stores.** The brief's threat surface does not cover
> TB-9: `shared-postgres`, `shared-redis` and `shared-minio` serve other projects on the same host
> (SAD TC-9), so a compromise of a neighbouring project reaches Muhabbet data without crossing TB-1.
> ADR-0031 removes PR jobs from the host but not co-tenancy. Proposed: add a risk-register row and
> record the current isolation (separate DB roles, buckets, Redis ACLs/DB numbers) in the 1.0
> crypto/security review; any change is an owner decision.

> **Reviewer note (open) RN-4 — account takeover.** The only control against SIM-swap/OTP takeover
> is the new-device safety-number change at 1.0. The brief does not say whether a successful OTP on
> a new handset revokes the victim's existing devices, nor whether a registration lock is wanted.
> Proposed as an owner question for 1.0 (not a design change here).

> **Reviewer note (open) RN-5 — dependency integrity.** CICD-13 and KMP-15 (no Gradle dependency
> verification, JitPack in global resolution, no `distributionSha256Sum`) are supply-chain threats
> to the very builds that will carry libsignal, but neither ADR-0031 nor any other ADR decides them,
> and the brief lists only SHA-pinning of actions. Proposed: add dependency verification, wrapper
> validation and `exclusiveContent` for the Signal repository to ADR-0031 before 1.0 (the Signal
> repository is the only source for libsignal versions above 0.86.5, ADR-0017).
