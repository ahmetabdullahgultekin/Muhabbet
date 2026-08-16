> ⚠️ **TASLAK** — yayımlanmadan önce Türk KVKK avukatı incelemesi gerekir; hukuki tavsiye değildir.
> ⚠️ **DRAFT** — requires review by a Turkish data-protection lawyer before publication; not legal advice.

---

# Muhabbet Gizlilik Politikası

**Yürürlük Tarihi:** [YÜRÜRLÜK TARİHİ — ör. GG.AA.YYYY]
**Sürüm:** v1.0-taslak
**Veri Sorumlusu:** [VERİ SORUMLUSU ÜNVANI]

Bu Gizlilik Politikası, Muhabbet mesajlaşma uygulamasını ("Muhabbet", "Uygulama", "Hizmet") kullanırken kişisel verilerinizin nasıl işlendiğini açıklar. Bu metin, 6698 sayılı Kişisel Verilerin Korunması Kanunu ("KVKK") ve uygulanabilir olduğu ölçüde Avrupa Birliği Genel Veri Koruma Tüzüğü ("GDPR") çerçevesinde hazırlanmıştır.

KVKK Aydınlatma Yükümlülüğü kapsamındaki ayrı **[Aydınlatma Metni](02-aydinlatma-metni.md)** ve onayınıza sunulan **[Açık Rıza Metni](03-acik-riza-metni.md)** bu politikanın tamamlayıcısıdır.

> **Önemli dürüstlük notu (taslak için):** Aşağıdaki ifadeler, uygulamanın **bugünkü gerçek teknik durumunu** yansıtacak şekilde yazılmıştır. Henüz devreye alınmamış güvenlik kontrolleri ("uçtan uca şifreleme", "depolamada şifreleme" gibi) bu metinde **gerçekleşmiş gibi gösterilmemiştir**; yalnızca *planlanan* olarak ve açıkça belirtilerek yer alır. Bu kontroller fiilen devreye alındığında politika güncellenmelidir.

## 1. Veri Sorumlusunun Kimliği

| Alan | Bilgi |
|---|---|
| Veri Sorumlusu Ünvanı | [VERİ SORUMLUSU ÜNVANI] |
| Adres | [ADRES] |
| VERBİS Kayıt No | [VERBİS NO] |
| Ticaret Sicil No | [TİCARET SİCİL NO] |
| KEP Adresi | [KEP] |
| İletişim E-posta | [İLETİŞİM E-POSTA — ör. privacy@muhabbet.com.tr] |
| Veri Sorumlusu Temsilcisi | [VERİ SORUMLUSU TEMSİLCİSİ] |

## 2. İşlediğimiz Kişisel Veriler

Muhabbet, yalnızca hizmeti sunmak için gerekli olan verileri işler (veri minimizasyonu ilkesi). Aşağıdaki tablo, **uygulamada fiilen toplanan** veri kategorilerini gösterir:

| Veri Kategorisi | Toplanan Veri | Kaynak | Zorunlu mu? |
|---|---|---|---|
| **Kimlik / iletişim** | Telefon numarası (E.164 formatında) | Sizden (kayıt) | Telefon ile kayıtta evet |
| **Profil** | Görünen ad, profil fotoğrafı, "hakkında" metni | Sizden | Hayır (isteğe bağlı) |
| **Mesaj içeriği** | Gönderdiğiniz ve aldığınız metin mesajları sunucularımızda saklanır | Sizden | Hizmetin işleyişi için gerekli |
| **Medya dosyaları** | Paylaştığınız fotoğraf, video, ses kaydı ve belgeler | Sizden | Paylaşırsanız |
| **Konum** | Paylaşmayı seçtiğiniz konum bilgisi (mesaj içeriği olarak) | Sizden | Hayır |
| **Bağlantı (durum/son görülme)** | Çevrimiçi/çevrimdışı durumu, son görülme zamanı, "yazıyor" göstergesi | Otomatik | Hizmetin işleyişi için |
| **Cihaz / bildirim** | Cihaz platformu (Android/iOS), cihaz adı, push bildirim jetonu (FCM token) | Cihazınızdan | Bildirim alacaksanız |
| **Rehber eşleştirme (opt-in)** | Rehberinizdeki telefon numaralarının **SHA-256 özetleri (hash)** — yalnızca Muhabbet kullanan kişileri bulmak için | Sizden (izin verirseniz) | **Hayır — isteğe bağlı** |
| **Moderasyon** | Şikâyet ve engelleme kayıtları (şikâyet sebebi, açıklama, ilgili mesaj/kullanıcı kimliği) | Sizden (şikâyet ederseniz) | Hayır |
| **Teknik kayıtlar** | OTP doğrulama kayıtları (kod **bcrypt** ile özetlenir), yenileme jetonu özetleri, IP/oturum meta verileri | Otomatik | Güvenlik için gerekli |

### Telefon numarası zorunlu değildir

Telefon numarası, **yalnızca** "rehbere göre otomatik keşif" (WhatsApp tarzı kişi bulma) özelliğini açar; bu bir **özelliktir, kimlik doğrulama zorunluluğu değildir.** Yol haritamızda kullanıcı adı / QR / davet bağlantısı ile telefonsuz keşif birincil yöntem olarak konumlanmaktadır. Telefon-rehber eşleştirmesi **isteğe bağlıdır (opt-in)** ve açık rızanıza tabidir.

### Hassas (özel nitelikli) veri toplamıyoruz

Muhabbet, KVKK m.6 anlamında özel nitelikli kişisel veri (sağlık, din, biyometrik vb.) toplamayı **amaçlamaz**. Biyometrik kimlik doğrulama Muhabbet tarafında tutulmaz; bu işlev gelecekte ayrı bir veri işleyen olan **FIVUCSAS** üzerinden sağlanacaktır (bkz. §6).

## 3. Verileri Hangi Amaçlarla ve Hangi Hukuki Sebeple İşliyoruz

| Amaç | İşlenen Veri | KVKK Hukuki Sebebi (m.5) |
|---|---|---|
| Hesap oluşturma ve doğrulama | Telefon numarası, OTP kaydı | Sözleşmenin kurulması/ifası (m.5/2-c) |
| Mesaj ve medya iletimi | Mesaj içeriği, medya | Sözleşmenin ifası (m.5/2-c) |
| Rehberden Muhabbet kullanıcısı bulma | Telefon numarası özetleri | **Açık rıza (m.5/1)** — opt-in |
| Push bildirimi gönderme | Cihaz jetonu | Sözleşmenin ifası / meşru menfaat (m.5/2-f) |
| Çevrimiçi durum / son görülme | Bağlantı verileri | Sözleşmenin ifası; görünürlük ayarlarınıza tabi |
| Kötüye kullanımın önlenmesi, moderasyon (5651) | Şikâyet/engelleme kayıtları | Kanuni yükümlülük (m.5/2-ç) ve meşru menfaat (m.5/2-f) |
| Güvenlik, hata takibi ve hizmet kalitesi | Teknik kayıtlar | Meşru menfaat (m.5/2-f) |
| Yasal yükümlülüklerin yerine getirilmesi | İlgili kayıtlar | Kanuni yükümlülük (m.5/2-ç) |

Pazarlama, profilleme veya üçüncü taraflara reklam amaçlı veri satışı **yapmıyoruz.**

## 4. Verilerin Saklandığı Yer ve Aktarımı

- **Barındırma:** Verileriniz **Almanya'da bulunan Hetzner sunucu altyapısında (Hetzner CX43 VPS)** saklanır. Almanya, Avrupa Birliği içinde yer alır.
  > *(Düzeltme notu: Önceki taslakta yer alan "GCP europe-west1" ifadesi **yanlıştı** ve düzeltilmiştir — Google Cloud Platform kullanılmamaktadır.)*
- **Yurt dışına aktarım:** Almanya'da barındırma, KVKK m.9 anlamında yurt dışına aktarım teşkil eder. Bu aktarımın hukuki temeli için [Aydınlatma Metni §6](02-aydinlatma-metni.md)'ya ve uygun aktarım mekanizmasına (taahhütname / KVKK'ya bildirim / açık rıza) bakınız. *(Gerekli sözleşmesel güvenceler — DPA/standart sözleşme şartları — yayımdan önce tamamlanmalıdır; bkz. taslak notu.)*
- **Üçüncü taraf hizmet sağlayıcılar (veri işleyenler):**

  | Sağlayıcı | Amaç | Konum |
  |---|---|---|
  | **Hetzner Online GmbH** | Sunucu barındırma | Almanya (AB) |
  | **Netgsm** *(veya devreye alınırsa Twilio)* | OTP doğrulama SMS gönderimi | Türkiye / [SAĞLAYICIYA GÖRE] |
  | **Google Firebase Cloud Messaging (FCM)** | Push bildirimi iletimi | Google altyapısı (AB/ABD) |

  > *Hangi SMS sağlayıcısının fiilen kullanıldığı yayım öncesi netleştirilmeli ve bu tabloda doğru biçimde belirtilmelidir.*

## 5. Veri Güvenliği

Aşağıdaki ifadeler **mevcut durumu** dürüstçe yansıtır. Henüz uygulanmamış kontroller "planlanan" olarak işaretlenmiştir; bunların gerçekleştiği iddia edilmemektedir.

**Bugün uygulanan kontroller:**
- Cihazınız ile sunucularımız arasındaki **tüm iletişim TLS (taşıma katmanı şifrelemesi) ile şifrelenir.**
- OTP doğrulama kodları **bcrypt** ile, yenileme jetonları **SHA-256** ile özetlenerek saklanır; açık metin olarak tutulmaz.
- Rehber eşleştirmesi yalnızca telefon numarası **SHA-256 özetleri** ile yapılır.
- Erişim kontrolü, hız sınırlama (rate limiting) ve güvenlik başlıkları uygulanır.

**Henüz uygulanmayan / planlanan kontroller — bunlar şu an MEVCUT DEĞİLDİR:**
- **Mesajlarda uçtan uca şifreleme (E2E):** Şu anda **etkin değildir.** Mesajlarınız sunucuda iletim için işlenirken **TLS ile korunur ancak uçtan uca şifrelenmez.** Uçtan uca şifreleme *planlanmaktadır*; fiilen devreye alınana kadar bu özellik pazarlanmayacak ve burada "mevcut" olarak gösterilmeyecektir.
- **Medyada depolamada şifreleme (encryption-at-rest):** Medya dosyaları MinIO nesne depolamasında tutulur. **Bugün depolamada şifreleme (SSE) yapılandırılmamıştır.** Sunucu tarafı şifreleme *planlanmaktadır*. Bu kontrol devreye alınana kadar "şifreli depolama" iddiasında **bulunmuyoruz.**
  > *(Düzeltme notu: Önceki taslaktaki "Medya dosyaları şifreli depolama alanında tutulur" ifadesi **yanlıştı** ve kaldırılmıştır.)*

Hiçbir sistem %100 güvenli değildir; alınan teknik ve idari tedbirlere rağmen veri güvenliğini mutlak olarak garanti edemeyiz.

## 6. Kimlik Sağlayıcı: FIVUCSAS

Muhabbet, kimlik doğrulamayı zamanla **FIVUCSAS** adlı ayrı bir kimlik sağlayıcı / **ayrı veri işleyen/sorumlu** üzerinden yürütmeyi planlamaktadır. Bu entegrasyon devreye alındığında:
- Kimlik doğrulama (örn. SMS-OTP) FIVUCSAS tarafından yürütülür; Muhabbet biyometrik veri tutmaz.
- Muhabbet'in kendi telefon+OTP yöntemi **kalıcı yedek (fallback)** olarak korunur.
- Bu politika, entegrasyon yayına alındığında FIVUCSAS'ın rolünü ve veri akışını açıklayacak şekilde güncellenecektir.

## 7. Saklama Süreleri

| Veri | Saklama Süresi |
|---|---|
| Hesap ve profil verileri | Hesabınız aktif olduğu sürece |
| Mesaj ve medya | Siz silene veya hesabınızı kapatana kadar |
| OTP doğrulama kayıtları | Kısa süreli; doğrulama sonrası kısa sürede geçersiz kılınır |
| 5651 sayılı Kanun kapsamı trafik/erişim kayıtları | Mevzuatın öngördüğü süre boyunca *(bkz. [README — 5651 notu](README.md))* |
| Moderasyon/şikâyet kayıtları | Yasal zamanaşımı ve kötüye kullanım önleme süresince |

Hesap silindiğinde verilerin gerçek anlamda silinmesi/anonimleştirilmesi süreci §8'de açıklanır.

## 8. KVKK Kapsamındaki Haklarınız

KVKK m.11 uyarınca şu haklara sahipsiniz:
- Kişisel verinizin işlenip işlenmediğini **öğrenme**,
- İşlenmişse buna ilişkin **bilgi talep etme**,
- İşlenme amacını ve amaca uygun kullanılıp kullanılmadığını **öğrenme**,
- Yurt içi/yurt dışı aktarıldığı üçüncü kişileri **bilme**,
- Eksik/yanlış işlenmişse **düzeltilmesini** isteme,
- KVKK m.7 koşullarında **silinmesini/yok edilmesini** isteme,
- Düzeltme/silme işlemlerinin aktarılan üçüncü kişilere **bildirilmesini** isteme,
- İşlenen verinin münhasıran otomatik sistemlerle analizi sonucu aleyhinize bir sonuç çıkmasına **itiraz etme**,
- Hukuka aykırı işleme nedeniyle zarara uğramanız hâlinde **tazminat** talep etme.

> **Dürüstlük uyarısı (taslak):** "Silme" hakkının gerçek anlamda yerine getirilebilmesi için arka uçta **kalıcı silme (hard-delete)** işleminin uygulanmış olması gerekir. Mevcut durumda hesap kapatma yalnızca **yumuşak silme (soft-delete)** yapmaktadır; telefon numarası, mesajlar ve medya tam olarak silinmemektedir. Bu politika, **kalıcı silme işi devreye alınmadan** silme hakkını eksiksiz yerine getirdiğini iddia etmemelidir. (Bkz. [README — Kod/operasyonel açıklar](README.md).)

**Haklarınızı kullanmak için:** [İLETİŞİM E-POSTA — ör. privacy@muhabbet.com.tr] adresine başvurabilirsiniz. Başvurular KVKK m.13 uyarınca en geç **30 gün** içinde yanıtlanır. Ayrıca Kişisel Verileri Koruma Kurumu'na şikâyet hakkınız saklıdır.

## 9. Çocukların Verileri

Muhabbet, **18 yaşından küçükler için tasarlanmamıştır.** *(Veya: 16 yaşından küçükler kullanamaz; 16-18 yaş arası için ebeveyn/veli onayı gerekir — nihai yaş eşiği [YAŞ POLİTİKASI KARARI] ile belirlenecektir.)* Kayıt sırasında yaş doğrulaması yapılacaktır.
> *(Dürüstlük uyarısı: Kayıt akışında yaş kapısı henüz uygulanmamıştır; yayım öncesi devreye alınmalıdır — bkz. README.)*

## 10. Değişiklikler

Bu politikayı zaman zaman güncelleyebiliriz. Önemli değişikliklerde uygulama içinde veya kayıtlı iletişim kanalınızla bilgilendirme yapılır. Güncel sürüm her zaman bu sayfada yayımlanır.

## 11. İletişim

Gizlilikle ilgili tüm sorular için: **[İLETİŞİM E-POSTA — ör. privacy@muhabbet.com.tr]**

---
---

> ⚠️ **DRAFT** — requires review by a Turkish data-protection lawyer before publication; not legal advice.

# Muhabbet Privacy Policy

**Effective Date:** [EFFECTIVE DATE — e.g. DD.MM.YYYY]
**Version:** v1.0-draft
**Data Controller:** [DATA CONTROLLER LEGAL NAME — placeholder]

This Privacy Policy explains how your personal data is processed when you use the Muhabbet messaging application ("Muhabbet", the "App", the "Service"). It is prepared under Turkish Law No. 6698 on the Protection of Personal Data ("KVKK") and, where applicable, the EU General Data Protection Regulation ("GDPR").

The separate **[Clarification Text (Aydınlatma Metni)](02-aydinlatma-metni.md)** required under the KVKK duty to inform, and the **[Explicit Consent Text (Açık Rıza Metni)](03-acik-riza-metni.md)** presented for your approval, complement this policy.

> **Important honesty note (for this draft):** The statements below describe the **actual current technical state** of the app. Security controls that have not yet been deployed (such as "end-to-end encryption" or "encryption at rest") are **not** presented as if they exist; they appear only as *planned*, clearly labelled. Update this policy once those controls are genuinely live.

## 1. Identity of the Data Controller

| Field | Information |
|---|---|
| Data Controller | [DATA CONTROLLER LEGAL NAME] |
| Address | [ADDRESS] |
| VERBİS Registration No | [VERBİS NO] |
| Trade Registry No | [TRADE REGISTRY NO] |
| Registered E-mail (KEP) | [KEP] |
| Contact E-mail | [CONTACT E-MAIL — e.g. privacy@muhabbet.com.tr] |
| Data Controller Representative | [DATA CONTROLLER REPRESENTATIVE] |

## 2. Personal Data We Process

Muhabbet processes only data necessary to provide the service (data minimisation). The table reflects what the app **actually collects**:

| Category | Data | Source | Mandatory? |
|---|---|---|---|
| **Identity / contact** | Phone number (E.164 format) | From you (registration) | Yes, if registering with phone |
| **Profile** | Display name, profile photo, "about" text | From you | No (optional) |
| **Message content** | Text messages you send/receive are stored on our servers | From you | Required for the service to function |
| **Media files** | Photos, videos, voice recordings and documents you share | From you | If you share them |
| **Location** | Location you choose to share (as message content) | From you | No |
| **Connection (presence/last-seen)** | Online/offline status, last-seen time, "typing" indicator | Automatic | For service operation |
| **Device / notifications** | Device platform (Android/iOS), device name, push token (FCM) | From your device | If you receive notifications |
| **Contact matching (opt-in)** | **SHA-256 hashes** of phone numbers in your address book — only to find people already on Muhabbet | From you (if you allow) | **No — optional** |
| **Moderation** | Report and block records (reason, description, related message/user ID) | From you (if you report) | No |
| **Technical logs** | OTP verification records (code **bcrypt**-hashed), refresh-token hashes, IP/session metadata | Automatic | Required for security |

### A phone number is not mandatory

A phone number **only** enables contacts-based auto-discovery (WhatsApp-style); it is a **feature, not an authentication requirement.** Our roadmap positions phone-free discovery (username / QR / invite link) as the primary method. Phone-contact matching is **opt-in** and subject to your explicit consent.

### We do not collect sensitive (special-category) data

Muhabbet does not intend to collect special-category personal data under KVKK Art. 6 (health, religion, biometrics, etc.). Biometric authentication is **not** held by Muhabbet; that function will, in future, be provided by **FIVUCSAS**, a separate processor (see §6).

## 3. Purposes and Legal Bases

| Purpose | Data | KVKK Legal Basis (Art. 5) |
|---|---|---|
| Account creation/verification | Phone number, OTP record | Performance of contract (5/2-c) |
| Message and media delivery | Message content, media | Performance of contract (5/2-c) |
| Finding Muhabbet users from contacts | Phone-number hashes | **Explicit consent (5/1)** — opt-in |
| Sending push notifications | Device token | Contract / legitimate interest (5/2-f) |
| Presence / last-seen | Connection data | Contract; subject to your visibility settings |
| Abuse prevention, moderation (Law 5651) | Report/block records | Legal obligation (5/2-ç) and legitimate interest (5/2-f) |
| Security, error tracking, service quality | Technical logs | Legitimate interest (5/2-f) |
| Compliance with legal obligations | Relevant records | Legal obligation (5/2-ç) |

We **do not** sell data, profile users for advertising, or share data with third parties for marketing.

## 4. Where Data Is Stored and Transferred

- **Hosting:** Your data is stored on **Hetzner server infrastructure located in Germany (Hetzner CX43 VPS)**. Germany is within the European Union.
  > *(Correction note: the previous draft's "GCP europe-west1" statement was **false** and has been corrected — Google Cloud Platform is not used.)*
- **Cross-border transfer:** Hosting in Germany constitutes a transfer abroad under KVKK Art. 9. See [Clarification Text §6](02-aydinlatma-metni.md) for the legal basis. *(Required contractual safeguards — DPA / standard contractual clauses — must be completed before publication; see draft note.)*
- **Third-party processors:**

  | Provider | Purpose | Location |
  |---|---|---|
  | **Hetzner Online GmbH** | Server hosting | Germany (EU) |
  | **Netgsm** *(or Twilio, if enabled)* | OTP verification SMS | Turkey / [PER PROVIDER] |
  | **Google Firebase Cloud Messaging (FCM)** | Push notification delivery | Google infrastructure (EU/US) |

  > *Which SMS provider is actually used must be confirmed before publication and stated correctly here.*

## 5. Data Security

The statements below honestly reflect the **current state**. Controls not yet implemented are marked "planned"; they are not claimed to exist.

**Controls in place today:**
- **All communication** between your device and our servers is **encrypted with TLS** (transport-layer encryption).
- OTP codes are **bcrypt**-hashed and refresh tokens **SHA-256**-hashed; never stored in clear text.
- Contact matching uses only **SHA-256 hashes** of phone numbers.
- Access control, rate limiting and security headers are applied.

**Not yet implemented / planned — these DO NOT exist today:**
- **End-to-end encryption (E2E) of messages:** Currently **not enabled.** Your messages are **protected by TLS** in transit but are **not end-to-end encrypted.** E2E is *planned*; until it is genuinely live, we will not market it or present it as "available" here.
- **Encryption at rest for media:** Media files are stored in MinIO object storage. **Encryption at rest (SSE) is not configured today.** Server-side encryption is *planned*. Until then, we make **no** "encrypted storage" claim.
  > *(Correction note: the previous draft's "Media files are kept in encrypted storage" was **false** and has been removed.)*

No system is 100% secure; despite our technical and organisational measures, we cannot absolutely guarantee data security.

## 6. Identity Provider: FIVUCSAS

Muhabbet plans to move authentication, over time, to **FIVUCSAS**, a separate identity provider acting as a **separate processor/controller**. When this integration is live:
- Authentication (e.g. SMS-OTP) is handled by FIVUCSAS; Muhabbet holds no biometric data.
- Muhabbet's own phone+OTP remains a **permanent fallback**.
- This policy will be updated to describe FIVUCSAS's role and data flow once the integration ships.

## 7. Retention

| Data | Retention |
|---|---|
| Account/profile | While your account is active |
| Messages and media | Until you delete them or close your account |
| OTP records | Short-lived; invalidated shortly after verification |
| Law-5651 traffic/access logs | For the period mandated by law *(see [README — Law 5651 note](README.md))* |
| Moderation/report records | For the statutory limitation and abuse-prevention period |

The true deletion/anonymisation process on account closure is described in §8.

## 8. Your KVKK Rights

Under KVKK Art. 11 you have the right to: learn whether your data is processed; request information; learn the purpose; know third parties to whom it is transferred; request rectification; request erasure under Art. 7; request that rectification/erasure be notified to third parties; object to outcomes produced solely by automated analysis; and claim compensation for damage caused by unlawful processing.

> **Honesty warning (draft):** Genuinely fulfilling the right to erasure requires a backend **hard-delete**. Currently, account closure performs only a **soft-delete**; phone number, messages and media are not fully erased. This policy must **not** claim full fulfilment of the erasure right **until the hard-delete job is implemented.** (See [README — Code/operational gaps](README.md).)

**To exercise your rights:** contact [CONTACT E-MAIL — e.g. privacy@muhabbet.com.tr]. Requests are answered within **30 days** (KVKK Art. 13). You may also lodge a complaint with the Turkish Data Protection Authority.

## 9. Children's Data

Muhabbet is **not designed for anyone under 18.** *(Or: under-16s may not use it; ages 16–18 require parental consent — the final age threshold is set by [AGE-POLICY DECISION].)* Age verification will be performed at registration.
> *(Honesty warning: the registration age gate is not yet implemented; it must be added before publication — see README.)*

## 10. Changes

We may update this policy from time to time. We will notify you of material changes in-app or via your registered contact channel. The current version is always published on this page.

## 11. Contact

For all privacy questions: **[CONTACT E-MAIL — e.g. privacy@muhabbet.com.tr]**
