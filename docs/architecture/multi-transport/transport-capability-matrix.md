# Muhabbet transport capability matrix

> **Status: Proposed — pending owner approval.** No code may be written for any scope described here
> until the owner records approval for that scope in [`APPROVAL.md`](APPROVAL.md). This document is
> derived from, and must agree with, the [decision brief](decision-brief-2026-11-12.md) (the single
> source of truth), in particular ADR-0015 (path scopes), ADR-0023 (internet-direct), ADR-0024
> (proximity), ADR-0025 (custody), ADR-0026 (Meshtastic), ADR-0027 (rejected transports), §12 and the
> 1.2 entry gate in §13. Where this matrix and the brief disagree, the brief wins; known gaps are
> recorded as reviewer-note callouts.

| | |
|---|---|
| **Date** | 2026-11-12 |
| **Base** | `dev` @ 2434324 (branch `claude/muhabbet-p2p-architecture-wdan5r`) |
| **Role** | The data source for the `PathPolicy` rules (ADR-0015) and the only place a range, throughput, platform-pair or background claim may be taken from for UI copy, store listings or docs. |
| **Rule** | **A device-dependent cell stays `UNMEASURED` until the owner has observed it** on a physical device or `emulator-5554` (P13, ADR-0032). An agent may fill the *documentation* columns; it may never mark a device cell as passed. |
| **Evidence** | [`docs/reviews/2026-11-12-findings-register.md`](../../reviews/2026-11-12-findings-register.md). IDs `PROX-*`, `NAT-*`, `P2P-*`, `QUIC-*`, `ICE-1`, `LAN-1`, `LINK-1`, `LIB-1`, `RF-*`, `LEG-*`, `KMP-*`, `MOB-*`. Area-qualified verifier misses are written `PROX MISS-01`, `NET MISS-1` etc., because `MISS-*` ids repeat per area. |

---

## 1. Evidence marks

Every cell carries exactly one mark, under these three conventions:

1. **Row mark.** Where a table has a `Mark` or `Sources` column holding a single mark, that mark applies
   to every cell of the row that carries none of its own.
2. **Composite cell.** A cell stating two distinct facts (platform vs app, or one fact per side) uses
   the form `platform: MARK / app: MARK` (or `A: MARK; i: MARK`); each part carries exactly one mark.
   Two marks on one undivided fact are not allowed.
3. **Design decisions** are marked **DECIDED (source)**, never with a bare "brief".

| Mark | Meaning | Who may set it |
|---|---|---|
| **VERIFIED-DOC** | Matches primary vendor/standards documentation that an adversarial verifier re-fetched in the 2026-11-12 review (register `Verify` = confirmed / partially-confirmed, or a verifier-found miss with its own source). | Any author, citing the finding ID |
| **VERIFIED-REPO** | True of this repository at `dev` @ 2434324, with file:line. | Any author |
| **VERIFIED-DEVICE** | Observed on a named device by the owner, with date, build and "observed by / where". **No cell has this mark today.** | Owner only |
| **UNVERIFIED** | A reviewer claim not re-fetched by the verifier, a single community report, an inference, or general platform knowledge not checked in this review. | Any author; must say why |
| **UNMEASURED** | Device-dependent; to be filled by the 1.2 device-matrix spike (§13) or a milestone exit. | Owner (fills it) |
| **BLOCKED (x)** | Cannot be measured or shipped until prerequisite *x* is met. | Any author |
| **NO (doc)** | Documented as impossible or unavailable; VERIFIED-DOC by construction. | Any author, citing the finding |
| **N/A** | Not a capability of this scope by design (ADR). | Any author, citing the ADR |
| **DECIDED (ADR-nnnn / brief §x)** | A design decision, not an empirical fact; true by decree until the ADR changes. | Any author, citing the ADR or brief section |

**Global rule for iOS cells.** The iOS app cannot launch today (KMP-01: no entry point, no Xcode
project, iOS DI never wired). Every `i↔i` and `A↔i` *Muhabbet* capability is therefore
**BLOCKED (KMP-01, 0.6 gate)** in addition to whatever the platform documentation says. Platform facts
in those columns describe the OS, not the app. If KMP-01 is not fixed at the 0.6 gate, 1.0 ships
Android-only (owner decision D2, 2026-11-12). The i↔i and A↔i columns then stay BLOCKED (D2), the UI
and store text say Android-only, and only a later ADR can re-open them.

---

## 2. Scopes, interfaces and adapters

### 2.1 Interfaces are not scopes (ADR-0015, LINK-1)

| Interface | Scope(s) it can carry | Chosen by | Note | Mark |
|---|---|---|---|---|
| Wi-Fi with internet | `SERVER_RELAY`, `IP_RELAYED`, `IP_DIRECT`, `LAN` | OS default route; ICE gathers all interfaces | — | VERIFIED-DOC (LINK-1, `ConnectivityManager`) |
| Ethernet / USB-C adapter | same as Wi-Fi | OS | "just another IP interface" | VERIFIED-DOC (LINK-1) |
| Cellular 4G/5G | `SERVER_RELAY`, `IP_RELAYED`, `IP_DIRECT` | OS / ICE | IPv6 ~22 % overall, ~1 % on TT Mobil | VERIFIED-DOC (NAT-3, https://stats.labs.apnic.net/ipv6/TR) |
| Starlink / LEO broadband | `SERVER_RELAY` (+ ICE scopes) | OS | ordinary IP; may set `constrained` | DECIDED (brief §12 #65) |
| Satellite direct-to-cell / NTN | `SERVER_RELAY` with `constrained = true` | carrier / OS | not a transport; no TR D2C data service | VERIFIED-DOC (RF-06) |
| Wi-Fi without internet (shared AP) | `LAN` | — | fails on client-isolating APs (§13.4 scenario X3) | UNMEASURED |
| BLE / Wi-Fi P2P radios | `PROXIMITY` (via Nearby Connections only) | the library | no custom BLE/Wi-Fi Direct adapter (ADR-0024) | DECIDED (ADR-0024) |
| LoRa via external node | `EXTERNAL_RF` | — | 1.4, TR-excluded | DECIDED (ADR-0026) |

### 2.2 Adapter catalogue

| Scope | Adapter | Port | Module | Milestone | Status at `dev` @ 2434324 | Mark |
|---|---|---|---|---|---|---|
| `SERVER_RELAY` | `WsServerLink` + `WsInboundSource` (strangled `WsClient`) | `ServerLink`, `InboundSource` | `:mobile:composeApp` | 0.5 | `WsClient` in production on Android | VERIFIED-REPO (`data/remote/WsClient.kt`) |
| `SERVER_RELAY` | `RestChangeFeedSource` | `InboundSource`, `ChangeFeedPort` | `:mobile:composeApp` | 0.5 | `/messages/since` by device clock (MOB-10); `/changes` not built | VERIFIED-REPO (`MessageRepository.kt:193-198`) |
| `SERVER_RELAY` | `FcmHintSource` / APNs later | `InboundSource` | `:mobile:composeApp` | 0.5 / iOS | FCM carries content today (BE-15); APNs absent | VERIFIED-REPO |
| `SERVER_RELAY` | media HTTP upload (BULK) | — (media worker) | `:mobile:composeApp` | existing → 0.5 resumable | single multipart POST, server cap 100 MB | VERIFIED-REPO (`application.yml:61`) |
| `IP_RELAYED` | `LiveKitSession` (SFU + embedded TURN/TLS 443) | `LiveSessionPort` | `:mobile:composeApp` | 0.7 | `livekit-android:2.28.0` linked; **no server deployed, calls have never worked** (#367–#373) | VERIFIED-REPO (`build.gradle.kts:115`), NET MISS-1 |
| `IP_DIRECT` | `WebRtcP2PSession` (1:1 calls only) | `LiveSessionPort` | `:mobile:composeApp` | 1.1 (gated) | none | VERIFIED-REPO (absent: no WebRTC peer-connection code in `mobile/`) |
| `LAN` | `LanLink` (mDNS/DNS-SD + length-prefixed TCP + `LinkAuthenticator`) | `SealedLink`, `InboundSource` | `:mobile:transport-lan` | 1.2 (gated) | none | VERIFIED-REPO (no proximity code, PROX-11) |
| `PROXIMITY` | `NearbyLink` (Google Nearby Connections; iOS Swift package only if the spike passes) | `SealedLink`, `InboundSource` | `:mobile:transport-nearby` | 1.2 (gated) | none | VERIFIED-REPO (PROX-11) |
| `UPLINK_RELAY` | `UplinkHandover` (hop rides `LanLink`/`NearbyLink`; mule POSTs `/api/v1/uplink`) | `SealedLink`, `CustodyTarget` | `:mobile:messaging` + transports | 1.3 (gated, droppable) | none | VERIFIED-REPO (absent, PROX-11) |
| (custody) | `OwnDeviceCustody` (rides `LanLink`/`NearbyLink` between one user's devices) | `CustodyTarget` | `:mobile:messaging` | 1.3 (gated) | no custody code; multi-device linking exists default OFF (ADR 0007, V18) | VERIFIED-REPO (absent; V18 present) |
| `EXTERNAL_RF` | `MeshtasticLink` + `AirtimeLedger` | `SealedLink` | `:mobile:transport-meshtastic` (excluded from TR flavours) | 1.4 (gated, droppable) | none | VERIFIED-REPO (absent) |

### 2.3 Declared `LinkCapabilities` (ADR-0015)

Declared values are design constants; measured values (`LinkMetrics`: RTT, loss, goodput) are
runtime EWMAs and are **never** hard-coded from this document.

| Adapter | `maxFrameBytes` | `supportsLive` | `supportsBulk` | `offServer` | Source |
|---|---|---|---|---|---|
| `WsServerLink` | n/a (`ServerLink` declares no caps; BULK goes over HTTP ≤ 100 MB) | no | via HTTP worker | false | brief §5, §6.7 |
| `LanLink` | 64 KiB per record | no | yes (offset-resume) | true | brief §6.7 |
| `NearbyLink` | the library's BYTES payload limit, **read at runtime** | no | yes (FILE/STREAM payloads) | true | DECIDED (brief §6.7, §10 "MTU negotiated"); the limit value is UNVERIFIED until the spike reads the library constant |
| `UplinkHandover` | inherits the hop link | no | per-bundle cap of ADR-0025 (value open) | true | brief §9 |
| `MeshtasticLink` | 200 B, single frame | no | no | true | brief §6.7, ADR-0026 |

`supportsLive = false` for every sealed link: live media is only `LiveSessionPort` (ADR-0023). A
"live" Nearby-mode session (brief §1.1) means interactive text while both users have Nearby mode open.

---

## 3. Matrix — `SERVER_RELAY`

Both ends talk to the server; the "pair" is the two clients.

| Capability | A↔A | i↔i | A↔i | Sources |
|---|---|---|---|---|
| Discovery (fg / bg) | N/A (server rendezvous) | N/A | N/A | ADR-0010 |
| Receive while app killed | FCM (content in payload today, wake-only from 0.5); OEM throttling **UNMEASURED** (R11) | BLOCKED (KMP-01; APNs not built) | BLOCKED (KMP-01) | VERIFIED-REPO (BE-15), brief ADR-0019 |
| Text | in production; at-most-once outbox and RAM acks until 0.4/0.5 (MOB-03/04) | BLOCKED (KMP-01) | BLOCKED (KMP-01) | VERIFIED-REPO |
| Bulk (media) | HTTP upload ≤ 100 MB; resumable 1 MiB ranges from 0.5 (not built) | BLOCKED | BLOCKED | VERIFIED-REPO (`application.yml:61`) |
| Live | N/A (see `IP_RELAYED`) | N/A | N/A | ADR-0023 |
| Prompts / permissions | Android: notifications permission (`platform/NotificationPermission.kt` exists); no others for messaging — VERIFIED-REPO | iOS: notification authorisation — UNVERIFIED (not built) | A: VERIFIED-REPO; i: UNVERIFIED | `platform/NotificationPermission.kt` |
| Min OS | Android `minSdk 26`, `targetSdk 36` | none defined (no Xcode project) | — | VERIFIED-REPO (`mobile/composeApp/build.gradle.kts:145-150`), KMP-01 |
| Legal gate | Baseline only: OTT classification (LEG-01, 5809 Art. 9(14) → Q-L1), 5651/7578 age gate (LEG-02, ADR-0029, 0.4), KVKK Art. 9 for push (Q-L6) | same | same | DECIDED (ADR-0029) |

`constrained = true` (metered, low-bandwidth, satellite-optimised bearer) restricts the server path to
CONTROL + INTERACTIVE and defers BULK (brief §7 `ConstrainedRule`). Detection source per platform
(metered/low-data flags) is **UNVERIFIED** and is to be specified in the 1.2 `PathPolicy` work.

## 4. Matrix — `IP_RELAYED` (0.7) and `IP_DIRECT` (1.1)

| Capability | A↔A | i↔i | A↔i | Sources |
|---|---|---|---|---|
| Discovery | N/A (WS-signalled) | N/A | N/A | ADR-0023 |
| Text / bulk | N/A — **no messaging over WebRTC data channels** | N/A | N/A | ADR-0023, ICE-1 |
| Live (`IP_RELAYED`, LiveKit SFU + TURN/TLS 443) | UNMEASURED — 0.7 exit: two physical devices, two operators, one TT Mobil (owner) | BLOCKED (KMP-01; iOS LiveKit Swift bridge not built, NET MISS-3, LIB-1) | BLOCKED (same) | brief §13 0.7 |
| Live (`IP_DIRECT`, ICE host/srflx, 1:1, mutual contacts only) | UNMEASURED (1.1 per-ASN A/B) | BLOCKED | BLOCKED | brief §13 1.1 |
| Expected relay share | budget 30–50 % relayed until measured (DECIDED, ADR-0023); hole-punch success is not a planning number (≈63 % birthday estimate, ≈70 % DCUtR in IPFS, ≈0 % EDM↔EDM) — VERIFIED-DOC | — | — | NAT-1 (https://tailscale.com/blog/how-nat-traversal-works), P2P-2 (https://arxiv.org/abs/2510.27500) |
| Turkish CGNAT behaviour | **UNVERIFIED** — no published measurement; ICE telemetry (no IPs) at 0.7 replaces any survey | — | — | NAT-2, NAT-3 |
| Prompts / permissions | microphone (camera for video) — UNMEASURED | iOS microphone/camera usage strings — BLOCKED (KMP-01; not built) | UNMEASURED / BLOCKED (KMP-01) per side | ADR-0023 (call media) |
| Server prerequisite | LiveKit + TURN on a **separate host**, UDP range open, `LIVEKIT_*` in `.env.prod`; none exists — VERIFIED-REPO (absent) | — | — | NET MISS-1, ADR 0009 |
| Legal / privacy gate | `iceTransportPolicy = relay` unless mutual contacts with a conversation; "always relay" setting; SFU disclosure; DPIA for TURN/ICE logging; Q-L6, Q-L8 | same | same | DECIDED (ADR-0023, ADR-0029 rule 6); NET MISS-2 |

## 5. Matrix — `LAN` (`LanLink`, 1.2)

| Capability | A↔A | i↔i | A↔i | Sources |
|---|---|---|---|---|
| Discovery, foreground | `NsdManager` (DNS-SD); reliability problems and Wi-Fi multicast power-save filtering reported, **UNVERIFIED** → UNMEASURED | Bonjour via Network.framework with types declared in `NSBonjourServices`; Local Network prompt — platform fact **VERIFIED-DOC** (PROX MISS-01, tn3179); app UNMEASURED/BLOCKED | UNMEASURED / BLOCKED | LAN-1 (https://developer.apple.com/forums/thread/663875, https://developer.apple.com/news/?id=0oi77447), https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy.md |
| Discovery, background | within Nearby mode (Android `connectedDevice` FGS, ≤ 15 min auto-off): UNMEASURED (X4); outside Nearby mode: N/A by design | N/A: iOS foreground only (ADR-0024, link state L13) | A side within Nearby mode: UNMEASURED (X4) / BLOCKED (KMP-01); otherwise N/A | ADR-0024 |
| Raw multicast | not used (would need Apple's restricted multicast entitlement) — DECIDED (ADR-0024) | not used — DECIDED | — | LAN-1 (https://developer.apple.com/tutorials/data/documentation/bundleresources/entitlements/com.apple.developer.networking.multicast.md) |
| Text (sealed, ≤ 64 KiB records) | UNMEASURED | BLOCKED | BLOCKED | brief §6.7 |
| Bulk (offset-resume) | UNMEASURED | BLOCKED | BLOCKED | brief §6.7 |
| Live | N/A | N/A | N/A | ADR-0023, §2.3 |
| Client-isolating / guest AP | expected **failure** (mDNS and peer TCP dropped) — UNMEASURED | same | same | ADR-0024 reviewer note |
| HMS (no Play services) | expected to work (no Google dependency) — UNMEASURED | — | UNMEASURED | ADR-0024 |
| Prompts / permissions | Android: no runtime prompt expected for NSD + TCP; a local-network permission on newer Android versions is reported (`ACCESS_LOCAL_NETWORK`, Android 17) — **UNVERIFIED** | Local Network prompt (VERIFIED-DOC); `NSLocalNetworkUsageDescription` | both | factcheck-proximity proposals; PROX MISS-01 |
| Min OS | gate API 29: DECIDED (ADR-0024) / NSD API predates `minSdk 26`: UNVERIFIED (not re-fetched) | none defined — VERIFIED-REPO (KMP-01) | — | ADR-0024 |
| Legal gate | Q-L2 (E2E over Wi-Fi/LAN, Art. 39/63(6)); proximity DPIA; G-PROXIMITY | same | same | DECIDED (ADR-0029 rule 7); R6 |

## 6. Matrix — `PROXIMITY` (`NearbyLink`, 1.2)

| Capability | A↔A | i↔i | A↔i | Sources |
|---|---|---|---|---|
| What the library does | BLE discovery, then Wi-Fi bandwidth upgrade — **VERIFIED-DOC** | Swift package: BLE discover/advertise, Wi-Fi LAN (local-network permission), optional Wi-Fi hotspot — **VERIFIED-DOC**; package is open source and marked "not officially supported" (UNVERIFIED wording) | **interop not stated by Google — UNVERIFIED**; spike decides | PROX-06 (https://developers.google.com/nearby/connections/overview, https://developers.google.com/nearby/connections/strategies, https://developers.google.com/nearby/connections/android/get-started, https://developers.google.com/nearby/connections/swift/get-started, https://github.com/google/nearby) |
| Discovery, foreground | UNMEASURED | platform: UNMEASURED / app: BLOCKED (KMP-01) | platform: UNMEASURED / app: BLOCKED (KMP-01) | §13 |
| Discovery, background | within Nearby mode (FGS, ≤ 15 min): UNMEASURED (X4); outside Nearby mode: N/A by design (ADR-0024); platform fact "needs a live process or FGS": VERIFIED-DOC | iOS↔iOS background BLE only via overflow-area UUIDs at degraded rates — VERIFIED-DOC; still N/A by design | **NO (doc)**: a backgrounded iPhone's overflow-area advertisement is not decodable by Android | PROX-02 (Core Bluetooth background guide: https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html, https://developer.apple.com/forums/thread/837516, https://developer.android.com/develop/connectivity/bluetooth/ble/background) |
| Text (BYTES payloads) | UNMEASURED | BLOCKED | BLOCKED | §13 |
| Bulk (FILE/STREAM; Wi-Fi upgrade) | UNMEASURED | BLOCKED; hotspot join requires location authorisation, without which iOS silently rejects the join — VERIFIED-DOC | BLOCKED; iPhone joins the Android peer's hotspot after a system prompt (prompt: UNVERIFIED) | PROX-06, PROX MISS-01 |
| BLE-only throughput reference (not Muhabbet's) | — | measured 1 MB iPhone↔iPhone transfers ≈ 36–138 kbit/s; Apple best case ≈ 197–394 kbit/s with tuning | planning assumption 50–150 kbit/s (PROX-01 recommendation) — UNMEASURED | PROX-01 — VERIFIED-DOC (https://developer.apple.com/forums/thread/89644) |
| Wi-Fi P2P throughput / range | unsourced; Gemini's 50–250 Mbps / 50–200 m are PHY ceilings, not goodput — **UNVERIFIED** | same | same | PROX-08 |
| Live | N/A | N/A | N/A | §2.3 |
| HMS (no Play services) | **expected unavailable — inferred, not measured** (library depends on Play services); the register's verifier notes this was asserted without evidence — UNVERIFIED until the spike's HMS device observes it (P13); HMS share in TR to be measured (G-PROXIMITY) | — | — | PROX-06 verifier note, brief §13 1.2 |
| Prompts / permissions | Android runtime permissions for Bluetooth scan/advertise/connect and nearby Wi-Fi devices, location on older API levels, `connectedDevice` FGS — exact set **required by the library per API level: UNMEASURED** (spike enumerates) | Bluetooth, Local Network (VERIFIED-DOC), Location for hotspot (VERIFIED-DOC), hotspot-join confirmation (UNVERIFIED) | union of both | PROX-02 sources (https://developer.android.com/develop/connectivity/bluetooth/bt-permissions, https://developer.android.com/develop/connectivity/wifi/wifi-permissions), PROX MISS-01 |
| Min OS | gate API 29: DECIDED (ADR-0024) / library minimum: UNVERIFIED | Swift package minimum iOS — **UNVERIFIED** | — | ADR-0024 |
| Legal gate | Q-L2 (E2E over BLE and Wi-Fi Direct via Nearby); proximity DPIA; certificate + verified safety number + both opted in; G-PROXIMITY | same | same | DECIDED (ADR-0024, ADR-0029); R6 |

## 7. Matrix — `UPLINK_RELAY` and own-device custody (1.3)

The hop from sender to custodian rides `LanLink` or `NearbyLink`, so every discovery, prompt and
platform-pair cell **inherits §5/§6**. Only the differences are listed.

| Capability | Own-device custody | Contact uplink (mule) | Sources |
|---|---|---|---|
| Who may hold | the same user's linked devices (ADR 0007, multi-device default OFF) | a **verified** contact's online phone; strangers never | DECIDED (ADR-0025); PROX-03 |
| What is held | normal per-device E2E envelopes | opaque bucket-padded HPKE blob (sealed to the server's uplink key) | DECIDED (brief §9, §10) |
| Text | inherits hop (§5/§6 marks) | inherits hop; mule → server is `SERVER_RELAY` HTTPS POST | DECIDED (brief §9) |
| Bulk | open (ADR-0025 per-bundle cap) | ≤ 50 bundles / 10 MB per mule, 24 h hold | DECIDED (brief §9) |
| Live | N/A | N/A | ADR-0023 |
| Device cells | UNMEASURED (1.3 exit: phone offline, tablet online over LAN) | UNMEASURED (1.3 exit) | brief §13 |
| Legal / entry gate | DPIA; no third-party data — DECIDED | DECIDED: Q-L5 (KVKK role of the mule), written KVKK opinion, external crypto review of the uplink seal, telemetry above the owner's threshold (Q-O5), uplink key off the CI/prod host (ADR-0031) | ADR-0025, LEG-04 |

## 8. Matrix — `EXTERNAL_RF` (`MeshtasticLink`, 1.4)

| Capability | A↔A (via nodes) | i↔i (via nodes) | A↔i (via nodes) | Sources |
|---|---|---|---|---|
| Phone ↔ node link | BLE or serial to a certified Meshtastic node — UNMEASURED | BLE — platform: UNMEASURED / app: BLOCKED (KMP-01) | — | ADR-0026 |
| Text and receipts | ≤ 200 B single frame — UNMEASURED | BLOCKED | BLOCKED | ADR-0026 |
| Bulk / live | **DECIDED: NO** | **DECIDED: NO** | **DECIDED: NO** | ADR-0026, RF-07 |
| Expected rate | single-digit messages per hour at SF12 (RF-07); one 200 B packet ≈ 7.2 s on air — computed in this document with the Semtech time-on-air method used in GEM-20 (SF12, BW 125 kHz, CR 4/5, explicit header, CRC on, LDRO on, 8-symbol preamble) | — | — | RF-07, GEM-20 — UNVERIFIED as a measurement |
| Duty cycle | 1 % on 868.0–868.6 MHz; 10 % on 869.4–869.65 MHz (Meshtastic EU_868 slot), per hour per sub-band; enforced by `AirtimeLedger` | same | same | RF-07 (https://meshtastic.org/docs/configuration/radio/lora); the BTK table read was marked TASLAĞI (draft) → in-force figures **UNVERIFIED** (ADR-0026 reviewer note) |
| Prompts | Bluetooth permissions for node pairing — UNMEASURED | Bluetooth — platform: UNMEASURED / app: BLOCKED (KMP-01) | — | ADR-0026 |
| Legal gate | DECIDED: **compiled out of TR flavours by Gradle** until a written opinion on Law 5809 Art. 39/63(6), the 2010 coded/encrypted communications regulation and KET conformity; certified hardware only | same | same | LEG-03, RF-07, Q-L2, ADR-0026 |

## 9. Watch-list and rejected (no cells are filled for these)

| Item | A↔A | i↔i | A↔i | Disposition | Sources |
|---|---|---|---|---|---|
| Wi-Fi Aware | real on Android 8+, hardware-dependent | iOS 26+, iPhone 12+, after one-time system pairing; background to paired devices possible (unmeasured) | **not available in practice**: needs NAN v4 pairing that Android firmware reportedly lacks — single community report, UNVERIFIED | watch-list (ADR-0027) | PROX-05 (https://developer.android.com/develop/connectivity/wifi/wifi-aware, https://developer.android.com/reference/android/net/wifi/aware/AwarePairingConfig, https://developer.apple.com/tutorials/data/documentation/wifiaware.md, https://hackmd.io/@grjte/cross-platform-wifi-aware), PROX MISS-04 |
| Multipeer / AWDL | NO (doc) | yes (Apple only) | NO (doc) | watch-list | PROX-04, PROX-10 |
| Wi-Fi Direct (custom) | API 29+; no-dialog connect not documented as guaranteed; BLE-assisted R2 pairing on API 36 | NO (doc): no Wi-Fi Direct on iOS | NO (doc) | rejected custom; implicit via Nearby (ADR-0024) | PROX-04 (https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder), PROX MISS-02 |
| Custom BLE GATT / L2CAP CoC | API 29+ for CoC | iOS 11+ | PSM needs a GATT round-trip; 36–140 kbit/s | rejected; reconsidered only if the spike fails on iOS **and** demand is shown | PROX-01, ADR-0024 |
| BLE multi-hop mesh, stranger DTN | — | — | — | rejected | PROX-03 |
| 5G NR sidelink | NO (doc) | NO (doc) | NO (doc) | rejected | PROX-07 |
| Ultrasonic / animated QR | 8–16 B/s | — | — | rejected (static QR for safety numbers instead) | PROX-09 |
| 5G Broadcast / ROM, DVB, RTL-SDR | downlink only, no handset API | — | — | rejected | RF-02, RF-03, RF-05 |
| Amateur VHF/UHF, PMR446 data | — | — | — | rejected permanently | RF-08, RF-09 (PMR446 digital use correction: RF MISS-03) |
| libp2p / DHT / QUIC migration / data-channel messaging | — | — | — | rejected | P2P-1, P2P-3, QUIC-1, ICE-1 |
| Multipath QUIC (RFC 10069) | — | — | — | watch-list | QUIC-2 |

## 10. Platform version prerequisites

| Item | Value | Mark | Source |
|---|---|---|---|
| App `minSdk` / `targetSdk` / `compileSdk` | 26 / 36 / 36 | VERIFIED-REPO | `mobile/composeApp/build.gradle.kts:145-150` |
| Proximity runtime gate (both 1.2 adapters) | API 29 | DECIDED (ADR-0024) | ADR-0024 |
| BLE L2CAP CoC (fallback only) | Android API 29; iOS 11 | VERIFIED-DOC | PROX-01 |
| `WifiP2pConfig.Builder` | API 29 | VERIFIED-DOC | PROX-04 |
| Wi-Fi Aware pairing API | API 34 | VERIFIED-DOC | PROX-05 |
| BLE-assisted Wi-Fi Direct R2 pairing | API 36 | VERIFIED-DOC (PROX MISS-02) | PROX MISS-02 |
| Wi-Fi Aware on iOS | iOS 26, iPhone 12+ | VERIFIED-DOC | PROX-05 |
| iOS deployment target of the app | **none** (no Xcode project) | VERIFIED-REPO | KMP-01 |
| Nearby Connections Swift package minimum iOS | — | UNVERIFIED | — |
| `livekit-android` | 2.28.0 | VERIFIED-REPO | `build.gradle.kts:115` |
| libsignal (gates every sealed path) | not a dependency today | VERIFIED-REPO | KMP-M3, ADR-0017 |

## 11. Consent and permission matrix (PROX MISS-01, ADR-0024 §8)

One row per prompt the user can see in Nearby mode. "Seen?" is filled by the spike (§13) per device.

| Prompt | Android | iOS | Needed by | Seen? |
|---|---|---|---|---|
| Nearby devices / Bluetooth | Bluetooth scan/advertise/connect runtime permissions; nearby-Wi-Fi-devices permission on newer API levels | Bluetooth usage prompt | `NearbyLink` | UNMEASURED |
| Local network | reported for newer Android (UNVERIFIED) | Local Network prompt (VERIFIED-DOC) | `LanLink`, `NearbyLink` (LAN medium) | UNMEASURED |
| Location | API 29–31: BLE scanning and Nearby Connections need `ACCESS_FINE_LOCATION`, i.e. a location prompt on every proximity-capable Android 10–12 device (DPIA-relevant, DT-1); API 31+: `BLUETOOTH_SCAN`/`ADVERTISE`/`CONNECT`; API 33+: `NEARBY_WIFI_DEVICES` (platform documentation; prompts UNMEASURED) | required for the Nearby hotspot join (VERIFIED-DOC) | `NearbyLink`; `NearbyLink` bulk on iOS | UNMEASURED |
| Hotspot join | — | system confirmation (UNVERIFIED) | `NearbyLink` A↔i bulk | UNMEASURED |
| Foreground-service notification | `connectedDevice` FGS while Nearby mode is on. On Android 14+ this FGS type requires a declared prerequisite permission **and** a Play Console foreground-service declaration (with a video): a 1.2 release-checklist item and a Play policy risk | n/a | Nearby mode | UNMEASURED |
| Notifications | existing | not built | `SERVER_RELAY` wake | VERIFIED-REPO (Android) |
| Microphone / camera | calls | calls | `IP_RELAYED`, `IP_DIRECT` | UNMEASURED |

The DPIA for proximity scanning (ADR-0029 rule 6) and the Nearby-mode onboarding are built from the
filled version of this table.

## 12. How `PathPolicy` reads this matrix (1.2)

| Rule (brief §7) | Column(s) it reads |
|---|---|
| `AssuranceRule` | `offServer` ⇒ `SealedFrame` (type fact, ADR-0016) |
| `RelationshipRule`, `CertificateRule` | not here — identity/certificate state (ADR-0018) |
| `ConversationModeRule`, `ScopeRule` | not here — ADR 0008/0021; 1:1 only off-server |
| `ConsentRule` | §11 prompts + Nearby mode state |
| `RegulatoryRule` | "Legal gate" rows; `EXTERNAL_RF` absent from TR builds |
| `QuotaRule` | §7 quotas |
| `SizeRule` | §2.3 `maxFrameBytes`, `supportsBulk` |
| `ConstrainedRule` | `constrained` flag on `SERVER_RELAY` (§3) |
| Preference ranking | scope order per mode × class (brief §7), not measured numbers |

Measured cells (§13 spike) feed **UI claims and degradation thresholds**, never ranking weights
(brief ADR-0015: no fitness score).

---

## 13. Spike protocol — 1.2 device matrix (G-PROXIMITY)

### 13.1 Purpose and outputs

Fill §5, §6 and §11 with owner-observed values, decide whether `NearbyLink` ships on iOS, and choose
the `LinkAuthenticator` library. Outputs, all signed by the owner (P13):

1. The filled matrix (cells → `VERIFIED-DEVICE` or a recorded failure), published in this file.
2. A raw results table (§13.6) committed next to it.
3. The handshake-library decision (ADR-0018 link-key type follows it).
4. A go / Android-plus-LAN-only / no-go recommendation per the decision rules (§13.7).

### 13.2 Preconditions

| # | Precondition | Source |
|---|---|---|
| SP-P1 | 1.0 exit (real E2E, fail-closed) — needed for the 1.2 **milestone**, not for the spike's throwaway harness | brief §13 |
| SP-P2 | Spike code explicitly authorised by the **owner** as a `Conditions:` line under scope 1.2 (1.2 stays Proposed (gated); under ADR-0033 G3 only the owner writes `APPROVAL.md`, and under G5 every non-docs path needs an open scope): "harness code only, in a harness location outside product modules (e.g. a separate `spikes/` tree excluded from release builds), never distributed through Play, deleted after the report" ([OI-18](open-items.md#oi-18)) | owner decision 4, D6 |
| SP-P6 | Before the spike transmits UKEY2- and Noise/TLS-encrypted frames over BLE and Wi-Fi in Turkey: either Q-L2 (a)(1)–(3) is answered, or counsel confirms in writing that test-only encrypted traffic between owner-held devices is acceptable ([OI-18](open-items.md#oi-18)) | G-PROXIMITY, Q-L2 |
| SP-P3 | Harness apps are throwaway, outside the product modules, never shipped, carrying no user data | this protocol |
| SP-P4 | Device list available (§13.3); Play services version recorded on every Android device | brief |
| SP-P5 | Pass thresholds set by the owner before the first run (§13.5) | this protocol |

### 13.3 Devices and pairs

| Device | Platform | Role |
|---|---|---|
| Google Pixel (current) | Android | reference |
| Samsung Galaxy A-series | Android | volume OEM |
| Xiaomi | Android | aggressive-OEM behaviour |
| iPhone 12 | iOS | oldest Wi-Fi-Aware-capable iPhone |
| iPhone 15 or later | iOS | current |
| **Huawei / HMS device (required)** | Android without Play services | verifies the `LanLink`-on-HMS claim and that Nearby is unavailable without Play services (SP-2); ADR-0024's `LanLink` justification depends on it (P13) |
| *(optional)* second iPhone pair for Wi-Fi Aware | iOS 26+, iPhone 12+ | optional cell: iOS↔iOS Wi-Fi Aware with prior DeviceDiscoveryUI pairing, background behaviour ([OI-24](open-items.md#oi-24)) |

Pairs: every A↔A combination of the three Android devices — Pixel–Samsung, Pixel–Xiaomi, Samsung–Xiaomi (3 pairs) — the
iPhone pair (i↔i), and Pixel↔iPhone 12, Samsung↔iPhone 15+, Xiaomi↔iPhone 15+ (A↔i). HMS device: LAN
scenarios only.

### 13.4 Scenarios

| ID | Scenario | Adapters | Network condition |
|---|---|---|---|
| X1 | Foreground discovery, both apps open in Nearby mode | Nearby, LAN | Wi-Fi on, same non-isolating AP, **no internet** (WAN unplugged) |
| X2 | Foreground discovery | Nearby | Wi-Fi **off**, airplane mode with Bluetooth on |
| X3 | Foreground discovery | LAN | client-isolated AP — **expected FAIL**, recorded; the UI must not imply LAN works |
| X3b | Foreground discovery and text | LAN | Wi-Fi without internet **while cellular is up** (Android default-network binding) |
| X4 | Peer backgrounded (home button) for 60 s, then foregrounded | Nearby, LAN | as X1/X2 |
| X5 | Text: 100 sealed-size messages (bucket-padded) | the winning link | as X1/X2 |
| X6 | Bulk: 5 MB file | the winning link; record whether the Wi-Fi upgrade happened | as X1/X2 |
| X7 | Link loss mid-transfer (walk out of range / toggle Wi-Fi) and resume | Nearby, LAN | — |
| X8 | First-run consent: fresh install, count and name every prompt | both | — |
| X9 | Over-the-air capture during X1/X2 (BLE advertisements, mDNS) | both | capture tool on a third device |
| X10 | Battery: 15 min Nearby mode idle + discovery, % drop | both | — |
| X11 | Handshake candidates: complete the link handshake against a certified test link key, both roles, both platforms | `LinkAuthenticator` candidates (vetted Noise library vs TLS 1.3 with pinned raw public key) | — |

Distances: 1 m and 10 m indoor line-of-sight for X1/X2/X5/X6.

### 13.5 Metrics and thresholds

| Metric | Definition | Repetitions | Reported | Proposed pass threshold (owner sets, SP-P5) |
|---|---|---|---|---|
| Time to discover (fg) | Nearby mode opened on the second device → peer tag matched | 10 | median, p90 | median ≤ 10 s |
| Background behaviour | discovered / not discovered while peer backgrounded; time to rediscover after foreground | 5 | pass/fail + median | reported only (Android within Nearby mode is in scope, X4; iOS is N/A by design) |
| Text RTT | send → harness application-level ack back over the candidate authenticated link (X11 handshake, test keys; no product E2E exists before 1.0) | 100 msgs × 3 runs | median, p90 | median ≤ 1 s, p90 ≤ 3 s |
| 5 MB goodput | acked application bytes / wall time (never PHY rate) | 5 | median, p10 | completes, SHA-256 matches; goodput reported |
| Resume | bytes re-sent after X7 / bytes total | 3 | ratio | resumes without restart from 0 |
| Prompts | number and names, per platform version | 1 per device | list | recorded, no threshold |
| Stable identifiers | any per-user/per-device identifier constant across two sessions or epochs | 2 sessions | list | none (app-level fingerprints such as service type disclosed; ADR-0024 reviewer note) |
| Battery | % per 15 min | 2 | mean | reported only |

### 13.6 Recording schema

One row per run, committed as `docs/architecture/multi-transport/spike-1.2-results.csv` (raw) with
the summary filled into §5/§6/§11.

| Column | Example |
|---|---|
| `date` | 2027-03-04 |
| `harness_sha` | git SHA of the harness |
| `device_a`, `os_a`, `play_services_a` | Pixel 8, Android 16, 25.xx |
| `device_b`, `os_b`, `play_services_b` | iPhone 15, iOS 26.x, — |
| `pair` | `A↔i` |
| `scenario` | X6 |
| `adapter`, `medium_observed` | NearbyLink, Wi-Fi hotspot |
| `distance_m` | 1 |
| `metric`, `n`, `median`, `p10`, `p90`, `unit` | goodput, 5, … |
| `result` | PASS / FAIL / PROMPT-REQUIRED / N/A |
| `prompts_seen` | Bluetooth; Local Network; Location; Hotspot |
| `notes` | — |
| `observed_by`, `where` | owner, office |

### 13.7 Decision rules

| Outcome | Decision | Source |
|---|---|---|
| A↔A Nearby and LAN pass | ship `NearbyLink` + `LanLink` on Android | ADR-0024 |
| i↔i or A↔i Nearby fails, LAN passes | **ship Android + LAN-only cross-platform, and say so** | brief §13 1.2 kill criterion |
| Nearby iOS fails **and** demand is shown | evaluate Ditto (paid) or a custom BLE floor in a new ADR | ADR-0024, PROX-06 |
| LAN fails on isolating APs (expected) | UI copy names the condition; no claim of "any Wi-Fi" | ADR-0024 reviewer note |
| A stable per-user identifier is observed | 1.2 exit blocked until removed | brief §13 1.2, P9 |
| No handshake candidate passes X11 on both platforms | 1.2 blocked; no hand-rolled handshake (P12) | ADR-0018 |
| HMS share high and LAN-only is the HMS experience | owner decides whether that is acceptable copy | R13 |

### 13.8 Time box and sign-off

Two weeks of calendar time (brief). Every result row is entered or confirmed by the owner;
agents may prepare harness builds and analyse the CSV but may not mark a cell `VERIFIED-DEVICE`
(ADR-0032). The completed matrix is a signed gate artefact listed under the `1.2` scope's
conditions in `APPROVAL.md`.

---

## 14. Change control

| Change | Requires |
|---|---|
| A documentation cell (VERIFIED-DOC/UNVERIFIED) | a cited source or finding ID |
| A device cell to `VERIFIED-DEVICE` | owner observation with date, build, device, "observed by / where" |
| A new adapter or scope row | an ADR (ADR-0015/0027) |
| A watch-list item becoming a candidate | a new ADR reopening ADR-0027 |
| Any UI, store-listing or doc claim about range, speed, background or cross-platform | the cell it relies on must be `VERIFIED-DEVICE` (brief §1.2) |

## 15. Evidence

PROX-01, PROX-02, PROX-03, PROX-04, PROX-05, PROX-06, PROX-07, PROX-08, PROX-09, PROX-10, PROX-11,
PROX MISS-01/02/03/04; NAT-1, NAT-2, NAT-3, NAT-4, P2P-1, P2P-2, P2P-3, QUIC-1, QUIC-2, ICE-1, LAN-1,
LINK-1, LIB-1, NET MISS-1/2/3; RF-02, RF-03, RF-05, RF-06, RF-07, RF-08, RF-09, RF MISS-03; LEG-01,
LEG-02, LEG-03, LEG-04; KMP-01, KMP-M3; MOB-03, MOB-04, MOB-10; BE-15.

## 16. Verification

| Claim in this document | Method | Evidence |
|---|---|---|
| Every cell carries a mark and a source | Inspection (review checklist) | this file |
| No device cell is `VERIFIED-DEVICE` without an owner record | Inspection against §13.6 rows | CSV + owner note |
| `PathPolicy` rules use only declared caps and gates, never measured weights | Inspection of the 1.2 PR; unit tests per rule | CI |
| UI copy makes no claim beyond verified cells | Inspection of `strings.xml` changes at 1.2 (claims register, RQ-20) | review |

## 17. Reviewer notes

> **Open item [OI-18](open-items.md#oi-18):** **SP-1:** the owner approves the spike as a Conditions line under scope 1.2 ("harness code only, outside product modules, never distributed, deleted after the report"), or adds a spike scope; 1.2 itself stays Proposed (gated). See §13.2 SP-P6.

> **Resolved (2026-11-12):** **SP-2:** one Huawei HMS device is added to the spike device list (§13.3)
> for the LAN scenarios and to confirm that Nearby is unavailable without Play services. Until it is
> observed (P13), the matrix marks "HMS gets LAN only" as **inferred**, not measured.

> **Resolved (2026-11-12):** **SP-3 (D2):** the spike uses native harness apps per platform. If KMP-01
> is fixed at the 0.6 gate, the iOS cells may also be repeated on the product app. If not, 1.0 is
> Android-only (D2) and the iOS cells measure library/OS behaviour only.

> **Open item [OI-23](open-items.md#oi-23):** **SP-4:** the §13.5 thresholds are frozen, dated and recorded together with the spike approval, before the first measurement; any later change needs a dated amendment written before data is seen.
