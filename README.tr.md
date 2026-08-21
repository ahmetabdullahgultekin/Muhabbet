# Muhabbet

**Türkiye için bir mesajlaşma uygulaması — söylediği şey doğru olsun diye yazılmış.**

[![Backend CI](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/backend-ci.yml)
[![Mobile CI](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/mobile-ci.yml/badge.svg)](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/mobile-ci.yml)
[![Security & Quality](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/security.yml/badge.svg)](https://github.com/ahmetabdullahgultekin/Muhabbet/actions/workflows/security.yml)
[![Lisans: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

> 🇬🇧 **English:** [`README.md`](README.md)

---

## Neden var

Türkiye'de yaklaşık 85 milyon insan ve pratikte tek bir mesajlaşma uygulaması var. O uygulama yurt
dışında sahiplenilir, başka birinin gizlilik hukukuyla yönetilir ve buradaki hiç kimse verisine
nasıl davranıldığını değiştiremez. Muhabbet bunun yerli alternatifi: veri, işleteninin kontrol
ettiği altyapıda durur; KVKK sona iliştirilen hukuki bir ek değil, tasarımın girdisidir; Türkçe bir
çeviri katmanı değil, varsayılan dildir.

Ama alternatif olmak, ancak iddialar kodla temas ettiğinde ayakta kalıyorsa bir şey ifade eder.
Bu yüzden projenin, özellik listesinden üstün tek bir kuralı var:

> **Uygulama, kullanıcıya doğru olmayan bir şey söylemez.**

Kilit simgesi şifreleme demektir. Tik işareti iletildi demektir. Bir anahtar, ayarın sunucu
tarafında gerçekten uygulandığı anlamına gelir. Kodun henüz karşılayamadığı bir iddia **kaldırılır**
— yeniden biçimlendirilmez, "yakında" etiketiyle soluklaştırılmaz. Bu bir slogan değil; sürüm
süreciyle zorunlu kılınır. Sürüm numarası da aynı kurala uyar: **1.0.0, uçtan uca şifrelemeyi açık
şekilde gönderen ilk sürüme ayrılmıştır** ve başka hiçbir ilerleme onu erkene alamaz.

Aşağıdaki bölümün övücü olmamasının sebebi de bu. Deponun içindeki her şeyi çalışıyormuş gibi
sıralayan bir README, projenin sahip olduğu tek kuralı çiğnerdi.

## Gerçekte nerede

Sürüm **0.3.10**; Play iç test kanalı ve
[GitHub releases](https://github.com/ahmetabdullahgultekin/Muhabbet/releases) üzerinden dağıtılıyor.
Tek mühendis, hızlı iterasyon, tek haneli sayıda gerçek kullanıcı.

| Alan | Durum |
|---|---|
| Kimlik doğrulama — telefon OTP, JWT, cihaz yönetimi | Üretimde çalışıyor |
| WebSocket üzerinden birebir ve grup mesajlaşma | Üretimde çalışıyor |
| Medya — fotoğraf, belge, sesli mesaj, küçük resim | Üretimde çalışıyor |
| Durum bilgisi, yazıyor göstergesi, iletim tikleri, push | Üretimde çalışıyor |
| Sohbet özellikleri — yanıt, ilet, düzenle, tepki, yıldız, arama, anket, durumlar | Üretimde çalışıyor |
| **Uçtan uca şifreleme** | **Kapalı — hem de iki kez.** Mesajlar aktarımda TLS ile şifrelenir ve sunucu tarafından okunabilir. Bayraklar `false`, **ayrıca** her iki platformda da bağımlılık enjeksiyonu no-op bir şifreleyici bağlıyor; yani bayrakları açmak hiçbir şeyi şifrelemez. Signal uygulaması `.disabled` dosyalarında duruyor ve libsignal artık bir bağımlılık bile değil. 1.0.0'ın kapısı budur. |
| **Sesli / görüntülü arama** | **Çalışmıyor.** Sinyalleşme tipleri, bir servis ve bir LiveKit adaptörü var; istemci hiçbir zaman arama başlatmıyor ve LiveKit üretimde yapılandırılmamış. |
| **Topluluklar** | Kısmi. Oluşturulup okunabiliyor ve her birinin artık bir duyuru kanalı var; alıcının kabul edebileceği bir davet hâlâ yok. |
| **iOS** | Kısmi. Compose Multiplatform hedefi derleniyor, bazı platform modülleri taslak ve uygulama hiç TestFlight'a girmedi. |

Bilinen kusurlar, onları gönderen sürümün kendi bölümü dâhil olmak üzere
[`CHANGELOG.md`](CHANGELOG.md) içinde açıkça listelenir. Envanter
[issue tracker](https://github.com/ahmetabdullahgultekin/Muhabbet/issues)'dır.

> Yukarıdaki kırmızı bir CI rozeti genellikle daldan değil ortamdan kaynaklanır — runner üretim
> sunucusunun kendisi ve kendi action'larını indirirken hız sınırına takılıyor
> ([#419](https://github.com/ahmetabdullahgultekin/Muhabbet/issues/419)). Bir değişikliği suçlamadan
> önce `main` ile karşılaştırın.

## Nasıl kurulu

Her yerde Kotlin — sunucu, ortak sözleşmeler ve iki mobil platform için tek dil.

| Katman | Teknoloji |
|---|---|
| Backend | Spring Boot 4.1, Kotlin 2.4, PostgreSQL 16, Redis 7, MinIO |
| Shared | Kotlin Multiplatform + `kotlinx.serialization` |
| Mobil | Compose Multiplatform, Ktor, Koin, Decompose, SQLDelight |
| Altyapı | Docker Compose, Traefik, self-hosted runner üzerinde GitHub Actions |

```text
muhabbet/
├── backend/   # Spring Boot modüler monolit, modül başına hexagonal
├── shared/    # KMP modülü: domain modelleri, WS protokolü, DTO'lar, doğrulama
├── mobile/    # Compose Multiplatform istemci (composeApp + designsystem)
├── infra/     # Docker Compose, betikler, yük testleri
└── docs/      # Mimari, API sözleşmesi, ADR'ler, QA, hukuk
```

Backend, tek bir mühendis için mikroservisler yerine bilinçli olarak seçilmiş bir **modüler
monolit**tir ve her modülün içinde Ports & Adapters uygulanır. `shared/` modülü hem sunucuya hem
uygulamaya derlenir; böylece hat protokolü ikisi arasında ayrışamaz.

**Açıklaması [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) dosyasındadır** — modül sınırları, ortak
modülün iki tarafı nasıl beslediği, bir mesajın uçtan uca yolu ve bilinçli olarak **yapılmamış**
olanlar.

## Çalıştırmak

**Gereksinimler:** JDK 21, Docker + Docker Compose ve uygulamayı derleyecekseniz Android SDK.

```bash
# 1) Yerel altyapı — PostgreSQL, Redis, MinIO
cd infra && docker compose up -d

# 2) Backend — OTP kodları SMS yerine konsola yazılır
OTP_MOCK_ENABLED=true ./gradlew :backend:bootRun     # Windows: $env:OTP_MOCK_ENABLED="true"

# 3) Sağlık kontrolü
curl http://localhost:8080/actuator/health

# 4) Kapılar
./gradlew :backend:test :shared:jvmTest
./gradlew :mobile:composeApp:compileCommonMainKotlinMetadata   # ucuz mobil derleme kontrolü
```

Sizi yanıltacak iki şey var. `:backend:test` için **Docker ve Redis çalışıyor olmalı** — onlarsız on
adet Testcontainers sınıfı başlangıçta düşer ve sessizce hiç yürütülmez; yani yeşil görünen bir koşu
göründüğünden az şey kanıtlar. Bir de mobil uygulamanın base URL'i **üretime sabitlenmiştir**; yerel
bir backend'e yöneltmek `ApiClient.BASE_URL` üzerinde geçici bir düzenleme gerektirir. Bunlar ve
diğer bütün keskin kenarlar [`CLAUDE.md`](CLAUDE.md) içinde yazılıdır.

## Dokümantasyon

| Belge | Cevapladığı soru |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Bu sistem hangi şekilde ve neden |
| [`docs/api-contract.md`](docs/api-contract.md) | REST uçları ve WebSocket protokolü |
| [`ROADMAP.md`](ROADMAP.md) | Hangi sürümde ne çıkar ve buna nasıl karar verilir |
| [`CHANGELOG.md`](CHANGELOG.md) | Ne değişti ve ne hâlâ bozuk |
| [`docs/adr/`](docs/adr/) · [`docs/decisions.md`](docs/decisions.md) | Bir şeyin neden şöyle değil böyle yapıldığı |
| [`docs/design/muhabbet-design-system.md`](docs/design/muhabbet-design-system.md) | Görsel dil ve koruma bantları |
| [`docs/legal/`](docs/legal/) | KVKK belgeleri — gizlilik politikası, açık rıza, kullanım koşulları |
| [`docs/qa/`](docs/qa/) | ISO/IEC 25010 kalite dokümantasyonu |
| [`CLAUDE.md`](CLAUDE.md) | Ajanlar için çalışma talimatları — uzundur ve bir sistem anlatımı değildir |

## Katkı, güvenlik, lisans

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — kurulum, standartlar, dallanma
- [`SECURITY.md`](SECURITY.md) — güvenlik açıklarını herkese açık bir issue ile değil,
  security@rollingcatsoftware.com adresine bildirin
- MIT — bkz. [`LICENSE`](LICENSE)

---

## Ahmet Abdullah Gültekin'den daha fazlası

Kişisel portföy + yazılar: **[ahmetabdullah.gultek.in](https://ahmetabdullah.gultek.in)**
LinkedIn: **[ahmet-abdullah-gultekin](https://www.linkedin.com/in/ahmet-abdullah-gultekin)**
