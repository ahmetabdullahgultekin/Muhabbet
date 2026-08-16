> ⚠️ **TASLAK** — yayımlanmadan önce Türk KVKK avukatı incelemesi gerekir; hukuki tavsiye değildir.
> ⚠️ **DRAFT** — requires review by a Turkish data-protection lawyer before publication; not legal advice.

---

# Muhabbet Çerez Politikası

**Yürürlük Tarihi:** [YÜRÜRLÜK TARİHİ]
**Sürüm:** v1.0-taslak
**Veri Sorumlusu:** [VERİ SORUMLUSU ÜNVANI]

Bu Çerez Politikası, Muhabbet'in **web sitesi/web istemcisi** üzerinde çerezlerin (ve yerel depolama gibi benzer teknolojilerin) nasıl kullanıldığını, KVKK ve Kişisel Verileri Koruma Kurumu'nun **Çerez Uygulamaları Rehberi** çerçevesinde açıklar.

> **Kapsam notu (taslak):** Muhabbet **mobil uygulaması** geleneksel anlamda tarayıcı çerezi kullanmaz; oturum yönetimi için cihazda güvenli yerel depolama (ör. Android EncryptedSharedPreferences, iOS Keychain) ve push için cihaz jetonu kullanılır — bunlar [Gizlilik Politikası](01-gizlilik-politikasi.md)'nda ele alınır. Bu Çerez Politikası öncelikle **web yüzeyleri** (ör. muhabbet.com.tr, yardım/indirme sayfaları, varsa web istemcisi) için geçerlidir.

## 1. Çerez Nedir?

Çerez, bir web sitesini ziyaret ettiğinizde tarayıcınıza/cihazınıza yerleştirilen küçük metin dosyalarıdır. Benzer teknolojiler arasında yerel depolama (localStorage), oturum depolama ve piksel/etiketler yer alır.

## 2. Kullanılan Çerez Kategorileri

| Kategori | Amaç | Rıza Gerekir mi? |
|---|---|---|
| **Zorunlu (Essential)** | Sitenin/oturumun çalışması, güvenlik (CSRF), dil/temadan oluşan tercihlerin hatırlanması, oturum yönetimi | **Hayır** — hizmet için zorunludur |
| **İşlevsel (Functional)** | İsteğe bağlı kullanıcı tercihleri (ör. gelişmiş arayüz tercihleri) | **Evet** |
| **Analitik (Analytics)** | Sayfa kullanımına ilişkin istatistik (*yalnızca böyle bir araç fiilen kullanılırsa*) | **Evet** |
| **Pazarlama (Marketing)** | Reklam/hedefleme | **Evet** — *(Muhabbet bunu kullanmamayı tercih eder; kullanılacaksa açıkça listelenmelidir.)* |

> **Dürüstlük notu (taslak):** Yukarıdaki **işlevsel/analitik/pazarlama** çerezleri yalnızca **fiilen kullanılacaksa** bu tabloda yer almalıdır. Web yüzeyi henüz yayında değilse veya yalnızca zorunlu çerezler kullanılıyorsa, gereksiz kategoriler kaldırılmalı ve banner buna göre sadeleştirilmelidir ("ihtiyacın yoksa isteme").

## 3. Çerez Listesi (örnek şablon — yayım öncesi gerçek değerlerle doldurulmalıdır)

| Çerez Adı | Sağlayıcı | Kategori | Amaç | Süre |
|---|---|---|---|---|
| `[ÇEREZ ADI]` | Muhabbet (birinci taraf) | Zorunlu | Oturum/güvenlik | Oturum |
| `[ÇEREZ ADI]` | Muhabbet (birinci taraf) | İşlevsel | Dil/tema tercihi | [SÜRE] |
| `[ANALİTİK ÇEREZ — varsa]` | [SAĞLAYICI] | Analitik | İstatistik | [SÜRE] |

> Bu liste **doldurulması zorunlu bir şablondur.** Gerçek çerezler envanterlenmeden banner yayımlanmamalıdır.

## 4. Rıza ve Tercih Yönetimi

- **Zorunlu** çerezler dışındaki tüm çerezler için, ilk ziyarette **rıza alınmadan önce yüklenmez** ("reddi varsayılan" / reject-by-default).
- Tercihlerinizi **çerez tercih panelinden** dilediğiniz zaman güncelleyebilir veya geri alabilirsiniz.
- Tarayıcı ayarlarından da çerezleri yöneteb/silebilirsiniz; zorunlu çerezleri engellemeniz hâlinde site bazı işlevleri çalışmayabilir.

## 5. İletişim

Çerezlerle ilgili sorularınız için: **[İLETİŞİM E-POSTA — ör. privacy@muhabbet.com.tr]**

---

## 6. Çerez Onay Banner'ı — Teknik/UX Şartnamesi

> Bu bölüm, geliştiriciler için **uygulama şartnamesidir** (kullanıcıya gösterilen metin değildir). KVKK Çerez Rehberi'ne uygun, **reddi varsayılan** bir banner tanımlar.

### 6.1 İlkeler
1. **Reject-by-default:** Sayfa ilk açıldığında **hiçbir** zorunlu-olmayan çerez/teknoloji yüklenmez. Analitik/işlevsel/pazarlama betikleri **yalnızca** ilgili kategoriye **açık onay** verildikten sonra yüklenir.
2. **Eşit belirginlik:** "Tümünü Kabul Et" ve "Tümünü Reddet" butonları **görsel olarak eşit** belirginlikte olmalıdır (renk/boyut/kontrast farkı ile reddi zorlaştırmak yasaktır — "dark pattern" yok).
3. **Granüler kontrol:** "Tercihleri Yönet" ile kategori bazında (işlevsel/analitik/pazarlama) **ayrı ayrı** açma/kapama. Zorunlu kategori daima açık ve değiştirilemez olarak gösterilir.
4. **Kolay geri alma:** Onay verildikten sonra her sayfada erişilebilir kalıcı bir "Çerez Tercihleri" bağlantısı/araç ile tercih değiştirilebilmelidir.
5. **Engel olmama:** Banner, içeriğe erişimi tümüyle kilitlememeli; ancak zorunlu-olmayan çerezler onaysız çalışmamalıdır.

### 6.2 Davranış (durum makinesi)
- **İlk ziyaret (kayıt yok):** Banner görünür. Yalnızca zorunlu çerezler aktif. Onay verilene kadar diğer kategoriler **kapalı.**
- **"Tümünü Reddet":** Tüm zorunlu-olmayan kategoriler `false` olarak kaydedilir; banner kapanır.
- **"Tümünü Kabul Et":** Tüm kategoriler `true`; banner kapanır.
- **"Tercihleri Kaydet" (granüler):** Seçilen kategoriler kaydedilir.
- **Tekrar ziyaret:** Kayıtlı tercihler uygulanır; banner gösterilmez (yalnızca kalıcı "Çerez Tercihleri" erişimi kalır).
- **Sürüm değişimi:** `consentVersion` arttığında banner yeniden gösterilir.

### 6.3 Saklanan onay kaydı (öneri)
Birinci-taraf, **zorunlu** kategoride bir tercih kaydı (ör. `mh_cookie_consent`) tutulur:
```json
{
  "version": "1",
  "timestamp": "2026-06-06T00:00:00Z",
  "categories": { "essential": true, "functional": false, "analytics": false, "marketing": false }
}
```
- Onay kanıtı (zaman damgası + sürüm) ispat için saklanmalıdır.
- Süre dolduğunda (ör. 6–12 ay) yeniden onay istenir.

### 6.4 Erişilebilirlik
- Klavye ile tam gezinilebilir; odak banner'a tuzaklanır (focus trap) ve butonlar erişilebilir etiketlere sahiptir.
- Yeterli renk kontrastı; ekran okuyucu için `role="dialog"` + `aria-label`.

---
---

> ⚠️ **DRAFT** — requires review by a Turkish data-protection lawyer before publication; not legal advice.

# Muhabbet Cookie Policy

**Effective Date:** [EFFECTIVE DATE]
**Version:** v1.0-draft
**Data Controller:** [DATA CONTROLLER LEGAL NAME]

This Cookie Policy explains how Muhabbet uses cookies (and similar technologies such as local storage) on its **website/web client**, under KVKK and the Turkish DPA's **Cookie Guidelines**.

> **Scope note (draft):** The Muhabbet **mobile app** does not use traditional browser cookies; it uses secure on-device storage for sessions (e.g. Android EncryptedSharedPreferences, iOS Keychain) and a device token for push — covered in the [Privacy Policy](01-gizlilik-politikasi.md). This Cookie Policy applies primarily to **web surfaces** (e.g. muhabbet.com.tr, help/download pages, and any web client).

## 1. What Is a Cookie?

A cookie is a small text file placed on your browser/device when you visit a website. Similar technologies include local storage, session storage and pixels/tags.

## 2. Cookie Categories

| Category | Purpose | Consent required? |
|---|---|---|
| **Essential** | Site/session operation, security (CSRF), remembering language/theme, session management | **No** — strictly necessary |
| **Functional** | Optional user preferences | **Yes** |
| **Analytics** | Usage statistics (*only if such a tool is actually used*) | **Yes** |
| **Marketing** | Advertising/targeting | **Yes** — *(Muhabbet prefers not to use these; if used, must be listed explicitly.)* |

> **Honesty note (draft):** The **functional/analytics/marketing** rows belong here only if such cookies are **actually used.** If the web surface is not live yet or only essential cookies are used, remove the unneeded categories and simplify the banner ("don't ask for what you don't need").

## 3. Cookie List (template — fill with real values before publication)

| Cookie | Provider | Category | Purpose | Duration |
|---|---|---|---|---|
| `[COOKIE NAME]` | Muhabbet (first-party) | Essential | Session/security | Session |
| `[COOKIE NAME]` | Muhabbet (first-party) | Functional | Language/theme preference | [DURATION] |
| `[ANALYTICS COOKIE — if any]` | [PROVIDER] | Analytics | Statistics | [DURATION] |

> This list is a **mandatory template.** Do not publish the banner until real cookies are inventoried.

## 4. Consent and Preference Management

- All non-**essential** cookies are **not loaded before consent** on first visit (**reject-by-default**).
- Update or withdraw your preferences any time via the **cookie preference panel**.
- You can also manage/delete cookies in your browser; blocking essential cookies may break some functions.

## 5. Contact

Cookie questions: **[CONTACT E-MAIL — e.g. privacy@muhabbet.com.tr]**

---

## 6. Cookie-Consent Banner — Technical/UX Specification

> This section is a **build spec for developers** (not user-facing text). It defines a KVKK-compliant, **reject-by-default** banner.

### 6.1 Principles
1. **Reject-by-default:** On first load, **no** non-essential cookies/technologies are loaded. Analytics/functional/marketing scripts load **only** after **explicit opt-in** for that category.
2. **Equal prominence:** "Accept All" and "Reject All" buttons must be **visually equal** (no dark pattern making "reject" harder via colour/size/contrast).
3. **Granular control:** "Manage preferences" allows per-category (functional/analytics/marketing) toggles. The essential category is shown always-on and non-editable.
4. **Easy withdrawal:** a persistent "Cookie preferences" link/tool, available on every page, lets users change preferences after consent.
5. **Non-blocking:** the banner must not fully lock content; but non-essential cookies must not run without consent.

### 6.2 Behaviour (state machine)
- **First visit (no record):** banner shown; only essential active; other categories **off** until consent.
- **"Reject All":** all non-essential categories saved as `false`; banner closes.
- **"Accept All":** all categories `true`; banner closes.
- **"Save preferences" (granular):** chosen categories saved.
- **Return visit:** stored preferences applied; banner hidden (only the persistent "Cookie preferences" entry remains).
- **Version change:** banner re-shown when `consentVersion` increments.

### 6.3 Stored consent record (suggested)
A first-party, **essential**-category record (e.g. `mh_cookie_consent`):
```json
{
  "version": "1",
  "timestamp": "2026-06-06T00:00:00Z",
  "categories": { "essential": true, "functional": false, "analytics": false, "marketing": false }
}
```
- Keep proof of consent (timestamp + version).
- Re-prompt after expiry (e.g. 6–12 months).

### 6.4 Accessibility
- Fully keyboard-navigable; focus trapped in the banner; buttons have accessible labels.
- Sufficient colour contrast; `role="dialog"` + `aria-label` for screen readers.
