# ADR 0027 — Rejected-transports record

**Status:** Proposed — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). This ADR authorises no code; on acceptance it forbids the items below until a new ADR reopens them · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0027; §1.2; §12 (feature disposition table)](../architecture/multi-transport/decision-brief-2026-11-12.md) · Depends on the verified fact-checks in the [findings register](../reviews/2026-11-12-findings-register.md). Referenced by ADR-0015, ADR-0023, ADR-0024, ADR-0025, ADR-0026.

## Context

The Gemini proposal (`gemini-chat`, reviewed 2026-11-12) listed roughly twenty transports, from
Ethernet to ultrasonic, as "levels" of one delivery stack. Every one was fact-checked by a reviewer
and an independent adversarial verifier; 0 of 211 findings were refuted. Most transports failed on
physics, platform API, law, or on already being solved by something we use.

Without a written record, future agents and contributors will re-propose them: the same ideas arrive
whenever someone asks a model for "messaging that works everywhere". This ADR is that record. It is
the single index that the feature disposition table (brief §12) and the capability matrix cite.

## Decision

### 1. Dispositions

| Disposition | Meaning |
|---|---|
| **Rejected permanently** | Illegal or structurally impossible for this product. Reopened only by a new ADR that cites a change in law or platform, with a counsel opinion where law is involved |
| **Rejected** | Not useful or not feasible with current platforms and evidence. Reopened only by a new ADR with new evidence |
| **Watch-list** | Not built. Re-evaluated when the named trigger happens; reopening still requires a new ADR |

### 2. Evidence table

| # | Item | Disposition | Reason (verified) | Finding IDs | Sources | Reopen trigger |
|---|---|---|---|---|---|---|
| 1 | Amateur VHF/UHF radio, AFSK, Baofeng-class radios | **Rejected permanently** | Licence required; encrypted traffic banned for domestic and international traffic; non-commercial use only. E2E app bundles break all three. Gemini's AFSK modem was also defective and never called | RF-08, RF-09, GEM-19, LEG-03 | https://btk.gov.tr/s3/web-btk-site/7708c26c-5161-4919-8b3d-daa31d4ac8fb/2026/05/01c0ec40-b281-44e2-a8e9-3576aea0f354.pdf ; https://lral.lv/oldwww/hp/yl2dx/S25.html | none foreseeable |
| 2 | PMR446 data | **Rejected permanently** | Integral antenna only and 180 s TX cap exclude Baofeng-class radios; encrypted data meets Law 5809 Art. 39. (Digital use **is** permitted since 2018; the rejection does not rest on "analog only") | RF-09, MISS-03 (legal verifier), LEG-03 | BTK table (as row 1); https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5809.pdf | BTK explicitly confirms the use and counsel clears Art. 39 |
| 3 | 5G Broadcast / FeMBMS / Receive-Only Mode | Rejected | Downlink-only linear TV/radio; no uplink, so it cannot carry a message; no consumer handset or third-party API in 2026 | RF-02, RF-01 | https://www.etsi.org/deliver/etsi_ts/103700_103799/103720/ | a handset API with an uplink path (not expected) |
| 4 | DVB-T2 / DVB-S2 / DVB-SH, RTL-SDR tuners, satellite TV | Rejected | Receive-only broadcast; n255/n256 are two-way NTN bands, not satellite TV; DVB-SH failed; an RTL2832U cannot decode DVB-T2; no Ku-band front end on phones | RF-03, RF-04, RF-05 | https://itecspec.com/3gpp/38.101-5/s/5.2.2 ; https://lists.osmocom.org/pipermail/osmocom-sdr/2019-August/001967.html ; https://www.wikiwand.com/en/articles/DVB-SH | none |
| 5 | Satellite direct-to-cell / NTN as a transport | Rejected → `constrained` flag on `SERVER_RELAY` (ADR-0015) | It arrives as an OS/carrier IP network, not something the app selects; no commercial D2C data in Turkey; Starlink unlicensed in Türkiye | RF-06, RF-03 | https://developer.android.com/develop/connectivity/satellite/constrained-networks ; https://developer.apple.com/documentation/bundleresources/configuring-your-app-for-ultra-constrained-networks ; https://www.dailysabah.com/business/tech/turkiye-denies-claims-elon-musks-starlink-will-launch-in-2026 | a Turkish operator launches D2C data → add the manifest opt-in and a text-only budget, still on `SERVER_RELAY` |
| 6 | Ultrasonic (ggwave) and animated QR | Rejected | 8–16 B/s; "ultrasonic" starts at 15 kHz (audible to many); both need proximity, where a static safety-number QR already works | PROX-09 | https://github.com/ggerganov/ggwave ; https://cdn.jsdelivr.net/gh/divan/txqr@master/README.md ; https://signal.org/blog/safety-number-updates/ | none |
| 7 | 5G NR sidelink (PC5) | Rejected | No Android/iOS app API | PROX-07 | https://www.androidenterprise.community/discussions/conversations/sidelink-in-5g-nr/10479 ; https://arxiv.org/html/2502.16650v1 | an app-level API ships |
| 8 | Kademlia DHT, self-certifying PeerID, libp2p, Circuit Relay v2, volunteer supernodes | Rejected | A DHT cannot hand out a one-time prekey exactly once and leaks who looks up whom; mobile nodes are DHT clients only; Circuit Relay v2 defaults (2 min / 128 KiB) coordinate hole punching, they do not carry media; volunteer relays bring Sybil, abuse and KVKK/5651 liability; PeerID conflicts with phone-OTP identity and Law 5809 Art. 9(14) operator duties | P2P-1, P2P-3, C2, C11, LIB-1, LEG-01, LEG-02, MISS-01 (legal verifier) | https://github.com/libp2p/specs/blob/master/relay/circuit-v2.md ; https://pkg.go.dev/github.com/libp2p/go-libp2p/p2p/protocol/circuitv2/relay ; https://berty.tech/blog/gomobile-ipfs/ ; https://signal.org/docs/specifications/pqxdh/ | owner decision 1 (server backbone) is reversed — not expected |
| 9 | Birthday-paradox NAT punching | Rejected | 256×256 random ports ≈ 63–64%, not > 86%; ≈ 0.01% when both sides are EDM; traffic looks like a port scan | NAT-1, NAT-2 | https://tailscale.com/blog/how-nat-traversal-works ; https://www.rfc-editor.org/rfc/rfc4787.html | our own ICE telemetry shows a large one-sided-EDM population (ADR-0023) |
| 10 | QUIC connection migration BLE ↔ Wi-Fi; custom QUIC P2P | Rejected | RFC 9000 migration is client-side over UDP/IP only; BLE L2CAP is not IP; continuity belongs to message ids, acks and ratchet state | QUIC-1, QUIC-2, LIB-1 | https://www.rfc-editor.org/rfc/rfc9000.html | none (see watch-list row W3 for IP-only QUIC) |
| 11 | Custom BLE mesh / multi-hop via other users' phones / stranger mules | Rejected | No phone API; background makes it near-impossible on iOS; Bridgefy broken twice; relays hold third parties' data (KVKK controller gap) | PROX-02, PROX-03, PROX-10, C7, GEM-09, LEG-04 | https://eprint.iacr.org/2021/214 ; https://berty.tech/blog/bluetooth-low-energy/ ; https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf | none; contacts-only multi-hop stays research R-DTN (ADR-0025) |
| 12 | Custom Wi-Fi Direct negotiation | Rejected | Android 10+ ↔ Android 10+ only; no iOS Wi-Fi Direct; no-dialog behaviour undocumented across OEMs; Nearby already upgrades to Wi-Fi on Android (ADR-0024) | PROX-04, PROX-06, MISS-02 (proximity verifier) | https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder ; https://developers.google.com/nearby/connections/strategies | Nearby fails the spike for A↔A bulk; if reopened, use the Android 16 BLE-assisted R2 pairing API, not fixed credentials |

### 3. Watch-list

| # | Item | Why not now | Finding IDs | Sources | Re-evaluate when |
|---|---|---|---|---|---|
| W1 | Wi-Fi Aware | Android↔iPhone interop not available in practice; iOS needs user pairing (iOS 26, iPhone 12+); iOS↔iOS paired connections may run in background (unmeasured) | PROX-05, MISS-04 (proximity verifier) | https://developer.android.com/develop/connectivity/wifi/wifi-aware ; https://developer.apple.com/tutorials/data/documentation/wifiaware.md ; https://developer.android.com/reference/android/net/wifi/aware/AwarePairingConfig | Android OEMs ship NAN pairing compatible with iOS; re-test yearly |
| W2 | Multipeer Connectivity / AWDL | iOS↔iOS only; Android cannot speak AWDL | PROX-04, PROX-10 | https://docs.ditto.live/sdk/v4/deployment/network-deployment | iOS is in scope (0.6 gate) and the 1.2 matrix shows an i↔i gap Nearby does not fill |
| W3 | Multipath QUIC (RFC 10069), QUIC NAT traversal | RFC 10069 is new; mobile stacks cannot be assumed to support it; NAT traversal is an individual draft | QUIC-2 | https://datatracker.ietf.org/doc/draft-ietf-quic-multipath/ ; https://datatracker.ietf.org/doc/draft-seemann-quic-nat-traversal/ | NAT traversal becomes a WG document and a mobile OS stack ships both |

### 4. Related rejections recorded in other ADRs

So that this ADR stays the one index:

| Item | Recorded in | Finding IDs |
|---|---|---|
| WebRTC data channels for chat; 4G/5G internet P2P messaging; separate IPv6-direct stage | ADR-0023 | ICE-1, NAT-4, P2P-2, MISS-2 (networking verifier) |
| Custom BLE GATT/L2CAP adapter; always-on background BLE discovery | ADR-0024 | PROX-01, PROX-02 |
| KISS/TNC, software AFSK, raw LoRa PHY, Codec2 over radio, `delay(200)` duty cycle | ADR-0026 | GEM-18, GEM-19, GEM-20, RF-07 |
| Epidemic routing, clear custody target hash, Proof-of-Work, per-chunk Reed-Solomon | ADR-0025 | GEM-07, GEM-09, C6, C7, C12 |
| "MU" sync word + CRC-32C frame; UUIDv7 on the outer wire | ADR-0022 | GEM-08 |
| Link "levels 0–6" by medium; fitness score | ADR-0015 | LINK-1, GEM-05, GEM-06 |
| Merkle-CRDT/DAG history, HLC authority | ADR-0014 | C4, C5, GEM-10, GEM-13 |

### 5. Rules that follow

1. No enum value, port, module or flag for a rejected item is added to the codebase (e.g. no
   `SATELLITE_D2C_NTN`, `VHF_UHF_AFSK_RADIO`, `ULTRASONIC` transport types). Reviewers reject such
   PRs by citing this ADR.
2. No design document cites Gemini's URLs; three of its five broadcast citations are dead and one
   is an unfollowed redirect stub (RF-01). Cite primary sources (e.g. ETSI TS 103 720 V1.2.1).
3. No Gemini code is copied (brief §12 #80; GEM-01…22, LEG-05).
4. Reopening any row requires a new ADR with new evidence; the watch-list trigger alone does not
   authorise work.

## Why

The verified fact-checks are the evidence (rows above). Recording them stops re-litigation by future
agents, and gives the capability matrix and the feature disposition table one place to point to.

## Consequences

- The feature disposition table (brief §12) and `transport-capability-matrix.md` cite this ADR
  instead of repeating the reasoning.
- The product's honest "what we do not promise" list (brief §1.2) has a citable basis.
- The watch-list needs a yearly re-check of W1 and an event-driven check of W2/W3; the owner decides
  whether to schedule it.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Leave rejections implicit in other ADRs | They get re-proposed; no single citation point |
| Keep every item as "maybe later" | Hides illegal or impossible items behind optimism; misleads planning |

## Evidence

- Findings: RF-01…RF-09, LEG-01…LEG-05, MISS-01…MISS-04 (legal verifier), PROX-01…PROX-10,
  MISS-02 and MISS-04 (proximity verifier), NAT-1, NAT-2, NAT-4, QUIC-1, QUIC-2, P2P-1…P2P-3,
  ICE-1, LIB-1, LINK-1, C2, C6, C7, C11, C12, GEM-05…GEM-10, GEM-18…GEM-21.
- All source URLs are listed per row above and come from the fact-check digests; none is from the
  Gemini proposal.

## Verification

| Check | Method | When |
|---|---|---|
| No rejected transport type, module or flag exists in the code | Grep guardrail over `shared/`, `mobile/`, `backend/` for the rejected identifiers; reviewer inspection | each PR touching transports, from 0.5 |
| The capability matrix and §12 cite this ADR for every rejected row | Inspection | doc review |
| Watch-list re-check performed | Dated note appended to this ADR | yearly (W1); on trigger (W2, W3) |
