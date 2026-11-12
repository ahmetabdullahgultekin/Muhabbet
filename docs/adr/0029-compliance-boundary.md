# ADR 0029 — Compliance boundary across all paths

**Status:** Accepted (2026-11-12, delegated) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); code against this ADR is written only while the matching scope reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits. Rule 1 (age gate) is a 0.4 build item once scope `0.4` is approved; the counsel checklist blocks the 1.0 licence step and the 1.1–1.4 gates, not the 0.x milestones · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0029; §1.2; §8.2; §13; §14 items 7–9; §15 R5–R7, R15, R18, R21; §17 Q-L1…Q-L9](../architecture/multi-transport/decision-brief-2026-11-12.md) · Depends on ADR-0010, ADR-0018, ADR-0019, ADR-0021. Gates ADR-0017 (licence step), ADR-0023 (1.1), ADR-0024, ADR-0025, ADR-0026. Supersedes nothing.

> This ADR records engineering controls that keep legal duties performable on every path. It is not
> legal advice and claims no compliance. Every statement about what a law requires is subject to the
> written counsel opinion requested in §8.

## Context

Verified legal facts (broadcast-RF-legal digest, checked against the current mevzuat.gov.tr texts):

| Law | What it means for Muhabbet | Finding |
|---|---|---|
| Law 5809 Art. 3(ççç), 9(14), 60(16)–(17) | Muhabbet matches the OTT (*şebekeler üstü hizmet*) definition: BTK authorisation through a Turkish company; fines of 1–30 M TL; throttling up to 95% or blocking. Art. 9(14) deems an OTT provider an *işletmeci* (operator) for whatever duties the Authority assigns — retention and interception cooperation may follow. The architecture does not change the classification | LEG-01, MISS-01 (legal) |
| Law 5809 Art. 39, 63(6); 2010 coded/encrypted communications regulation | Encrypted communication over *telsiz* is reserved; a breach is a crime. Coverage of app-level E2E over licence-exempt radio is unclear | LEG-03 |
| Law 5651 (consolidated through Law 7590, 24/7/2026) | Authority moved to the **Siber Güvenlik Başkanlığı**. If Muhabbet is a *sosyal ağ sağlayıcı* (channels, stories, communities make it more likely): prosecutor data requests, transparency reports, Turkish hosting above 1 M daily access (Ek Md. 4(6)) | LEG-02, MISS-02 (legal) |
| Law 7578 → 5651 Ek Md. 4(7), in force 2026-11-01 according to the consolidated text's *yürürlük* table (read, not independently verified; legal confirmation pending, Q-L3, [OI-27](../architecture/multi-transport/open-items.md#oi-27)) | If Muhabbet is a *sosyal ağ sağlayıcı* (Q-L4): no service to **under-15s**, with *yaş doğrulama* (age verification). The gate is built regardless, so the obligation can be met if the classification applies (MISS-04 legal) | LEG-02, MISS-04 (legal) |
| KVKK (Law 6698) Art. 3(ı), 9, 12 | On the reviewer's reading (LEG-04, partially confirmed), Muhabbet would be the controller of relaying and relaying users would be neither household-exempt nor contracted processors; Q-L5 asks counsel to confirm. Art. 9 allows consent only for incidental (*arızi*) transfers, not systematic hosting abroad; Art. 12 security duties apply. KVKK has no statutory DPIA article | LEG-04 |

Repo state: no age gate exists ("Kayıt akışında yaş doğrulama kapısı henüz uygulanmamıştır",
`docs/legal/04-kullanim-kosullari.md:35`); the Terms state 18+ with a 16+/parental-consent alternative pending an
`[AGE-POLICY DECISION]` (`docs/legal/04-kullanim-kosullari.md:34`); `docs/legal/README.md:80-91`
names BTK for 5651 and calls the OTT rules drafts; `docs/legal/03-acik-riza-metni.md` uses consent
as a transfer basis; report/block infrastructure exists (`user_reports`, `user_blocks`). Public docs
claim KVKK compliance while erasure is open (SSOT-09, MISS-02 governance, #327).

The architectural risk is specific: **an offline or serverless path is where legal duties evaporate**
— no point to verify age, no reachable controller for takedown, no way to stop a banned device
(LEG-02, MISS-04 legal). ADR-0010 keeps the server as the authority; this ADR makes the duties
reach every path.

## Decision

### Rule 1 — Age gate at registration (0.4 build item)

| Element | Decision |
|---|---|
| Data | `user_age_assurance(user_id, birth_date, level, assured_at)`, a separate table mapped only by the age adapter (Flyway V30; brief erratum E-18; the V27 row-rebuild hazard rules out columns on `users`); stored server-side only |
| Port | `AgeAssurancePort` (backend out-port): `fun assess(user: UserId, e: AgeEvidence): AgeVerdict` |
| Now | `SelfDeclarationAssurance` (date of birth at registration) |
| Later | e-Devlet / NFC-ID adapters behind the same port, if counsel requires (Q-L3) |
| Minimum age | Configuration, not a magic number: `muhabbet.age.minimum-years`, set to the **stricter** of the statute (15) and the published Terms. The Terms say 18 (`docs/legal/04-kullanim-kosullari.md:34`), so the value is **18** until the owner records the `[AGE-POLICY DECISION]` (18, 16 with parental consent, or 15; [OI-08](../architecture/multi-transport/open-items.md#oi-08)), informed by counsel Q-L3(d)/(f)/(h). It is never set below the published Terms |
| Refusal | Below the configured minimum → registration refused with an `ErrorCode` (no inline strings); nothing else is created |
| Existing accounts | Accounts registered before 0.4 have no age-assurance record. They get a one-time attestation prompt at next login/foreground and are not served past a grace period set by counsel (Q-L3(c); default **30 days**, [OI-35](../architecture/multi-transport/open-items.md#oi-35)). Scheduled as a 0.4 item if counsel answers in time, otherwise 0.5 |
| Claim | **No compliance claim** in any document or UI until counsel confirms whether self-declaration meets *yaş doğrulama* (Q-L3, R7) |
| From 1.2 | A valid age attestation is a **precondition for a device certificate** (ADR-0018), therefore for every off-server path |

```kotlin
sealed interface AgeVerdict {
    data class Assured(val level: AgeAssuranceLevel) : AgeVerdict   // SELF_DECLARED | EDEVLET | NFC_ID
    data object BelowMinimum : AgeVerdict
    data class NeedsStrongerEvidence(val accepted: Set<AgeAssuranceLevel>) : AgeVerdict
}
```

### Rule 2 — Broadcast surfaces are server-only

Channels, communities and groups above the off-server cap (1:1 only in 1.2, so **every** group
today) are **server-only**. They never use `LAN`, `PROXIMITY`, `UPLINK_RELAY` or `EXTERNAL_RF`
(`ScopeRule`, ADR-0015; custodians 2–4, ADR-0025). This keeps 5651 takedown central for every
one-to-many surface.

### Rule 3 — Reports are durable outbox commands

A report made offline is a CONTROL-class outbox entry (ADR-0013) and reaches the server when it is
reachable; it is never dropped. Reports carry reporter-supplied content (the server cannot read E2E
content, ADR-0019/0021).

### Rule 4 — Block lists on every inbound path

The block list is enforced in the mobile **inbound pipeline** for every `InboundSource`
(`WsInboundSource`, `RestChangeFeedSource`, `FcmHintSource`, `LanLink`, `NearbyLink`, uplink,
`MeshtasticLink`), after dedup and before persistence. The server keeps enforcing it on the server
path and in contact lookup (§8.6 of the brief).

### Rule 5 — Ban, takedown and revocation reach offline peers within ≤ 7 days

| Trigger | Server action (immediate) | Reach on server paths | Reach on off-server paths |
|---|---|---|---|
| Account ban / under-age discovered | `AccountBanned` → revoke all devices, refresh tokens, close all WS sessions (`SessionRevocationPort`, BE-12) | Immediate | Certificate **not renewed** + signed **CRL delta** pushed over WS (`crl.delta`); peers refuse at next CRL sync or certificate expiry, **≤ 7 days** |
| Device revoke / logout / account deletion | `DeviceRevoked` / `SessionRevoked` | Immediate (socket closes ≤ 5 s, 0.4 exit) | Same as above |
| Takedown of a channel/community item | Server removal (rule 2 keeps it server-only) | Immediate | n/a (never off-server) |

The ≤ 7-day window is **accepted and disclosed (owner decision D4, 2026-11-12)**: it is stated in the
transparency text and the Nearby-mode copy (R15, RQ-18).

### Rule 6 — DPIA triggers

A DPIA (best practice serving the KVKK Art. 12 security duty, and a self-imposed release gate; LEG-04) is written and signed **before**
the change ships when any of these is introduced:

| Trigger | First milestone |
|---|---|
| Proximity scanning (BLE/Wi-Fi/mDNS discovery) | 1.2 |
| Uplink relay (contacts transiently holding ciphertext) | 1.3 |
| TURN/ICE logging or telemetry (candidate type, IP family, ASN) | 0.7 |
| Contact-discovery changes | 0.4 / 0.6 |
| A new processor, or a new foreign host | any |
| DT-6: kill-criterion or other new telemetry about off-server sessions (e.g. Nearby sessions with one side offline) | 1.2 |
| DT-7: age assurance (date of birth, identity evidence) | **0.4** — the age-gate DPIA is required before 0.4 ships (counsel Q-L3(g); [OI-36](../architecture/multi-transport/open-items.md#oi-36)) |

> **Resolved (2026-11-12):** DT-6 is accepted (brief erratum E-14); DT-7 (age assurance) is added in
> the row above.

### Rule 7 — The counsel request names every path

The counsel request (brief §17) covers E2E over **BLE and Wi-Fi (Nearby/LAN), Wi-Fi Direct via
Nearby, and LoRa**, each separately — not LoRa alone (Q-L2, R6).

### 8. Counsel questions and what each one blocks or informs

| Question (brief §17) | Blocks / informs |
|---|---|
| Q-L1 OTT authorisation, *işletmeci* duties (5809 Art. 3(ççç)/9(14)) | Must be answered before 1.0 (R5: E2E vs. retention/interception) |
| Q-L2 Art. 39/63(6) and the 2010 regulation over BLE, Wi-Fi/LAN, Wi-Fi Direct, LoRa | 1.2 (BLE/Wi-Fi), 1.4 (LoRa) |
| Q-L3 Self-declared age vs. *yaş doğrulama* | any compliance claim about the age gate |
| Q-L4 *Sosyal ağ sağlayıcı* classification and thresholds; takedown for E2E content | Informs 1.0 moderation design (R18) |
| Q-L5 KVKK roles of relaying users | 1.3 |
| Q-L6 Art. 9 basis for FCM/APNs wake hints and LiveKit/TURN hosting | Informs the 0.5 KVKK notice update and the 0.7 DPIA; blocks nothing in 0.x |
| Q-L7 AGPL distribution via Google Play and the App Store | 1.0 licence step (ADR-0017) |
| Q-L8 ICE telemetry as personal data | Informs the 0.7 telemetry notice/DPIA; blocks nothing in 0.x |
| Q-L9 Retention of server-side delivery metadata vs. LOCAL_ONLY | LOCAL_ONLY (with Q-O3) |

### 9. Per-path compliance matrix

| Path (ADR-0015) | Who may use it | Age gate | Block list | Report path | Ban/takedown reach | Legal gate |
|---|---|---|---|---|---|---|
| `SERVER_RELAY` | registered accounts | at registration | server + inbound pipeline | direct | immediate | — |
| `IP_RELAYED` / `IP_DIRECT` (calls) | registered; relay-only for non-contacts | at registration | call signalling via server | direct | credential TTL ≤ 10 min | DPIA (rule 6); Q-L6/Q-L8 inform, do not block |
| `LAN` / `PROXIMITY` | verified 1:1 contacts with valid certificates | certificate precondition | inbound pipeline | outbox command | ≤ 7 days | Q-L2, DPIA (G-PROXIMITY) |
| `UPLINK_RELAY` | verified contacts as mules | certificate precondition (both) | server on ingest + inbound pipeline | outbox command | server-side, immediate | Q-L5, DPIA, crypto review |
| `EXTERNAL_RF` | verified contacts; **not in TR builds** | certificate precondition | inbound pipeline | outbox command | ≤ 7 days | Q-L2 (written), KET |

## Why

- Laws 5651/7590, 7578, 5809 Art. 9(14)/39 and KVKK Art. 9/12 (rows in Context).
- Offline modes must not be where legal duties evaporate; the only workable anchor is the server
  identity plus a short-lived certificate that cannot be renewed after a ban.
- These are the security judge's must-fixes (brief §3, judge must-fix closure).

## Consequences

- The registration flow changes in 0.4 (V30, `AgeAssurancePort`, new `ErrorCode`, TR + EN strings).
- `docs/legal` corrections (brief §14 item 8): 5651 authority is the Siber Güvenlik Başkanlığı; add
  Law 7578 and 5651 Ek Md. 4; cite 5809 Art. 9(14)/39/60 as statute, not draft; remove consent as
  the systematic Art. 9 transfer basis. #327 (media erasure) stays open.
- Moderation of E2E content relies on reporter-supplied content; message franking is post-1.0
  research needing legal input (R18).
- A **claims register** (each public privacy or compliance claim → the test, observation or opinion
  that proves it) is a 1.0 exit item (SSOT-09, RQ-20).
- The counsel checklist blocks the 1.1/1.2/1.3/1.4 gates and the 1.0 licence step, not 0.x.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| UI-only age check | Not enforceable server-side; no precondition for certificates |
| Client-side content scanning | Breaks the E2E promise; not required by any verified duty |
| Channels and communities over proximity | Puts one-to-many content outside central takedown (LEG-02) |
| Serverless/mesh identity | Cannot answer 5651 requests or enforce the under-15 ban; inability is not a defence (LEG-02, MISS-01 legal) |
| Year-long certificates or link-key rotation as revocation | Offline exposure window too wide (ADR-0018) |

## Evidence

- Findings: LEG-01, LEG-02, LEG-03, LEG-04, LEG-05, MISS-01, MISS-02, MISS-04 (legal verifier), RF-07,
  RF-08, C8, M3, BE-12, BE-13, BE-15, SSOT-09, MISS-03 and MISS-04 (product-state verifier),
  GOV-08, PROX-03, MISS-2 (networking verifier).
- Repo: `docs/legal/README.md:80-91`, `docs/legal/03-acik-riza-metni.md`,
  `docs/legal/04-kullanim-kosullari.md:32-35`,
  `backend/src/main/kotlin/com/muhabbet/moderation/`.
- External sources (from the broadcast-RF-legal digest; current consolidated texts):
  - https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5809.pdf (Law 5809)
  - https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5651.pdf (Law 5651)
  - https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf (KVKK)
  - https://www.rekabetregulasyon.com/ott-haberlesme-hizmetleri-icin-yeni-donem-btk-taslaklari-goruse-acti/ (BTK OTT drafts)

## Verification

| Req | Check | Method | Signed off by | When |
|---|---|---|---|---|
| RQ-10 | Registration below the configured `muhabbet.age.minimum-years` (currently 18) is refused and nothing is persisted; the test is parameterised on the configured minimum | Backend test + owner emulator check | CI + owner | 0.4 exit |
| RQ-10 | Self-declaration accepted as *yaş doğrulama* | Analysis (written opinion, Q-L3) | Counsel | before any compliance claim |
| RQ-09 | A banned or revoked device's sockets close within 5 s | Test + demonstration | CI + owner | 0.4 exit |
| RQ-18 | A banned device is refused off-server within ≤ 7 d (no renewal, CRL delta) | TestClock test + demonstration | CI + owner | 1.2 exit |
| — | Block list applied per `InboundSource` | One inbound-pipeline test per transport | CI | 0.5 (server sources), 1.2+ (others) |
| — | Channel/community/group messages never get an off-server candidate | `ScopeRule` unit test | CI | 1.2 |
| — | An offline report reaches the server after reconnect | Engine test (CONTROL outbox entry) | CI | 0.5 |
| — | DPIA signed for each trigger before its milestone ships | Inspection (milestone entry checklist) | Owner | per milestone |
| RQ-20 | Claims register complete | Inspection | Owner | 1.0 exit |

> **Resolved (2026-11-12):** adopted as the decision in Rule 1 above: the minimum age is configuration
> (`muhabbet.age.minimum-years`) set to the stricter of the statute and the published Terms, currently
> **18**, never 15.
> **Open item [OI-08](../architecture/multi-transport/open-items.md#oi-08):** the owner records the `[AGE-POLICY DECISION]` (18, 16 with parental consent, or 15) before 0.4 ships, informed by counsel Q-L3(d)/(f)/(h); until then the configured value is 18.

> **Note:** KVKK has no statutory DPIA article (LEG-04 verifier). Every document words the
> DPIA as best practice serving the Art. 12 security duty and a self-imposed release gate (brief
> erratum E-15); Q-L5(f) asks counsel whether one is legally required.

> **Resolved (2026-11-12):** Rule 1 above now covers accounts registered before 0.4: they get a
> one-time attestation prompt at next login/foreground and are not served past the grace period.
> **Open item [OI-35](../architecture/multi-transport/open-items.md#oi-35):** counsel Q-L3(c) sets the grace period for pre-0.4 accounts; the default until then is 30 days.
