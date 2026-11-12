# Hukuki görüş soruları — Kasım 2026 / Questions for counsel — November 2026

> **Status: Proposed — pending owner approval.** This question list is part of the multi-transport
> design package. Nothing in it is accepted until the owner records it in
> [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), and
> no code may be written for any scope until approval for that scope is recorded there. It may be
> sent to counsel once the owner approves the `phase-0` scope (brief §13 Phase 0: "Counsel engaged
> with the question list").

> **Bu belge hukuki görüş değildir / This is not legal advice.** Mevzuat alıntıları, tasarım
> incelemesi sırasında indirilen konsolide metinlerden alınmıştır ve avukat tarafından teyit
> edilmelidir. / Statute excerpts come from consolidated texts downloaded during the design review
> and must be confirmed by counsel. Nothing here claims compliance.

| | |
|---|---|
| **Tarih / Date** | 2026-11-12 |
| **Muhatap / Addressee** | Türk telekomünikasyon ve KVKK alanında uzman avukat / Turkish telecom and KVKK counsel |
| **Kaynak / Source of truth** | [decision brief §17 Q-L1…Q-L9](../architecture/multi-transport/decision-brief-2026-11-12.md) |
| **Kanıt / Evidence** | [findings register](../reviews/2026-11-12-findings-register.md): LEG-01…LEG-05, MISS-01/02/04 (legal), RF-07…RF-09, KMP-13, C8 |
| **İlgili / Related** | [ADR-0029](../adr/0029-compliance-boundary.md) (compliance boundary), [ADR-0017](../adr/0017-e2e-library-and-licence.md) (licence), [ADR-0026](../adr/0026-meshtastic-legal-gate.md) (RF), [threat model](../architecture/multi-transport/threat-model.md), [DPIA screening](../architecture/multi-transport/dpia-screening.md) |

---

## 1. Amaç ve beklenen çıktı / Purpose and expected deliverable

**TR:** Muhabbet, Türkiye'de yerleşik bir mesajlaşma uygulamasıdır. Mimari karar paketi, bazı
sürümleri (1.0 ve sonrası) yazılı hukuki görüşe bağlamıştır. Bu belge, o görüşün kapsamını
tanımlar: dokuz soru (Q-L1…Q-L9), her biri için bağlam, ilgili mevzuat, alt sorular ve cevabın hangi
kararın önünü açtığı.

**EN:** Muhabbet is a Turkish messaging app. The architecture decision package makes several
milestones (1.0 and later) conditional on a written legal opinion. This document scopes that
opinion: nine questions (Q-L1…Q-L9), each with context, the statute text it rests on, sub-questions
and the decision each answer unblocks.

**Requested deliverable / İstenen çıktı:** one written opinion (TR, with an EN summary) that, per
question: (1) answers each sub-question; (2) cites the article and paragraph relied on; (3) says
whether secondary legislation (BTK/Başkanlık regulations) is final, draft or absent; (4) states a
confidence level and what would change the answer; (5) names any duty that cannot be met by the
architecture described in §2.

---

## 2. Hizmetin kısa tanımı / The service, in facts counsel needs

### 2.1 Özet / Summary

| Konu / Topic | Durum / Fact |
|---|---|
| Hizmet / Service | Voice, text and visual person-to-person communication through a public app, independent of any operator; 1:1 chats, groups, channels, status/stories, communities |
| Kimlik / Identity | Phone number + SMS OTP (Twilio Verify in production); the number is stored in plaintext server-side |
| Sunucu / Server | One backend (Spring Boot) hosted by Hetzner in **Germany**; the server stays authoritative for identity, devices, keys, ordering, mailbox, push and moderation in every future mode (ADR-0010) |
| Şifreleme / Encryption | **Today: none end-to-end** — content is protected by TLS only and is readable by the server. Planned at 1.0: end-to-end encryption with libsignal for 1:1 and small groups; channels and communities stay server-readable by design |
| İşlemciler / Processors | Hetzner (hosting), Google FCM (push), Twilio (OTP); later Apple APNs, a LiveKit call server (location open), a certificate-signing host |
| Tüzel kişilik / Entity | Not yet formed (`docs/legal/README.md` uses placeholders) |
| Ölçek / Scale | MVP, solo developer; far below 1 M daily accesses today |
| Yaş / Age | No age gate exists yet; Terms say 18+ (or 16+ with parental consent, undecided); a registration age gate is planned for the current milestone (0.4) |

### 2.2 Yol başına ne iletilir, ne saklanır / What is transmitted and stored, per path

| Yol / Path | Sürüm / Milestone | Ne iletilir / Transmitted | Kim saklar, ne kadar / Stored by whom, how long | Sunucu içeriği görür mü / Server sees content |
|---|---|---|---|---|
| Sunucu üzerinden (WS + REST) / Server relay | today | Message content, metadata | Server: content and metadata (retention not yet defined) | **Yes** (until 1.0) |
| Sunucu üzerinden, uçtan uca / Server relay, E2E | 1.0 | Ciphertext per recipient device; metadata (sender device, recipient devices, sequence number, sizes, timestamps, delivery/read acknowledgements) | Server: ciphertext until each device acknowledges, max 30 days; metadata (retention per Q-L9) | **No** (metadata yes) |
| Push bildirimi / Push wake-up | 0.5 | `{type: "wake", conversationId, seq}` — no content, no sender | Google/Apple transiently | n/a |
| Arama (SFU/TURN) / Calls via relay | 0.7 | Encrypted audio (DTLS-SRTP); IP addresses at the relay | LiveKit host (location open) | The SFU can access media; disclosed in the UI |
| Doğrudan arama / Direct 1:1 call | 1.1 | Encrypted audio between the two phones | — | No |
| Yerel ağ / LAN (Wi-Fi without internet) | 1.2 | End-to-end ciphertext between **verified contacts** only, user-started foreground mode | The two phones | No |
| Yakındaki cihazlar / Nearby (BLE → Wi-Fi, Wi-Fi Direct/hotspot via Google Nearby Connections) | 1.2 | Same; advertisements carry only rotating random tags | The two phones | No |
| Kendi cihazları / Own linked devices | 1.3 | Ciphertext held by the user's own tablet for the user's other device | The user's device, ≤ 24 h | No |
| Kişi aracılığıyla iletim / Uplink via a contact's phone | 1.3 | An opaque, padded blob sealed to the server's key, containing the end-to-end ciphertext | The contact's phone (≤ 24 h, ≤ 50 items / 10 MB), then the server | No |
| LoRa (Meshtastic) | 1.4 | ≤ ~200-byte encrypted frames over licence-exempt EU868 radio via certified nodes | Nodes in transit | No — **excluded from Turkish builds until Q-L2 is answered** |

Every off-server path is limited to registered users who already share a conversation and whose
devices hold a server-issued certificate valid for at most 7 days; age attestation is a
precondition for that certificate (ADR-0029). Channels, communities and groups never use
off-server paths.

---

## 3. Mevzuat dayanakları / Statutory texts referenced

Excerpts are copied from the consolidated texts the fact-check downloaded. "Verified" means an
independent verifier re-read the article against the current mevzuat.gov.tr text (findings
register). Counsel should confirm all of them.

| # | Mevzuat / Instrument | Hüküm / Provision (excerpt) | Durum / Status | Kaynak / Source |
|---|---|---|---|---|
| S-1 | **5809** Elektronik Haberleşme Kanunu, md. 3 (ççç) (Ek: 7418/36) | "*Şebekeler üstü hizmet:* İnternet erişimine sahip abone ve kullanıcılara, işletmecilerden veya sağlanan internet hizmetinden bağımsız olarak kamuya açık bir yazılım vasıtası ile sunulan; sesli, yazılı, görsel iletişim kapsamındaki kişiler arası elektronik haberleşme hizmetlerini" | Verified (LEG-01) | <https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5809.pdf>; copy used: <https://www.alomaliye.com/wp-content/uploads/2008/11/5809-sk-25-12-2025.doc> |
| S-2 | 5809 md. 9(14) (Ek: 7418/37) | Kurum may regulate OTT and take any measure, including obligations on operators to block unauthorised OTT. "Şebekeler üstü hizmet sağlayıcılar, faaliyetlerini Türkiye'de kurdukları anonim şirket ya da limited şirket statüsündeki tam yetkili temsilcileri vasıtası ile Kurumca yapılacak yetkilendirme çerçevesinde yürütürler." OTT providers "… Kurumca belirlenecek hak ve yükümlülükler açısından işletmeci olarak kabul edilir." | Verified (LEG-01, MISS-01 legal) | as S-1 |
| S-3 | 5809 md. 60(16)–(17) (Ek: 7418/38) | Administrative fine of "bir milyon Türk lirasından otuz milyon Türk lirasına kadar"; failing that, bandwidth throttling "yüzde doksan beşine kadar" or access blocking | Verified | as S-1 |
| S-4 | 5809 md. 3 (ss) | "*Telsiz:* Aralarında herhangi bir fiziki bağlantı olmaksızın elektromanyetik dalgalar yoluyla açık, kodlu veya kriptolu ses ve veri vermeye, almaya veya yalnızca vermeye veya almaya yarayan sistemleri" | Read in the copy used; not separately verified | as S-1 |
| S-5 | 5809 md. 39(1) | "Telsiz haberleşme sistemleri üzerinden kriptolu haberleşme yapmaya Türk Silahlı Kuvvetleri, Jandarma Genel Komutanlığı ve Sahil Güvenlik Komutanlığı, Milli İstihbarat Teşkilatı, Emniyet Genel Müdürlüğü ve Dışişleri Bakanlığı yetkilidir. Ayrıca … gerçek ve tüzel kişilerin elektronik haberleşme hizmeti içinde kodlu veya kriptolu haberleşme yapma usul ve esasları Kurum tarafından belirlenir." | Verified (LEG-03) | as S-1 |
| S-6 | 5809 md. 63(6) | "Bu Kanunun 39 uncu maddesine aykırı olarak kodlu ve kriptolu haberleşme yapan ve yaptıranlar beş yüz günden bin güne kadar adli para cezası ile cezalandırılır." | Verified (LEG-03) | as S-1 |
| S-7 | Kamu Kurum ve Kuruluşları ile Gerçek ve Tüzel Kişilerin Elektronik Haberleşme Hizmeti İçinde Kodlu veya Kriptolu Haberleşme Yapma Usul ve Esasları Hakkında Yönetmelik (2010) | Reported to require crypto device makers to deposit codes or keys with the Authority | **Not verified** — in-force status, amendments and the scope of its md. 2(2) exemption are unknown | <https://www.alomaliye.com/2010/10/23/kamu-kurum-ve-kuruluslari-ile-gercek-ve-tuzel-kisilerin-elektronik-haberlesme-hizmeti-icinde-kodlu-veya-kriptolu-haberlesme-yapma-usul-ve-esaslari-hakkinda-yonetmelik/> |
| S-8 | Law 7590 (24/7/2026) | Amended Law 5651 (authority → Siber Güvenlik Başkanlığı) **and Law 5809 itself**; the 5809 copy used above predates it | Verified that the cited 5809 articles are unchanged; which 5809 powers moved is open (MISS-02 legal) | as S-1 |
| S-9 | **5651** md. 2(1)(s) | "*Sosyal ağ sağlayıcı:* Sosyal etkileşim amacıyla kullanıcıların internet ortamında metin, görüntü, ses, konum gibi içerikleri oluşturmalarına, görüntülemelerine veya paylaşmalarına imkân sağlayan gerçek veya tüzel kişileri" | Verified (LEG-02) | <https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5651.pdf> |
| S-10 | 5651 md. 2(1)(v) (Ek: 7590/27) | "*Başkanlık:* Siber Güvenlik Başkanlığını" | Verified | as S-9 |
| S-11 | 5651 md. 2(1)(j), 2(1)(m), md. 5(3) | *Trafik bilgisi*: "Taraflara ilişkin IP adresi, kaynak ve hedef port bilgisi, verilen hizmetin başlama ve bitiş zamanı, yararlanılan hizmetin türü, aktarılan veri miktarı ve varsa abone kimlik bilgilerini"; *yer sağlayıcı*: "Hizmet ve içerikleri barındıran sistemleri sağlayan veya işleten gerçek veya tüzel kişileri"; md. 5(3): the *yer sağlayıcı* keeps traffic data "bir yıldan az ve iki yıldan fazla olmamak üzere yönetmelikte belirlenecek süre kadar" | Read in the downloaded text; **not separately verified** | as S-9 |
| S-12 | 5651 Ek md. 4(1), (3), (4), (6) | Thresholds of **more than one million daily accesses from Turkey**: Turkish representative for foreign providers (1); answer applications within 48 hours (3); six-monthly Turkish reports to the Başkanlık (4); "Türkiye'deki kullanıcıların verilerini Türkiye'de barındırma yönünde gerekli tedbirleri alır" (6) | Verified (LEG-02) | as S-9 |
| S-13 | 5651 Ek md. 4(5) | For listed TCK offences (md. 103, 217/A, 302, 309–316, 328–337), information needed to reach the perpetrators is given to the prosecutor/court "ilgili sosyal ağ sağlayıcının Türkiye'deki temsilcisi tarafından" | Verified | as S-9 |
| S-14 | 5651 Ek md. 4(7) (Değişik: 22/4/2026-7578/22) | "Sosyal ağ sağlayıcı, onbeş yaşını doldurmamış çocuklara hizmet sunamaz ve bu hizmetin sunulmaması konusunda yaş doğrulama dâhil gerekli tedbirleri almakla yükümlüdür. Sosyal ağ sağlayıcı, onbeş yaşını doldurmuş çocuklara özgü ayrıştırılmış hizmet sunma konusunda gerekli tedbirleri alır. Bu fıkra kapsamında alınan tedbirler sosyal ağ sağlayıcının kendi internet sitesinde yayınlanır." The consolidated text's *yürürlük* table lists Law 7578 → Ek Madde 4, Ek Madde 5 → **1/11/2026** | Text verified (LEG-02); in-force date read from the table, not separately verified | as S-9 |
| S-15 | **6698** KVKK md. 3(1)(ğ), (ı) | *Veri işleyen*: processes on the controller's behalf under its authority; *veri sorumlusu*: "Kişisel verilerin işleme amaçlarını ve vasıtalarını belirleyen …" | Verified (LEG-04) | <https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf> |
| S-16 | KVKK md. 9 (Değişik: 2/3/2024-7499/34) | (1) adequacy decision; (4) appropriate safeguards incl. (c) Board-announced standard contract; (5) standard contract notified "imzalanmasından itibaren beş iş günü içinde"; (6) without adequacy or safeguards, "arızi olmak kaydıyla" exceptions, incl. (a) explicit consent after being informed of risks | Verified (LEG-04) | as S-15 |
| S-17 | KVKK md. 12(1), (2), (5) | Controller's duty to take all technical and administrative measures for an appropriate security level; joint responsibility with processors; breach notification "en kısa sürede". **No statutory DPIA article exists** | Verified (LEG-04) | as S-15 |
| S-18 | KVKK md. 28(1)(a) | Not applicable to processing by natural persons "tamamen kendisiyle veya aynı konutta yaşayan aile fertleriyle ilgili faaliyetler kapsamında", not given to third parties | Read in the downloaded text | as S-15 |
| S-19 | BTK *Frekans Tahsisinden Muaf … Teknik Ölçütler* — **TASLAĞI** (May 2026) | Rows: 868–868.6 MHz 25 mW ≤ 1% or LBT+AFA; 869.4–869.65 MHz 500 mW ≤ 10% | **Draft**; in-force KET text not fetched (RF-07) | <https://btk.gov.tr/s3/web-btk-site/7708c26c-5161-4919-8b3d-daa31d4ac8fb/2026/05/01c0ec40-b281-44e2-a8e9-3576aea0f354.pdf> |
| S-20 | BTK OTT draft regulations | Authorisation and thresholds; whether published in final form is unknown | Draft; status unknown | <https://www.rekabetregulasyon.com/ott-haberlesme-hizmetleri-icin-yeni-donem-btk-taslaklari-goruse-acti/> |
| S-21 | libsignal licence | "Licensed under the GNU AGPLv3"; "Use outside of Signal is unsupported" | Verified (KMP-13) | <https://github.com/signalapp/libsignal> |

---

## 4. Sorular / Questions

### Q-L1 — OTT yetkilendirmesi ve işletmeci yükümlülükleri / OTT authorisation and operator duties

**Soru (TR):** Muhabbet, 5809 sayılı Kanun md. 3 (ççç) ve 9(14) kapsamında BTK yetkilendirmesi
gerektiren bir şebekeler üstü hizmet midir? Bundan hangi saklama, dinleme/izleme işbirliği veya yerel
temsilci yükümlülükleri doğar ve bunlardan herhangi biri uçtan uca şifreleme ile çatışır mı?

**Question (EN):** Is Muhabbet an OTT service requiring BTK authorisation under Law 5809 Art.
3(ççç)/9(14)? What retention, interception-cooperation or local-representative duties follow, and
do any conflict with E2E?

**Bağlam / Context.** The verified review found Muhabbet "squarely" within the S-1 definition
(LEG-01): a public app offering voice, text and visual interpersonal communication independent of
the operator. The S-2 deeming clause can attach operator-grade duties chosen by the Authority
(MISS-01 legal). The architecture keeps the server authoritative and reachable precisely so such
duties remain performable (ADR-0010), but from 1.0 the server cannot read content of E2E
conversations (ADR-0019, ADR-0021).

**Mevzuat / Statute:** S-1, S-2, S-3, S-8, S-20.

**Alt sorular / Sub-questions**

| # | Sub-question |
|---|---|
| a | Does Muhabbet fall within md. 3 (ççç)? Does the answer change for features that do not traverse the server (direct calls, LAN/Nearby delivery)? |
| b | When must authorisation be obtained — before public launch, or from a user/traffic threshold? Has BTK's OTT authorisation regulation been published in final form, and with what thresholds (S-20)? |
| c | Entity: md. 9(14) requires an AŞ or Ltd "tam yetkili temsilci" in Turkey. What is the minimum structure for a solo developer, and what must be in place before launch? |
| d | Which *işletmeci* obligations has the Authority actually assigned to OTT providers under md. 9(14): traffic-data retention, lawful-interception cooperation, data localisation, others? |
| e | Would any of them require access to message **content** — incompatible with E2E from 1.0 — or forbid a mode where the server deletes ciphertext on delivery (LOCAL_ONLY)? |
| f | After Law 7590 (S-8), which 5809 powers relevant to OTT now sit with the Siber Güvenlik Başkanlığı rather than BTK? |
| g | What is the exposure under md. 60(16)–(17) while operating unauthorised during the MVP phase? |

**Neyin önünü açar / What it unblocks:** 1.0 entry (E2E vs. retention/interception, risk R5);
public launch; company formation; confirmation of ADR-0010 and ADR-0019 as compliant designs;
`docs/legal` correction "cite 5809 Art. 9(14)/39/60 as statute, not draft" (brief §14 item 8).
**Needed by:** before 1.0 entry; sub-questions (b), (c), (g) before public launch.

---

### Q-L2 — Telsiz üzerinden kriptolu haberleşme / Encrypted communication over radio

**Soru (TR):** 5809 sayılı Kanun md. 39 ve 63(6) ile 2010 tarihli Kodlu veya Kriptolu Haberleşme
Yönetmeliği; frekans tahsisinden muaf **BLE**, **Wi-Fi/yerel ağ**, **Wi-Fi Direct (Nearby
üzerinden)** ve **LoRa (EU868, KET'e uygun donanım)** üzerinden taşınan uygulama düzeyindeki uçtan uca
şifreli içeriğe uygulanır mı? Her ortam için ayrı ayrı cevaplayınız.

**Question (EN):** Do Art. 39 / 63(6) and the 2010 coded/encrypted communications regulation apply
to app-level E2E ciphertext carried over licence-exempt **BLE, Wi-Fi/LAN, Wi-Fi Direct (via
Nearby)** and **LoRa (EU868, KET-conformant hardware)**? Answer for each separately.

**Bağlam / Context.** Art. 39 reserves "telsiz haberleşme sistemleri üzerinden kriptolu haberleşme"
to listed state bodies; Art. 63(6) makes a breach a crime for those who do it "ve yaptıranlar"
(S-5, S-6). The *telsiz* definition (S-4) covers any system using electromagnetic waves without a
physical connection, which on its face includes Wi-Fi and Bluetooth. The review found no statute
explicitly banning app-level E2E and notes that E2E messengers are used in Turkey, but the risk
sharpens when the app itself emits encrypted frames over radio (LEG-03). ADR-0029 rule 7 requires
that this question names BLE and Wi-Fi, not only LoRa. The Meshtastic module is compiled out of
Turkish builds until a written answer exists (ADR-0026).

**Mevzuat / Statute:** S-4, S-5, S-6, S-7, S-19.

**Alt sorular / Sub-questions**

| # | Sub-question |
|---|---|
| a | For each medium — (1) BLE, (2) Wi-Fi infrastructure LAN without internet, (3) Wi-Fi Direct / hotspot established by Google Nearby Connections, (4) LoRa EU868 via certified Meshtastic nodes — is app-level E2E ciphertext "telsiz üzerinden kriptolu haberleşme"? |
| b | Does the same reasoning reach ordinary E2E messaging over Wi-Fi or cellular **internet** access (i.e. today's mainstream messengers)? If not, what distinguishes (a)(1)–(3)? |
| c | Is the 2010 regulation (S-7) in force unamended? Does it bind software providers or only manufacturers of crypto devices? Does its md. 2(2) exemption cover licence-exempt (KET) devices? Does it impose a code or key deposit duty on Muhabbet? |
| d | Who bears liability under md. 63(6) — the user who sends, Muhabbet as the party that "yaptıran", or both? |
| e | KET conformity: what must Muhabbet verify before recommending or listing Meshtastic hardware? Is the May 2026 BTK technical-criteria document (S-19) still a draft, and what is in force? |
| f | Does driving a user-owned radio from the app create spectrum-use liability for Muhabbet? |
| g | If a key deposit or similar duty applied, would it be satisfiable without defeating E2E? (The design position: never accepted silently; a product-level decision, LEG-03.) |

**Neyin önünü açar / What it unblocks:** 1.2 G-PROXIMITY gate ("counsel opinion covering E2E over
BLE/Wi-Fi") for (a)(1)–(3); 1.4 entry and any inclusion of `:mobile:transport-meshtastic` in a TR
flavour for (a)(4), (c), (e), (f); risk R6. **Needed by:** 1.2 entry; 1.4 entry.

---

### Q-L3 — Yaş doğrulama / Age verification

**Soru (TR):** Kayıt sırasında beyan edilen doğum tarihi, 7578 sayılı Kanunla değişik 5651 sayılı
Kanun Ek md. 4(7) kapsamındaki "yaş doğrulama" yükümlülüğünü karşılar mı? Karşılamıyorsa hangi
yöntem kabul edilir (e-Devlet, NFC ile kimlik okuma, başka)?

**Question (EN):** Does self-declared date of birth satisfy *yaş doğrulama* under Law 7578 / 5651
Ek Md. 4(7)? If not, what is acceptable (e-Devlet, NFC-ID)?

**Bağlam / Context.** Ek md. 4(7) (S-14) bars a *sosyal ağ sağlayıcı* from serving under-15s and
requires "yaş doğrulama dâhil gerekli tedbirler"; the consolidated text dates its entry into force
to 1/11/2026. Unlike paragraphs (1), (3), (4) and (6), paragraph (7) contains **no daily-access
threshold** in the text read. Muhabbet has no age gate today (`docs/legal/04-kullanim-kosullari.md:35`).
The 0.4 milestone builds one: date of birth stored server-side, under-minimum refused, behind an
`AgeAssurancePort` so a stronger method can be added later; the design makes **no compliance
claim** until this question is answered (ADR-0029 rule 1, risk R7). From 1.2 a valid age
attestation is a precondition for any off-server path. The published Terms say 18+ (or 16+ with
parental consent, decision pending), which is stricter than the statute.

**Mevzuat / Statute:** S-9, S-14; KVKK S-15 (date of birth as personal data).

**Alt sorular / Sub-questions**

| # | Sub-question |
|---|---|
| a | Does Ek md. 4(7) apply to Muhabbet only if it is a *sosyal ağ sağlayıcı* (Q-L4)? Does it apply below the one-million threshold? |
| b | Does self-declaration meet *yaş doğrulama*? If not, which methods are acceptable, and is guidance from the Başkanlık expected? |
| c | Accounts created before the gate have no date of birth. Must they be re-attested, and within what grace period? |
| d | What does "onbeş yaşını doldurmuş çocuklara özgü ayrıştırılmış hizmet" require for users aged 15–17 if Muhabbet admits them? |
| e | What must be published on the website about the measures taken? |
| f | Which minimum age should the product enforce, given statute (15) and the published Terms (18, or 16 with parental consent)? |
| g | What is the lawful basis and retention for the date of birth and any e-Devlet/NFC evidence (KVKK Art. 5, Art. 4(2)(d))? |

**Neyin önünü açar / What it unblocks:** any compliance statement about the 0.4 age gate (RQ-10
analysis); the Terms update; the age-policy decision (`[AGE-POLICY DECISION]`); whether an e-Devlet
or NFC adapter must be built; the age-gate DPIA (DPIA screening §3.1). **Needed by:** as soon as
possible — the provision is dated in force from 1/11/2026 and 0.4 is the live milestone.

---

### Q-L4 — Sosyal ağ sağlayıcı sınıflandırması / Social network provider classification

**Soru (TR):** Muhabbet, 5651 sayılı Kanun md. 2(1)(s) anlamında bir "sosyal ağ sağlayıcı" mıdır
(denetim artık Siber Güvenlik Başkanlığında), ve hangi kullanıcı eşiğinden itibaren? Uçtan uca
şifreli içerik için hangi içerik çıkarma ve bildirim yükümlülükleri uygulanır?

**Question (EN):** Is Muhabbet a "sosyal ağ sağlayıcı" under 5651 (now overseen by the Siber
Güvenlik Başkanlığı), and at what user threshold? What takedown and reporting obligations apply to
E2E content?

**Bağlam / Context.** The definition (S-9) covers anyone enabling users to create, view or share
text, image, sound or location "sosyal etkileşim amacıyla". Channels, status/stories and
communities make the classification more likely than for 1:1 chat alone (LEG-02). The architecture
keeps channels, communities and every group **server-only** so central takedown remains possible
(ADR-0029 rule 2); reports are durable and reach the server even if made offline (rule 3). From
1.0 the server cannot read 1:1 and small-group content; moderation of that content relies on what a
reporter supplies (risk R18; message franking is research). Hosting is in Germany.

**Mevzuat / Statute:** S-9, S-10, S-11, S-12, S-13, S-14.

**Alt sorular / Sub-questions**

| # | Sub-question |
|---|---|
| a | Is Muhabbet a *sosyal ağ sağlayıcı*? Does the answer differ per surface (1:1 chat, groups, channels, stories, communities)? |
| b | Once a Turkish AŞ/Ltd exists, is Muhabbet "yurt içi kaynaklı" even though it is hosted abroad? Which Ek md. 4 duties then apply? |
| c | How are "günlük erişim" from Turkey counted for the one-million thresholds? |
| d | Takedown and 48-hour application duties for content the provider cannot read (E2E): is acting on reporter-supplied content sufficient? Is a technical capability such as message franking expected? |
| e | Ek md. 4(5): what identifying information must be producible to a prosecutor or court, and does E2E change what is expected? |
| f | Is Muhabbet also a *yer sağlayıcı* (S-11), with the md. 5 duties including traffic-data retention (see Q-L9)? |
| g | Ek md. 4(6) hosting in Turkey: when does the duty bite, and does it cover ciphertext and metadata as well as content? |

**Neyin önünü açar / What it unblocks:** 1.0 (E2E for 1:1 and small groups; server-only channels
and communities confirmed); ADR-0029 rules 2–3; risk R18; the hosting plan; transparency reporting
design; the `docs/legal/README.md` correction naming the Siber Güvenlik Başkanlığı for 5651.
**Needed by:** before 1.0 entry.

---

### Q-L5 — Aktaran kullanıcıların KVKK rolü / KVKK role of relaying users

**Soru (TR):** Bir kullanıcının telefonu başka bir kullanıcının şifreli verisini geçici olarak
taşıdığında (kendi cihazları arası saklama; kişi aracılığıyla iletim) KVKK rolleri nelerdir?
Aktaran kullanıcı veri işleyen mi, veri sorumlusu mu, yoksa hiçbiri mi? Veri koruma etki
değerlendirmesi zorunlu mudur?

**Question (EN):** KVKK roles when a user's phone transiently holds another user's ciphertext
(own-device custody, contact uplink mule). Is the relaying user a processor, a controller, or
neither? Is a DPIA mandatory?

**Bağlam / Context.** 1.3 (gated, droppable) adds two custodians: the user's own linked device, and
a **verified contact's** online phone that carries an opaque, padded blob sealed to the server's key
for at most 24 hours (≤ 50 items / 10 MB) and uploads it under its own account (ADR-0025). The mule
cannot read content or learn the recipient; it can see the blob's size bucket, the time and that the
sender lacked connectivity. The verified review concluded Muhabbet sets the purposes and means of
relaying (controller, S-15), relaying users fall outside the household exemption (S-18) and are not
contracted processors (LEG-04). KVKK has no statutory DPIA article (S-17); the design requires one
anyway (ADR-0029 rule 6).

**Mevzuat / Statute:** S-15, S-16, S-17, S-18.

**Alt sorular / Sub-questions**

| # | Sub-question |
|---|---|
| a | Is an opaque blob the mule cannot decode personal data *in the mule's hands*? Are its size, timing and the sender's offline state personal data of the sender? |
| b | What is the relaying user's role: none, processor (Art. 3(1)(ğ)), or (joint) controller? Does Art. 28(1)(a) apply in any case? |
| c | What must Muhabbet put in place: terms for relaying users, a processing instruction, notice to senders and recipients? |
| d | If a mule's phone travels abroad while holding a blob, is that an Art. 9 transfer, and by whom? |
| e | Is anything different for **own-device** custody (the same user's tablet)? |
| f | Is a DPIA legally required, or best practice under Art. 12? What should it contain to satisfy the Board on inspection? |

**Neyin önünü açar / What it unblocks:** 1.3 entry ("written KVKK opinion on contacts transiently
holding ciphertext", brief §13); ADR-0025 custodians 2–3; risk R21; the DT-2 DPIA. A negative answer
is a 1.3 kill criterion. **Needed by:** 1.3 entry.

---

### Q-L6 — Yurt dışına aktarım dayanağı / Basis for transfers abroad

**Soru (TR):** İçerik taşımayan FCM/APNs uyandırma bildirimleri ile LiveKit/TURN barındırması için
KVKK md. 9 kapsamındaki aktarım dayanağı nedir? Açık rıza artık sistematik dayanak olarak
kullanılmayacaktır.

**Question (EN):** KVKK Art. 9 basis for FCM/APNs wake hints (content-free) and for LiveKit/TURN
hosting. Consent is no longer to be used as the systematic basis.

**Bağlam / Context.** From 0.5 push payloads carry only `{type: "wake", conversationId, seq}` — no
content, no sender (ADR-0019) — but still reach Google (and later Apple) with a device token and
timing. Hosting is already in Germany (Hetzner). The LiveKit call server's location is undecided
(Q-O8). The current drafts use explicit consent as a supplementary basis for systematic transfers
(`docs/legal/03-acik-riza-metni.md:31-36`, `02-aydinlatma-metni.md:64`), which Art. 9(6) allows only
for incidental transfers (S-16, LEG-04).

**Mevzuat / Statute:** S-16, S-17; S-12 (hosting in Turkey, if Q-L4 applies).

**Alt sorular / Sub-questions**

| # | Sub-question |
|---|---|
| a | For each recipient — Hetzner (Germany), Google FCM, Apple APNs, Twilio Verify, the future LiveKit host — which Art. 9 mechanism applies (adequacy decision, standard contract with five-business-day notification, other safeguard)? |
| b | Is a content-free wake hint (device token, internal conversation id, sequence number, timing) a transfer of personal data? |
| c | Does the choice of LiveKit host location (Turkey vs. EU) change the answer materially? |
| d | What exact changes should be made to `02-aydinlatma-metni.md` and `03-acik-riza-metni.md`? |
| e | Is there any processing here for which explicit consent remains the right basis? |

**Neyin önünü açar / What it unblocks:** the 0.5 aydınlatma update for wake-only push (ADR-0019
consequences); the 0.7 LiveKit host decision (Q-O8) and DT-3/DT-5 DPIAs; the `docs/legal`
correction "remove consent as the systematic Art. 9 transfer basis" (brief §14 item 8).
**Needed by:** 0.5 (notice); 0.7 entry (host).

---

### Q-L7 — AGPL dağıtımı / AGPL distribution

**Soru (TR):** libsignal'e bağlanan, tek telif hakkı sahibine ait bir uygulamanın AGPL-3.0 lisansıyla
Google Play ve Apple App Store üzerinden dağıtılması mümkün müdür ve hangi koşullarla?

**Question (EN):** AGPL-3.0 distribution through Google Play and the Apple App Store for a
single-copyright-holder app linking libsignal.

**Bağlam / Context.** libsignal is AGPLv3 (S-21); the repository is MIT (KMP-13). The plan relicenses
only `mobile/` (the code that links libsignal) to AGPL-3.0-or-later; `shared/` and `backend/` stay
MIT because the backend links `shared/` and never links libsignal (ADR-0017). Preconditions are
owner approval, a contributor audit (commits by AI agents under the owner's identity) and this
opinion. If the opinion is negative, the fallback is an Apache-2.0 library (vodozemac), which needs
its own ADR and loses PQXDH.

**Mevzuat / Statute:** S-21 (licence text); no Turkish statute.

**Alt sorular / Sub-questions**

| # | Sub-question |
|---|---|
| a | Can an AGPL-3.0-or-later app be distributed through Google Play? Through the Apple App Store, given its terms of service? |
| b | Does it matter that the owner is the sole copyright holder of `mobile/` (subject to the contributor audit)? How should commits authored by AI agents under the owner's account be treated? |
| c | Does AGPL §13 (network interaction) reach the MIT backend, which talks to the app over the network but does not link libsignal? |
| d | Is including MIT-licensed `shared/` in the AGPL-licensed app sound? |
| e | What must the corresponding-source offer contain for store builds (build scripts; not signing keys)? |
| f | Mechanics of relicensing the existing `mobile/` code. |

**Neyin önünü açar / What it unblocks:** ADR-0017 precondition (c); the 1.0 licence step; risk R8;
the decision between libsignal and the vodozemac fallback. **Needed by:** before 1.0 entry.

---

### Q-L8 — ICE telemetrisi kişisel veri mi / Is ICE telemetry personal data

**Soru (TR):** IP adresi içermeyen ICE telemetrisi (aday türü, IP ailesi, ASN) bildirim gerektiren
kişisel veri midir?

**Question (EN):** Is ICE telemetry (candidate type, IP family, ASN; no IPs) personal data requiring
notice?

**Bağlam / Context.** From 0.7, calls use WebRTC ICE; the app would report per call only the
candidate type, IP family and ASN — never IP addresses — to size TURN and decide whether 1.1 P2P
calls are worth building (ADR-0023). An opt-in NAT-behaviour probe is considered only if that data
shows a need. Separately, the TURN/SFU server necessarily processes participants' IP addresses
while relaying.

**Mevzuat / Statute:** S-15, S-17; S-11 (*trafik bilgisi* includes IP address and timing).

**Alt sorular / Sub-questions**

| # | Sub-question |
|---|---|
| a | Is the triple (candidate type, IP family, ASN) personal data when linked to an account? When reported without any account or device identifier? |
| b | What notice and lawful basis are required? |
| c | Same questions for an opt-in NAT probe recording ASN, mapping/filtering behaviour and IPv6 availability, without IPs. |
| d | Are IP addresses processed at the TURN/SFU *trafik bilgisi* that must be retained, or may they be discarded immediately (link to Q-L9)? |

**Neyin önünü açar / What it unblocks:** the 0.7 telemetry design (RQ-15 context); the DT-3 DPIA.
**Needed by:** 0.7 entry.

---

### Q-L9 — Teslim üst verisinin saklanması / Retention of delivery metadata

**Soru (TR):** 5651 ve 5809 sayılı Kanunlar kapsamında sunucu tarafındaki teslim üst verisi için bir
saklama yükümlülüğü var mıdır? LOCAL_ONLY modunun "teslimde silme" taahhüdü ile nasıl bağdaşır?

**Question (EN):** The retention obligation, if any, for server-side delivery metadata under 5651
and 5809, given LOCAL_ONLY's delete-on-delivery promise.

**Bağlam / Context.** From 1.0 the server keeps, for every conversation, a metadata row (sender
device, recipient devices, sequence number, sizes, timestamps, delivery and read acknowledgements)
and deletes ciphertext on acknowledgement or after 30 days (ADR-0021). A planned LOCAL_ONLY mode
promises no history anywhere and deletion on delivery; it is blocked until the owner decides how long
receipt metadata is kept (Q-O3) and this question is answered. *Trafik bilgisi* (S-11) includes IP
addresses, ports, start/end times, data amounts and subscriber identity; md. 5(3) sets a 1–2-year
range for a *yer sağlayıcı*. Md. 9(14) of 5809 may add operator duties (Q-L1).

**Mevzuat / Statute:** S-2, S-11, S-12, S-16, S-17; KVKK Art. 4(2)(d) and Art. 7 (storage limitation,
erasure).

**Alt sorular / Sub-questions**

| # | Sub-question |
|---|---|
| a | Which retention duty, if any, applies to Muhabbet (5651 md. 5(3) as *yer sağlayıcı*; 5809 via md. 9(14); other)? To which data items, for how long, starting when? |
| b | Is per-message delivery metadata (who sent to whom, when, size, acknowledgements) *trafik bilgisi*? |
| c | Can a LOCAL_ONLY conversation delete delivery receipts at delivery? If not, what minimum must remain, and for how long? |
| d | How is a statutory retention duty reconciled with KVKK storage limitation and the right to erasure for the same data? |
| e | Do access logs (IP addresses at the reverse proxy and at the TURN/SFU) fall under the same duty? |

**Neyin önünü açar / What it unblocks:** LOCAL_ONLY (ADR-0021, ADR-0025) together with owner question
Q-O3; retention configuration in `application.yml`; DT-3 DPIA retention fields; T3 storage-mode
design. **Needed by:** before LOCAL_ONLY is offered; (e) before 0.7.

---

## 5. Öncelik ve sıralama / Priority and sequencing

| Öncelik / Priority | Soru / Question | Engellediği / Blocks | Gerektiği an / Needed by |
|---|---|---|---|
| 1 | Q-L3 | Any compliance claim about the 0.4 age gate; Terms update | Now (provision dated in force 1/11/2026) |
| 2 | Q-L6 | 0.5 notice update; 0.7 host choice | 0.5 |
| 3 | Q-L1 (b), (c), (g) | Public launch; entity | Before public launch |
| 4 | Q-L8, Q-L9 (e) | 0.7 telemetry and logging | 0.7 entry |
| 5 | Q-L1, Q-L4, Q-L7 | 1.0 (E2E vs. duties; licence) | 1.0 entry |
| 6 | Q-L2 (BLE, Wi-Fi, Wi-Fi Direct) | 1.2 G-PROXIMITY | 1.2 entry |
| 7 | Q-L5 | 1.3 | 1.3 entry |
| 8 | Q-L2 (LoRa), (c), (e), (f) | 1.4; TR flavour inclusion | 1.4 entry |
| 9 | Q-L9 (a)–(d) | LOCAL_ONLY | Before LOCAL_ONLY |

The counsel checklist blocks the 1.1/1.2/1.3/1.4 gates and the 1.0 licence step, but not the 0.x
milestones (ADR-0029 consequences). Q-L3 and Q-L6 do not block 0.4/0.5 work; they block **claims**
and **notices** made about it.

---

## 6. Cevap kayıt şablonu / Answer record template

```text
Q-L<n> — <title>
Opinion ref.: <counsel, date, document id>
Answer per sub-question: (a) … (b) …
Articles relied on: <law, article, paragraph>
Secondary legislation: final | draft | absent (<reference>)
Confidence: high | medium | low — what would change it: …
Duties the described architecture cannot meet: …
Design consequence (filled by the owner): ADR-00xx amended | gate passed | kill criterion met | none
Recorded in APPROVAL.md scope: <scope>, Conditions line updated on <date>
```

---

## 7. Kapsam dışı / Out of scope

Trademark and app-store listing questions; tax and company-formation mechanics beyond Q-L1(c);
employment and contractor law; consumer-protection terms; content policy beyond Q-L4. They may be
added by the owner.

---

## 8. Reviewer notes

> **Reviewer note (open):** brief Q-L4 asks "at what user threshold" as if every 5651 Ek md. 4 duty
> were threshold-bound. In the consolidated text read, paragraphs (1), (3), (4) and (6) carry the
> one-million daily-access threshold, but paragraph (7) — the under-15 ban with *yaş doğrulama* —
> does not. The brief's design (age gate in 0.4 regardless of scale) is already the conservative
> reading, so no design change follows; Q-L3(a) asks counsel to confirm.

> **Reviewer note (open):** ADR-0029 and the SAD carry an open note that the 1/11/2026 in-force
> date for the 7578 amendment was not in the verified evidence. The consolidated 5651 text
> downloaded by the legal fact-check (<https://www.mevzuat.gov.tr/MevzuatMetin/1.5.5651.pdf>) lists
> "7578 — Ek Madde 4, Ek Madde 5 — 1/11/2026" in its *yürürlük* table. That supports the brief's
> date; it has not been re-read by an independent verifier and is still put to counsel in Q-L3.

> **Reviewer note (open):** the Law 5809 copy actually used for the excerpts (alomaliye.com,
> 25-12-2025) predates Law 7590. The verifier confirmed the cited articles are unchanged in the
> current mevzuat.gov.tr text, but counsel should quote from the current text only (MISS-02 legal);
> Q-L1(f) asks which powers moved.

> **Reviewer note (open):** the fact-check's legal checklist also listed KET conformity of
> recommended LoRa hardware and spectrum-use liability when the app drives user-owned radios. The
> brief has no separate Q-L for them; they are folded into Q-L2 (e)–(f) rather than numbered
> separately, to keep the brief's Q-L1…Q-L9 numbering intact.
