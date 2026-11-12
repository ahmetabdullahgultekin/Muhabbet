# ADR 0026 — Meshtastic adapter: optional, legally gated, compiled out of TR builds

**Status:** Proposed (gated) — confirmed as Proposed (gated) by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); not accepted, and no code may be written against this ADR until its scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits. Even after approval, **no line of the adapter is written** before the written legal opinion in §1 exists · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0026; §6.7, §12 #58–#62, §13 (1.4), §17 Q-L2](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Milestone:** 1.4 "Off-grid hardware" (gated, droppable, TR-excluded until cleared) · Depends on ADR-0012, ADR-0015, ADR-0016, ADR-0018, ADR-0022, ADR-0024 (G-PROXIMITY), ADR-0027, ADR-0028, ADR-0029. Supersedes nothing.

## Context

Gemini proposed LoRa, KISS/TNC framing, a software AFSK modem and amateur VHF/UHF as Muhabbet
transports. Verified facts:

| Topic | Verified fact | Finding |
|---|---|---|
| Duty-cycle code | `"%10 legal limit"` plus a fixed `delay(200L)`. 10% applies only to 869.4–869.65 MHz; 868.0–868.6 MHz is 1%; the limit is per hour, per sub-band. A 200 B frame at SF12/125 kHz is ≈ 7.2 s on air, so a 200 ms pause means ≈ 97% duty cycle | RF-07, GEM-20 |
| AFSK modem | Not Bell 202/AX.25 compatible, no demodulator, never called, inside the transport adapter | GEM-19 |
| KISS decoder | Command byte not stripped, no size limit, leaked collector | GEM-18 |
| Single global dispatcher | Carrier-sense backoff stalls every transport for ≈ 25 s | MISS-01 (Gemini verifier) |
| Amateur VHF/UHF | Licence required, encryption banned, non-commercial only (domestic and international) | RF-08 → ADR-0027 |
| PMR446 | Integral antenna only, 180 s TX cap; Baofeng-class radios excluded | RF-09, MISS-03 (legal) → ADR-0027 |
| **Turkish law** | Law 5809 Art. 39 reserves encrypted communication over *telsiz* to authorised bodies; Art. 63(6) makes a breach a crime (judicial fine). A 2010 regulation on coded/encrypted communication may impose key-deposit duties; whether it covers app-level E2E over licence-exempt (KET) devices is **unclear** | LEG-03 |
| Source quality | The BTK frequency table read by the reviewer is marked *TASLAĞI* (draft); the in-force KET text was not fetched | RF-07 |

On a literal reading of Art. 39 (LEG-03), Muhabbet's E2E payloads over LoRa would be "encrypted
communication over *telsiz*"; whether that reading applies to licence-exempt KET devices is counsel
question 1 (§1).

## Decision

### 1. Legal precondition (blocking)

`:mobile:transport-meshtastic` may be built in 1.4 **only after a written Turkish telecom-law
opinion** that answers, separately:

1. Law 5809 Art. 39 and Art. 63(6): does app-level E2E ciphertext carried over a licence-exempt
   LoRa node fall under "encrypted communication over *telsiz*"?
2. The 2010 regulation on coded/encrypted communications: is it in force unamended, does its
   exemption cover KET (short-range) devices, and does it impose a key-deposit duty?
3. KET conformity of the hardware Muhabbet recommends (Q-L2, brief §17; ADR-0029 rule 7).

A negative or qualified opinion ends 1.4 (kill criterion). Key escrow, if demanded, is a product
decision for the owner, never accepted silently (LEG-03).

### 2. What the adapter is

| Aspect | Decision |
|---|---|
| Hardware | **Certified off-the-shelf Meshtastic nodes** only, from a published hardware list; no raw LoRa PHY, no KISS/TNC, no software modem |
| Interface | BLE or serial to the node, speaking the **Meshtastic protobuf API**; the node firmware does PHY and MAC |
| Port | `SealedLink` (ADR-0012), scope `EXTERNAL_RF` (ADR-0015); accepts only `SealedFrame` (ADR-0016) |
| Payload | Text and receipts only (INTERACTIVE text, CONTROL receipts); **one frame ≈ ≤ 200 B** (unproven: a worked byte budget — `TransportFrame` header with 16 B `frameId`, routing tag and `linkSeq`, link AEAD tag, libsignal `SignalMessage` overhead, padding bucket, and Meshtastic's own payload limit — is written before 1.4 entry, [OI-33](../architecture/multi-transport/open-items.md#oi-33)); no fragmentation; no media, no BULK, no EPHEMERAL. Capacity figures assume SF12/125 kHz; Meshtastic's default preset differs, which the per-preset golden vectors cover |
| Peers | Contacts only, under the same eligibility as ADR-0024 (verified, certificates valid) |
| Sender | Own sender coroutine; duty-cycle waits suspend only this link (MISS-01, Gemini verifier) |
| Package | `com.muhabbet.*` (never `tr.gov.*`, GEM-21) |

### 3. `AirtimeLedger`

```kotlin
// :mobile:transport-meshtastic — pure commonMain, injected Clock (P10)
class AirtimeLedger(private val limits: Map<SubBand, DutyCycleLimit>, private val clock: Clock) {
    fun tryReserve(airtime: Duration, band: SubBand, now: Instant = clock.now()): Boolean
    fun nextAvailable(airtime: Duration, band: SubBand): Instant
}
data class DutyCycleLimit(val fraction: Double, val window: Duration = 1.hours)
```

1. Time-on-air is **computed** per frame from the node's modulation parameters (SF, BW, CR,
   preamble, header and payload length), never assumed.
2. Enforcement is a **sliding one-hour window per sub-band**: 1% on 868.0–868.6 MHz; 10% on
   869.4–869.65 MHz (the Meshtastic EU_868 slot). Limits are **configuration**, not
   constants, so the counsel-confirmed in-force figures replace the draft-table values without code
   change.
3. Reserve **before** send. If the reservation fails, `send` returns `Backpressure(after =
   nextAvailable)` (ADR-0012); nothing is transmitted.
4. `MeshtasticLink` declares `LinkCapabilities(maxFrameBytes = negotiated ≈ 200 B, supportsLive =
   false, supportsBulk = false, offServer = true)`. `SizeRule` and the Preference table
   (`EXTERNAL_RF` last, ADR-0015) do the rest. An exhausted budget surfaces as
   `Backpressure(after)`, not as `constrained`, which marks a bearer on the **server** path only
   (ADR-0015 §2).

> **Resolved (2026-11-12):** brief erratum E-12: the 1.4 scope reads "RF admission policy
> (capability- and size-based: `SizeRule`, `AirtimeLedger`, established-session-only), not the
> server-path `constrained` flag".
5. The ledger is defence in depth; the certified firmware's own regional limits stay on.

### 4. Compiled out of TR builds

- The module is **excluded from TR release flavours by Gradle** (not referenced in their dependency
  graph), not hidden behind a flag.
- A build check fails if `:mobile:transport-meshtastic` appears on any TR release runtime classpath
  (same mechanism as the testkit check, ADR-0016/0028).
- `RegulatoryRule` (ADR-0015) denies `EXTERNAL_RF` in TR as a second line.
- The TR flavour may include the module only after counsel signs off, by an amendment to this ADR.

## Why

- RF-07/08/09 and LEG-03: the legal exposure is criminal, not regulatory, and it attaches to the
  owner as the party that ships the code path.
- Code behind an off flag still ships; anyone who flips it (a user, a fork, a future agent) creates
  that exposure. Absence from the binary is the only control that holds.
- Gemini's `delay(200)` breaks the rules by about 36× (RF-07): ≈ 97% duty cycle for a 200 B SF12
  frame. A ledger computed from the modulation is the minimum correct mechanism (GEM-20).
- Buying the radio (certified Meshtastic firmware) follows P12; a software modem and KISS stack were
  defective in every reviewed aspect (GEM-18, GEM-19).

## Consequences

- **This may never ship in Turkey.** The architecture guarantees only that it would be one adapter
  plus one admission rule.
- Capacity is tiny. At SF12/125 kHz a 200 B frame is ≈ 7.2 s on air: ≈ 5 frames/h on a 1%
  sub-band, ≈ 50 frames/h on the 10% sub-band that Meshtastic EU_868 uses, with receipts consuming
  the same budget (RF-07). The UI must not imply chat-like behaviour over RF.
- Entry to 1.4 also requires G-PROXIMITY in place (certificates, link authentication, `PathPolicy`).
- Exit: ledger tests plus a **one-hour field log within the duty cycle**; text and receipts
  delivered between two certified nodes (owner observation, P13).

> **Resolved (2026-11-12):** adopted as a normative rule (an ML-KEM-1024 ciphertext is 1568 B):
> RF carries only messages in an established session; a conversation becomes RF-eligible only after
> the recipient has replied (no pending `PreKeySignalMessage`). The RF padding bucket in ADR-0022 is
> defined at 1.4 entry (brief erratum E-12).

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| KISS/TNC + software AFSK modem | Wrong layer, defective (GEM-18, GEM-19); hardware does PHY/MAC |
| Raw LoRa PHY from the phone | No certified path; duty-cycle and conformity burden on us |
| Codec2 voice over LoRa / radio | Airtime and legal exposure (brief §12 #62) |
| "Plaintext SOS-only" LoRa | Breaks the no-plaintext-off-server rule (ADR-0016); does not resolve conformity |
| Flag-gated module in TR builds | Shipped code can be enabled; criminal exposure (LEG-03) |
| `delay(200)` cool-down | ≈ 97% duty cycle (RF-07) |

## Evidence

- Findings: RF-07, RF-08, RF-09, RF-01, LEG-03, LEG-01, MISS-01, MISS-02 and MISS-03 (legal
  verifier), GEM-18, GEM-19, GEM-20, GEM-21, MISS-01 (Gemini verifier), PROX-10, C12.
- External sources (from the broadcast-RF-legal, crypto and Gemini-review digests):
  - https://btk.gov.tr/s3/web-btk-site/7708c26c-5161-4919-8b3d-daa31d4ac8fb/2026/05/01c0ec40-b281-44e2-a8e9-3576aea0f354.pdf (BTK draft table)
  - https://docdb.cept.org/download/2369
  - https://meshtastic.org/docs/configuration/radio/lora
  - https://meshtastic.org/docs/introduction/
  - https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5809.pdf (Law 5809, current consolidated text)
  - https://www.alomaliye.com/2010/10/23/kamu-kurum-ve-kuruluslari-ile-gercek-ve-tuzel-kisilerin-elektronik-haberlesme-hizmeti-icinde-kodlu-veya-kriptolu-haberlesme-yapma-usul-ve-esaslari-hakkinda-yonetmelik/ (2010 regulation)
  - https://www.ax25.net/kiss.aspx
  - https://signal.org/docs/specifications/pqxdh/

## Verification

| Req | Check | Method | Signed off by | When |
|---|---|---|---|---|
| — | Written opinion covering §1 items 1–3 exists and is filed with the 1.4 approval conditions | Inspection | Owner | before any code |
| RQ-19 | Ledger refuses a frame that would exceed the hourly budget on each sub-band; time-on-air matches golden vectors: 200 B @ SF12, BW 125 kHz, CR 4/5, preamble 8, explicit header, CRC on, LDRO on ≈ 7.2 s; plus one vector per Meshtastic modem preset actually used, with parameters read from the node config (§3 item 1) | Unit tests with TestClock | CI | 1.4 |
| RQ-19 | One-hour field log stays within the duty cycle | Demonstration | **Owner** | 1.4 exit |
| RQ-06 | `MeshtasticLink` accepts only `SealedFrame` | Compile + `SealedLink` contract suite | CI | 1.4 |
| — | Module absent from every TR release runtime classpath | Gradle build check | CI | from module creation |
| — | `RegulatoryRule` denies `EXTERNAL_RF` in TR | Unit test | CI | 1.4 |
| — | Text and receipts delivered between two certified nodes | Demonstration | Owner | 1.4 exit |

> **Open item [OI-05](../architecture/multi-transport/open-items.md#oi-05):** counsel Q-L2(e) confirms the in-force KET regulation versus the May 2026 *TASLAĞI* table; the limits stay configuration, and no document presents 1%/10% as the in-force Turkish rule until the opinion exists.

> **Open item [OI-06](../architecture/multi-transport/open-items.md#oi-06):** owner decision before 1.4 entry, recorded with Q-O5: how a non-TR build reaches users (separate `applicationId`, Play country targeting, or no non-TR build) and what happens when a Turkish user installs it (runtime region check disabling RF, or nothing). Product flavours do not exist today.

> **Open item [OI-07](../architecture/multi-transport/open-items.md#oi-07):** counsel Q-L2(a)(4) now explicitly covers the channel encryption (AES) performed by the Meshtastic node firmware itself, independently of Muhabbet's E2E payload.
