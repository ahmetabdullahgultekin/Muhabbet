> ⚠️ **TASLAK** — yayımlanmadan önce Türk KVKK avukatı incelemesi gerekir; hukuki tavsiye değildir.
> ⚠️ **DRAFT** — requires review by a Turkish data-protection lawyer before publication; not legal advice.

---

# Muhabbet — Kişisel Verilerin İşlenmesine İlişkin Aydınlatma Metni

**(6698 sayılı KVKK m.10 ve Aydınlatma Yükümlülüğünün Yerine Getirilmesinde Uyulacak Usul ve Esaslar Hakkında Tebliğ kapsamında)**

**Yürürlük Tarihi:** [YÜRÜRLÜK TARİHİ]
**Sürüm:** v1.0-taslak

> Bu Aydınlatma Metni, KVKK Kurulu yaklaşımı uyarınca **Açık Rıza Metni'nden ayrı, bağımsız bir belgedir**; açık rıza alınmasından önce ve ondan bağımsız olarak ilgili kişiye sunulur. Onay (açık rıza) için [Açık Rıza Metni](03-acik-riza-metni.md)'ne bakınız.

## 1. Veri Sorumlusu

İşbu aydınlatma, aşağıda kimliği belirtilen veri sorumlusu tarafından yapılmaktadır:

| Alan | Bilgi |
|---|---|
| Veri Sorumlusu | [VERİ SORUMLUSU ÜNVANI] |
| Adres | [ADRES] |
| VERBİS Kayıt No | [VERBİS NO] |
| Ticaret Sicil No | [TİCARET SİCİL NO] |
| KEP | [KEP] |
| İletişim | [İLETİŞİM E-POSTA — ör. privacy@muhabbet.com.tr] |
| Veri Sorumlusu Temsilcisi | [VERİ SORUMLUSU TEMSİLCİSİ] |

## 2. İşlenen Kişisel Veriler ve Kategorileri

Muhabbet uygulamasını kullanmanız kapsamında işlenen kişisel veri kategorileri:

| Kategori | Veri |
|---|---|
| Kimlik / iletişim | Telefon numarası (E.164) |
| Profil | Görünen ad, profil fotoğrafı, "hakkında" metni |
| Müşteri işlem / içerik | Metin mesajları, paylaşılan medya (fotoğraf/video/ses/belge), paylaşılan konum, anket ve durum (story) içerikleri |
| İşlem güvenliği | OTP doğrulama kayıtları (kod bcrypt ile özetlenir), yenileme jetonu özetleri, oturum/IP meta verileri |
| Cihaz | Cihaz platformu, cihaz adı, push bildirim jetonu (FCM) |
| Bağlantı | Çevrimiçi/çevrimdışı durumu, son görülme zamanı, "yazıyor" göstergesi |
| Rehber eşleştirme (opt-in) | Rehberdeki telefon numaralarının SHA-256 özetleri |
| Hukuki işlem / moderasyon | Şikâyet ve engelleme kayıtları |

> **Not:** Muhabbet, özel nitelikli kişisel veri (KVKK m.6 — sağlık, biyometrik vb.) toplamayı amaçlamaz. Biyometrik kimlik doğrulama, devreye alındığında ayrı veri sorumlusu/işleyen **FIVUCSAS** tarafında yürütülecektir; Muhabbet bu verileri tutmaz.

## 3. Kişisel Verilerin İşlenme Amaçları

Kişisel verileriniz; hesabınızın oluşturulması ve doğrulanması, mesaj ve medya içeriğinin iletilmesi ve saklanması, çevrimiçi durum/son görülme gibi temel mesajlaşma işlevlerinin sağlanması, push bildirimlerinin iletilmesi, (yalnızca açık rızanızla) rehberinizden Muhabbet kullanıcılarının bulunması, hizmet güvenliğinin sağlanması ve kötüye kullanımın önlenmesi, içerik moderasyonu ve 5651 sayılı Kanun kapsamındaki yükümlülüklerin yerine getirilmesi, hata takibi ve hizmet kalitesinin iyileştirilmesi ile yasal yükümlülüklerin yerine getirilmesi amaçlarıyla işlenmektedir.

## 4. Kişisel Verilerin İşlenmesinin Hukuki Sebepleri (KVKK m.5)

- **Sözleşmenin kurulması veya ifası (m.5/2-c):** Hesap doğrulama, mesaj/medya iletimi, bildirim ve temel işlevler.
- **Kanuni yükümlülük (m.5/2-ç):** Moderasyon ve 5651 sayılı Kanun ile diğer mevzuat kapsamındaki yükümlülükler.
- **Meşru menfaat (m.5/2-f):** Güvenlik, kötüye kullanımın önlenmesi, hata takibi ve hizmet kalitesi.
- **Açık rıza (m.5/1):** Rehber-telefon eşleştirmesi (opt-in) ve gerektiğinde yurt dışına aktarım — bkz. [Açık Rıza Metni](03-acik-riza-metni.md).

## 5. Kişisel Verilerin Toplanma Yöntemi

Kişisel verileriniz; uygulamaya kayıt ve kullanım sırasında **sizin tarafınızdan** (telefon numarası, profil, mesaj, medya, konum, şikâyet) ve uygulamanın işleyişi gereği **otomatik yollarla** (cihaz/bağlantı/teknik kayıtlar) elektronik ortamda toplanır. Rehber eşleştirmesi yalnızca cihaz izni vererek **açık rızanızla** gerçekleşir.

## 6. Kişisel Verilerin Aktarımı

- **Yurt içi:** Verileriniz, hizmetin sunulması için zorunlu olduğu ölçüde **hizmet sağlayıcılara (veri işleyenlere)** aktarılır: SMS/OTP sağlayıcısı (**Netgsm** veya devreye alınırsa **Twilio**), bildirim sağlayıcısı (**Google Firebase Cloud Messaging**). Ayrıca yetkili kamu kurum ve kuruluşlarına, mevzuatın gerektirdiği hâllerde aktarım yapılabilir.
- **Yurt dışı (KVKK m.9):** Sunucu barındırma **Almanya'daki Hetzner altyapısında (AB)** gerçekleştiği için veriler **yurt dışında işlenir.** Bu aktarım, KVKK m.9 kapsamında uygun bir aktarım mekanizmasına (taahhütname/standart sözleşme şartları, KVKK'ya bildirim, veya gerektiğinde açık rıza) dayandırılır.
  > *(Dürüstlük notu — taslak: Yayım öncesi, kullanılan sağlayıcılarla **veri işleme sözleşmeleri (DPA)** ve uygun **aktarım güvenceleri** tamamlanmalı; barındırma yerinin "Almanya/Hetzner" olduğu doğru biçimde belirtilmelidir. Önceki taslaktaki "GCP europe-west1" ifadesi yanlıştı.)*

## 7. İşlenen Verilerin Saklanma Süresi

Verileriniz, ilgili mevzuatta öngörülen veya işlendikleri amaç için gerekli olan süre boyunca saklanır; bu sürelerin sonunda silinir, yok edilir veya anonim hâle getirilir. Detaylı saklama süreleri için [Gizlilik Politikası §7](01-gizlilik-politikasi.md)'ye bakınız.

## 8. KVKK m.11 Kapsamındaki Haklarınız

Veri sorumlusuna başvurarak; verilerinizin işlenip işlenmediğini öğrenme, bilgi talep etme, işlenme amacını öğrenme, aktarıldığı üçüncü kişileri bilme, düzeltilmesini/silinmesini/yok edilmesini isteme, bu işlemlerin üçüncü kişilere bildirilmesini isteme, otomatik analiz sonucu aleyhinize çıkan sonuçlara itiraz etme ve zararınızın giderilmesini talep etme haklarına sahipsiniz.

**Başvuru:** Taleplerinizi [İLETİŞİM E-POSTA — ör. privacy@muhabbet.com.tr] üzerinden veya KEP ([KEP]) aracılığıyla iletebilirsiniz. Başvurularınız en geç **30 gün** içinde sonuçlandırılır. Yanıttan memnun kalmazsanız **Kişisel Verileri Koruma Kurumu**'na şikâyette bulunabilirsiniz.

> **Dürüstlük uyarısı (taslak):** Silme/yok etme talepleri, arka uçta **gerçek (kalıcı) silme işlemi** devreye alınana kadar tam olarak karşılanamayabilir; bu durum yayım öncesi giderilmelidir (bkz. [README](README.md)).

---
---

> ⚠️ **DRAFT** — requires review by a Turkish data-protection lawyer before publication; not legal advice.

# Muhabbet — Privacy Clarification Text (Aydınlatma Metni)

**(Under KVKK Art. 10 and the Communiqué on the Procedures and Principles for Fulfilling the Duty to Inform)**

**Effective Date:** [EFFECTIVE DATE]
**Version:** v1.0-draft

> Per the Turkish DPA's approach, this Clarification Text is a **standalone document, separate from the Explicit Consent Text**; it is presented to the data subject before and independently of any consent. For consent, see the [Explicit Consent Text](03-acik-riza-metni.md).

## 1. Data Controller

| Field | Information |
|---|---|
| Data Controller | [DATA CONTROLLER LEGAL NAME] |
| Address | [ADDRESS] |
| VERBİS No | [VERBİS NO] |
| Trade Registry No | [TRADE REGISTRY NO] |
| KEP | [KEP] |
| Contact | [CONTACT E-MAIL — e.g. privacy@muhabbet.com.tr] |
| Controller Representative | [DATA CONTROLLER REPRESENTATIVE] |

## 2. Categories of Personal Data Processed

| Category | Data |
|---|---|
| Identity / contact | Phone number (E.164) |
| Profile | Display name, profile photo, "about" text |
| Transaction / content | Text messages, shared media (photo/video/voice/document), shared location, poll and status (story) content |
| Processing security | OTP records (bcrypt-hashed code), refresh-token hashes, session/IP metadata |
| Device | Device platform, device name, push token (FCM) |
| Connection | Online/offline status, last-seen time, "typing" indicator |
| Contact matching (opt-in) | SHA-256 hashes of address-book phone numbers |
| Legal / moderation | Report and block records |

> **Note:** Muhabbet does not intend to process special-category data (KVKK Art. 6). Biometric authentication, once enabled, is handled by the separate controller/processor **FIVUCSAS**; Muhabbet does not hold it.

## 3. Purposes of Processing

To create and verify your account; deliver and store message and media content; provide core messaging functions such as presence/last-seen; deliver push notifications; (only with your explicit consent) find Muhabbet users from your contacts; ensure service security and prevent abuse; perform content moderation and meet Law-5651 obligations; track errors and improve quality; and comply with legal obligations.

## 4. Legal Bases (KVKK Art. 5)

- **Performance of a contract (5/2-c):** verification, message/media delivery, notifications, core features.
- **Legal obligation (5/2-ç):** moderation and Law-5651 and other statutory obligations.
- **Legitimate interest (5/2-f):** security, abuse prevention, error tracking, quality.
- **Explicit consent (5/1):** contact-phone matching (opt-in) and, where needed, cross-border transfer — see [Explicit Consent Text](03-acik-riza-metni.md).

## 5. Method of Collection

Personal data is collected electronically, **from you** during registration and use (phone, profile, messages, media, location, reports) and **automatically** by operation of the app (device/connection/technical logs). Contact matching occurs only with your **explicit consent** via a device permission.

## 6. Transfers

- **Domestic:** to processors strictly necessary to provide the service: the SMS/OTP provider (**Netgsm** or **Twilio**, if enabled) and the notification provider (**Google Firebase Cloud Messaging**); and to competent public authorities where law requires.
- **Cross-border (KVKK Art. 9):** because hosting is on **Hetzner infrastructure in Germany (EU)**, data is **processed abroad.** This transfer relies on an appropriate Art. 9 mechanism (undertaking / standard contractual clauses, notification to the DPA, or explicit consent where applicable).
  > *(Honesty note — draft: before publication, **DPAs** and appropriate **transfer safeguards** must be completed with the providers used, and the hosting location stated correctly as "Germany / Hetzner." The previous draft's "GCP europe-west1" was false.)*

## 7. Retention

Data is retained for the period required by law or necessary for the purpose, after which it is erased, destroyed or anonymised. See [Privacy Policy §7](01-gizlilik-politikasi.md).

## 8. Your Rights (KVKK Art. 11)

You may learn whether your data is processed; request information; learn the purpose; know recipients; request rectification, erasure or destruction; request notification of such actions to third parties; object to outcomes of solely-automated analysis; and seek compensation.

**To apply:** contact [CONTACT E-MAIL] or via KEP ([KEP]). Requests are concluded within **30 days.** You may complain to the Turkish Data Protection Authority.

> **Honesty warning (draft):** erasure/destruction requests may not be fully satisfied until a **true (hard) delete** is implemented in the backend; this must be resolved before publication (see [README](README.md)).
