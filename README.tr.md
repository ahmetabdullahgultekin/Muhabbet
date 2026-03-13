# Muhabbet (Türkçe)

Türkiye odaklı mesajlaşma platformu. Gizlilik önceliklidir, KVKK uyumludur ve uçtan uca Kotlin ile geliştirilmiştir.

## Öne Çıkanlar

- WebSocket tabanlı gerçek zamanlı birebir ve grup mesajlaşma
- Gelişmiş sohbet özellikleri: yanıtlama/iletme, düzenleme/silme, tepkiler, yıldızlama, arama
- Medya altyapısı: fotoğraf/dosya/ses paylaşımı, önizleme ve sıkıştırma
- Durumlar (Stories), kanallar, anketler, konum paylaşımı
- OTP + JWT + cihaz yönetimi kimlik doğrulama akışı
- SQLDelight önbellek ve çevrimdışı kuyruk ile mobilde dayanıklılık
- Güvenlik sertleştirmesi: sanitizasyon, hız limiti, güvenlik başlıkları, CI güvenlik taramaları

## Teknoloji Yığını

| Katman | Teknoloji |
|---|---|
| Backend | Spring Boot 4, Kotlin 2.3.10, PostgreSQL 16, Redis 7, MinIO |
| Mobil | Compose Multiplatform, Ktor, Koin, Decompose, SQLDelight |
| Shared | Kotlin Multiplatform + `kotlinx.serialization` |
| Altyapı | Docker Compose, nginx, GitHub Actions |

## Proje Yapısı

```text
muhabbet/
├── backend/
├── shared/
├── mobile/
├── infra/
└── docs/
```

## Hızlı Başlangıç

### Gereksinimler

- JDK 21+
- Docker + Docker Compose
- Android SDK (mobil derleme için)

### 1) Altyapıyı başlatın

```bash
cd infra && docker compose up -d
```

### 2) Backend'i çalıştırın

```bash
# Linux/macOS
OTP_MOCK_ENABLED=true ./gradlew :backend:bootRun

# Windows (PowerShell)
$env:OTP_MOCK_ENABLED="true"; .\gradlew.bat :backend:bootRun
```

### 3) Sağlık kontrolü

```bash
curl http://localhost:8080/actuator/health
```

### 4) Testleri çalıştırın

```bash
./gradlew :backend:test
```

## Mimari Notlar

- Backend tarafı modüler monolit + Hexagonal Architecture yaklaşımını kullanır.
- Domain katmanı framework bağımsızdır.
- Controller'lar use case arayüzlerine (in-port) bağlıdır.
- Service'ler repository arayüzlerine (out-port) bağlıdır.
- Domain modelleri ile persistence entity'leri ayrıdır.

## Dokümantasyon

- API sözleşmesi: [`docs/api-contract.md`](docs/api-contract.md)
- Yol haritası: [`ROADMAP.md`](ROADMAP.md)
- Değişiklik geçmişi: [`CHANGELOG.md`](CHANGELOG.md)
- Gizlilik politikası: [`docs/privacy-policy.md`](docs/privacy-policy.md)
- QA dokümanları: [`docs/qa/`](docs/qa/)

## Katkı ve Güvenlik

- Katkı rehberi: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Güvenlik politikası: [`SECURITY.md`](SECURITY.md)

## Lisans

Özel depo — tüm hakları saklıdır.
