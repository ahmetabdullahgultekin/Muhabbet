> ⚠️ **TASLAK** — yayımlanmadan önce Türk KVKK avukatı incelemesi gerekir; hukuki tavsiye değildir.
> ⚠️ **DRAFT** — requires review by a Turkish data-protection lawyer before publication; not legal advice.

---

# Muhabbet — Hukuki Belgeler / Legal Documents

Bu klasör, Muhabbet için KVKK/GDPR uyumlu **taslak** hukuki belge setini içerir. **Tüm belgeler taslaktır** ve yayımlanmadan önce Türk veri koruma avukatı tarafından incelenmelidir.

This folder contains the KVKK/GDPR-aligned **draft** legal set for Muhabbet. **All documents are drafts** and must be reviewed by a Turkish data-protection lawyer before publication.

## Belgeler / Documents

| # | Belge / Document | Dosya / File | Düzenleme / Basis |
|---|---|---|---|
| 1 | Gizlilik Politikası / Privacy Policy | [`01-gizlilik-politikasi.md`](01-gizlilik-politikasi.md) | KVKK genel + GDPR |
| 2 | Aydınlatma Metni / Clarification Text | [`02-aydinlatma-metni.md`](02-aydinlatma-metni.md) | KVKK m.10 (standalone) |
| 3 | Açık Rıza Metni / Explicit Consent | [`03-acik-riza-metni.md`](03-acik-riza-metni.md) | KVKK m.5/1, m.9 (granular, revocable) |
| 4 | Kullanım Koşulları / Terms of Service | [`04-kullanim-kosullari.md`](04-kullanim-kosullari.md) | TR law, consumer/e-commerce, store policy |
| 5 | Çerez Politikası + Banner Şartnamesi / Cookie Policy + Banner Spec | [`05-cerez-politikasi.md`](05-cerez-politikasi.md) | KVKK Çerez Rehberi |

### Belgeler nasıl ilişkilenir / How they relate
- **Aydınlatma Metni (2)** ve **Açık Rıza Metni (3)** KVKK Kurulu yaklaşımı gereği **ayrı belgelerdir**: aydınlatma rızadan önce/bağımsız sunulur; rıza ayrıca alınır.
- **Gizlilik Politikası (1)** genel/kapsayıcı bilgilendirmedir; 2 ve 3'ü tamamlar.
- Bu set, eski `docs/privacy-policy.html` dosyasının **düzeltilmiş** halefidir (aşağıdaki düzeltmelere bakınız).

## Eski politikadaki düzeltilen yanlış iddialar / Corrected false claims from the old policy

`docs/privacy-policy.html` iki **maddi olarak yanlış** iddia içeriyordu; bu sette düzeltildi:
1. **Barındırma:** "GCP europe-west1" → **Yanlış.** Gerçek: **Almanya / Hetzner CX43 VPS.** Düzeltildi.
2. **Medya şifreleme:** "Medya dosyaları şifreli depolama alanında tutulur" → **Yanlış.** Gerçek: **MinIO'da depolamada şifreleme (SSE) yapılandırılmamıştır.** İddia kaldırıldı; SSE yalnızca *planlanan* olarak geçer.

Ayrıca: **uçtan uca şifreleme (E2E)** bu sette "mevcut" olarak **gösterilmez** — gerçekte etkin değildir; yalnızca *planlanan* olarak belirtilir.

---

## BTK / 5651 sayılı Kanun — Mesajlaşma Hizmeti Notu / Law 5651 Messaging-Service Note

**TR:** Muhabbet, kullanıcıların içerik ürettiği/paylaştığı bir iletişim hizmeti olduğundan **5651 sayılı İnternet Ortamında Yapılan Yayınların Düzenlenmesi ve Bu Yayınlar Yoluyla İşlenen Suçlarla Mücadele Edilmesi Hakkında Kanun** kapsamında değerlendirilebilir. Bu, aşağıdaki olası yükümlülükleri gündeme getirir (kesin kapsam ve sınıflandırma — "yer sağlayıcı" vb. — **avukat tarafından belirlenmelidir**):
- **Trafik/erişim kayıtlarının (log) mevzuatın öngördüğü süre boyunca** tutulması ve bütünlüğünün korunması (uygulamada genelde belirli bir süre — kesin süre ve kapsam hukuki görüşle netleştirilmelidir).
- **Hukuka aykırı içeriğe ilişkin şikâyet ve kaldırma (notice-and-takedown)** mekanizması ve yetkili merci taleplerine yanıt SLA'sı (uygulamada 24 saat hedefi sıkça anılır).
- Gerekirse **BTK'ya temsilci/iletişim noktası** bildirimi ve şeffaflık raporlaması.
- **BTK OTT/elektronik haberleşme taslak düzenlemeleri** (yetkilendirme, yerel temsilci/varlık eşikleri) ayrıca değerlendirilmelidir.

> **Mevcut durum (kod):** Uygulamada şikâyet/engelleme (moderation) altyapısı vardır (`user_reports`, `user_blocks`). Ancak **5651 uyumlu log saklama süresi, kaldırma SLA'sı ve şeffaflık raporu süreçleri tanımlı/uygulanmış değildir.** Bu, **hukuki + operasyonel** bir iştir; yayım öncesi avukatla netleştirilmelidir.

**EN:** Because Muhabbet is a user-generated-content communication service, it may fall within **Law No. 5651** (regulation of internet publications). This raises possible obligations (exact scope/classification — e.g. "hosting provider" — **to be determined by counsel**): retention and integrity of **traffic/access logs** for the legally mandated period; a **notice-and-takedown** mechanism and an authority-response SLA (a 24-hour target is commonly cited); designation of a **BTK contact/representative** and transparency reporting if required; and assessment of the **draft BTK OTT/electronic-communications regulations** (authorisation, local-representative/entity thresholds).
> **Current state (code):** report/block moderation infrastructure exists (`user_reports`, `user_blocks`), but **Law-5651-compliant log retention, takedown SLA and transparency-report processes are not defined/implemented.** This is a **legal + operational** task to settle with counsel before publication.

---

## Yaş Kapısı Önerisi / Age-Gate Recommendation

**Öneri (TR):** Kayıt sırasında bir **yaş kapısı** uygulanmalıdır. Önerilen eşik:
- **18 yaş**, veya
- **16 yaş** + 16–18 yaş arası için **ebeveyn/veli onayı** (bazı yerel uygulamalarda, ör. BiP, benimsenen yaklaşım).

KVKK'da çocukların verileri özel bir hassasiyet gerektirir. Yaş kapısı, yalnızca bir onay kutusu değil; **doğum tarihi/yaş beyanı + altında ise erişimin engellenmesi** şeklinde olmalı ve `birthDate`/`ageVerifiedAt` gibi bir kayıt tutulmalıdır. Nihai eşik [YAŞ POLİTİKASI KARARI] ile sahibi tarafından belirlenmelidir.

**Recommendation (EN):** Implement an **age gate at registration.** Suggested threshold: **18**, or **16 with parental/guardian consent** for ages 16–18 (the approach some local apps such as BiP take). Children's data is specially sensitive under KVKK. The gate should be a **date-of-birth/age declaration + block-if-under**, not just a checkbox, and should record something like `birthDate`/`ageVerifiedAt`. The final threshold is the owner's call via [AGE-POLICY DECISION].

> **Mevcut durum / Current state:** Yaş kapısı **uygulanmamıştır** (kayıt akışında yaş kontrolü yok). Belgelerdeki yaş hükümleri, bu kapı kodlanana kadar **vaat edilen ama uygulanmayan** bir kontroldür.

---

## Sahibinin Doldurması Gereken Yer Tutucular / Placeholders the Owner Must Fill

Tüm belgelerde geçen ve **doldurulması zorunlu** yer tutucular:

| Yer Tutucu / Placeholder | Açıklama / Description |
|---|---|
| `[VERİ SORUMLUSU ÜNVANI]` | Tescilli şirket/işletme ünvanı |
| `[ADRES]` | Tescilli/merkez adres |
| `[VERBİS NO]` | VERBİS kayıt numarası (önce kayıt olunmalı — KVKK m.16) |
| `[TİCARET SİCİL NO]` | Ticaret sicil numarası |
| `[KEP]` | Kayıtlı Elektronik Posta adresi |
| `[İLETİŞİM E-POSTA — ör. privacy@muhabbet.com.tr]` | Gizlilik/başvuru iletişim e-postası |
| `[VERİ SORUMLUSU TEMSİLCİSİ]` | (Gerekiyorsa) veri sorumlusu temsilcisi |
| `[YÜRÜRLÜK TARİHİ]` / `[EFFECTIVE DATE]` | Belgelerin yürürlük tarihi |
| `[YETKİLİ MAHKEME/İCRA DAİRESİ — ör. İstanbul]` / `[COMPETENT VENUE]` | ToS yetkili mahkeme |
| `[YAŞ POLİTİKASI KARARI]` / `[AGE-POLICY DECISION]` | 18 mi, 16+veli onayı mı |
| `[SAĞLAYICIYA GÖRE]` / `[PER PROVIDER]` | SMS sağlayıcı konumu (Netgsm/Twilio) |
| `[ÇEREZ ADI]` / `[COOKIE NAME]`, `[SÜRE]`/`[DURATION]`, `[SAĞLAYICI]`/`[PROVIDER]` | Çerez envanteri gerçek değerleri |

> **Hiçbir yer tutucu uydurulmamıştır.** Bunlar, gerçek tescil/kuruluş bilgileriyle doldurulmadan belgeler yayımlanmamalıdır.

---

## Bu Belgelerin Bağlı Olduğu Kod/Operasyonel Açıklar / Code & Operational Gaps These Docs Depend On

Belgelerdeki bazı vaatler, **henüz uygulanmamış** teknik kontrollere dayanır. Belgeler bu kontrolleri "mevcut" göstermez; ancak yayım öncesi (veya ilgili vaat kaldırılarak) **kapatılması gereken** açıklar şunlardır:

| Açık / Gap | Belgedeki etki / Doc impact | Yapılması gereken / Action |
|---|---|---|
| **Gerçek silme yok (yalnızca soft-delete)** — `requestAccountDeletion` telefon/mesaj/medya/phone_hash silmiyor | Silme hakkı (KVKK m.7) tam karşılanamaz | Kalıcı silme (hard-delete) işi + 30 gün gecikmeli temizlik; PII null/anonimleştirme, MinIO medya silme, phone_hash/anahtar temizliği, silme denetim kaydı |
| **Depolamada şifreleme yok (MinIO SSE yok)** | "Şifreli depolama" iddia edilemez | SSE-S3/SSE-KMS yapılandır **veya** iddiayı kaldır (bu sette kaldırıldı) |
| **Yaş kapısı yok** | Yaş hükmü uygulanmıyor | Kayıt akışına doğum tarihi/yaş kapısı + `ageVerifiedAt` |
| **Açık rıza onboarding kapısı yok** — `consentVersion`/`consentTimestamp` saklanmıyor | Açık Rıza Metni geçerli işleyemez | Granüler, önceden-işaretsiz onay + sürüm/zaman damgası saklama + geri-alma akışı |
| **GET /users/{id} telefon numarasını sızdırıyor; görünürlük ayarları okunmuyor** | "Veri minimizasyonu/güvenlik" (m.12) vaadiyle çelişir | Public DTO'dan telefon kaldır; `onlineStatusVisibility`/`lastSeen` görünürlüğünü uygula (Phase 0) |
| **IDOR'lar (mesaj-arama, medya-URL, mesaj-info) + SSRF** | "Teknik güvenlik tedbirleri" (m.12) vaadiyle çelişir; geçmişe dönük ihlal bildirimi tetikleyebilir | İki-kullanıcı 403 testleriyle kapat (Phase 0) |
| **Üretimde gerçek SMS yok (mock varsayılan)** | Hesap doğrulama fiilen çalışmıyor | Netgsm/Twilio'yu prod'da etkinleştir; prod'da mock'u reddet |
| **VERBİS kaydı yok** | Politikada VERBİS no boş | KVKK m.16 kaydı; numarayı belgelere işle |
| **DPA/SCC dosyalanmamış (Hetzner/FCM/SMS)** | Yurt dışı aktarım güvencesi eksik | Veri işleme sözleşmeleri + uygun aktarım mekanizması |
| **5651 log saklama/SLA/şeffaflık süreci yok** | 5651 vaatleri eksik | Hukuki görüşle log politikası + takedown SLA + şeffaflık raporu |
| **Veri ihlali bildirim süreci yok** | KVKK m.12 yükümlülüğü | 72 saat KVKK + ilgili kişi bildirim süreci |

> Kaynaklar / Sources: `docs/PROD_READINESS_AND_PLAN_2026-06-06.md` (P0-9/10/23/24, §5) ve `docs/PRODUCT_ROADMAP_2026-06-06.md` §5 (Legal & compliance).

---

## English summary

This is the **draft** KVKK/GDPR legal set for Muhabbet (Turkish-primary, English-secondary). Every document carries the mandatory DRAFT / lawyer-review banner. The set corrects the old `privacy-policy.html`'s two false claims (GCP→Hetzner Germany; "encrypted MinIO storage"→no SSE) and never presents E2E as live. **All entity/registration fields are placeholders — nothing is fabricated.** Publication is gated on the legal + code/operational gaps listed above (true erasure, SSE-or-omit, age gate, consent gate, the live IDOR/SSRF/phone-leak fixes, VERBİS, DPAs/SCCs, Law-5651 log/SLA/transparency, breach process).
