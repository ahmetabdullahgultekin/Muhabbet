# ADR 0023 — Internet-direct means WebRTC ICE plus our own TURN, for live sessions only

**Status:** Accepted (2026-11-12, delegated) for 0.7, except the ADR 0009 hosting/budget choice (Q-O8), which stays with the owner — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); code against this ADR is written only while the matching scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits. The 0.7 part (SFU + TURN, privacy defaults, telemetry) is accepted; the 1.1 "Direct" scope (P2P 1:1 calls) is **Proposed (gated)** on the 1.1 entry criteria · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0023; §5 `LiveSessionPort`, `TurnCredentialPort`; §8.2; §12 items 30–39; §13 (0.7.0, 1.1); §17 Q-O8, Q-L8](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Builds on** [ADR 0009](0009-call-media-infrastructure.md) (unchanged; LiveKit host placement remains its decision) · Depends on ADR-0010, ADR-0012, ADR-0015, ADR-0029. Related: ADR-0027 (rejected transports). Supersedes nothing.

## Context

- **Calls have never worked** (#367–#373): the client never sends `call.initiate`, no microphone
  track is published, and `LIVEKIT_*` is absent in production so `NoOpCallRoomProvider` is the live
  bean. There is **no media-path infrastructure** at all: no LiveKit server, no STUN/TURN, no UDP
  port opened (MISS-1, networking verifier). ADR 0009 proposes a dedicated LiveKit host off the
  production box; its placement is still open (Q-O8).
- The repo already depends on `io.livekit:livekit-android:2.28.0`
  (`mobile/composeApp/build.gradle.kts:115`); LiveKit ships an embedded TURN server with TURN/TLS on
  443 (ICE-1). There is no official LiveKit KMP client; iOS needs a Swift bridge through an `@objc`
  shim (LIB-1, MISS-3).
- Gemini's internet path rebuilt what WebRTC ships, on wrong numbers (verified):

| Gemini claim | Verified fact | ID |
|---|---|---|
| Birthday-paradox punching > 86 % | ≈ 63–64 % for 256×256 ports; ≈ 0 % (0.01 % after 20 s) when both sides are EDM; looks like a port scan | NAT-1 |
| "Symmetric CGNAT" in Turkey | Outdated terminology (RFC 4787 mapping/filtering axes); no published measurement for TR operators | NAT-2, NAT-3 |
| IPv6 direct as a stage | TR ≈ 21.9 % IPv6-capable (APNIC 30-day, 2026-10-31); TT Mobil ≈ 1 %, Turkcell ≈ 14 %, Vodafone TR ≈ 56–58 % | NAT-3, NAT-4 |
| libp2p Circuit Relay for media | Defaults 2 min / 128 KiB per direction — a coordination channel, not a media relay | P2P-1 |
| Hole punching mostly succeeds | DCUtR ≈ 70 % ± 7.1 %; ≈ 30 % of pairs still need a relay | P2P-2 |
| QUIC migrates BLE L2CAP → Wi-Fi Direct | RFC 9000: client-only migration between UDP paths; BLE L2CAP is not UDP/IP | QUIC-1 |
| Kademlia DHT for rendezvous/keys | Not viable on mobile; breaks one-time prekeys | P2P-3, C2 |

- **Direct paths expose IP addresses.** ICE host and server-reflexive candidates reveal the public IP
  (and with IPv6 often a stable /64) to the peer — a KVKK and privacy issue for a privacy-first
  product (MISS-2, networking verifier).
- **An SFU terminates DTLS-SRTP**, so whoever runs it can access call media (ADR 0009 fact 3).

## Decision

### 1. One port for live sessions

```kotlin
// :mobile:messaging / composeApp adapters (brief §5)
interface LiveSessionPort {
    suspend fun join(spec: LiveSessionSpec, creds: SessionCredentials): LiveSession
}
// backend
interface TurnCredentialPort {
    fun mint(d: DeviceId, session: UUID, ttl: Duration): TurnCredential   // ttl ≤ 10 min
}
```

Signalling (`call.*` frames) travels over the existing server WS as **CONTROL**-class entries
through the engine (ADR-0012, brief §6.5) with a 60 s lifetime. Scopes are `IP_RELAYED` (TURN/SFU on
our infrastructure) and `IP_DIRECT` (ICE host/srflx, calls only) (ADR-0015).

**Sealed fingerprints (1.1 rule).** From 1.1, the SDP offer and answer (at minimum the DTLS
`a=fingerprint` lines) travel inside sealed plane-B content (`Content.Control{callOffer|callAnswer}`,
ADR-0022) through `EnvelopeSealer` between the two devices' libsignal sessions. The client aborts the
call if the DTLS certificate presented does not match the sealed fingerprint. `call.*` server frames
carry only routing and ringing. Without this rule the signalling server could swap fingerprints and
sit in the middle of the media, so DTLS-SRTP would not be end to end.

| Milestone | Implementation | Media path | E2E? | Credential source |
|---|---|---|---|---|
| **0.7 Live** | `LiveKitSession` | LiveKit SFU with embedded TURN/TLS on 443, on a **separate host** (ADR 0009) | **No** — SFU can access media; disclosed in UI | Backend-minted LiveKit token, short TTL |
| **1.1 Direct** (gated) | `WebRtcP2PSession` for **1:1 calls** | WebRTC peer connection; ICE over **our own TURN** | **Yes, only when the fingerprints are exchanged sealed** (rule above) — DTLS-SRTP end to end between the two devices; otherwise **No** | `TurnCredentialPort`; LiveKit-embedded TURN or a standalone TURN with HMAC credentials, chosen by the 1.1 spike |
| Group calls | `LiveKitSession` | SFU | No (disclosed) | as 0.7 |

### 2. Privacy defaults

| Rule | Detail |
|---|---|
| Relay by default | `iceTransportPolicy = relay` **unless** both parties are mutual contacts with an existing conversation |
| User override | "Always relay calls" setting in Privacy (forces `relay` for everyone) |
| SFU disclosure | If an SFU carries a 1:1 call, the call UI states that the server can access the media |
| Telemetry | ICE telemetry records **only** selected candidate type (host/srflx/relay), IP family and ASN — **never IP addresses** — with a KVKK notice (Q-L8) |
| Credentials | Minted per session and device, TTL ≤ 10 minutes, never cached across calls. The TTL applies to starting new allocations; the 0.7/1.1 spike verifies that a 30-minute call survives allocation refresh and ICE restart (re-minting over WS if needed) |

### 3. What is not built

- **No messaging over WebRTC data channels.** Text and media stay on WS + REST + MinIO and, later,
  the sealed off-server links (ADR-0024). When the server is up it is strictly better; when it is
  down there is no rendezvous for a data channel.
- No libp2p, DCUtR, Circuit Relay, volunteer supernodes, Kademlia DHT.
- No birthday-paradox punching, no custom ICE, no QUIC-native P2P, no QUIC migration.
- **IPv6 is only an ICE candidate**, gathered alongside IPv4 srflx and relay, never a separate stage.
- No separate coturn for messaging.
- No opt-in NAT survey now; LiveKit ICE telemetry (no IPs) replaces it. An RFC 5780 probe per ASN
  is optional at 1.1, only if the 0.7 data shows a need (NAT-2/NAT-3 axes: mapping, filtering,
  port preservation, hairpinning, binding lifetime — no cone/symmetric labels).

## Why

- **ICE already races candidate pairs** across every interface and handles IPv4/IPv6, NAT and relay
  fallback (ICE-1); re-implementing it gains nothing.
- **NAT-1**: birthday punching is ≈ 63 %, not 86 %, and ≈ 0 % for the EDM↔EDM case it was meant to
  solve. **P2P-2**: ≈ 30 % of pairs need a relay even with good punching, so TURN is mandatory, not
  an edge case. **P2P-1**: Circuit Relay v2 cannot carry media. **QUIC-1**: migration is
  client-side and UDP-only.
- **NAT-3/4**: Turkish IPv6 is ≈ 22 % overall and ≈ 1 % on TT Mobil, so TT Mobil users are almost
  always IPv4 behind CGNAT; capacity must be sized for relay.
- **Relay-by-default for non-contacts** closes MISS-2 (IP exposure) and makes the privacy default
  the safe one.
- **P2P only for 1:1 calls** because that is the one place it buys something users can feel: E2E
  media without trusting the SFU.
- **Data channels add no availability over the server** and would be a second reliability stack.

## Consequences

- **TURN egress is budgeted at 30–50 % relayed calls** until our own ICE statistics exist; the
  relay-by-default policy pushes the estimate toward the upper end (Q-O8, R19).
- 0.7 depends on ADR 0009 being accepted and a separate host provisioned with `LIVEKIT_*` in
  `.env.prod` (ADR-0031 rotation applies to the new secrets).
- The iOS call path needs a Swift bridge (`@objc` shim over LiveKit Swift) spiked before it is
  committed (MISS-3); until KMP-01 is resolved, calls are Android-only.
- 1:1 calls become E2E only at 1.1; until then the UI must not claim it.
- **Watch-list**, not build items: Multipath QUIC (RFC 10069), QUIC NAT traversal
  (individual draft), Happy Eyeballs v3 (draft) — revisited only when a mobile OS stack ships them
  (QUIC-2; ADR-0027).
- **1.1 kill criterion**: if the SFU satisfies the need and the E2E-calls claim is not wanted, 1.1
  is dropped (brief §13).

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Birthday-paradox port punching | ≈ 63 %; ≈ 0 % EDM↔EDM; port-scan behaviour (NAT-1) |
| libp2p / DCUtR / Circuit Relay v2 / supernodes | 2 min / 128 KiB relay limit; KVKK/5651 liability for volunteer relays (P2P-1) |
| Custom ICE over QUIC; QUIC migration across links | Rebuilds libwebrtc; contradicts RFC 9000 (ICE-1, QUIC-1) |
| IPv6-direct stage | Minority path in TR; already an ICE candidate (NAT-4) |
| WebRTC data channels for chat | Second reliability stack with no availability gain |
| Separate coturn for messaging | No messaging over P2P, so nothing to relay |
| Direct candidates for everyone | Exposes IP to strangers (MISS-2) |
| LiveKit Cloud for production | Decided against in ADR 0009 (processor, positioning) |

## Evidence

- Findings: NAT-1, NAT-2, NAT-3, NAT-4, P2P-1, P2P-2, P2P-3, QUIC-1, QUIC-2, ICE-1, LIB-1, LINK-1,
  MISS-1, MISS-2, MISS-3 (networking verifier), C2, KMP-01.
- Code/docs: `mobile/composeApp/build.gradle.kts:115`, backend `application.yml` `livekit` block,
  [ADR 0009](0009-call-media-infrastructure.md), [T2 calls design](../design/T2-voice-and-video-calls.md).
- External (from the networking digest): https://tailscale.com/blog/how-nat-traversal-works,
  https://www.rfc-editor.org/rfc/rfc4787.html, https://www.rfc-editor.org/rfc/rfc6888.html,
  https://stats.labs.apnic.net/ipv6/TR, https://www.rfc-editor.org/rfc/rfc6092.html,
  https://www.rfc-editor.org/rfc/rfc9000.html, https://datatracker.ietf.org/doc/draft-ietf-quic-multipath/,
  https://datatracker.ietf.org/doc/draft-seemann-quic-nat-traversal/,
  https://github.com/libp2p/specs/blob/master/relay/circuit-v2.md, https://arxiv.org/abs/2510.27500,
  https://datatracker.ietf.org/doc/draft-ietf-happy-happyeyeballs-v3/,
  https://docs.livekit.io/home/self-hosting/deployment/.

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| RQ-14 | A call connects, carries audio both ways and appears in history, on **two physical devices on two operators, one of them TT Mobil** | Demonstration, owner (P13) | 0.7 exit |
| RQ-15 | A call with a non-contact uses only relay candidates | Test (policy unit test) + Inspection (ICE stats) | 0.7 exit |
| — | TURN credentials expire ≤ 10 min after minting and are per device and session | Backend unit/integration test | 0.7 |
| — | A 30-minute call survives TURN allocation refresh and an ICE restart despite the ≤ 10 min credential TTL | Spike on physical devices | 0.7 / 1.1 spike |
| — | ICE telemetry rows contain no IP address | Test + inspection of stored rows | 0.7 |
| — | One week of relayed share and TURN egress measured | Analysis of telemetry | 0.7 exit |
| — | 1:1 P2P call: per-ASN setup success and latency A/B vs SFU; no IP exposure to non-contacts | Analysis + candidate inspection | 1.1 exit |
| — | Two-operator physical-device P2P call | Demonstration, owner | 1.1 exit |
| — | A call whose server-relayed SDP fingerprint was tampered with fails to connect | Integration test with a MITM signalling fake | 1.1 exit |
