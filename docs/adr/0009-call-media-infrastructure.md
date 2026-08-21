# ADR-0009: Where call media runs

**Date:** 2026-08-21
**Status:** Proposed — the decision is the owner's, and no code should start before it is made
**Decision Makers:** Solo engineer (owner)
**Design:** [`docs/design/T2-voice-and-video-calls.md`](../design/T2-voice-and-video-calls.md)

## Context

Voice and video calling has never worked in this app (#367–#373). The signalling half is built and
the media half has never had anywhere to run: `LIVEKIT_ENABLED` defaults to `false`, no `LIVEKIT_*`
key exists in the production container, and `NoOpCallRoomProvider` is the live bean. `call_history`
holds 0 rows.

The `0.7.0 — Live` milestone states the constraint directly: *"Blocked on a decision before any code:
where LiveKit comes from — self-hosted on an already-strained box, LiveKit Cloud, or the feature is
hidden until it is real. Do not start the client work before that is settled."*

The architecture is already chosen and is not reopened here: **signalling over the existing
WebSocket, media through an SFU, LiveKit as the SFU.** `CallRoomProvider` is a port with two
implementations, so a different SFU is a swap rather than a rewrite. What this ADR decides is *where
the SFU process runs*, which is a cost, privacy and operations question rather than a code one.

Four facts frame it, each verified on 2026-08-21 and detailed in the design document:

1. **The scale is nine registered users.** A hundred five-minute calls a month is roughly 0.4 GB of
   server egress and a small fraction of one CPU core. At every option considered, **the money is
   under €100/year.** Cost is not the discriminator.
2. **The existing production host cannot take it.** Load average moved between 4.73 and 12.69 on
   8 vCPU over a five-minute window with 3,792 MB of 4,095 MB of swap in use; it is also the only
   self-hosted CI runner, running one job at a time. UFW opens no media UDP port, Traefik publishes
   only 80/tcp and 443/tcp and cannot carry WebRTC, and LiveKit's TURN/TLS fallback wants the 443
   that Traefik holds.
3. **An SFU decrypts the media.** Selective forwarding terminates each participant's DTLS-SRTP
   session. Whoever runs the SFU can listen. This project's E2E is off and libsignal is not even a
   dependency, so frame-level encryption is not available to change that (ADR-003, and
   `CLAUDE.md` → "libsignal upgrade (BLOCKED)").
4. **Processor paperwork is already behind.** `docs/legal/README.md` records that KVKK transfer
   mechanisms and data-processing agreements are unfiled for Hetzner, FCM *and* the SMS provider.

## Decision

**Self-host LiveKit on a small dedicated server that is not the production host.**

1. **Not on the production host.** Not for capacity reasons alone — the firewall, Traefik's hold on
   443, and the CI-runner contention are each independently disqualifying.
2. **Prefer an Istanbul datacentre** if a provider can be verified with a real trial. An SFU is the
   ideal first workload for an unproven Turkish provider: it is stateless, holds no database, and
   its failure mode is "calls stop", not "data is lost". Latency drops from the 35–45 ms band to
   1–10 ms, doubled on the media round trip.
3. **Otherwise a second Hetzner box in Germany**, starting at **CX33 (4 vCPU / 8 GB / 20 TB,
   €8.49/month + €0.50 IPv4)**. Escalate to a dedicated-vCPU plan (CCX23, €85.99) only if call
   quality complaints arrive — the risk with a shared vCPU is scheduling jitter, which is heard
   rather than measured, and it is unverified either way.
4. **LiveKit Cloud is used for stage 0 only**, on the free tier, against test accounts, to prove the
   existing `LiveKitRoomAdapter` token minting works before any time is spent on server operations.
   It is never pointed at real user traffic.
5. **Size against the video numbers, not the voice ones.** Voice is roughly a thirty-fifth of `h720`
   video per minute; a box sized for voice would have to be replaced at stage 4.

## Rationale

- **Cost does not decide it.** Every option is under €100/year at this scale. Choosing on price here
  would be choosing on noise.
- **Who runs the SFU is a privacy claim.** This is a domestic, privacy-first messenger whose main
  competitor is telecom-owned. "Your calls go through a US-hosted service" is a sentence the product
  cannot afford, and with a cloud SFU it would be true. Self-hosting is the only version in which
  the answer to "who can hear this?" is "we can, and nobody else".
- **A fourth unpapered processor is the wrong direction** while three are outstanding, and voice is
  the most sensitive category to add.
- **Latency is the one place domestic hosting is measurably better**, not merely better-sounding.
- **The blast radius of trying a Turkish provider with an SFU is small**, and the operational
  evidence it produces is exactly what a later decision about moving more would need.

## What was rejected, and why

**LiveKit Cloud for production.** It is free at this scale, would cost roughly zero engineer-hours,
and at ten times this scale is a flat $50/month. This is a genuinely strong option and the rejection
is a judgement, not an arithmetic result. If the owner weighs shipping speed above positioning, it is
defensible — with two conditions: the privacy policy and the `/guvenlik` page must say so plainly,
and the processing agreement must be filed **before** the first real call.

**Peer-to-peer WebRTC with no SFU.** Cheaper for 1:1 and end-to-end encrypted by construction. It
still needs a TURN relay for symmetric and carrier-grade NAT, which Turkish mobile networks use
heavily; it exposes each participant's IP address to the other; and it does not extend to group
calls. The existing code already chose an SFU and the `NoOpCallRoomProvider` docstring's claim that
"call signaling still works via WebSocket (SDP/ICE relay)" is **false** — no client here has a
`PeerConnection` or produces SDP.

**Hiding the feature until it is real.** This is what the app does *today*, and it is the correct
state until stage 3 lands. It is rejected as a permanent answer, not as the current one.

## Consequences

- **A second machine to run.** Patching, a TLS certificate, firewall rules, monitoring. Nothing to
  back up — an SFU holds no state. Redis is not needed for a single node; configuring it is what
  switches LiveKit into distributed mode.
- **TURN needs its own domain and a CA-signed certificate.** LiveKit's docs are explicit that
  self-signed certificates do not work, and that without a load balancer the TURN/TLS port must be
  443. That is a second hostname and a second certificate lifecycle.
- **A UDP path must exist**, either the 50000–60000 range or the single muxed UDP port 7882. On a
  dedicated box this is a firewall rule; on the production host it was a fight with Traefik.
- **Calls are not end-to-end encrypted, and must never be described as though they are.** No padlock,
  no shield, no *şifreli* on the call screen. Frame-level E2EE composes with the 1.0.0 encryption
  work and is a follow-up, not a blocker for 0.7.0.
- **Reversible.** `muhabbet.livekit.enabled=false` returns the backend to `NoOpCallRoomProvider`, and
  `CallRoomProvider` means a different SFU or a move to Cloud is four environment variables. Nothing
  in this decision is expensive to unwind.
- **Prices move.** Every figure here was read on 2026-08-21 and the Hetzner numbers are materially
  higher than that vendor's own 2023–24 press releases. Re-read them on the day money is spent.

## Open

- Which Istanbul provider, and does it survive a trial? Most Turkish providers do not publish
  pricing at all — Türk Telekom, Turkcell, Radore and DGN are all quote-only — so this needs contact,
  not research.
- Whether stage 4 (video) changes the sizing enough to want a dedicated-vCPU plan from the start.
  The design document says size for video; the counter-argument is that video may never ship.
