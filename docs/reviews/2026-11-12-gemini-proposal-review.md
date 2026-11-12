# Review of the Gemini multi-transport proposal — 2026-11-12

> **Status: Proposed — pending owner approval.** This review is part of the multi-transport design
> package. Nothing in it is accepted, and no code may be written on the strength of it, until the
> owner records approval for the relevant scope in
> [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md).
> It authorises nothing on its own; the decisions it points to live in the
> [decision brief](../architecture/multi-transport/decision-brief-2026-11-12.md) (the single source of
> truth) and ADR-0010 … ADR-0033.

| | |
|---|---|
| **Date** | 2026-11-12 |
| **Base** | `dev` @ 2434324, branch `claude/muhabbet-p2p-architecture-wdan5r` |
| **Subject** | The owner's three-turn chat with Google Gemini on P2P / multi-transport messaging for Muhabbet (Turkish; pasted by the owner) |
| **Owner's request** | "Question, criticise, and present your likes about the Gemini chat; share revisions; say what is right and wrong." |
| **Evidence** | [Findings register](2026-11-12-findings-register.md): 211 findings, each produced by a reviewer and re-checked by an independent adversarial verifier. This review uses the 26 code-review findings (`GEM-*` plus 4 verifier misses) and the 65 fact-check findings (`PROX-*`, `NAT-*`, `QUIC-*`, `P2P-*`, `ICE-*`, `LAN-*`, `LINK-*`, `LIB-*`, `C1`–`C13`, `M1`–`M3`, `RF-*`, `LEG-*` plus verifier misses). |
| **Conventions** | `G:n` = line *n* of `gemini-chat.md` in the review workspace: an English, condensed rendering of the owner's Turkish three-turn paste (Gemini's Kotlin reproduced as pasted; editorial notes marked NOTE). It is not committed (see reviewer note 3 in §8). Verifier-found misses share IDs across sections in the register, so they are prefixed here: `GEM MISS-01`, `PROX MISS-01`, `NAT MISS-1`, `CRYPTO M1`, `RF MISS-01`. Severities are the register's post-verification values. |

---

## Türkçe özet

- **Genel hüküm:** Gemini doğru *kelimeleri* ve büyük ölçüde doğru *yönü* buldu (taşıyıcıdan bağımsız
  hexagonal çekirdek, doğrudan/dolaylı mod ayrımı, BLE ile keşif ve Wi-Fi'ye yükseltme, aday
  yarıştırma, dönen anonim tanımlayıcılar, ciddi bir doküman seti). Ancak ayrıntıların çoğu
  yanlış, abartılı ya da Türkiye'de yasal olarak riskli. Gözden geçirilen 51 bulgudan etiketli 37 iddianın yalnızca 1'i
  tamamen doğru, 20'si kısmen doğru, 14'ü yanlış, 2'si doğrulanamadı; kalan 14 bulgunun iddia etiketi yok — çoğu
  Gemini'nin hiç değinmediği önkoşul eksikleri. Uydurma (fabricated) olduğu kanıtlanan iddia yok, ama verdiği 5
  kaynak bağlantısının 3'ü ölü, 1'i takip edilmemiş bir yönlendirme sayfası.
- **En önemli hatalar:** "Doğum günü paradoksu ile %86" (gerçekte ≈%63, iki taraf da EDM ise ≈%0);
  BLE'den Wi-Fi Direct'e QUIC bağlantı göçü (RFC 9000'e aykırı); arka planda sürekli BLE keşfi (iOS
  bunu Android'e görünmez kılar); "kilometrelerce BLE mesh" (Bridgefy iki kez kırıldı); Kademlia
  DHT'de PQXDH ön-anahtarları (tek kullanımlık anahtar garantisini bozar); sunucusuz MLS; LoRa'da
  `delay(200)` ile "%10 görev döngüsü" (gerçekte ≈%97); amatör telsiz (lisans, şifre yasağı,
  ticari olmayan kullanım — üç kez yasa dışı); `tr.gov.muhabbet` paket adı.
- **Kod:** Reddedildi (REJECT). Düzeltme değil yeniden tasarım gerekiyor: güvenilirlik katmanı yok
  (ACK, yeniden gönderim, kalıcı kuyruk yok), tek global dağıtıcı P0'ı aç bırakıyor, çerçevede adres
  ve kimlik doğrulama yok, şema şifre çözülmüş metni ve düz FTS indeksini şifreli metnin yanında
  tutuyor, kod commonMain'de derlenmiyor. Gemini kodundan hiçbir satır kopyalanmayacak.
- **Ne kalıyor:** Sunucu otorite olarak kalıyor (ADR-0010); diğer her yol yalnızca uçtan uca şifreli
  bayt taşıyan ek bir boru. Sıra: önce mevcut yolun güvenilirliği (0.4–0.5), sonra gerçek E2E (1.0),
  sonra kapılı ve iptal edilebilir ek yollar: P2P 1:1 arama (1.1), LAN + Google Nearby Connections
  (1.2), kendi cihazların ve doğrulanmış bir kişinin telefonu üzerinden tek atlamalı mühürlü iletim
  (1.3), Meshtastic LoRa (1.4, Türkiye derlemelerinden avukat onayına kadar çıkarılmış).
- **Sorularınıza kısa yanıt:** Telefonda uydu TV uygulaması bugün mümkün değil ve mesajlaşma taşıyıcısı
  değil. "En güçlüden zayıfa" sıralaması kabul, ama ölçüt medya değil *kapsam ve garanti*: önce kapı
  (yasal, E2E, onay), sonra teslim garantisi, sonra gecikme, sonra maliyet. Dolaylı mod kabul, ama
  yabancıların telefonları üzerinden değil: sunucu posta kutusu → kendi cihazlarınız → doğrulanmış
  bir kişinin çevrimiçi telefonu. İstediğiniz doküman ve diyagramların tamamı bu pakette var (§6.4).

---

## 1. Verdict summary

Gemini produced a **good map and a bad route**. It named the right building blocks (ports and
adapters, ICE/STUN/TURN, BLE-then-Wi-Fi escalation, PQXDH and the Double Ratchet, store-and-forward,
rotating identifiers, QoS lanes, an arc42/ADR/ICD/threat-model document set) and the right
direct/indirect distinction. It then assembled them in ways that break each part's own assumptions,
quoted numbers that do not survive arithmetic or measurement, proposed rebuilding things Muhabbet
already depends on or can buy, ignored Turkish law, and attached code that would not compile in
`commonMain`, loses every message on process death and would key an amateur transmitter illegally.
Of the 51 reviewer fact-check findings (verifier misses excluded), 37 carry a claim label: **1 correct,
20 partially correct, 14 wrong, 2 unverifiable, 0 shown to be fabricated**; the other 14 carry no label and mostly record
prerequisites Gemini never mentioned, most importantly that Muhabbet's E2E is a NoOp that sends plaintext (C9). The code is **REJECT**.
What survives is the *shape*, re-expressed under a server-authoritative backbone (ADR-0010) with
every non-server path gated on real, fail-closed E2E (ADR-0016) and on a written admission gate (P8).

### Scorecard per Gemini section

Legend for "Direction": **sound** = the idea survives into the design; **mixed** = the idea survives
after a material correction; **unsound** = rejected. "Facts" counts register findings against that
section's claims.

| Chat section | What Gemini proposed | Direction | Facts (register) | Survives as |
|---|---|---|---|---|
| T1 matrix (G:12–22) | 11 technologies × speed/latency/range/fitness | mixed | PROX-07 correct; PROX-08 unverifiable; PROX-01/09 partial; RF-02/03/06 partial or wrong | [Capability matrix](../architecture/multi-transport/transport-capability-matrix.md), every device cell "unmeasured" until observed (P13) |
| T1 A — internet P2P (G:24) | DHT or signalling, ICE/STUN, WebRTC/QUIC/libp2p, TURN or Circuit Relay, mDNS | mixed | NAT-3 unverifiable; P2P-1 partial; P2P-3 wrong; ICE-1, LIB-1, LAN-1 partial | ADR-0023 (WebRTC ICE + own TURN, calls only); ADR-0024 (`LanLink`) |
| T1 B — off-grid wireless (G:25) | BLE mesh "km via phones", BLE→Wi-Fi Direct/Aware, sidelink | mixed | PROX-02/03 wrong; PROX-04/05 partial; PROX-06 (already exists); PROX-07 correct | ADR-0024 (buy Nearby Connections, foreground, 1:1 verified contacts) |
| T1 C — RF (G:26) | LoRa, VHF/UHF AFSK, PMR446 | unsound except Meshtastic | RF-07/08/09 wrong; LEG-03 | ADR-0026 (Meshtastic, gated, TR-excluded); ADR-0027 (rest rejected) |
| T1 D — satellite (G:27) | D2C text, NTN broadband, Starlink as Wi-Fi | mixed | RF-06 partial | `constrained` flag on `SERVER_RELAY` (ADR-0015); Starlink = ordinary IP (§12 #65) |
| T1 E — acoustic/optical (G:28) | ggwave 18–22 kHz, animated QR | unsound | PROX-09 partial | Static safety-number QR (ADR-0018 (6)) |
| T2 Q1 — satellite TV on phone (G:40–41) | Dish physics; 5G Broadcast/ROM; L/S-band TV; RTL-SDR | physics sound, alternatives unsound | RF-04 partial; RF-02 partial; RF-03, RF-05 wrong | Not a feature (ADR-0027); answered in §6.1 |
| T2 Q2 — levels 0–6, direct/indirect (G:42–50) | Medium-ranked levels; Happy Eyeballs; epidemic DTN | mixed | LINK-1 wrong; C6, C7; LEG-04 | Path scopes + rule-based `PathPolicy` (ADR-0015); custody hierarchy (ADR-0025) |
| T2 3A — architecture (G:51–53) | Hexagonal core, `ChannelMetrics`, RS/fountain FEC | mixed | PROX MISS-03; C12 partial; GEM-05, GEM-07 | ISP-split link ports (ADR-0012); no FEC (§6.7) |
| T2 3B — BLE + Wi-Fi Direct escalation (G:54–60) | ~1 mW passive BLE, L2CAP ~500 kbps, zero-click Wi-Fi Direct, QUIC migration | mixed | PROX-01/04 partial; PROX-02 wrong; QUIC-1 wrong; PROX-06 | Make-before-break escalation inside Nearby; continuity by entry ids and acks (§6.8) |
| T2 3C — CGNAT + serverless E2EE (G:61–67) | IPv6 → ICE → birthday punching → volunteer relays; PeerID, DHT prekeys, P2P MLS | unsound | NAT-1, P2P-3, C2, C3 wrong; NAT-2/4, P2P-1, C1, C11 partial; P2P-2 | ADR-0010, ADR-0017, ADR-0018, ADR-0023; rejected items in ADR-0027 |
| T2 Q4 — patterns (G:68–73) | CQRS, transactional outbox, P0–P3 queue, event sourcing, Merkle-CRDT, HSM, event bus | mixed | C4, C5 partial; GEM-01/02 | Light CQRS, mobile outbox, per-link lanes, flat sealed states (ADR-0011/0012/0013, §12 #72–75) |
| T3 1 — 5G Broadcast sources (G:84–95) | "Not theoretical"; 4 standards; 5 URLs | partly right, sources weak | RF-01 (3 of 5 URLs dead) | §3.2 |
| T3 2 — documents and diagrams (G:96–106) | 7 documents, 8 diagrams | sound | — | This package (§6.4) |
| T3 wire frame (G:107–117) | "MU" sync word, UUIDv7, CRC-32C, per-frame RS | unsound | GEM-06/07/08; GEM MISS-03 | `TransportFrame` with AEAD-authenticated header (ADR-0022) |
| T3 Kotlin code (G:118–483) | Orchestrator, HLC, Merkle-DAG, SQL, RF adapter | unsound | GEM-01…22, GEM MISS-01…04 | Nothing copied (§12 #80); see §4 |
| T3 ADR-001…004 (G:485–489) | CRDT not server; L2CAP not GATT; QUIC migration; FEC not ARQ | unsound (3 of 4; ADR-002's L2CAP-over-GATT choice is sound but superseded by buying Nearby) | C5, QUIC-1, C12, PROX-06 | Replaced by ADR-0010, ADR-0024, ADR-0023/0027, §6.7 |

---

## 2. What Gemini got right

Credit where it is due. These are specific, and each one is in the design.

1. **The framing.** Splitting the problem into physical/link media and logical P2P transport, and
   judging each by bandwidth, latency and loss (G:7), is the right first cut. The design keeps the
   split and adds the axis Gemini missed: *who is allowed to carry what* (admission).
2. **A transport-agnostic, hexagonal core.** "The crypto engine does not know the physical channel"
   (G:30, G:51) is exactly right for Muhabbet's ports-and-adapters house style. It becomes
   `ServerLink` / `SealedLink` / `InboundSource` (ADR-0012), with the twist that a non-server port
   accepts only a `SealedFrame` (ADR-0016).
3. **Direct versus indirect mode.** Naming a live session and store-and-forward as two modes (G:50)
   is the most useful idea in the chat, and the owner was right to like it. It is the backbone of the
   brief's thesis (§1.1 item 4) and ADR-0025.
4. **Discover over BLE, then move to Wi-Fi.** The pattern is sound (PROX strengths); it is precisely
   what Google Nearby Connections and Ditto ship. The design buys it rather than builds it (ADR-0024).
5. **Racing instead of sequential timeouts.** Happy-Eyeballs-style racing (G:42) is right; ICE already
   does it for candidate pairs (ICE-1). The design races at link establishment (brief §6.8, §7).
6. **Graceful de-escalation** after idle (G:60) — adopted with hysteresis (§6.8: 30 s idle,
   10 s minimum up).
7. **Rolling ephemeral identifiers against tracking** (G:55) — adopted as rotating pairwise tags that
   are not linkable to a public key (§8.5).
8. **"The relay sees only ciphertext"** (G:66) is the right principle for relays, TURN and custodians
   (NAT strengths). The design enforces it by type rather than by intention.
9. **Correct physics and spectrum facts.**
   - A phone cannot receive GEO Ku-band TV without a dish; the ≈36 dB path-loss gap is right at
     550 km (20·log10(35786/550) = 36.3 dB) (RF-04).
   - The n255/n256 frequency ranges are numerically correct (RF-03).
   - ETSI TS 103 720 Receive-Only Mode really needs no USIM (clause 5.4.8; RF strengths).
   - 5G NR sidelink is not exposed to apps (PROX-07).
   - LoRa and VHF need external hardware (G:26).
   - Starlink is "normal broadband via Wi-Fi" (G:27) — exactly how the design treats it (§12 #65).
10. **Crypto primitives named correctly.** PQXDH = X25519 + an IND-CCA post-quantum KEM with
    Kyber-1024 as the spec's example (C1); Double Ratchet for 1:1; the HLC send/receive rule matches
    Kulkarni et al. (C4, GEM strengths); the Merkle node hash covers ciphertext rather than plaintext.
11. **QoS classes and backpressure.** Separating control, interactive, media and bulk (G:70), and
    saying backpressure must exist (G:73), is right. It becomes CONTROL / INTERACTIVE / BULK /
    EPHEMERAL with per-link lanes and credits (§6.5, §6.6, §6.9).
12. **Transactional outbox and CQRS** (G:69–70) — adopted on mobile, lightly (§12 #72, #74).
13. **Small-MTU awareness** (G:52–53): LoRa and BLE do need different framing than IP. The design
    delegates fragmentation to the medium and caps Meshtastic at one ≈200 B frame (§6.7).
14. **Out-of-band verification** by QR (G:67) — adopted as Signal-style single-scan safety-number QR,
    which is also the eligibility condition for proximity (ADR-0018 (6)).
15. **Meshtastic and Reticulum as references** (G:26) — the only RF idea that survives, as an adapter
    to certified off-the-shelf Meshtastic nodes (ADR-0026).
16. **Side channels as side channels.** Gemini treated ultrasonic and QR as key-exchange channels, not
    data channels (PROX strengths).
17. **KISS escaping** in `encodeKissFrame` (G:451–454) matches the KISS spec, and the p-persistent
    backoff intent fits a shared half-duplex medium (GEM strengths).
18. **The document set** (G:96–106): arc42 SAD, ADRs, ICD, STRIDE + LINDDUN threat model, data model,
    chaos/test plan, C4. This is a professional list and the package adopts it almost entirely
    (§12 #77, §6.4 below).
19. **L2CAP CoC rather than GATT for bulk BLE** (G:56, ADR-002 at G:487) is the right primitive on both
    platforms (iOS 11+, Android API 29+; PROX strengths). It is moot only because the design buys
    Nearby (ADR-0024); the throughput figure was wrong (PROX-01).

---

## 3. What is wrong

### 3.1 Claim-by-claim table

Verdicts: **correct**, **partially correct**, **wrong**, **unverifiable**, **fabricated** (none were
shown to be), and **omission** where the finding records something Gemini needed to say and did not.
"Evidence" lists the register's primary sources (URLs exactly as they appear in the area digests) or
repo file:line. The register holds the verifier's full notes for each row.

#### Proximity (PROX)

| ID | Gemini claim | Verdict | Corrected statement | Evidence |
|---|---|---|---|---|
| PROX-01 (medium) | BLE L2CAP CoC instead of GATT, "~500 kbps, ~40 ms" (G:56) | partially correct | API exists on both platforms (iOS 11+, Android API 29+). Apple's own best case is ≈197–394 kbit/s; measured iPhone↔iPhone 1 MB transfers are ≈36–138 kbit/s. iOS assigns the PSM dynamically, so a GATT round-trip precedes every cross-platform L2CAP session. Muhabbet's minSdk is 26 (`mobile/composeApp/build.gradle.kts:149`), so API 26–28 has no CoC. | https://developer.apple.com/forums/thread/89644 · https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createL2capChannel(int) · https://developer.apple.com/tutorials/data/documentation/corebluetooth/cbperipheralmanager/publishl2capchannel(withencryption:).md |
| PROX-02 (high) | "BLE passive discovery ~1 mW", continuous, with a 16-byte rolling EID (G:55) | wrong | Background iOS advertising drops the local name and moves service UUIDs into an "overflow" area readable only by iOS scanners, so a backgrounded iPhone is effectively invisible to Android; the EID cannot ride in it. Android needs a live process or foreground service. Proximity must be a **foreground, user-started mode**. | https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html |
| PROX-03 (high) | "Multi-hop BLE mesh can reach kilometres via other users' phones" (G:25) | wrong | No phone API: Bluetooth SIG Mesh treats phones as GATT-proxy clients. App-level meshes failed (FireChat) or were broken cryptographically twice (Bridgefy: CT-RSA 2021; USENIX Security 2022, after adopting libsignal). With today's NoOp crypto it would be plaintext custody by strangers. | https://eprint.iacr.org/2021/214 · https://www.usenix.org/conference/usenixsecurity22/presentation/albrecht · https://www.nordicsemi.com/Products/Bluetooth-mesh/What-is-Bluetooth-mesh?lang=en |
| PROX-04 (high) | "Zero-click" Wi-Fi Direct by bypassing the OS dialog (G:58) | partially correct | Works only Android 10+ ↔ Android 10+ with a known `DIRECT-xy` name and passphrase (`WifiP2pConfig.Builder`, API 29); "no dialog" is not documented and is vendor-dependent. **iOS has no Wi-Fi Direct API**, so Android↔iOS Wi-Fi Direct is impossible. | https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder |
| PROX-05 (medium) | Wi-Fi Aware as a peer of Wi-Fi Direct for offline 1080p calls (G:14, G:44) | partially correct | Real on Android 8+ and iOS 26 (iPhone 12+). iOS requires explicit user pairing per peer and an entitlement. iPhone↔Android needs NAN v4 pairing that Android firmware reportedly does not yet implement (one community report: plausible, not vendor-confirmed). Android↔Android opportunistic upgrade only. | https://developer.android.com/develop/connectivity/wifi/wifi-aware · https://developer.android.com/reference/android/net/wifi/aware/AwarePairingConfig · https://developer.apple.com/tutorials/data/documentation/wifiaware.md |
| PROX-06 (high) | Build the BLE-discover → Wi-Fi-upgrade protocol (G:54–60) | omission (already exists) | Google Nearby Connections already does this on Android and ships an iOS Swift package (BLE, LAN, hotspot upgrade with an iOS system prompt and location authorisation). The iOS package is "not officially supported"; Android↔iOS interop is undocumented and must be spiked. Needs Play services. **Buy, do not build.** | https://developers.google.com/nearby/connections/overview · https://developers.google.com/nearby/connections/strategies · https://developers.google.com/nearby/connections/android/get-started |
| PROX-07 (info) | 5G NR sidelink is not accessible to standard Android/iOS APIs (G:25) | correct | Correct. But the T1 matrix still rates it "10–100+ Mbps, 200 m–1 km, perfect" (G:16), which is misleading in a capability table. Listed as rejected (ADR-0027). | https://developer.android.com/develop/connectivity/wifi/wifi-permissions · https://arxiv.org/html/2502.16650v1 |
| PROX-08 (low) | Wi-Fi Direct/Aware 50–250 Mbps, 50–200 m; BLE 125 kbps–2 Mbps, 10–100 m (G:14, G:17) | unverifiable | Unsourced PHY ceilings presented as app goodput. 2 Mbit/s is the BLE 2M PHY air rate; phone-to-phone goodput is ≈0.05–0.4 Mbit/s. Replace with measured medians and p10 from the 1.2 spike; every unmeasured cell reads "unmeasured". | https://docs.ditto.live/sdk/v4/deployment/network-deployment · https://developer.apple.com/forums/thread/89644 |
| PROX-09 (low) | Ultrasonic 18–22 kHz at 0.1–2 kbps; animated QR (G:22, G:28) | partially correct | ggwave runs at 8–16 bytes/s (64–128 bit/s) and its "ultrasonic" band starts at 15 kHz; phone hardware rolls off above ≈19 kHz. Neither channel adds anything over a static safety-number QR. Rejected (ADR-0027). | https://github.com/ggerganov/ggwave · https://signal.org/blog/safety-number-updates/ |
| PROX-10 (medium) | Implied: "always, everywhere" via phones (G:30) | omission (reference systems) | What is feasible: same-OS high-bandwidth proximity, slow cross-OS BLE text between awake devices, contact-to-contact sync (Briar), long range only with dedicated LoRa hardware. Berty describes BLE as the only Android↔iOS link and calls it unstable. Briar Android-only status was not re-verified (404). | https://berty.tech/protocol · https://berty.tech/blog/bluetooth-low-energy/ · https://briarproject.org/how-it-works/ |
| PROX-11 (high) | — (Gemini assumes a working crypto layer) | omission | Muhabbet has zero proximity code, minSdk 26, and `NoOpEncryption` on both platforms (`PlatformModule.android.kt:44`, `PlatformModule.ios.kt:32`). Link-layer encryption exists (WPA2, LE Secure Connections) but authenticates the radio peer, not the recipient. Proximity is strictly downstream of real E2E. | repo (verified) |
| PROX MISS-01 (medium) | — | omission | iOS shows a Local Network prompt for Bonjour / Multipeer / Nearby LAN, plus Bluetooth, and the hotspot path adds Location and a join confirmation. Needs a consent matrix. | verifier |
| PROX MISS-02 (low) | BLE then Wi-Fi Direct handoff (G:58) | partially correct | Android API 36 standardises BLE-assisted Wi-Fi Direct R2 pairing — Android 16+ with R2 firmware only; no help for iOS. Moot: custom Wi-Fi Direct is rejected. | verifier |
| PROX MISS-03 (low) | "BLE L2CAP 512B" MTU (G:52) | wrong | 512 B is the ATT attribute-value cap, not the L2CAP CoC SDU MTU; CoC throughput is set by MPS, credits, DLE and connection interval. Derive chunk size at runtime. | verifier |
| PROX MISS-04 (medium) | — | omission | Apple Wi-Fi Aware allows background connections to already-paired devices — a better iOS↔iOS path than background BLE. Unmeasured. See reviewer note 1 (§8). | verifier |

#### Networking and NAT (NAT, QUIC, P2P, ICE, LAN, LINK, LIB)

| ID | Gemini claim | Verdict | Corrected statement | Evidence |
|---|---|---|---|---|
| NAT-1 (high) | "Birthday-paradox punching from 256 random ports: probability of a collision is over 86%" (G:65) | wrong | 1−(1−256/65535)^256 ≈ **63%** (exact 0.6336). Tailscale's 64% models only *one* side behind a hard (EDM) NAT; with **both** sides EDM — Gemini's own justifying case — Tailscale gives ≈0.01% after 20 s. It also "really looks like a port scan". Rejected. | https://tailscale.com/blog/how-nat-traversal-works |
| NAT-2 (low) | "Symmetric CGNAT (address and port-dependent mapping)" (G:62) | partially correct | Right concept, outdated label. Record mapping (EIM / ADM / APDM) and filtering (EIF / ADF / APDF) separately, plus port preservation, hairpinning and binding lifetime. RFC 4787 makes EIM a MUST; EIM with any filtering is punchable by plain ICE. | https://www.rfc-editor.org/rfc/rfc4787.html · https://www.rfc-editor.org/rfc/rfc6888.html |
| NAT-3 (medium) | "Turkish mobile operators put users behind symmetric CGNAT" (G:24, G:62) | unverifiable | No published measurement shows Turkish CGNs are EDM. IPv6 capability (APNIC, rolling 30 days): Turkey ≈22%; Vodafone TR ≈56%, Turkcell ≈14%, TTNet ≈31%, **TT Mobil ≈0.6–1.2%**. Only our own ICE telemetry can answer the NAT question (brief §12 #39). | https://stats.labs.apnic.net/ipv6/TR |
| NAT-4 (medium) | Stage 1: IPv6 direct with simultaneous open through stateful firewalls (G:63) | partially correct | Standard ICE/UDP behaviour, so not a separate stage; ICE gathers IPv6 host candidates alongside IPv4 srflx and relay. Mobile carrier firewalls are not bound by RFC 6092 and are unmeasured in Turkey. | https://www.rfc-editor.org/rfc/rfc6092.html |
| QUIC-1 (high) | "QUIC connection migration keeps the session and moves the flow from BLE to the 5 GHz Wi-Fi socket" (G:59; ADR-003 at G:488) | wrong | RFC 9000: "Only clients are able to migrate in this version of QUIC", and migration is a change of UDP/IP 4-tuple. BLE L2CAP is not IP, and Gemini's own 512 B MTU is below QUIC's 1200 B minimum datagram. Continuity belongs to the application layer: entry ids, acks and ratchet state (§6.8). | https://www.rfc-editor.org/rfc/rfc9000.html |
| QUIC-2 (info) | "QUIC + custom ICE" (G:45, G:104) | omission | Multipath QUIC is now RFC 10069 (Nov 2026); QUIC NAT traversal is an individual draft (draft-seemann-quic-nat-traversal). Neither is in a mobile OS stack. Watch-list only. | https://datatracker.ietf.org/doc/draft-ietf-quic-multipath/ · https://datatracker.ietf.org/doc/draft-seemann-quic-nat-traversal/ |
| P2P-1 (high) | Decentralised relay via libp2p Circuit Relay v2 and volunteer desktop supernodes (G:66) | partially correct | Relay v2 exists but defaults to **2 min / 128 KiB per direction** (go-libp2p `resources.go`): a hole-punch coordination channel, not a media relay. Volunteer supernodes add Sybil, availability and KVKK/5651 liability. Use TURN on our own infrastructure. | https://github.com/libp2p/specs/blob/master/relay/circuit-v2.md · https://pkg.go.dev/github.com/libp2p/go-libp2p/p2p/protocol/circuitv2/relay |
| P2P-2 (medium) | Implied: hole punching mostly succeeds, relay is the edge case (G:62–66) | omission | The largest measurement (4.4M attempts, 167 countries) finds DCUtR succeeds **70% ± 7.1%**; TCP and QUIC are statistically indistinguishable. ≈30% of pairs need a relay. Budget TURN for 30–50% relayed calls. | https://arxiv.org/abs/2510.27500 |
| P2P-3 (high) | PeerID identity and PQXDH prekeys published in a Kademlia DHT, no phone number (G:67) | wrong | Phones can only be DHT clients, so whoever runs the always-on nodes is a server under less control. A DHT cannot atomically hand out and consume a one-time prekey. Phone-less identity conflicts with the existing auth module and Turkish subscriber duties. | https://berty.tech/blog/gomobile-ipfs/ |
| ICE-1 (high) | Happy Eyeballs + custom ICE + QUIC adapter, `QuicWebRtcAdapter (ICE/STUN/TURN/BirthdayNAT)` (G:42, G:104) | partially correct | Racing is right; ICE (RFC 8445) already races candidate pairs, WebRTC adds DTLS-SRTP, congestion control and ICE restart. Happy Eyeballs is for client-to-server. LiveKit 1:1 is client-SFU-client, not P2P. Repo already depends on `io.livekit:livekit-android:2.28.0` (`mobile/composeApp/build.gradle.kts:115`, dev @ 2434324; the digest recorded 2.27.0 from an earlier snapshot). | https://datatracker.ietf.org/doc/draft-ietf-happy-happyeyeballs-v3/ · https://docs.livekit.io/home/self-hosting/deployment/ |
| LAN-1 (medium) | Same-LAN discovery via mDNS; `LanMdnsAdapter (multicast UDP + TLS 1.3)` (G:24, G:104) | partially correct | Bonjour with declared `NSBonjourServices` works but shows the iOS 14+ Local Network prompt; hand-rolled multicast needs a restricted Apple entitlement. Use `NsdManager` / `NWBrowser`, no raw multicast. | https://developer.apple.com/forums/thread/663875 |
| LINK-1 (medium) | "Level 0: USB-C Ethernet / Wi-Fi LAN … Level 2: Internet direct" (G:43–45); `LAN_MDNS_ETHERNET(0)` (G:133) | wrong | Ethernet, Wi-Fi and cellular are links; the OS picks the route and ICE enumerates all interfaces. The meaningful axis is reachability **scope**. Apps can request a transport (Android `NetworkRequest`); the iOS equivalent was not verified this session. | https://developer.android.com/reference/android/net/ConnectivityManager |
| LIB-1 (high) | KMP stack of QUIC-P2P, libp2p and WebRTC adapters (G:51, G:104) | partially correct | Only native WebRTC/LiveKit is production-ready. No official LiveKit KMP client exists; `webrtc-kmp` is a community wrapper (Apache-2.0, iOS SDK added manually). kwik is JVM-only; libp2p from KMP is a research project. | https://github.com/shepeliev/webrtc-kmp · https://github.com/ptrd/kwik · https://www.github.com/libp2p/jvm-libp2p |
| NAT MISS-1 (high) | — | omission | No media-path infrastructure exists: no LiveKit server, no STUN/TURN, no UDP exposed in either compose file; `LIVEKIT_*` unset in prod. This is the real critical path (0.7, ADR 0009). | verifier, repo |
| NAT MISS-2 (medium) | — | omission | Direct ICE paths reveal the peer's public IP (often a stable IPv6 /64). Privacy default: relay-only for non-contacts plus an "always relay" setting (ADR-0023). | verifier |
| NAT MISS-3 (low) | — | omission | The official LiveKit route means two native stacks; the iOS side needs an `@objc` Swift shim for cinterop. Spike before committing (1.1 entry). | verifier |

#### Crypto, sync and DTN (C1–C13, CRYPTO M1–M3)

| ID | Gemini claim | Verdict | Corrected statement | Evidence |
|---|---|---|---|---|
| C1 (medium) | "PQXDH (X25519 + ML-KEM/Kyber-1024)" (G:67); "PQXDH + Double Ratchet engine in Kotlin" offered (G:490) | partially correct | Primitives right. PQXDH protects only the handshake; post-quantum ratcheting is SPQR (availability in the Java/Android bindings unverified). PQXDH's guarantees rely on a server dispensing each one-time prekey once. **Never hand-roll it**: use libsignal (ADR-0017). | https://signal.org/docs/specifications/pqxdh/ · https://signal.org/blog/spqr/ |
| C2 (high) | Signed PQXDH prekey bundle published into a Kademlia DHT (G:67) | wrong | No atomic take-and-delete, so one-time prekeys are handed out twice or dropped; a signature proves authorship, not freshness; Sybil/Eclipse let an attacker deny and observe lookups; lookups leak who contacts whom. The server stays the prekey dispenser (ADR-0018). | https://signal.org/docs/specifications/x3dh/ · https://www.usenix.org/conference/usenixsecurity22/presentation/prunster |
| C3 (high) | "IETF MLS … tree-based key management for P2P groups of thousands" (G:67); `mls_epoch INTEGER` (G:353) | wrong | MLS needs a Delivery Service to order Commits; offline concurrent Commits fork the group, and keeping forks alive undermines forward secrecy. Decentralised MLS is an individual draft. MLS with a server DS is research; groups use sender keys at 1.0. | https://www.rfc-editor.org/rfc/rfc9750.html · https://datatracker.ietf.org/doc/draft-kohbrok-mls-dmls/ |
| C4 (high) | Hybrid Logical Clock for conflict-free ordering (G:71, G:286–325) | partially correct | Update rule matches the paper; the paper's out-of-bounds rejection is missing, so one peer with a clock years ahead poisons every clock it touches, transitively. `%05d` counter breaks lexicographic order past 99999. HLC is not an ordering authority (ADR-0014). | https://cse.buffalo.edu/tech-reports/2014-04.pdf |
| C5 (medium) | Event sourcing + Merkle-CRDT; each node Ed25519-signed and hash-chained (G:71, G:329–333) | partially correct | Long-term signatures make authorship transferable (no deniability); hash chains over payloads break on KVKK erasure and disappearing messages; with a server sequence the DAG adds nothing. Rejected (ADR-0014, §12 #12, #14). | https://arxiv.org/abs/2004.00107 |
| C6 (medium) | Epidemic / Spray-and-Wait, "4 copies + hop limit 8", custody, PoW anti-spam (G:101, G:105, G:371–381) | partially correct | Spray-and-Wait bounds copies, not hops; BPv7 (RFC 9171) moved custody transfer out of the core protocol; receipts that garbage-collect copies need authenticity; PoW punishes phones. BPv7 concepts are borrowed, the codec is not (ADR-0025). | https://www.rfc-editor.org/rfc/rfc9171.html |
| C7 (high) | Custody table with `target_peer_id_hash TEXT NOT NULL` and an index on it (G:373, G:381) | wrong | A stable recipient hash is a permanent pseudonym every mule can log with time and place: a co-location graph. Use rotating pairwise tags, padding, sealed sender. In the design the uplink mule sees only an HPKE-sealed, bucket-padded blob (ADR-0025). | https://signal.org/blog/sealed-sender/ |
| C8 (high) | — (Gemini treats hashed contacts as private) | omission | Muhabbet's contact discovery hashes E.164 with unsalted SHA-256 (`AuthService.kt:339`, helper `:478–479` on dev @ 2434324), accepts 1000 hashes per request (`ContactSyncService.kt:21`), returns name and avatar, and stores the plaintext number anyway (V1). Only an edge per-IP limiter bounds it (CRYPTO M3). Fix: per-account cap, discoverability with a reader (brief §8.6). | https://eprint.iacr.org/2020/1119 |
| C9 (critical) | "X cannot read" the custody bundle (G:105) | omission | E2E today is NoOp: `E2EConfig.kt:27 ENABLED=false`, `PlatformModule.android.kt:43–44` binds NoOp, four `*.kt.disabled` libsignal files, and the runbook's documented "falls back to plaintext on any failure". Every mule would hold plaintext. Fail-closed by type (ADR-0016). | repo (verified) |
| C10 (medium) | — (Gemini assumes an atomic dispenser exists somewhere) | omission | The server's one-time prekey dispenser is not atomic either: `EncryptionKeyPersistenceAdapter.kt:37–53` reads the list, takes the first, saves `used=true`, with no lock or version; two concurrent fetches can return the same key. Fix in 0.4 with `FOR UPDATE SKIP LOCKED` (ADR-0018). | https://signal.org/docs/specifications/x3dh/ · https://signal.org/docs/specifications/pqxdh/ |
| C11 (medium) | Identity = PeerID = Blake3(Ed25519 public key), no phone number (G:67) | partially correct | Self-certifying ids are sound in Briar/Session, but they are a different product: device loss is identity loss, multi-device needs per-device keys, and Blake3 gives no Sybil resistance. Keep the phone account; add per-device libsignal identity keys (ADR-0018). | https://getsession.org/session-protocol-v2 |
| C12 (low) | Reed-Solomon / fountain (LT) codes recover 20% loss without ARQ (G:53); RS parity per frame (G:114); ADR-004 (G:489) | partially correct | Per-frame RS duplicates PHY FEC + CRC; lost packets are erasures, recoverable only by coding *across* fragments (RaptorQ beats LT). The LoRa PHY claim was not fetched. No FEC in the design; cross-chunk erasure coding is research, RF only (§6.7). | https://www.rfc-editor.org/rfc/rfc6330 |
| C13 (medium) | — (Gemini proposes a DAG because there is no order) | omission | The server orders by `server_timestamp` (V1) with no per-conversation sequence: ties inside a transaction, no gap detection. *This* is the missing primitive, not a Merkle-DAG: per-conversation `seq` plus a change feed (ADR-0014, V28). | repo (verified) |
| CRYPTO M1 (medium) | — | omission | Any authenticated user can drain another user's one-time prekeys: no relationship check and no per-requester limit. Fix with C10 (0.4). | verifier |
| CRYPTO M2 (medium) | — | omission | The key schema is per-user, not per-device; per-device directory is a prerequisite (V33, 0.6). | verifier |
| CRYPTO M3 (low) | — | omission | C8 missed the deployed Traefik per-IP limiter; the real gap is per-account and no discoverability or block filter. | verifier |

#### Broadcast, RF and legal (RF, LEG, RF MISS)

| ID | Gemini claim | Verdict | Corrected statement | Evidence |
|---|---|---|---|---|
| RF-01 (low) | "Not theoretical: 3GPP/ETSI/EBU standardised" with 5 URLs (G:91–95) | partially correct | Only the ETSI URL supports the claim; 3 are dead and 1 is an unfollowed redirect stub (table 3.2). No design document cites Gemini's URLs. | §3.2 |
| RF-02 (medium) | 5G Broadcast (FeMBMS) live TV to all phones, no SIM, "used live … 2024 Paris Olympics" (G:41, G:84) | partially correct | ETSI TS 103 720 and ROM are real; ROM needs no USIM. But it is downlink-only linear TV, consumer phones do not ship ROM firmware/middleware, and the Paris example was a trial on specially configured handsets (reported as ≈250 Xiaomi Mi13; not re-fetched). It cannot carry a message. No Turkish deployment found (unverified either way). | https://www.etsi.org/deliver/etsi_ts/103700_103799/103720/ · https://digitalradioinsider.blogspot.com/2024/07/5g-broadcast-radio-and-tv-summer-tests.html |
| RF-03 (medium) | "S-band / L-band satellites to all phones … live TV" via n255/n256; DVB-SH (G:41, G:88–89) | wrong | n255 and n256 are **paired FDD two-way NTN bands** with uplinks, not broadcast TV. DVB-SH was a commercial failure (not re-fetched). Gemini merged three unrelated things. | https://itecspec.com/3gpp/38.101-5/s/5.2.2 · https://www.wikiwand.com/en/articles/DVB-SH |
| RF-04 (info) | "340–550 km vs 35,786 km, ~65×, ~36 dB" (G:40) | partially correct | Right at 550 km (36.3 dB), understated at 340 km. "Phones only hear 700 MHz–3.8 GHz" understates modern phones, but none has a Ku-band front end. Conclusion stands. | arithmetic |
| RF-05 (low) | "USB-C RTL-SDR or DVB-T2/S2 tuner … decode terrestrial digital broadcasts" (G:41) | wrong | The RTL2832U "does not support DVB-T2 as it lacks the required demodulator hardware". Any tuner is receive-only, with no messaging value. Turkey's DVB-T2 status unverified. | https://lists.osmocom.org/pipermail/osmocom-sdr/2019-August/001967.html |
| RF-06 (medium) | `TransportType.SATELLITE_D2C_NTN(7)` as an app-selected transport (G:20–21, G:134) | partially correct | Direct-to-cell is an OS/carrier **IP network**; the app sees a constrained network (Android `PROPERTY_SATELLITE_DATA_OPTIMIZED`). Nothing commercial in Turkey; Starlink reported unlicensed (Daily Sabah, per RF-06). → a `constrained` flag on `SERVER_RELAY` (ADR-0015). | https://developer.android.com/develop/connectivity/satellite/constrained-networks |
| RF-07 (high) | "LoRa / radio duty-cycle (%10 legal RF occupancy limit) and cool-down" then `delay(200L)` (G:444–445) | wrong | 10% applies only to 869.4–869.65 MHz; 868.0–868.6 MHz is 1%; 868.7–869.2 MHz is 0.1%; duty cycle is airtime over an hour per sub-band. A 200 B SF12/125 kHz packet is ≈7.2 s on air, so a 200 ms pause is **≈97% duty cycle**; at 1% it needs ≈714 s of silence. The BTK table read is a **draft** (TASLAĞI). → `AirtimeLedger` (ADR-0026). | https://btk.gov.tr/s3/web-btk-site/7708c26c-5161-4919-8b3d-daa31d4ac8fb/2026/05/01c0ec40-b281-44e2-a8e9-3576aea0f354.pdf · https://meshtastic.org/docs/configuration/radio/lora · https://docdb.cept.org/download/2369 |
| RF-08 (high) | VHF/UHF (144/430 MHz) AFSK packet radio as a Muhabbet transport (G:19, G:26; `VHF_UHF_AFSK_RADIO`) | wrong | Illegal three ways under the BTK draft and ITU RR: an amateur licence is required, "kriptolu" traffic is barred for domestic and international traffic, and use must be non-commercial. Rejected permanently (ADR-0027). | https://lral.lv/oldwww/hp/yl2dx/S25.html · BTK PDF above |
| RF-09 (medium) | PMR446 and Baofeng-class handhelds for data (G:26) | wrong | PMR446 requires an integral antenna (500 mW e.r.p.) and a 180 s transmitter timeout; Baofeng-class radios have detachable antennas. Data over AFSK is legally unclear. (Correction RF MISS-03: digital use is permitted since 2018 — this does not rescue AFSK.) Rejected permanently. | BTK PDF above (Tablo 14) |
| LEG-01 (high) | — (Gemini treats architecture as able to avoid regulation) | omission | Muhabbet matches Law 5809's OTT definition (Art. 3(ççç)); Art. 9(14) requires BTK authorisation through a Turkish AŞ/Ltd; Art. 60(16)–(17): fines of 1–30M TL and throttling up to 95% or blocking. A P2P or mesh mode does not change the classification. | https://www.alomaliye.com/wp-content/uploads/2008/11/5809-sk-25-12-2025.doc (verifier re-checked against the current mevzuat.gov.tr text) |
| LEG-02 (high) | — | omission | Law 5651 (through Law 7590, 24/7/2026) moved powers to the Siber Güvenlik Başkanlığı. If Muhabbet is a "sosyal ağ sağlayıcı" (channels, stories, communities make it likelier): Turkish hosting above 1M daily access, prosecutor data duties and, from Law 7578 (in force 1/11/2026 per the consolidated text's *yürürlük* table, not separately verified; counsel-questions S-14), an under-15 ban with *yaş doğrulama*. A serverless mode removes the ability to comply, not the duty. → age gate in 0.4 (ADR-0029). | https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5651.pdf |
| LEG-03 (high) | — (Gemini proposes encrypted LoRa/radio) | omission | Law 5809 Art. 39(1) reserves encrypted communication over *telsiz* to TSK and listed bodies, others only under Authority rules; Art. 63(6) makes a breach a crime (500–1000 days' judicial fine). The 2010 coded/encrypted communications regulation (status unverified) may add a key-deposit duty. Item 1 of the counsel request; names BLE and Wi-Fi too (Q-L2). | 5809 text above · verifier |
| LEG-04 (high) | — (Gemini's DTN leaves bundles on strangers' phones) | omission | On the reviewer's reading (register: partially confirmed), Muhabbet decides purposes and means of relaying and would therefore be the KVKK controller, with relaying users neither household-exempt nor contracted processors; counsel to confirm (Q-L5). Custody metadata is personal data. KVKK Art. 9(6) allows explicit consent only for incidental transfers. No statutory DPIA article, but Art. 12 applies; a DPIA is required by ADR-0029. | https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf |
| LEG-05 (low) | `package tr.gov.muhabbet…` (G:120, G:286, G:386) | wrong (artefact) | `gov.tr` is the government's zone; a private product must not claim it. Keep `com.muhabbet.*`; never copy Gemini code. Same as GEM-21. | chat |
| RF MISS-01 (high) | — | omission | Law 5809 Art. 9(14) deems an OTT provider an *işletmeci* (operator) for obligations the Authority assigns — potentially retention and interception cooperation that a serverless mode could not meet. The strongest legal argument against serverless (Q-L1). | verifier |
| RF MISS-02 (medium) | — | omission | Law 7590 also amended Law 5809; the relied-on articles are unchanged but some powers now sit with the Siber Güvenlik Başkanlığı. Cite only the current consolidated text. | verifier |
| RF MISS-03 (low) | — | correction to RF-09 | PMR446 digital use permitted since 1 January 2018; integral-antenna and type-approval rules still exclude Baofeng-class radios. | verifier |
| RF MISS-04 (medium) | — | omission | The age gate is not built; under 5651 verification, not a policy line, is the requirement, and it cannot be enforced on an unregistered mesh peer. → only registered, age-attested accounts on every path (ADR-0029). | verifier |

#### Gemini claims that were not fact-checked

These were not in any reviewer's mandatory set. They are recorded so nobody cites them as verified.

| Claim | Status | Why it does not matter to the design |
|---|---|---|
| "Live video needs 500 kbps–2 Mbps and < 150 ms latency" (G:31) | not checked; plausible | Media rate adaptation is LiveKit/WebRTC's job (§12 #67). |
| LoRa "2–5 km urban, 15+ km open" (G:26) | not checked | Any range claim comes from the 1.4 field log (RQ-19), not from a chat. |
| ETSI TS 103 720 "250 km/h mobile reception, up to 100 km tower range" (G:86) | not checked | Broadcast is rejected (ADR-0027). |
| 3GPP TS 36.300 and TS 23.246 define FeMBMS/EnTV (G:87) | not checked | As above. |
| GATT "limited to 20–247-byte notifications" (Gemini ADR-002, G:487) | not checked | Custom GATT/L2CAP is rejected in favour of Nearby (ADR-0024). |
| Escalate at 250 KB queued or a live call; de-escalate after 15 s idle (G:57, G:60) | design parameters, not facts | The brief uses `ESCALATE_BYTES` = 256 KiB, `IDLE_CLOSE_MS` = 30 s, hysteresis 10 s (§6.8), to be tuned by the spike. |
| Battery weights 0.1 (LAN) … 0.9 (5G/Wi-Fi Direct) (G:52) | invented constants | No numeric fitness score in the design (ADR-0015, GEM-05). |

### 3.2 Gemini's cited URLs

Gemini cited sources only once, in turn 3, after the owner explicitly asked for them. Status as
re-fetched by the RF verifier with `curl -L` (RF-01):

| URL as cited by Gemini (G:91–95) | Result | Usable as evidence? |
|---|---|---|
| https://www.etsi.org/deliver/etsi_ts/103700_103799/103720/ | HTTP 200, directory listing; TS 103 720 V1.2.1 is real and clause 5.4.8 confirms ROM needs no USIM | Yes — for that one fact |
| https://www.5g-mag.com/broadcast | HTTP 200, 337-byte meta-refresh stub to another path, not followed | No — content unverified |
| https://www.3gpp.org/technologies/multimedia-broadcast-multicast-service-mbms | Redirects to https://www.3gpp.org/404 | No — dead |
| https://www.rohde-schwarz.com/solutions/test-and-measurement/wireless-communication/cellular-standards/5g-broadcast_253399.html | HTTP 404 | No — dead |
| https://tech.ebu.ch/5g | HTTP 403 | No — dead for us |

A dead link does not prove invention (pages move), so nothing is labelled *fabricated*. It does mean
that **3 of 5 sources supported nothing and a fourth could not be read**, after the owner had asked
"are you sure, give sources" — and the one source that resolves supports only the narrowest part of
the claim (ROM exists), not the conclusion (TV to all phones today). No design document cites any
Gemini URL; the ETSI spec is cited directly.

### 3.3 Patterns of error

| Pattern | Examples |
|---|---|
| Plausible numbers that fail arithmetic or measurement | 86% (NAT-1), ~500 kbps BLE (PROX-01), 2 kbps ultrasonic (PROX-09), 10% duty cycle (RF-07), 512 B L2CAP MTU (PROX MISS-03), 36-byte header (GEM-06) |
| Correct primitive, broken composition | PQXDH in a DHT (C2), MLS without a Delivery Service (C3), HLC without its bound (C4), signatures on a deniable protocol (C5) |
| Category errors | Link vs path (LINK-1), satellite as a transport (RF-06), broadcast as messaging (RF-02/03), QUIC over BLE (QUIC-1) |
| Rebuilding what exists | Nearby Connections (PROX-06), ICE (ICE-1), LiveKit already a dependency (ICE-1), TNC/firmware PHY (GEM-19) |
| Background and cross-platform optimism | Always-on BLE (PROX-02), zero-click Wi-Fi Direct to iOS (PROX-04), Aware interop (PROX-05) |
| Legal blindness | OTT operator status (LEG-01, RF MISS-01), encryption over *telsiz* (LEG-03), amateur radio (RF-08), KVKK relaying (LEG-04), age gate (LEG-02), `tr.gov` (LEG-05) |
| Silence on prerequisites | NoOp E2E (C9), non-atomic prekeys (C10), no seq (C13), contact enumeration (C8), no TURN at all (NAT MISS-1) |

---

## 4. Code review summary

**Verdict: REJECT.** Not "fix and merge", and not "keep as a reference implementation" (brief §12 #80).

### 4.1 Findings

| ID | Sev | Defect | Where the design answers it |
|---|---|---|---|
| GEM-03 | critical | No reliability layer: every `transmitChunk` `Result` discarded, no ACK, retransmission or reassembly; in-memory `Channel` queues lost on process death — a regression from the existing `PendingMessage` table | Durable outbox state machine, persist-then-ack, idempotent receivers (ADR-0013, §6.1) |
| GEM-08 | critical | Wire frame has no source, destination, lifetime or nonce; CRC-32C is not integrity; UUIDv7 id leaks creation time | Outer `TransportFrame`: random 16-byte id, `ttlSec`, rotating `routingTag`, header as AEAD associated data (ADR-0022) |
| GEM-01 | high | Idle dispatcher suspends only on the P1 queue: a P0 call-setup envelope waits for an unrelated text, then loses to it | Per-link sender coroutine with biased `select` over CONTROL / INTERACTIVE / BULK (§6.6) |
| GEM-02 | high | Priority per whole envelope; one large envelope (≈5,600 LoRa chunks, > 18 min) blocks everything; Wi-Fi escalation runs under a `Mutex` on the data path | Preemption between chunks; escalation is a link-state action, make-before-break, inside Nearby (§6.6, §6.8) |
| GEM-04 | high | `!!` on a metrics map read twice; any exception kills the single unsupervised dispatcher for good | Per-link supervised senders; no `!!` (CLAUDE.md) |
| GEM-06 | high | Header constant 36 B vs 31/32 B in the diagram; no nonce or tag budget; `coerceAtLeast(64)` builds a 132-byte frame for a 51-byte MTU | MTU negotiated, never constant; sizes computed by one codec (§10 rules, MISS-03) |
| GEM-07 | high | Reed-Solomon parity inside each chunk cannot recover a lost chunk — the actual failure | No FEC; delegate to the medium; cross-chunk erasure coding is research (§6.7) |
| GEM-09 | high | "Spray-and-Wait" double-counts one peer over BLE and LoRa, sends full copies with no copy budget, no custody handshake, dedup or quota | Custody hierarchy; strangers never; multi-hop is research R-DTN with an L ≤ 4 budget and quotas (ADR-0025) |
| GEM-10 | high | HLC has no drift bound: one far-future clock poisons every node transitively | Server `seq` is the order; sender time advisory and clamped to `[receivedAt − 5 min, receivedAt]` (ADR-0014) |
| GEM-11 | high | `java.security.MessageDigest`, `System.currentTimeMillis`, `String.format` in "pure domain" code: does not compile in `commonMain`; `@Synchronized` is a no-op on Native | Injected `Clock` / `IdGenerator` (P10); crypto only in `:mobile:crypto` adapters (P12) |
| GEM-13 | high | Merkle hash concatenates fields with no length prefix or domain separation: `("AB","xyz")` = `("A","Bxyz")` | Anything hashed or signed uses length-prefixed, domain-separated encoding (§10 rules); no DAG at all |
| GEM-14 | high | Schema keeps decrypted content and a plaintext FTS5 index beside the ciphertext; the repo's DB is unencrypted today | SQLCipher from 0.5; contentless FTS5 *inside* the encrypted DB with delete triggers (ADR-0020) |
| GEM-19 | high | AFSK "modulator" puts raw KISS bytes on air MSB-first, no NRZI, HDLC flags, bit stuffing or FCS; no demodulator; never called | No software modem; certified Meshtastic hardware only (ADR-0026, ADR-0027) |
| GEM-20 | high | `delay(200)` is not a duty-cycle limiter: a 51 B SF12 packet is ≈2.47 s on air, needing ≈245 s silence at 1% | `AirtimeLedger` sliding one-hour window per sub-band from SF/BW/CR (ADR-0026) |
| GEM-05 | medium | Fitness score mixes units (20 ms of RTT = 1 s of transmission; 1% loss = 5 s), magic weights, ignores priority, excludes DTN from the DTN links | No numeric score: capabilities + metrics + rule chain (ADR-0015) |
| GEM-12 | medium | `%05d` counter breaks string order past 99999; Int overflow; negative times sort backwards | No HLC column; integer `seq` |
| GEM-15 | medium | Parents as JSON, free-text enums, no edge table or indexes, no SQLDelight migrations | `.sqm` migrations + `verifyMigrations` before any schema change (ADR-0028); §11.2 schema |
| GEM-17 | medium | Fat `TransportPort`; RF adapter returns fake `Result.success` for a session it cannot open (ISP/LSP) | ISP split: `ServerLink`, `SealedLink`, `InboundSource`, `LiveSessionPort` (ADR-0012, ADR-0023) |
| GEM-18 | medium | KISS receiver keeps the command byte, accepts bad escapes, unbounded boxed buffer, leaked collector | Moot (no KISS); fuzz targets for every parser (§10 rules) |
| GEM-21 | medium | `tr.gov.muhabbet` package | `com.muhabbet.*` (ADR-0028) |
| GEM-22 | medium | Sharing starts in a property initialiser; double start gives two dispatchers; no stop | Engine lifecycle owned by `MessagingEngine` and `SessionTeardown` (ADR-0012, ADR-0020) |
| GEM-16 | low | `ByteArray` in data classes: reference equality breaks dedup | Value classes with content equality (§10 rules) |
| GEM MISS-01 | high | CSMA backoff (up to 24,750 ms per chunk) and the post-send delay run inside the global dispatcher, stalling every transport | Per-link senders: medium access suspends only its own link (§6.6) |
| GEM MISS-02 | medium | P3 to a reachable but slow peer is sprayed to custodians instead of sent directly | Custody is offered only when no eligible direct path exists (§7: `Stay(QUEUED)` → `CustodyPolicy`) |
| GEM MISS-03 | medium | `serializeWireChunk` returns only the payload bytes: no header goes on the wire, so nothing can be reassembled | Codec with golden vectors before any adapter (§10, §16.2) |
| GEM MISS-04 | low | Integer HLC columns exist, but the index sorts on the broken TEXT copy | Moot |

The verifier also corrected the code reviewer in four places (GEM-18 misstated the KISS spec on
invalid escapes; GEM-12 overstated how fast the counter overflows; GEM-17 blamed paste-garbled
generics on the design; several severities were lowered). The table uses the corrected severities.

### 4.2 Why this is a redesign, not a fix

A defect list this long could still describe fixable code. It does not, because the defects sit in
the load-bearing decisions, and fixing each one means replacing it:

1. **The state model is missing.** There is no persisted message state at all (GEM-03), so "add ACKs"
   means adding the outbox, inbox, idempotency keys, receipts and the state machine — the whole of
   ADR-0013. Nothing of the original survives that.
2. **The concurrency model is wrong.** One global dispatcher that owns every link (GEM-01, GEM-02,
   GEM-04, GEM MISS-01) cannot be patched into per-link lanes; it has to be replaced by them (§6.6).
3. **The wire format cannot express the problem.** A frame without addressing, lifetime or an
   authenticated header (GEM-08) and with the wrong FEC layer (GEM-07) is a new ICD, not an edit.
4. **The data model contradicts the security goal.** Plaintext and an FTS index beside ciphertext in an
   unencrypted DB (GEM-14), plus a signed hash-chained DAG (C5, GEM-13), conflict with E2E,
   deniability and KVKK erasure. Removing them removes the data model.
5. **The ordering model is unsafe** (GEM-10, GEM-12, C4) and unnecessary once the server sequences
   (C13, ADR-0014).
6. **The RF half is unbuildable as a product.** It is illegal on amateur and PMR bands (RF-08, RF-09),
   non-compliant on LoRa (GEM-20, RF-07), and incompatible with every receiver (GEM-19).
7. **It does not compile where it must live** (GEM-11) and claims a government namespace (GEM-21).
8. **It solves a problem Muhabbet does not have yet.** Muhabbet's one existing path still loses and
   mis-reports messages (MOB-03, MOB-04, BE-06), and E2E is a NoOp (C9). A second, parallel message
   stack would double the surface to verify (brief §1.1).

**Where the code reviewer's remedies differ from the brief, the brief wins.** The code-review digest
sketched remedies that assumed Gemini's serverless premise: "DAG first, HLC second", signed per-author
sequence numbers as the authority, proof-of-work or rate limits for relays, a cross-bundle chunk
scheduler and a generic fragmenter. The brief rejects each: the server `seq` is the authority with no
DAG and no long-term message signatures (ADR-0014, C5); anonymous rate tokens replace PoW and only for
research R-DTN (ADR-0025); fragmentation is delegated to the medium (§6.7). The digest's
fail-safe principles (persist, per-link senders, codec-first, `AirtimeLedger`) are kept.

---

## 5. Revisions: Gemini's architecture as corrected

### 5.1 What survives, and where

| Gemini idea | Survives as | Decision |
|---|---|---|
| Transport-agnostic hexagonal core | `:mobile:messaging` engine with ISP-split ports; `SealedLink` takes only `SealedFrame` | [ADR-0012](../adr/0012-delivery-engine-and-link-ports.md), [ADR-0016](../adr/0016-e2e-fail-closed-type-gate.md) |
| Direct mode | `LiveSessionPort` (calls: SFU at 0.7, P2P 1:1 at 1.1) and Nearby-mode sessions (1.2) | [ADR-0023](../adr/0023-internet-direct-ice-turn.md), [ADR-0024](../adr/0024-proximity-lan-and-nearby.md) |
| Indirect mode | Custody hierarchy: server mailbox → own devices → single-hop sealed uplink via a verified contact; multi-hop is research | [ADR-0025](../adr/0025-store-and-forward-custody.md) |
| Level 0–6 hierarchy | Path **scopes** (`SERVER_RELAY`, `IP_RELAYED`, `IP_DIRECT`, `LAN`, `PROXIMITY`, `UPLINK_RELAY`, `EXTERNAL_RF`) with a rule-based `PathPolicy` | [ADR-0015](../adr/0015-path-scopes-and-policy.md) |
| Happy Eyeballs racing | Racing at link establishment (`LanLink` first, `NearbyLink` +250 ms); ICE races IP candidates for calls | brief §6.8, §7 |
| BLE discovery → Wi-Fi escalation | Bought: Nearby Connections; escalation at 256 KiB queued BULK; continuity by entry ids and acks | ADR-0024, brief §6.8 |
| Rolling ephemeral EID | Rotating pairwise tags `HMAC-SHA256(pairKey, "mbt-prox-v1" ‖ epoch15m)[0..8]` | brief §8.5 |
| QUIC/WebRTC + STUN/TURN | WebRTC ICE + our own TURN, **calls only**; relay-only for non-contacts | ADR-0023 |
| Same-LAN mDNS | `LanLink` (NsdManager / Bonjour, length-prefixed TCP, vetted handshake) | ADR-0024 |
| PQXDH + Double Ratchet + MLS | libsignal (PQXDH, Kyber, Double Ratchet, sender keys) at the platform edge; MLS with a server DS is research | [ADR-0017](../adr/0017-e2e-library-and-licence.md) |
| QR / NFC verification | Safety-number QR; verified status gates proximity and uplink | [ADR-0018](../adr/0018-identity-keys-device-certificates.md) |
| QoS P0–P3 + priority queue | CONTROL / INTERACTIVE / BULK / EPHEMERAL; per-link lanes, biased `select`, credits | brief §6.5, §6.6, §6.9 |
| Transactional outbox, CQRS | Durable mobile outbox; light CQRS (facade + DB projections, no buses); local DB is the SSOT | ADR-0013, [ADR-0011](../adr/0011-local-db-ssot-no-viewmodels.md) |
| Message states with "Relayed_To_Mesh" tick | Honest ticks: clock / one tick (server seq) / two ticks (recipient committed) / read; handover shows clock + "handed to …"; never a mesh tick | [ADR-0013](../adr/0013-delivery-semantics-and-ticks.md) |
| Wire frame | JSON control plane until 1.0; protobuf inner `Content`; outer `TransportFrame` with AEAD-authenticated header | [ADR-0022](../adr/0022-wire-protocol-envelope.md) |
| Chunking to MTU | Delegated to the medium; no generic fragmenter | brief §6.7 |
| LoRa via Meshtastic | `MeshtasticLink` + `AirtimeLedger`, compiled out of TR builds until counsel clears it | [ADR-0026](../adr/0026-meshtastic-legal-gate.md) |
| Satellite D2C / NTN, Starlink | `constrained` flag on `SERVER_RELAY`; Starlink is ordinary IP | ADR-0015, [ADR-0027](../adr/0027-rejected-transports.md) |
| SQLCipher + FTS5 | SQLCipher from 0.5; FTS inside the encrypted DB | [ADR-0020](../adr/0020-data-at-rest-and-erasure.md) |
| Threat model (STRIDE + LINDDUN) | [`threat-model.md`](../architecture/multi-transport/threat-model.md) plus a KVKK [DPIA screening](../architecture/multi-transport/dpia-screening.md) | [ADR-0029](../adr/0029-compliance-boundary.md) |

### 5.2 Side by side

| Concern | Gemini | Muhabbet design | Why the change | Ref |
|---|---|---|---|---|
| Authority | No central server; Merkle-CRDT history | Server is the authority for identity, devices, keys, order, mailbox, push, TURN, moderation; other paths move bytes only | One-time prekeys need a dispenser (C2); MLS needs a DS (C3); 5809 *işletmeci* and 5651/7578 need a reachable controller (RF MISS-01, LEG-01/02) | ADR-0010 |
| Identity | PeerID = Blake3(Ed25519), no phone | Phone + OTP account; per-device libsignal identity; server-attested 7-day device certificates; separate link key | Recovery, multi-device, subscriber duties (C11); bounded offline revocation | ADR-0018 |
| Key distribution | Signed prekey bundles in Kademlia | Server directory, atomic claim (`FOR UPDATE SKIP LOCKED`), rate-limited, relationship-checked | C2, C10, CRYPTO M1 | ADR-0018 |
| Groups | P2P MLS for thousands | Sender keys over 1:1 sessions, membership sequenced by the server; groups never off-server in 1.2 | C3 | ADR-0017, brief §8.3 |
| Ordering | HLC + Merkle-DAG | Per-conversation server `seq` + change feed; client causal header; clamped advisory time | C4, C5, C13, GEM-10 | ADR-0014 |
| Delivery | In-memory queues, fire and forget | Durable outbox, at-least-once, persist-then-ack, effectively-once to the user | GEM-03, MOB-03/04 | ADR-0013 |
| Path choice | Fitness score over medium "levels" | Scopes + capabilities + metrics + Admission → CloudPersistence → Preference rules | GEM-05, LINK-1; hard constraints are not weights | ADR-0015 |
| Plaintext safety | Assumed ("X cannot read") | `SealedFrame` constructor `internal` to `:mobile:crypto`; plaintext fallback deleted; testkit off release classpath | C9 | ADR-0016 |
| Internet P2P | IPv6 → ICE → birthday punching → libp2p relay / supernodes, for everything | WebRTC ICE + own TURN for **calls only**; relay-only for non-contacts; no data channels for chat | NAT-1, P2P-1/2, ICE-1, NAT MISS-2 | ADR-0023 |
| Proximity | Always-on BLE, custom L2CAP, zero-click Wi-Fi Direct, QUIC migration | Foreground Nearby mode, 15 min auto-off; `LanLink` built + `NearbyLink` bought; 1:1, verified, E2E only | PROX-02/04/06, QUIC-1 | ADR-0024 |
| Indirect | Epidemic / Spray-and-Wait through strangers, clear target hash, PoW | Mailbox → own devices → HPKE-sealed single-hop uplink via a verified contact; multi-hop research only | C6, C7, LEG-04, PROX-03, GEM-09 | ADR-0025 |
| Push | Not addressed | Wake-only data push `{conversationId, seq}` from 0.5 | KVKK transfer of content to US processors (BE-15) | [ADR-0019](../adr/0019-wake-only-push-and-server-plaintext.md) |
| Local storage | "SQLCipher" label, plaintext + FTS beside ciphertext | SQLCipher via fresh DB + resync at 0.5; crypto-erase on teardown | GEM-14, MOB-01 | ADR-0020 |
| RF | LoRa, VHF/UHF AFSK, PMR446, software modem, `delay(200)` | Meshtastic hardware only, ledgered, TR-excluded; everything else rejected | RF-07/08/09, LEG-03, GEM-19/20 | ADR-0026, ADR-0027 |
| Broadcast / satellite / ultrasonic / sidelink | Levels or transports | Rejected; satellite is a `constrained` IP bearer | RF-02/03/05/06, PROX-07/09 | ADR-0027 |
| Compliance | Absent | Age gate at registration (0.4); channels/communities server-only; block lists on every inbound path; ≤ 7-day offline revocation | LEG-01…04, RF MISS-01/04 | ADR-0029 |
| Modules | `tr.gov.muhabbet.core.transport` | `com.muhabbet.*`; `:mobile:crypto`, `:mobile:messaging`, `:mobile:transport-*` introduced only when needed | GEM-21; P7 | [ADR-0028](../adr/0028-kmp-module-structure.md) |
| Sequencing | All transports at once | Reliability (0.4–0.5) → Reachable (0.6) → Live (0.7) → Encrypted (1.0) → Direct, Nearby, Relay, Off-grid (1.1–1.4), each gated and droppable | Brief §1.1, §13; R4 | [roadmap](../architecture/multi-transport/roadmap.md) |

---

## 6. Answers to the owner's own questions in the chat

### 6.1 "Can TV broadcasts be watched on the phone, like a dish? Is a satellite-TV-on-phone app possible?" (T2 Q1, T3 Q1)

**No, not as an app on a retail phone today, and it would not help messaging anyway.**

| Route | Status | Evidence |
|---|---|---|
| GEO satellite TV (DVB-S2, Ku-band) direct to the phone | Physically impossible without a dish: ≈36 dB more path loss than LEO at 550 km, and no phone has a Ku-band (10.7–12.75 GHz) front end. Gemini was right here. | RF-04 |
| 5G Broadcast / FeMBMS Receive-Only Mode | The standard is real and ROM needs no SIM (ETSI TS 103 720 clause 5.4.8). But it is broadcaster-to-device linear TV; retail phones do not ship ROM-capable firmware and middleware, and there is no third-party app API to build on. Paris 2024 was a trial on specially configured handsets. No Turkish deployment found. | RF-02 (claim partly unverifiable) |
| "L/S-band satellites to all phones" via n255/n256 | Wrong premise: these are two-way NTN bands with uplinks (data/voice), not TV broadcast. DVB-SH, the S-band mobile-TV standard, failed commercially. | RF-03 |
| USB-C RTL-SDR or DVB-T2 tuner | An RTL2832U cannot demodulate DVB-T2. A dedicated tuner dongle is receive-only TV, not a Muhabbet feature; Turkey's DVB-T2 status is unverified. | RF-05 |

For Muhabbet the question that matters is whether any of these can **carry a message**. None can:
they are downlink-only. Satellite that *can* carry data (direct-to-cell, Starlink) reaches the app
as an ordinary IP network, which the design handles as a `constrained` flag on the server path
(ADR-0015), not a transport. All of this is recorded as rejected in ADR-0027 so it is not
re-proposed.

### 6.2 "Try strongest to weakest, maybe in parallel — ethernet, then wifi, then cellular … support all; it's just a port, an adapter, a use case, right?" (T2 Q2)

**Yes to the idea, with three corrections.**

1. **"Strongest" is not bandwidth.** The brief ranks a path by, in order: (a) it passes its admission
   gate (legal, E2E, verified contact, consent, certificates); (b) it gives the strongest delivery
   guarantee (custody, ordering, multi-device fan-out); (c) latency; (d) cost and battery. Bandwidth
   never outranks a gate (§1.1 item 5). For a durable CLOUD message the strongest path is the server
   whenever it is reachable, even when a faster LAN peer is present.
2. **Ethernet / Wi-Fi / cellular are not choices the app makes.** They are interfaces; the OS picks
   the route and ICE already gathers candidates on all of them (LINK-1). The app chooses among
   *scopes* (server relay, LAN, proximity, uplink, RF), shown in
   [`path-scopes.mmd`](../diagrams/path-scopes.mmd).
3. **"In parallel" happens at link establishment, not per message.** Racing a LAN and a Nearby link
   for the same peer is good (§6.8); sending every message down every path is not, because it
   multiplies exposure and duplicates (brief ADR-0015 rejected alternatives). Ordering and dedup come
   from one sequence and idempotent receivers, not from the transport.

**"It's just an adapter"** is true of the code and false of the cost. The adapter is the cheap part.
Each new path also needs: real E2E first (C9), a legal opinion (LEG-01/03, RF-08), a KVKK DPIA
(LEG-04), a consent flow (PROX MISS-01), a device-matrix measurement that only the owner can sign off
(P13), and abuse controls. That is why every non-server path ships behind a written admission gate
(P8) and is droppable with kill criteria (§13). "Support all" also collides with the law for amateur
radio, PMR446 and, until counsel says otherwise, encrypted LoRa (ADR-0026, ADR-0027).

### 6.3 "Indirect mode is very logical and nice." (T3 Q3)

**Agreed — and it is the part of the chat that changes the product most.** The brief adopts
INDIRECT mode as custody of bundles, offered in strict order through the `CustodyTarget` port
(ADR-0025):

| # | Custodian | When | Why this one |
|---|---|---|---|
| 1 | Server mailbox | Exists today as the `messages` table (pulled via `/messages/since`); seq columns arrive in 0.4 (V28) and the `/changes` pull feed in 0.5; ciphertext from 1.0 | Legal clarity, ordering, multi-device |
| 2 | The user's own linked devices (e.g. a tablet on the same LAN) | 1.3 | No third-party data, no new KVKK controller question |
| 3 | Single-hop sealed uplink through a **verified contact's** online phone | 1.3, gated and droppable | The mule sees only an HPKE-sealed, padded blob and POSTs it to `/api/v1/uplink`; the server attributes it to the true sender (5651) and ingests it idempotently |
| 4 | Multi-hop contact mules | Research (R-DTN), unscheduled | Needs anonymous rate tokens, rotating tags, quotas, DPIA, counsel |

> **Reviewer note (open):** brief ADR-0025 item 1 says the mailbox "exists today as `messages` +
> `/changes`"; `/changes` is not on dev (no endpoint; last migration V27). Suggest a brief erratum.

What does **not** survive from Gemini's version: carriers who are strangers (LEG-04, PROX-03), a
clear recipient hash (C7), epidemic routing, proof-of-work (C6), and a "relayed to mesh" tick. In the
Pendik → train → Ulukışla story (G:105), the carrier X on the train is a stranger, so X is never a
custodian. In the design, X must be a verified contact of the sender, and X does not deliver to
Mehmet: X uploads the sealed blob to the server in a single hop, and Mehmet receives it when he or one
of his own devices next reaches the server, or directly from Ahmet over LAN/Nearby if the two of them
are in range with Nearby mode open (1.2; 1:1, verified safety number, E2E). A mule delivering straight to the recipient is rejected (ADR-0025).
Kill criteria: drop the uplink if 1.2 telemetry shows too few Nearby sessions where one side lacked
internet (owner sets the threshold, Q-O5), or if counsel or the crypto review objects.

### 6.4 "What documents and diagrams does such a project need? All of them." (T3 Q4, Q6)

Gemini's list was right; the package delivers it, with additions Gemini did not name.

| Gemini item | Delivered as | Notes |
|---|---|---|
| DOC-01 SAD (arc42) | [`sad.md`](../architecture/multi-transport/sad.md) | |
| DOC-02 ADRs | ADR-0010 … ADR-0029 (product), ADR-0030 … ADR-0033 (process); index in the [package README](../architecture/multi-transport/README.md) | Gemini's own ADR-001…004 are all replaced: ADR-001 (CRDT not server) by ADR-0010/0014; ADR-002 (L2CAP not GATT) by ADR-0024 (buy Nearby); ADR-003 (QUIC migration) rejected by QUIC-1, ADR-0023/0027; ADR-004 (FEC not ARQ) by §6.7 |
| DOC-03 ICD & wire protocol | [`icd-wire-protocol.md`](../architecture/multi-transport/icd-wire-protocol.md) | Without Reed-Solomon; with golden vectors and fuzz targets |
| DOC-04 crypto & threat model (STRIDE / LINDDUN) | [`threat-model.md`](../architecture/multi-transport/threat-model.md) | Plus [`dpia-screening.md`](../architecture/multi-transport/dpia-screening.md) (KVKK Art. 12) |
| DOC-05 DTN & routing spec | **Not written** — only if research R-DTN starts (§12 #77) | Custody for 1.3 is specified in ADR-0025 and the engine spec |
| DOC-06 data model & local-first sync | [`data-model.md`](../architecture/multi-transport/data-model.md) | Flyway V28–V36, SQLDelight SSOT; no Merkle-DAG |
| DOC-07 test & chaos plan | [`vv-plan.md`](../architecture/multi-transport/vv-plan.md) | `SimulatedLink` harness (loss, duplication, reorder, partition, crash points), RQ-01…RQ-20 traceability, owner-only device checks |
| — (not in Gemini's list) | [`delivery-engine-spec.md`](../architecture/multi-transport/delivery-engine-spec.md), [`transport-capability-matrix.md`](../architecture/multi-transport/transport-capability-matrix.md), [`roadmap.md`](../architecture/multi-transport/roadmap.md), [`risk-register.md`](../architecture/multi-transport/risk-register.md), [`APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), counsel questions Q-L1…Q-L9, process docs for ADR-0030…0033, this review, the [findings register](2026-11-12-findings-register.md) | The capability matrix replaces Gemini's T1 table with measured cells; APPROVAL is where the owner signs |

| Gemini diagram | Delivered as ([`docs/diagrams/`](../diagrams/)) |
|---|---|
| C4 L1 context (peers, DTN mule, RF hardware, DHT/STUN/relay, NTN) | `c4-context.mmd` — no DHT, no volunteer relays; server, FCM/APNs, LiveKit/TURN, Meshtastic (gated) |
| C4 L2/L3 hexagonal container/component | `c4-container.mmd`, `c4-component-backend.mmd`, `c4-component-mobile.mmd` |
| DTN flow (Ahmet → X → Mehmet) | `custody-state-machine.mmd` (custody hierarchy), `seq-nearby-reconcile.mmd` (off-server delivery reconciled to the server seq) |
| Connection hierarchical state machine | `link-state-machine.mmd` (flat sealed states, including `CONSENT_REQUIRED`, `BLOCKED_BY_POLICY`) |
| Message state machine | `outbox-state-machine.mmd` |
| Wire frame bit diagram | Field tables in the ICD |
| — | `seq-send-receive.mmd`, `seq-wake-push-sync.mmd`, `path-scopes.mmd` |
| "Off-grid mesh radar" UI | Rejected: no mesh exists; a Nearby-mode screen shows measured peers only (§12 #70) |

### 6.5 Other questions in the chat

- **"Which architecture patterns: CQRS, Command, Event, Queue …?" (T2 Q4).** Adopted: light CQRS
  (facade plus DB read projections, no buses); Command as a persisted outbox entry; State as flat
  sealed classes with CAS transitions; Strategy + Chain of Responsibility for path policy; Observer
  over inbound sources; Facade (`MessagingEngine`); Adapter for protocol codecs; bounded queues per
  link lane. Rejected: event sourcing (KVKK erasure), Merkle-CRDT, hierarchical state-machine
  framework, a backend transactional outbox table (pull-by-seq makes lost hints harmless), a global
  priority queue with a `Mutex`. Brief §12 #72–75, ADR-0011/0012/0013.
- **"BLE discovery + Wi-Fi Direct escalation; CGNAT traversal; E2E in P2P" (T2 Q3).** Answered in
  §5: Nearby Connections does the escalation; ICE + own TURN does traversal, for calls; E2E is
  libsignal per device, and no non-server path exists until it is real.
- **"Muhabbet: Dünyaya Açılan Kapı" (T3 Q2) and "always and everywhere" (T2 Q2).** A slogan is a
  claim: under P13 and RQ-20 every user-visible claim maps to a test or an owner observation.
  "Gateway to the world" is supportable for the server path; the off-server promise is restated
  honestly in brief §1.1–1.2 (no first contact off-server, no background proximity, no off-grid
  mesh).

---

## 7. Lessons for using LLM output

1. **Treat every URL as unread until fetched.** Asked directly for sources, Gemini gave five; three
   were dead and one was a stub (§3.2). Rule: no LLM-supplied URL enters a design document unless an
   agent fetched it and quoted the supporting sentence.
2. **Recompute every number.** The 86% (NAT-1), the 36-byte header (GEM-06), the 200 ms "duty cycle"
   (RF-07, GEM-20) and the 2 kbps ultrasonic rate (PROX-09) each fell to arithmetic or a README.
   Plausible numbers are the most dangerous output because they read like measurements.
3. **Fluency in vocabulary is not correctness of composition.** Every primitive in the crypto section
   was real; the assemblies broke their assumptions (C2, C3, C4, C5). Ask of each primitive: "what
   does its own spec require of its environment?"
4. **Ask "who already ships this?" before designing it.** Nearby Connections (PROX-06), ICE and
   LiveKit (ICE-1), Meshtastic firmware (GEM-19) — the chat proposed rebuilding all three.
5. **Ask what the OS allows in the background and across platforms.** Most proximity claims were true
   for one OS in the foreground and false for Android ↔ iOS in the background (PROX-02/04/05).
6. **LLMs do not volunteer law.** Not one Turkish statute, licence, KVKK article or age rule appeared
   in three turns about radios, relays and encryption (LEG-01…05, RF-08). For anything touching RF,
   encryption or third-party data, the legal check is a gate, not a footnote (P8, ADR-0029). The one
   regulatory figure it did give, a "%10 legal" LoRa duty cycle (G:444), was wrong by sub-band and by
   method (RF-07), and it proposed amateur and PMR radios (G:384) with no licence caveat (RF-08/09).
7. **They answer the question asked, not the one that matters.** The chat designed transports on top
   of a crypto layer that is a NoOp (C9), a sequence that does not exist (C13) and a send path that
   loses messages (MOB-03/04). An agent should state prerequisites before features.
8. **Agreement with the asker is not evidence.** "We can support all of them — it's just an adapter"
   was affirmed rather than tested. The correct answer was "the adapter is cheap; the gate is not"
   (§6.2).
9. **Code from a chat is a sketch.** It did not compile in `commonMain` (GEM-11), contained `!!`
   (GEM-04) and a government package name (GEM-21). Never copy it (brief §12 #80); start from an ICD
   and property tests.
10. **Verify the verifiers.** Our own reviewers were also wrong in places: C8 missed the edge rate
    limiter, RF-09 misread the BTK table, GEM-18 misstated the KISS spec, and several severities were
    inflated. The two-layer method (reviewer plus independent adversarial verifier, 0 of 211 findings
    refuted, many corrected) is the standard for any LLM-derived input to this project, including this
    document.
11. **Record rejections.** Without ADR-0027 the same ideas will return the next time anyone asks a
    model for "messaging that works everywhere".
12. **Device claims need the owner's eyes.** No model or agent can observe Bluetooth discovery, a
    hotspot prompt or a call on TT Mobil; those criteria are signed off only by the owner (P13,
    ADR-0032).

---

## 8. Evidence, verification and open reviewer notes

**Evidence.** Every row in §3 and §4 carries a register ID; the register carries the verifier's
verdict and the area digest carries the full source trail. URLs in this document are copied from the
digests; truncated digest URLs were omitted rather than reconstructed.

**How this document is verified.**

1. Each register ID cited here exists in the [findings register](2026-11-12-findings-register.md)
   with the severity shown (spot-check by `grep`).
2. Every decision this review attributes to the design appears in the decision brief under the cited
   section or ADR; any mismatch is a defect in this document.
3. The scorecard counts (37 labelled: 1 / 20 / 14 / 2; 14 unlabelled; 51 total) can be recomputed from
   the *(claim: …)* labels in the register's four fact-check sections, excluding the `MISS-* (verifier)`
   rows (which add 4 more labels: 3 wrong, 1 partial).
4. The owner reads §6 against the chat and confirms each of his questions is answered.

> **Reviewer note (open):** (1) **iOS↔iOS Wi-Fi Aware is missing from the 1.2 spike.** PROX MISS-04
> (verifier) found that Apple's Wi-Fi Aware framework allows background connections to devices
> already paired through DeviceDiscoveryUI (iPhone 12+, iOS 26), and recommended it as a spike
> candidate. The brief keeps Wi-Fi Aware on the watch-list (ADR-0027) and its G-PROXIMITY spike
> covers Nearby and LAN only. Suggest adding "iOS↔iOS Wi-Fi Aware with prior pairing, background
> behaviour" as one optional cell of the 2-week device-matrix spike, so the watch-list decision rests
> on a measurement. This review follows the brief as written.

> **Reviewer note (open):** (2) **The HMS exclusion is inferred, not measured.** ADR-0024 states that
> Nearby's media require Play services, so HMS devices get LAN only. The proximity verifier noted that
> PROX-06 "asserts an HMS exclusion without evidence". G-PROXIMITY requires measuring the HMS *share*
> in Turkey; suggest it also confirms on one HMS device that Nearby Connections is unavailable, before
> the capability matrix states it as fact. This review follows the brief as written.

> **Reviewer note (open):** (3) **The chat transcript is not in the repository.** `G:n` references in this
> review and in the code-review digest point at the owner-pasted transcript, which exists only in the
> review workspace. Suggest committing it (e.g. `docs/reviews/2026-11-12-gemini-chat-transcript.md`,
> as-is (an English condensed rendering with Gemini's code as pasted), plus the owner's original
> Turkish paste if available, both marked as third-party LLM output that must not be copied) so the line references remain
> checkable. This is a package-completeness note, not a disagreement with the brief.

---

## Appendix A. How the corrected design was reached

The review did not stop at "Gemini is wrong". Three independent design proposals were written from
the verified findings — **evolutionary**, **target-state** and **risk-first** — and judged by three
independent judges: **security-legal**, **feasibility** and **architecture-quality**. The evolutionary
proposal won two of three judges and became the base; it was grafted with risk-first's type-level
fail-closed gate, wake-only push, compliance boundary and CI-trust prerequisites, and with
target-state's path scopes, link keys, LAN adapter, tick semantics and own-device custody. Every
judge's must-fix is closed, and every disagreement between judges is decided with a reason in the
decision brief §3 ("Judge disagreements and how they were decided"; "Judge must-fix closure").
Gemini's fully decentralised design was considered there as a rejected alternative to ADR-0010.
