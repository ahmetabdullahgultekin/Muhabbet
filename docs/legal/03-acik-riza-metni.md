> ⚠️ **TASLAK** — yayımlanmadan önce Türk KVKK avukatı incelemesi gerekir; hukuki tavsiye değildir.
> ⚠️ **DRAFT** — requires review by a Turkish data-protection lawyer before publication; not legal advice.

---

# Muhabbet — Açık Rıza Metni

**(6698 sayılı KVKK m.3, m.5/1 ve m.9 kapsamında)**

**Sürüm:** v1.0-taslak

> **Önemli:** Açık rıza; **özgür iradeyle, belirli bir konuya ilişkin ve bilgilendirmeye dayalı** olarak verilir. Bu metindeki rızalar **ayrı ayrı (granüler)** olup hizmetin temel işlevlerini kullanmanızın **ön koşulu değildir.** Açık rıza gerektirmeyen işlemler (örn. sözleşmenin ifası, kanuni yükümlülük, meşru menfaat) için [Aydınlatma Metni](02-aydinlatma-metni.md)'ne bakınız — bunlar için ayrıca rıza istenmez. Aşağıdaki onayların **her birini reddedebilir** ve verdiğiniz rızayı **dilediğiniz zaman geri alabilirsiniz.**

Uygulamada bu rızalar, **önceden işaretlenmemiş (opt-in)** ayrı kutucuklar olarak sunulmalı; reddedilen rıza ilgili işlevin devre dışı kalmasıyla sonuçlanmalı, ancak temel mesajlaşma erişimini engellememelidir.

## Bilgilendirme

Bu rızaları vermeden önce [Aydınlatma Metni](02-aydinlatma-metni.md)'ni ve [Gizlilik Politikası](01-gizlilik-politikasi.md)'nı okuduğunuzu varsayıyoruz. Veri sorumlusu: **[VERİ SORUMLUSU ÜNVANI]** — İletişim: **[İLETİŞİM E-POSTA — ör. privacy@muhabbet.com.tr]**.

---

### Rıza 1 — Rehber Eşleştirmesi (Telefon-rehber senkronizasyonu)

> ☐ **Onaylıyorum:** Cihazımdaki rehberde kayıtlı telefon numaralarının **SHA-256 özetlerinin (hash)** Muhabbet sunucularına gönderilerek, **yalnızca** Muhabbet kullanan kişileri bulmak (otomatik keşif) amacıyla işlenmesine açık rıza veriyorum.

- **İşlenen veri:** Rehberdeki telefon numaralarının SHA-256 özetleri.
- **Amaç:** Tanıdıklarınızın Muhabbet'te olup olmadığını bulmak.
- **Niteliği:** **İsteğe bağlıdır (opt-in).** Bu rızayı vermezseniz mesajlaşma dâhil temel işlevleri kullanmaya devam edebilirsiniz; yalnızca rehbere göre otomatik keşif çalışmaz. Telefon numarası, Muhabbet için bir kimlik doğrulama zorunluluğu değildir.
- **Geri alma:** Bu rızayı uygulama ayarlarından dilediğiniz zaman geri alabilirsiniz; geri aldığınızda yeni eşleştirme yapılmaz.

### Rıza 2 — Verilerin Yurt Dışında İşlenmesi (KVKK m.9)

> ☐ **Onaylıyorum:** Kişisel verilerimin, sunucu barındırmanın **Almanya'daki (AB) Hetzner altyapısında** gerçekleşmesi nedeniyle ve OTP/bildirim sağlayıcıları (Netgsm/Twilio, Google FCM) aracılığıyla, **yurt dışında işlenmesine/aktarılmasına** açık rıza veriyorum.

- **Niteliği:** Bu rıza, yurt dışı aktarımın diğer uygun KVKK m.9 mekanizmalarıyla (taahhütname/standart sözleşme şartları, KVKK'ya bildirim) karşılanamadığı durumda **tamamlayıcı bir hukuki temel** olarak istenir.
  > *(Hukuki not — taslak: Tercih edilen yöntem, açık rızaya dayanmak yerine uygun **aktarım güvencelerini (DPA/SCC)** tesis etmektir. Avukat incelemesinde, bu rızanın gerekli olup olmadığı netleştirilmelidir.)*
- **Geri alma:** Dilediğiniz zaman geri alabilirsiniz; ancak hizmetin barındırıldığı altyapı gereği, geri alma hizmetin bu altyapıda sunulmasını etkileyebilir; bu durumda alternatif sunulamıyorsa hesabınızı kapatma hakkınız saklıdır.

### Rıza 3 — Ürün Geliştirme Amaçlı İsteğe Bağlı İletişim *(yalnızca böyle bir iletişim yapılacaksa)*

> ☐ **Onaylıyorum:** Muhabbet ile ilgili duyurular, anketler ve ürün geliştirme amaçlı bilgilendirmelerin tarafıma iletilmesine açık rıza veriyorum.

- **Niteliği:** Tamamen isteğe bağlıdır; reddetmeniz hizmeti kullanmanıza engel değildir.
- **Geri alma:** Her iletişimde bulunan "abonelikten çık" bağlantısı veya uygulama ayarları ile dilediğiniz zaman geri alabilirsiniz.
  > *(Not — taslak: Bu rıza yalnızca böyle bir iletişim fiilen yapılacaksa metinde tutulmalıdır; aksi hâlde kaldırılmalıdır — "ihtiyacın yoksa isteme" ilkesi.)*

---

## Rızanın Geri Alınması

Verdiğiniz açık rızaları **dilediğiniz zaman, gerekçe göstermeksizin** geri alabilirsiniz:
- Uygulama içi **Gizlilik / KVKK Paneli** üzerinden, veya
- **[İLETİŞİM E-POSTA — ör. privacy@muhabbet.com.tr]** adresine başvurarak.

Geri alma, geri alma anına kadar gerçekleştirilen hukuka uygun işlemleri etkilemez; ileriye dönük olarak ilgili işleme durdurulur.

> **Dürüstlük uyarısı (taslak):** Bu metnin geçerli biçimde işleyebilmesi için uygulamanın kayıt akışında **her bir rıza için ayrı, önceden işaretlenmemiş onay** alması ve **`consentVersion` + `consentTimestamp`** bilgilerini saklaması gerekir. Mevcut durumda bu onboarding onay kapısı **henüz uygulanmamıştır** (bkz. [README — Kod/operasyonel açıklar](README.md)). Rıza geri-alma akışının da uçtan uca çalıştığı doğrulanmalıdır.

---
---

> ⚠️ **DRAFT** — requires review by a Turkish data-protection lawyer before publication; not legal advice.

# Muhabbet — Explicit Consent Text (Açık Rıza Metni)

**(Under KVKK Art. 3, 5/1 and 9)**

**Version:** v1.0-draft

> **Important:** Explicit consent must be given **freely, on a specific matter, and informed.** The consents here are **granular (separate)** and are **not a precondition** for using the core features of the service. Processing that does **not** require consent (e.g. contract performance, legal obligation, legitimate interest) is described in the [Clarification Text](02-aydinlatma-metni.md) and is **not** bundled here. You may **decline each** consent and **withdraw** any consent **at any time.**

In-app, these consents must appear as **separate, un-pre-ticked (opt-in)** checkboxes; declining one disables the related feature but must not block core messaging access.

## Information

Before giving these consents, we assume you have read the [Clarification Text](02-aydinlatma-metni.md) and [Privacy Policy](01-gizlilik-politikasi.md). Data controller: **[DATA CONTROLLER LEGAL NAME]** — Contact: **[CONTACT E-MAIL — e.g. privacy@muhabbet.com.tr]**.

---

### Consent 1 — Contact Matching (phone-contact sync)

> ☐ **I consent:** to the **SHA-256 hashes** of phone numbers in my device address book being sent to Muhabbet's servers and processed **solely** to find which of my contacts already use Muhabbet (auto-discovery).

- **Data processed:** SHA-256 hashes of address-book phone numbers.
- **Purpose:** find which of your contacts are on Muhabbet.
- **Nature:** **Opt-in.** If you decline, you can still use core features including messaging; only contacts-based auto-discovery is disabled. A phone number is not an authentication requirement for Muhabbet.
- **Withdrawal:** withdraw any time in app settings; no new matching is performed thereafter.

### Consent 2 — Cross-Border Processing (KVKK Art. 9)

> ☐ **I consent:** to my personal data being **processed/transferred abroad**, given that hosting is on **Hetzner infrastructure in Germany (EU)** and via OTP/notification providers (Netgsm/Twilio, Google FCM).

- **Nature:** sought as a **complementary legal basis** where the transfer cannot be covered by other appropriate Art. 9 mechanisms (undertaking/SCC, DPA notification).
  > *(Legal note — draft: the preferred approach is to establish proper **transfer safeguards (DPA/SCC)** rather than rely on consent. Counsel should clarify whether this consent is needed at all.)*
- **Withdrawal:** any time; however, because the service is hosted on this infrastructure, withdrawal may affect provision of the service on that infrastructure; if no alternative can be offered, you retain the right to close your account.

### Consent 3 — Optional Product Communications *(keep only if such communication will actually occur)*

> ☐ **I consent:** to receiving announcements, surveys and product-improvement communications about Muhabbet.

- **Nature:** entirely optional; declining does not affect your use of the service.
- **Withdrawal:** any time via the unsubscribe link in each message or in app settings.
  > *(Note — draft: keep this consent only if such communication will actually happen; otherwise remove it — "don't ask for what you don't need.")*

---

## Withdrawing Consent

You may withdraw any explicit consent **at any time, without giving a reason**:
- via the in-app **Privacy / KVKK panel**, or
- by contacting **[CONTACT E-MAIL — e.g. privacy@muhabbet.com.tr]**.

Withdrawal does not affect lawful processing carried out before withdrawal; processing stops going forward.

> **Honesty warning (draft):** for this text to operate validly, the app's registration flow must collect **a separate, un-pre-ticked consent per item** and store **`consentVersion` + `consentTimestamp`**. This onboarding consent gate is **not yet implemented** (see [README — Code/operational gaps](README.md)). The consent-withdrawal flow must also be verified end-to-end.
