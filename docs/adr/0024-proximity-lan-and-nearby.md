# ADR 0024 — Proximity: build LAN, buy Google Nearby Connections; foreground "Nearby mode"; verified contacts only; 1:1 only

**Status:** Proposed (gated) — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). Even after approval, work starts only when entry gate **G-PROXIMITY** (brief §13, 1.2) is met · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0024; §6.3, §6.7, §6.8, §7, §8.2, §8.5, §13 (1.2)](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Milestone:** 1.2 "Nearby" · Depends on ADR-0010, ADR-0012, ADR-0013, ADR-0015, ADR-0016, ADR-0017, ADR-0018, ADR-0022, ADR-0028, ADR-0029, ADR-0031. Supersedes nothing.

## Context

The owner wants delivery "somehow" when the server is unreachable but the other person is nearby.
What exists on `dev` @ 2434324 and what was verified:

- **No proximity code at all.** A grep for BLE, Wi-Fi P2P/Aware, Nearby and Multipeer APIs across
  `.kt/.kts/.xml/.swift/.toml` returns nothing (PROX-11). `minSdk = 26`
  (`mobile/composeApp/build.gradle.kts:149`), below the API 29 that L2CAP CoC and
  `WifiP2pConfig.Builder` need. Both platforms bind `NoOpEncryption` (C9), so any proximity link
  built today would move plaintext between radios.
- **iOS cannot launch** (KMP-01), so every Android↔iOS claim below is hypothetical until the 0.6
  iOS gate (ADR-0028) is honoured.
- **Gemini's proximity layer is mostly wrong in the case Muhabbet needs** — Android↔iOS, possibly in
  the background:

| Gemini claim | Verified reality | Finding |
|---|---|---|
| Continuous background BLE discovery at ~1 mW, cross-platform | A backgrounded iPhone moves service UUIDs to an overflow area only iOS scanners read and drops the local name; Android needs a live process or foreground service | PROX-02 |
| BLE L2CAP ~500 kbit/s, 512 B MTU | Measured iPhone-to-iPhone 36–138 kbit/s; 512 B is the ATT limit, not the CoC SDU; CoC needs API 29 | PROX-01, MISS-03 (prox) |
| Multi-hop BLE mesh for kilometres via other users' phones | No phone API; Bridgefy broken twice (CT-RSA 2021, USENIX Sec '22); FireChat failed | PROX-03, PROX-10 |
| Zero-click Wi-Fi Direct | Android 10+ ↔ Android 10+ only; no Wi-Fi Direct on iOS | PROX-04 |
| Wi-Fi Aware for offline calls | Real on Android 8+ and iOS 26, but iOS needs user pairing and Android↔iPhone interop is not available in practice | PROX-05 |
| Build BLE→Wi-Fi escalation | Google Nearby Connections already does it, on Android and as an iOS Swift package | PROX-06 |
| mDNS discovery "just works" | iOS shows the Local Network prompt and needs declared Bonjour service types; raw multicast needs a restricted entitlement | LAN-1, MISS-01 (prox) |

- Nearby's LAN medium depends on Play services. So "Nearby covers LAN" is false for HMS devices and
  for iPhone↔Android on shared Wi-Fi. That is why a LAN adapter is built (architecture judge
  must-fix; brief §3 judge disagreements, "LAN adapter").
- Without server TLS, peer authentication must come from device-level credentials: the device
  certificate and link key of ADR-0018. BLE pairing and Nearby's UKEY2 authenticate the radio peer,
  not the recipient (ADR-0016).

## Decision

### 1. Two `SealedLink` adapters ship in 1.2

| Adapter | Build/buy | Module (ADR-0028) | Discovery | Data | Pairs served |
|---|---|---|---|---|---|
| `LanLink` | **Built** (it is just sockets) | `:mobile:transport-lan` | mDNS/DNS-SD: Android `NsdManager`, iOS Bonjour / Network.framework. No raw multicast | Length-prefixed TCP records ≤ 64 KiB, offset-resume for BULK; vetted link handshake (ADR-0018 §4) | A↔A, i↔i, A↔i, HMS, on the same network, **no internet needed** |
| `NearbyLink` | **Bought**: Play-services Nearby Connections (Android); unofficial Nearby Swift package (iOS) **only if the spike passes** | `:mobile:transport-nearby` | Library-owned (BLE), upgrade to Wi-Fi by the library | BYTES payloads for INTERACTIVE/CONTROL; FILE/STREAM payloads for BULK; fragmentation is the library's | A↔A (Play services); A↔i and i↔i only after the spike |

Both implement `SealedLink` and `InboundSource` (ADR-0012). Both accept **only** `SealedFrame`
(ADR-0016), carried inside the outer `TransportFrame` (ADR-0022 §C). Neither touches ordering,
identity or history (ADR-0010).

```kotlin
// :mobile:transport-lan / :mobile:transport-nearby (sketch; ports from ADR-0012)
class LanLink(/* NsdPort, SocketPort, LinkAuthenticator, Clock */) : SealedLink, InboundSource
class NearbyLink(/* NearbyConnectionsPort, LinkAuthenticator, Clock */) : SealedLink, InboundSource
// both: override val scope = PathScope.LAN / PathScope.PROXIMITY
```

### 2. Nearby mode (the only time proximity runs)

1. Proximity runs only while the user has started **Nearby mode** from a foreground screen. There is
   no background or always-on discovery or advertising.
2. **Android:** a `connectedDevice` foreground service runs only while Nearby mode is on, with a
   persistent notification. **iOS:** foreground only.
3. **Auto-off after 15 minutes** (companion constant `NEARBY_MODE_AUTO_OFF`), resettable by user
   action. Leaving the screen does not extend it.
4. Proximity features are gated at **runtime on API ≥ 29**; the app's `minSdk` stays 26. Below 29 the
   Nearby-mode entry point is hidden with an honest reason.
5. There is **no** custom BLE GATT/L2CAP, Wi-Fi Direct or Wi-Fi Aware adapter. A custom BLE floor is
   reconsidered only if the spike shows Nearby's iOS interop failing **and** usage data shows demand
   (new ADR required; ADR-0027).

### 3. Eligibility (all must hold; enforced as `PathRule`s, ADR-0015)

| # | Condition | Enforced by |
|---|---|---|
| E1 | Conversation is 1:1 (no groups, channels, communities in 1.2) | `ScopeRule` |
| E2 | Conversation is E2E (libsignal session exists) | type gate: only `SealedFrame` can reach the link (ADR-0016) |
| E3 | Safety number is **verified** (QR, ADR-0018 §6) | `RelationshipRule` |
| E4 | Both devices present valid device certificates and are not on the CRL | `CertificateRule` (`DeviceCertificateVerifier`) |
| E5 | Both users opted in to proximity, and Nearby mode is open on both | `ConsentRule` |
| E6 | Both users hold a valid age attestation (precondition of the certificate, ADR-0029) | certificate issuance (E4) |
| E7 | Storage mode permits it (ADR 0008 / ADR-0021); CLOUD/BYO keep `serverOwed` | `ConversationModeRule`, `CloudPersistence` |

A failed condition removes the candidate. It never downgrades content (ADR-0015).

### 4. Link authentication

| Step | What happens | Notes |
|---|---|---|
| 1 | Transport-level connect (TCP accept, or Nearby `onConnectionInitiated`/accept) | Nearby's UKEY2 is accepted as hygiene only |
| 2 | `LinkAuthenticator.handshake(io, role, expected)` over the raw duplex | Vetted Noise library or TLS 1.3 with pinned raw public key — **chosen by the 1.2 spike**; no hand-rolled handshake (P12) |
| 3 | Each side presents its `DeviceCertificate`; the peer verifies signature chain, validity window (≤ 7 days), CRL, and that the handshake key equals the certified **link key** | ADR-0018 §4–5 |
| 4 | Link state `AUTHENTICATING → UP`; any failure → `BACKOFF`, peer not retried until next epoch or user action | Link state machine, brief §6.3 |

The link channel provides AEAD over the `TransportFrame` (header fields as associated data,
ADR-0022). Content confidentiality still comes from the libsignal envelope inside `SealedFrame`; the
link layer adds peer authentication and metadata protection, not content protection.

### 5. Discovery privacy (brief §8.5)

1. `pairKey` comes from a `rendezvousSecret` exchanged **inside** the E2E session (preferred: the
   server never sees it), or from an agreement between the two certified link keys through the vetted
   library (spike decides).
2. `tag = HMAC-SHA256(pairKey, "mbt-prox-v1" ‖ epoch15m)[0..8]`, computed in `:mobile:crypto`
   (P12: no primitive outside the crypto adapters). The transport modules receive tags, never keys.
3. Advertisements carry only rotating pairwise tags. No account, phone or device identifiers.
   Nearby endpoint names and mDNS service-instance names are **random per session**.
4. A scanner matches observed tags only against the tags of its **own verified contacts** for the
   current and adjacent epoch, and ignores everything else.
5. **Carrier:** Nearby `endpointInfo` = per-session random nonce ‖ tags; mDNS = random instance
   name + a TXT key carrying tags. Only tags of peers with current demand (§6 Trigger) are
   advertised, bounded by the carrier's size limit; re-advertise at each epoch boundary. The
   `endpointInfo` and TXT size limits are measured in the 1.2 spike.

```kotlin
// :mobile:crypto
fun interface ProximityTagSource { fun tagsFor(epoch: Long): Map<ContactDevice, ProximityTag> } // ProximityTag: 8-byte value class
```

### 6. Establishment, escalation, de-escalation (brief §6.8)

| Rule | Value |
|---|---|
| Trigger | Demand for a peer: queued entries for it, or the user opened its chat in Nearby mode |
| Race order | `LanLink` first when both peers are on the same network; `NearbyLink` after `ATTEMPT_DELAY_MS = 250` |
| Winner | First link to reach `UP`; a higher-ranked later `UP` may take over; losers cancelled |
| Escalation | Request a Nearby bandwidth upgrade when queued BULK for the peer ≥ `ESCALATE_BYTES = 256 KiB`; the BLE→Wi-Fi switch is the library's, make-before-break |
| Continuity | From outbox entry ids and acks, **never** from QUIC migration (QUIC-1) |
| De-escalation | Close the costlier link after `IDLE_CLOSE_MS = 30 s` without BULK or live demand; hysteresis `DEESCALATE_MIN_UP_MS = 10 s` |
| Per-link sender | Each sealed link has its own sender coroutine and lanes, so a slow BLE link never blocks the server or LAN (brief §6.6) |

Constants live in companion objects (CLAUDE.md "no magic numbers").

### 7. Delivery semantics on proximity paths

- **Persist before ack** (P4): a peer emits a sealed DELIVERED receipt only after its inbox commit;
  the receipt is also uploaded to the server when reachable (ADR-0013, §6.4).
- **Ticks** (ADR-0013): delivered directly to the recipient device → two ticks plus **"not yet
  synced"** until the server assigns a seq. There is never a "relayed" or "mesh" tick.
- **`serverOwed`** (ADR-0010): for CLOUD/BYO, the sender's entry stays `HOP_ACKED(serverOwed=true)`
  and re-queues to the server on server link-up. The recipient deduplicates by `clientMessageId`.
- **Inbound pipeline**: `inbound_dedup`, block-list filter (ADR-0029 rule 4) and envelope open, in
  that order, before any row is written.
- **Ordering**: off-server rows sort provisionally (ADR-0014) and re-sort stably once seq arrives.

### 8. Consent and platform matrix (to be measured; nothing claimed until published)

| Prompt / permission | Android | iOS |
|---|---|---|
| Nearby devices / Bluetooth | `BLUETOOTH_SCAN` / `ADVERTISE` / `CONNECT`, `NEARBY_WIFI_DEVICES` (as required by the library on the device's API level) | Bluetooth usage prompt |
| Local network | Local-network permission where the OS version requires it | Local Network prompt; `NSLocalNetworkUsageDescription` + declared `NSBonjourServices` |
| Location | Only if the library requires it on that API level | Required for the Nearby hotspot join; without it the join is silently rejected |
| Hotspot join | — | System confirmation prompt (Nearby Wi-Fi upgrade) |
| Foreground service | `connectedDevice` type, notification | n/a (foreground only) |
| Wi-Fi without internet while cellular is up | `LanLink` binds its sockets to the Wi-Fi `Network` from `ConnectivityManager` (it is usually not the default network); to be verified in the spike | to be measured in the spike |

Every cell is **unmeasured** until the device-matrix spike publishes it in
`transport-capability-matrix.md` (P13: only the owner's observation counts).

### 9. Non-goals in 1.2

Groups over proximity; background delivery to a backgrounded iPhone; first contact without the
server (brief §1.2); multi-hop relaying (ADR-0025); range or throughput claims not taken from the
published matrix.

## Why

- **Buy the radio stack** (P12). Nearby Connections already implements BLE discovery plus Wi-Fi
  upgrade (PROX-06); a solo engineer cannot maintain hand-written Wi-Fi Direct negotiation or
  BLE→Wi-Fi escalation across OEM firmware.
- **Build LAN** because it is the only cross-platform, HMS-inclusive floor and it is plain sockets
  plus OS discovery APIs (LAN-1). Without it, HMS phones and iPhone↔Android on shared Wi-Fi would get
  nothing.
- **Foreground only** because the OSes make cross-platform background discovery impossible (PROX-02);
  promising it would be a false claim.
- **Verified contacts only, 1:1 only**: every stranger-relayed mesh that shipped was broken (PROX-03);
  verification turns the link key into a human-checked binding; 1:1 keeps provisional ordering and
  revocation exposure small (R15, R16).
- **Rotating pairwise tags** remove stable over-the-air identifiers (P9, C7).

## Consequences

- HMS devices get LAN only. Measure the HMS share in Turkey before 1.2 (G-PROXIMITY).
- iOS shows several consent prompts; the Nearby-mode onboarding is designed around the matrix in §8.
- A DPIA on proximity scanning (ADR-0029 rule 6) and a counsel opinion naming E2E over BLE and
  Wi-Fi (Q-L2) are entry conditions.
- New tables: mobile `peer_link`, `crl_entry` (brief §11.2); backend V35 (certificates, link keys,
  CRL deltas).
- A device offline for more than 7 days cannot authenticate to proximity peers until it reconnects
  (ADR-0018; disclosed).
- Messages may visibly re-sort once on sync (R16); the UI shows "not yet synced".
- If the spike fails on iOS, 1.2 ships Android plus LAN-only cross-platform, and says so (kill
  criterion, brief §13). Ditto stays only as a paid fallback for the iOS cell.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Hand-built BLE L2CAP + Wi-Fi Direct + QUIC migration | 36–138 kbit/s measured; API 29; no iOS Wi-Fi Direct; QUIC cannot migrate off a non-UDP link (PROX-01, PROX-04, QUIC-1) |
| BLE multi-hop mesh | No phone API; repeated cryptographic breaks (PROX-03) |
| Always-on background discovery | Impossible cross-platform (PROX-02) |
| Wi-Fi Aware as primary | No Android↔iOS interop in practice (PROX-05); watch-list, ADR-0027 |
| "Nearby covers LAN" (no `LanLink`) | False for HMS and iPhone↔Android on shared Wi-Fi |
| Ditto as primary | Commercial; kept only as the iOS fallback if the spike fails |
| Authenticate with BLE pairing / UKEY2 only | Authenticates the radio peer, not the recipient (ADR-0016) |
| MAC under a key derived from the E2E session | libsignal does not expose ratchet-key derivation; would be guessed crypto (ADR-0018) |

## Evidence

- Findings: PROX-01, PROX-02, PROX-03, PROX-04, PROX-05, PROX-06, PROX-08, PROX-10, PROX-11, MISS-01,
  MISS-03 and MISS-04 (proximity verifier), LAN-1, LINK-1, QUIC-1, C7, C9, C11, KMP-01, KMP-09,
  LEG-02, LEG-04, CICD-17.
- Repo: `mobile/composeApp/build.gradle.kts:149` (`minSdk = 26`); `PlatformModule.android.kt`,
  `PlatformModule.ios.kt` (NoOp bindings).
- External sources (from the proximity and networking digests):
  - https://developers.google.com/nearby/connections/overview
  - https://developers.google.com/nearby/connections/strategies
  - https://developers.google.com/nearby/connections/android/get-started
  - https://developers.google.com/nearby/connections/swift/get-started
  - https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html
  - https://developer.apple.com/forums/thread/89644
  - https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy.md
  - https://developer.apple.com/forums/thread/663875
  - https://developer.apple.com/tutorials/data/documentation/bundleresources/entitlements/com.apple.developer.networking.multicast.md
  - https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder
  - https://developer.android.com/develop/connectivity/wifi/wifi-aware
  - https://eprint.iacr.org/2021/214
  - https://docs.ditto.live/sdk/v4/deployment/network-deployment
  - https://berty.tech/blog/bluetooth-low-energy/

## Verification

| Req | Check | Method | Signed off by | When |
|---|---|---|---|---|
| RQ-06 | `LanLink`/`NearbyLink` accept only `SealedFrame` | Compile + `SealedLink` contract suite | CI | 1.2 |
| RQ-16 | Two Android phones in airplane mode exchange E2E text and a photo via `LanLink` on shared Wi-Fi, and via `NearbyLink` with Wi-Fi off | Demonstration | **Owner** (P13) | 1.2 exit |
| RQ-02 / RQ-04 | On reconnect each message appears once, in seq order, on both devices and a linked companion | Demonstration + `SimulatedSealedLink` multi-node test | Owner + CI | 1.2 exit |
| RQ-17 | No stable identifiers in advertisements or instance names; tags rotate every 15 min | Golden-vector test + over-the-air capture | CI + owner | 1.2 exit |
| RQ-18 | A revoked device is refused after certificate expiry or a CRL update | TestClock test + demonstration | CI + owner | 1.2 exit |
| — | Each eligibility rule E1–E7 denies its case | One unit test per `PathRule` | CI | 1.2 |
| — | Nearby mode turns off after 15 min; no discovery runs outside it | Unit test (TestClock) + owner observation | CI + owner | 1.2 |
| — | Device matrix (Pixel, Samsung A, Xiaomi, iPhone 12, iPhone 15+; A↔A, i↔i, A↔i: discovery time, backgrounded behaviour, text RTT, 5 MB goodput, prompts) published | Spike report | Owner | G-PROXIMITY |

> **Reviewer note (open):** the exit criterion "over-the-air capture shows no stable identifiers"
> (brief §13, 1.2) is not fully under our control. Our payload (tags, instance names, endpoint names)
> can be made non-stable, but the mDNS **service type** must be a static string declared in
> `NSBonjourServices`, and Nearby's `serviceId` and the library's own BLE advertisement format are
> fixed per app. Both reveal "a Muhabbet user is here", though not which one. Recommend rewording the
> criterion to "no stable per-user or per-device identifiers", and listing the app-level fingerprints
> as a disclosed residual in `threat-model.md`.

> **Reviewer note (open):** many public and guest Wi-Fi access points isolate clients and drop
> multicast, which defeats both mDNS discovery and peer TCP. The brief's `LanLink` exit ("shared
> Wi-Fi") should name a non-isolating network, and the device matrix should add an isolated-AP row
> marked as an expected failure, so the UI does not imply LAN works on every Wi-Fi. It should also add
> the row "Wi-Fi without internet while cellular is up" (§8): the airplane-mode exit test has no
> cellular and therefore hides the Android default-network binding case.
